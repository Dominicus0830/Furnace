# NMS 어댑터 N2b-3 — 다른 스레드 읽기와 공개 스냅샷 (2026-11-14)

설계: `compat-nms-n2b-design-2026-11-14.md` 3.6·6장 N2b-3 줄. 앞 단계: `compat-nms-n2b1-2026-11-14.md`(PR #365), `compat-nms-n2b2-2026-11-14.md`(PR #373).
결정: 도미니코 11-14 D1~D4 모두 A — 이 단계에 걸리는 것은 **D4(다른 region 스레드 = 비동기 스레드와 같은 규칙)**.

## 1. 무엇이 생겼나

청크를 가진 region 스레드(주인)가 아닌 스레드 — 비동기 작업, FAWE 같은 플러그인 일꾼, **다른 region 의 스레드**(D4) — 가 NMS 청크 객체를 **기다리지 않고 읽는다**.
주인이 틱 끝에 그 청크의 불변 사본(공개 스냅샷)을 내고, 다른 스레드는 그 사본을 읽는다. 값은 최대 한 틱 옛 값이다(Paper 의 경쟁 읽기와 같은 성질).

| 부르는 스레드 | NMS 읽기(`getBlockState`·섹션·컨테이너·하이트맵·`getBlockEntitiesPos`) | 기다리지 않는 조회(`getChunkNow`·`getChunkAtIfLoadedImmediately`·`getChunkIfLoaded`) | 기다리는 조회(`getChunk(x,z)`·`getChunk(.., true)`·`CraftChunk.getHandle`) |
|---|---|---|---|
| 주인 region / 전역 빌림 구간 | 살아 있는 값(N2a 그대로) | shim(붙이면 그 자리에서 공개) | shim |
| region 이 아닌 스레드 | 공개 스냅샷 | 공개본 있으면 shim, 없으면 **null** + 주인에게 공개 부탁(다음 틱부터 보임) | 공개본 없으면 주인이 낼 때까지 기다림(바닐라 `ServerChunkCache.getChunk` 의 주 스레드 join 과 같은 간선) |
| 다른 region 의 스레드(D4) | 공개 스냅샷 | 같음 | 공개본 없으면 **기다리지 않고** `IllegalStateException`(create=false 는 null) — region 끼리 기다리면 교착 |

로드 안 된 청크는 그대로: 기다리지 않는 조회 null, 기다리는 조회 `IllegalStateException`(Furnace 는 동기로 불러오지 않는다). 쓰기는 여전히 주인만(다른 스레드 쓰기 일지는 N2b-4, D1).

불변식(설계 R16): **주인이 아닌 스레드가 받는 shim 은 반드시 공개본이 있다.** `NmsChunks.loadedChunk` 가 주인이 아니면 공개본이 있을 때만 shim 을 준다. 드물게 공개본을 붙이는 사이에 거둔 경우는 null(다음 틱부터).

## 2. 서버 쪽

### 2.1 공개 스냅샷 `ChunkReadView`(새 파일)
- 내용: 섹션마다 블록 팔레트 사본(`blocksReadCopy` — 팔레트 저장소 복제, scratch 가 열려 있으면 그 순간 값을 펼친 배열), 바이옴 사본, Furnace 비공기 수, 바닐라 개수 셋(N2b-2 의 nonEmpty·tickingBlock·tickingFluid), 하이트맵 네 종류(고정 모드면 고정 값), 블록 엔티티 칸 목록, 판 번호, 로드 표시.
- **판 재사용**: 컬럼 `modCount`·로드 표시·섹션 객체·섹션 판 번호가 모두 같으면 앞 판 그대로(할당 0). 바뀐 섹션만 새 사본, 나머지는 앞 판 사본을 공유한다.
- **섹션 판 번호** `ChunkSection.version`: 칸·바이옴 쓰기, 개수 대입, scratch 열기, 압축 때 오른다. 컬럼을 거치지 않는 쓰기(예: region 의 바이옴 쓰기)도 잡는다. scratch 가 열린 섹션 사본은 재사용하지 않는다(바깥이 배열을 고칠 수 있다).
- 하나의 읽기 메서드 안의 값은 한 판에서 나온다(`LevelChunkSection.write` 는 개수·블록·바이옴을 한 판에서, `Heightmap.getRawData` 는 256칸을 한 판에서).

### 2.2 누구에게 내나 — `Region.viewWatch`
- 대상: shim 이 붙은 청크(서버 컬럼이 shim 을 **약한 참조**로 쥔다), 최근 100틱 안에 다른 스레드가 읽었거나 공개를 부탁한 청크. 나머지 청크는 비용 0.
- `Region.runTick` 끝(`finally`)에 `publishReadViews()` — 바뀐 것이 없으면 판 재사용이라 비교만 한다. 한동안 아무도 안 읽고 shim 도 사라지면 판을 거두고 대상에서 뺀다.
- 거두는 순간과 다른 스레드의 shim 붙이기가 겹치는 경쟁: 거둔 뒤 shim·부탁을 다시 보고, 있으면 다시 낸다(volatile 순서상 둘 중 하나는 상대를 본다).
- 나누기·합치기(`splitData`·`absorbData`)는 대상 집합을 청크와 함께 옮긴다. 재생성(`replaceRegenerated`)은 로드 표시·shim·판 번호를 새 컬럼에 잇고 공개본이 있었으면 곧바로 다시 낸다(Paper `regenerateChunk` 도 같은 LevelChunk).
- 공개 부탁: 다른 스레드가 공개본 없는 청크를 읽으면 CAS 표시 하나로 주인 작업(`runAtChunk → publishReadViewNow`)을 한 번만 보낸다.

### 2.3 다리(`ChunkInternals`, 불안정 내부 API) 새 메서드
`vanillaStateIdNow`, `blockEntityPositionsNow`, `chunkView`(기다리지 않음), `awaitChunkView`(주인이면 바로, region 이 아니면 기다림, 다른 region 이면 예외), `chunkShim`·`attachChunkShim`, 공개본 인터페이스 `ChunkView`.

## 3. 호환 계층 쪽
- `ChunkAccess.furnaceForeignView`: 모든 `furnace*` 읽기의 출처를 고른다 — 주인이면 다리 "Now", 아니면 공개본. 공개본도 없으면(드묾) `ThreadOwnershipException` + 부탁.
- `NmsChunks.loadedChunk`: 전역 잠금 `CHUNK_LOCK` 과 `WeakHashMap` 을 없애고 서버 컬럼의 shim 칸(CAS, 먼저 붙은 쪽이 이김)을 쓴다 — PR #365 검토 R7. 기다리는 판 `awaitLoadedChunk`.
- `ServerChunkCache.getChunk` 는 기다리는 판, `getChunkNow`·`hasChunk`·`Level.getChunkIfLoaded` 는 기다리지 않는 판.
- `CraftChunk`(이전 `CompatChunk`, CraftBukkit 이름으로 옮김): `getHandle(ChunkStatus)`(기다리는 조회)·`getCraftWorld()`. FAWE 의 `getChunkAtAsync(..).thenApply(c -> ((CraftChunk) c).getHandle(FULL))` 가 된다.
- `getChunkAtAsync` 는 따로 공개하지 않는다: Furnace 도 Paper 처럼 주인 스레드에서 Future 를 끝내므로 그 이음에서 부른 `getHandle` 이 주인 스레드에서 shim 을 붙이고 **그 자리에서** 공개한다. 이미 끝난 Future 라 이음이 부른 스레드에서 돌면 기다리는 조회가 공개를 기다린다. 이렇게 해서 NMS 를 쓰지 않는 플러그인(Chunky 처럼 `getChunkAtAsync` 를 많이 부르는 것)에는 사본 비용이 없다. 설계 3.6-2 의 "그 직전에 스냅샷을 공개한다"를 이렇게 바꿨다.

## 4. PR #365·#373 검토 후속(같이 반영)

| 항목 | 반영 |
|---|---|
| #373 R6 MBNL 훑기 줄이기 | `ChunkColumn.mbnlLocalY`: MBNL 술어는 MOTION_BLOCKING 의 부분집합이라 MB 꼭대기(지연 캐시) 위에는 답이 없다 — MB 꼭대기가 잎이 아니면 그 칸, 잎이면 그 아래부터 훑는다. MB 캐시가 낡을 수 있는 길에 대비해 MB 꼭대기 칸이 정말 MB 인지 확인하고 아니면 처음부터 훑는다. 시험 `ChunkReadViewTest.motionBlockingNoLeavesMatchesFullScan` |
| #373 N3 보기 `getBlockState` 의 `int[3]` 할당 | 다리 `vanillaStateIdNow`(섹션 바닐라 nonEmpty == 0 이면 공기)로 배열 없이 한 번에. 공개본도 같은 판정(`vanillaStateId`) |
| #373 5장 2 청크 패킷 섹션 개수 | 바닐라 `LevelChunkSection.write` 처럼 섹션의 바닐라 nonEmpty 를 보낸다(`ChunkSection.packetNonEmptyCount` — 섹션을 바꾸지 않는다). 읽은 섹션은 물 칸이 두 번 세어지고 칸 쓰기는 공기 여부로만 ±1(바닐라 그대로). 다만 바닐라 값이 0 인데 블록이 있으면(hasOnlyAir 틈) Furnace 비공기 수 — 결정 `entries/2026-11-14-n2b2-hasonlyair-gap.md` 가 A 인 동안 서버 읽기와 클라이언트가 다른 블록을 보지 않게. 시험 `packetSectionCountIsVanillaNonEmpty` |
| #365 R7 `CHUNK_LOCK` | 위 3장 — 잠금 없음 |

## 5. 시험

- 서버 `ChunkReadViewTest`(7): 공개본 = 살아 있는 값(칸·개수·하이트맵 네 종류), 안 바뀌면 같은 판, 바뀐 섹션만 새 사본·판 번호 오름·낸 판은 불변, 개수 대입도 판 올림, scratch 뒤 쓰기가 새지 않음, MBNL(R6), 패킷 nonEmpty.
- 호환 `NmsSnapshotReadTest`(8, V5 읽기·교착 장면): 기다리지 않는 조회는 공개 전 null → 다음 틱 shim, 비 region 스레드 기다리는 조회, 주인 쓰기가 틱 뒤 보이고 판이 오름·안 바뀌면 같은 판, 블록 엔티티 칸, **다른 region 스레드(D4)**(공개본 읽기·기다리는 조회는 예외·부탁이 다음 틱에 보임), **플러그인 잠금 교착 장면**(잠금을 쥔 비동기 스레드가 읽는 동안 region 이 같은 잠금을 기다림 — 끝남), **동시 읽기 일관성**(읽는 스레드 셋 × 30틱: 한 판 안의 두 섹션 칸이 늘 같음·판 번호 안 줄어듦), `getChunkAtAsync` + `CraftChunk.getHandle`.
- 변이 확인: 섹션 재사용 판정에서 판 번호 비교를 빼면 `ownerWritesBecomeVisibleAfterTickWithNewVersion`·`concurrentReadersSeeConsistentVersions` 가 실패한다.
- 바꾼 기존 시험: `NmsChunkShimsTest.unloadedAndOffThreadBehaviour`(다른 스레드 읽기가 예외 → 공개본 값), `NmsOwnerRawWriteTest.offThreadRawWritesThrow`(shim 을 기다리는 조회로 얻음), `ChunkFieldCensusTest`(새 필드 분류: `readView`·섹션 `version` = FURNACE_CACHE — N2b-4 들여오기는 `touch()`·섹션 판 올림을 빠뜨리면 안 된다).

## 6. 남은 것·가정

1. **Bukkit 비동기 읽기는 이 단계 밖**: `Block.getType` 같은 Bukkit 읽기를 다른 스레드에서 부르면 지금처럼 `ThreadOwnershipException`. 설계 3.7 의 "다른 스레드 Bukkit 읽기 = 스냅샷"은 Bukkit 어댑터 쪽 일(필요해지면 같은 공개본을 쓰면 된다).
2. **빛**은 공개본에 없다(N2b-5).
3. 언로드 뒤 옛 shim 의 읽기는 같은 좌표의 새 로드를 볼 수 있다(N2a 부터 같은 성질 — shim 에 로드 표시를 묶는 것은 N2b-4 상태 기계에서).
4. 공개 비용은 shim 이 붙었거나 최근 다른 스레드가 읽은 청크에만 든다. 판이 바뀐 틱에만 바뀐 섹션의 팔레트 복제(섹션당 저장소 long 배열 하나와 팔레트)를 한다. 대량 편집 실측(FAWE 64³)은 N2b-6 노트북에서.
5. `getChunkAtAsync` 이음이 이미 끝난 Future 에 붙으면 부른 스레드에서 돈다(Paper 도 같다). 그때 `getHandle` 은 주인을 기다린다 — 그 스레드가 주인이 원하는 잠금을 쥐고 있으면 Paper 의 주 스레드 join 과 같은 위험(새 위험 아님).
