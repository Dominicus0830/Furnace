# NMS 어댑터 N2b 설계 — 필드·배열 수준 청크 쓰기 (2026-11-14)

상태: **설계안(구현 전)**. 독립 검토 토론 뒤 확정한다. 결정이 필요한 것은 `docs/user-todo/entries/2026-11-14-nms-n2b-design.md`.

근거: 계획 `compat-nms-adapter-plan-2026-11-11.md` 4장 6번, 계획 검토(4.3 "필드·배열 수준은 보기로는 불가능"), N1+N2a 문서
`compat-nms-n1-n2a-2026-11-14.md`, 2차 CSP B6 검토(FAWE NMS 기호 664 중 없음 330), 도미니코 11-14 결정 "N2b 는 설계 먼저".
대조 자료: FAWE 2.16.2 소스(태그 커밋 `eef5a6ff5` = 도미니코 기동 로그의 `v2.16.2+eef5a6f` 와 같은 커밋) `adapter-1_21` 모듈,
바닐라 1.21.1 Mojang 이름 소스(`extremeheat/extracted_minecraft_data` `client1.21.1`, 커밋하지 않음).

원칙 두 가지를 그대로 따른다.
1. **바닐라(여기서는 같은 플러그인을 올린 Paper 1.21.1)와 관측 결과가 같아야 한다.** 내부 구조·스레드는 자유.
2. **특정 플러그인 저격 금지.** FAWE 는 검증 대상 중 하나다. 설계 단위는 "NMS 청크 객체의 필드·배열을 직접 만지는 방식" 전체다.

---

## 1. 무엇을 지원해야 하나 — 쓰기 방식 셋

플러그인이 청크 내부를 쓰는 방식은 세 층으로 나뉜다. N2a 는 첫째 층의 일부(`Level.setBlock`·`LevelChunk.setBlockState`)만 했다.

| 층 | 방식 | 대표 사용처 | 가로챌 수 있나 |
|---|---|---|---|
| **W1 메서드 쓰기** | `LevelChunkSection.setBlockState(x,y,z,state[,useLocks])`, 살아 있는 섹션의 `PalettedContainer.set/getAndSet/getAndSetUnchecked`, `Heightmap.setRawData/update`, `DataLayer.set`, `LevelLightEngine.checkBlock`, `ServerChunkCache.blockChanged` | 미니게임·건축 플러그인의 "빠른 블록 놓기" 도우미(섹션에 바로 쓰고 청크를 다시 보냄), WorldEdit, Denizen | 예 — shim 메서드 |
| **W2 객체 교체** | 플러그인이 `new LevelChunkSection(...)`·`new PalettedContainer(...)`·`Data` 레코드를 만들어 **`getSections()` 배열 칸에 대입**(FAWE 는 `VarHandle.compareAndSet`), 리플렉션으로 `PalettedContainer.data`·`LevelChunkSection.biomes`·`tickingBlockCount`·`tickingFluidCount` 를 대입, `chunk.heightmaps` 맵 직접 접근 | FAWE(쓰기의 본체), 맵 생성·청크 복원 도구 | 부분 — 아래 3.3 |
| **W3 원시 배열 쓰기** | `BitStorage.getRaw()` 의 `long[]`, `DataLayer.getData()` 의 `byte[]` 에 제자리 쓰기 | FAWE `removeSectionLighting`(빛 0 채우기), 일부 비트 조작 도구 | 아니오(배열 원소 쓰기는 소유 객체가 모른다) |

FAWE 2.16.2 `v1_21_R1` 의 실제 쓰기 순서(`PaperweightGetBlocks.internalCall`, 바뀐 청크 하나마다):
1. FAWE 작업 스레드에서 `nmsChunk.getSections()` 배열을 받고, 기존 섹션의 `tickingBlockCount`·`tickingFluidCount` 를 0 으로 대입(편집 중 무작위 틱 멈춤).
2. 같은 스레드에서 새 `PalettedContainer`(5인자 생성자: `IdMap, Strategy, Configuration, BitStorage, List`)·새 `LevelChunkSection(states, biomes)` 를 만들어
   `ReflectionUtils.compareAndSet(sections, expected, value, layer)`(= `MethodHandles.arrayElementVarHandle(Object[]).compareAndSet`)로 배열 칸을 바꾼다. 바이옴만 바꿀 때는 기존 섹션의 `biomes` 필드를 리플렉션으로 대입.
3. 블록 엔티티 지우기는 `nmsChunk.removeBlockEntity(pos)`(같은 스레드), 만들기·엔티티는 `TaskManager.sync`(주 스레드)로 넘긴다.
4. 하이트맵은 `chunk.heightmaps.get(type).setRawData(...)`, 빛은 relighter(Paper 판별이면 `ThreadedLevelLightEngine.starlight$serverRelightChunks`) 또는 `DataLayer` 직접 쓰기.
5. 끝으로 `setLightCorrect(true)`·`mustNotSave=false`·**`setUnsaved(true)`**, 그리고 `ChunkMap.getVisibleChunkIfPresent`(리플렉션) → `MinecraftServer.execute` 안에서 `new ClientboundLevelChunkWithLightPacket(...)` 을 `chunkMap.getPlayers` 에게 보낸다.
6. 읽기(`update()`)는 FAWE 스레드에서 `getStates()` → 리플렉션 `data`·`storage`·`palette` → `getRaw()`·`valueFor` 로 한다. Paper 판별이면 `ThreadingDetector` 잠금을 쓰지 않는다.

→ **모든 쓰기·읽기가 region 스레드가 아닌 플러그인 스레드에서 일어난다.** 이것이 N2b 의 본질적 충돌이다(절대 규칙 4: 살아 있는 `ChunkSection` 은 주인 region 스레드만 고친다).

## 2. 바닐라에서 이 쓰기들이 "하지 않는 일" — 후처리 책임표

바닐라 1.21.1 소스 대조(`LevelChunkSection.setBlockState`, `Heightmap.setRawData`, `ChunkHolder.broadcastChanges`). 배열 칸 대입·필드 대입은 그 자체로 아무 부수 효과가 없다.

| 파생 상태 | W1 섹션 `setBlockState` | W2 섹션 교체 | 바닐라에서 누가 맞추나 | **Furnace 책임** |
|---|---|---|---|---|
| 섹션 개수(nonEmpty·ticking 블록·ticking 유체) | 칸마다 ±1 | 새 섹션 생성자가 `recalcBlockCounts` | 섹션 자신 | 같게: W1 은 ±1(이미 틀린 값이면 틀린 채로), W2 는 다시 셈. 필드 대입은 값 그대로(음수 포함) |
| 무작위 틱 대상 | 개수에서 | 개수에서 | — | 같게(T-053 구역 마스크도 함께) |
| 하이트맵 4종 | **안 고침** | **안 고침** | 플러그인(`setRawData`) 또는 아무도 | **낡은 채로 둔다**(아래 3.5 고정 하이트맵) |
| 빛 | **안 고침** | **안 고침** | 플러그인(relight·`checkBlock`) 또는 아무도 | **낡은 채로 둔다**(결정 D2) |
| 클라이언트 | 안 보냄 | 안 보냄 | 플러그인(청크 패킷·`blockChanged`) | 안 보냄. 단 같은 틱에 이미 모인 칸 변경 목록은 바닐라처럼 **보낼 때의 현재 블록**을 읽는다 |
| 저장 표시(unsaved) | 안 함 | 안 함 | 플러그인 `setUnsaved(true)` | 안 함(결정 D3). Furnace 직렬화 캐시는 버린다 |
| POI | 안 고침 | 안 고침 | 아무도 | 안 고침 |
| 블록 엔티티 | 안 고침 | 안 고침 | 플러그인 | 안 고침. 블록과 안 맞는 블록 엔티티는 바닐라처럼 틱 때 "invalid state" 경고 한 번 뒤 건너뛴다(같은지 검증 항목) |
| 이웃·모양 갱신, 게임 이벤트, Bukkit 이벤트 | 없음 | 없음 | — | 없음 |
| **Furnace 전용 캐시**(직렬화 본문·스냅샷·하이트맵 long[] 캐시·지연 하이트맵·빛 구역 캐시·`lightFrozen` 공유·scratch·COW 공유 섹션·region 블록 캐시) | — | — | 해당 없음 | **반드시 버리거나 다시 만든다.** 이것을 놓치면 바닐라와 결과가 갈린다(위험 R1) |

요약: **바닐라에 있는 파생 상태는 바닐라처럼 플러그인 몫으로 두고, Furnace 에만 있는 캐시는 Furnace 가 책임진다.** 이 경계가 "결과가 같다"를 지키는 기준선이다.

## 3. 설계 — 거울(mirror)과 들여오기(import)

### 3.1 한 문장
플러그인에게는 바닐라 모양의 **거울 객체**(진짜 필드를 가진 `LevelChunk`·섹션 배열·`LevelChunkSection`·`PalettedContainer`·`Data`)를 주고,
플러그인 스레드는 거울만 만지게 하며, Furnace 가 **주인 region 스레드에서** 바뀐 거울을 서버 청크로 들여온다(바닐라가 하지 않는 일은 하지 않고).

### 3.2 객체 정체성 (N2a 와 달라지는 점)
- N2a 는 `getSections()` 를 부를 때마다 새 배열·새 보기를 만든다. W2 는 **같은 배열을 다시 받아 칸을 CAS** 하므로 정체성이 안정해야 한다.
- 로드된 청크 한 세대(로드 → 언로드)마다 `LevelChunk` shim 하나, 그 안에 `LevelChunkSection[]` 배열 하나, 칸마다 섹션 객체 하나. 섹션은 `states`·`biomes` 필드를, 컨테이너는 `data` 필드를 실제로 가진다.
- 보관은 약한 참조 캐시(청크 키 + 로드 세대). 플러그인이 모든 참조를 놓으면 GC 가 거둔다. 언로드 뒤 다시 로드하면 새 객체(바닐라도 새 `LevelChunk`).
- 섹션 객체는 두 상태를 가진다.
  - **붙음(attached)**: 서버 섹션의 보기. 메서드는 N2a 처럼 살아 있는 서버 데이터로 간다. 필드(`data` 등)를 처음 읽을 때 그 순간의 서버 섹션에서 바닐라 배치 `Data`(팔레트 + `SimpleBitStorage`)를 만들어 채운다(지연 실체화).
  - **떨어짐(detached)**: 플러그인이 `new` 로 만든 섹션, 또는 필드가 바뀐 섹션. 자기 데이터만 갖는다. 들여오기가 끝나면 다시 "붙음"이 되고(객체는 그대로 — FAWE 다음 편집의 `expected` 비교가 맞게), 실체화된 `Data` 는 들여온 값과 같다.

### 3.3 쓰기를 알아채는 길 — 세 겹
가로챌 수 있는 곳은 즉시 알리고, 못 가로채는 곳은 훑어서 잡는다. 셋째 겹이 있어서 **특정 플러그인의 호출 순서에 기대지 않는다**(계획 검토의 "맞춤 지점은 FAWE 순서에 기댄다" 위험을 없앤다).

1. **쓰기 장벽(로드 때 바이트코드 변환, NmsLinker 확장)** — 이미 플러그인 바이트코드의 `getDeclaredField`·`findGetter/findSetter` 를 `NmsReflect` 로 돌리고 있다. 같은 자리에서:
   - `java/lang/reflect/Field.get*/set*` 호출 → `NmsReflect.fieldGet/fieldSet`(대상이 shim 이면 shim 접근자, 아니면 원래 호출).
   - shim 클래스 필드에 대한 `GETFIELD/PUTFIELD`(Paper 가 공개로 넓힌 `heightmaps`·`level`·`loaded`·`blockEntities`·`mustNotSave`·`locX/locZ` 등) → shim 접근자 호출.
   - `findSetter/unreflectSetter/findVarHandle` → 알림을 다는 손잡이로 바꿔 준다.
   - `VarHandle.compareAndSet/set/setVolatile/getAndSet` 의 참조 배열 서술자 호출 → `NmsArrays.cas(...)`: 원래 연산 뒤, 그 배열이 등록된 거울 배열이면(정체성 표 조회) 알림. 타입이 `LevelChunkSection[]` 로 보이는 `AASTORE` 도 같은 길.
   - 비용: 바뀐 호출 자리에서만 정적 메서드 하나와 정체성 조회 하나. 일반 플러그인 코드의 배열 쓰기에는 손대지 않는다(서술자로 거른다).
2. **맞춤 지점(바닐라 의미가 있는 메서드)** — `setUnsaved`, 청크 패킷 생성자, `starlight$serverRelightChunks`·`checkBlock`, 주인 스레드·전역 빌림 구간에서 그 청크의 아무 shim 메서드에 들어올 때(읽기 전에 그 청크의 대기 중 거울을 먼저 들여온다 = 자기가 쓴 것을 읽는다), 저장·언로드·region 나누기/합치기 직전.
3. **훑기(안전망)** — 거울이 살아 있는 청크(대개 수십~수백)만 주인 region 이 틱마다 한 번, 섹션 배열 칸과 섹션의 `states`·`biomes`·컨테이너의 `data` 필드 정체성을 마지막으로 들여온 값과 비교한다(청크당 참조 비교 약 70개). Unsafe 오프셋 쓰기 등 1·2 를 빠져나간 객체 교체를 1틱 안에 잡는다.

W3(원시 배열 제자리 쓰기)는 잡지 않는다. 거울의 `long[]`·`byte[]` 는 사본이라 그 쓰기는 서버에 닿지 않는다 — **지원하지 않음으로 문서화**하고, 맞춤 지점에서 "실체화 때의 해시와 다르면 한 번 경고"만 한다(선택, 비용은 들여오기 때만).

### 3.4 들여오기 — 주인 region 스레드에서 한 섹션
`ChunkInternals` 에 번호 단위 다리 메서드를 더한다(모듈 경계는 N2a 결정 그대로: compat-paper 는 furnace-api 만 본다).

1. 검사: 비트 수·`long[]` 길이·팔레트 항목(null·범위 밖)·바이옴 번호. 틀리면 들여오지 않고 (플러그인, 사유)마다 한 번 WARN. 바닐라라면 나중에 터지거나 깨진 청크가 되는 입력이라 관측 차이를 감수한다(위험 R6).
2. **하이트맵 고정(3.5)** — 블록을 바꾸기 전에.
3. 섹션 내용 교체: 팔레트 값 → 상태 번호, `BitStorage` → 칸 인덱스 그대로 `ChunkSection.replaceBlocksIndexed`(이미 있음, 디스크 로드 경로) + 바이옴. `ownSection` 으로 COW·`lightFrozen` 공유를 끊는다. 개수는 W2 면 다시 세고, 필드로 들어온 개수 대입은 그 값을 그대로 둔다.
4. Furnace 전용 캐시 무효화: `ChunkColumn.touch()`(직렬화 본문·스냅샷·하이트맵 long[]) + 빛 엔진의 블록 파생 캐시 + region 블록 캐시(목록은 N2b-0 에서 확정, 위험 R1).
5. **하지 않는 것**: 저장 표시, 빛, 하이트맵 재계산, POI, 블록 엔티티, 패킷, 이웃 갱신, 이벤트(2장 표).
6. 거울 섹션을 "붙음"으로 되돌리고 들여온 판 번호를 적는다.

들여오기 작업은 청크 키로 `enqueueIfOwned` 에 넣는다(`BlockEdits` 처럼 주인이 바뀌면 그때의 주인에게 다시 보낸다). 같은 청크의 작업은 넣은 순서대로 돈다.

### 3.5 고정 하이트맵 — 낡은 값을 바닐라처럼 유지하기
Furnace 하이트맵은 일부가 **지연 계산**이다(`MOTION_BLOCKING`·`OCEAN_FLOOR` 는 칸 쓰기 때 "모름"으로 두고 읽을 때 지금 블록에서 계산, `MOTION_BLOCKING_NO_LEAVES` 는 늘 계산).
블록이 `setBlock` 으로만 바뀌는 세계에서는 이것이 바닐라의 증분 갱신(`Heightmap.update`)과 같다. 그러나 원시 쓰기 뒤에는 다르다 —
바닐라는 낡은 값을 들고 가고, Furnace 는 새 블록에서 "고쳐진" 값을 낸다(비 내림·눈·몹 스폰·`getHighestBlockYAt`·청크 패킷 하이트맵이 갈린다).

그래서 원시 쓰기(W1·W2)를 처음 받는 청크는 **고정 모드**로 바꾼다: 그 순간 네 종류를 모두 지금 값으로 실체화해 저장 배열에 두고,
그 뒤로는 바닐라 `Heightmap.update` 알고리즘(놓을 때 위면 올림, 꼭대기를 치우면 아래로 훑음)을 그대로 포팅한 경로로만 고친다.
`setRawData` 는 바닐라처럼 길이가 맞으면 그대로 복사, 다르면 경고 + 그 종류만 다시 계산. 고정 모드는 언로드까지(저장에는 그 값이 그대로 들어간다 — 바닐라와 같다).
비용은 원시 쓰기를 받은 청크에만 든다.

### 3.6 스레드별 규칙

| 부르는 스레드 | 읽기 | W1 메서드 쓰기 | W2 객체 교체 |
|---|---|---|---|
| 주인 region 스레드 / 전역 빌림 구간 | 살아 있는 값(먼저 대기 중 거울 들여오기) | 바로 서버에(바닐라 원시 의미) | 거울에 → 다음 맞춤 지점·훑기에서 들여옴(같은 스레드면 다음 shim 호출 전에) |
| region 이 아닌 플러그인 스레드(비동기 작업·FAWE 작업 스레드) | 주인에게 한 번 건너가 섹션 스냅샷을 받아 거울을 새로 고침(블로킹 대기 — 이 스레드는 region 을 쥐지 않아 교착 없음). 같은 판이면 다시 건너가지 않는다 | **결정 D1**: 거울에 쓰고 들여오기 대기열(추천) / 예외 | 거울에 → 대기열 |
| 다른 region 의 스레드 | N2a 와 같이 `ThreadOwnershipException`(남의 region 을 기다리면 교착) | 같음 | 거울에 쓰는 것은 막지 않되(객체 생성·대입은 순수), 들여오기는 주인 대기열 |

판 번호: 서버 섹션마다 쓰기 횟수를 세고(주인 전용), region 틱 끝에 "공개 판 번호"(volatile)로 내놓는다. 거울은 실체화 때의 판 번호를 기억해 다른 스레드 읽기가 새로 고칠지 정한다.
Paper 에서도 비동기 읽기는 경쟁 읽기라 "조금 낡은 값"은 관측 차이가 아니다.

### 3.7 들여오기 사이의 경쟁
배열 칸 대입과 들여오기 사이(최대 1 region 틱)에 플레이어가 그 섹션을 바꾸면 들여오기가 덮어쓴다(플러그인 섹션이 이긴다).
Paper 도 FAWE 가 섹션을 만든 뒤 CAS 하기 전의 주 스레드 쓰기는 잃는다 — 같은 성질의 경쟁이고 창의 크기만 다르다. 이 차이는 **가정으로 문서화**한다.
필요하면 후속으로 "대입 시각 뒤의 서버 쓰기 재적용"을 더할 수 있다(판 번호 비교, N2b-3 범위 밖).

### 3.8 빛·클라이언트·저장
- **빛**: `getLayerListener(...).getDataLayerData(SectionPos)` 는 Furnace 빛 배열의 사본 `DataLayer`, `DataLayer.set`·`queueSectionData` 는 주인 대기열로 빛 칸 쓰기.
  Paper 전용 `starlight$serverRelightChunks(coords, chunkCb, progressCb)` 와 `checkBlock` 은 주인에서 대기 거울을 들여온 뒤 Furnace 빛 엔진으로 그 청크들을 처음부터 다시 계산(`LevelLight.lightChunk` + 가장자리), 바뀐 빛은 바닐라 `sectionLightChanged` 처럼 저장 표시와 빛 갱신 패킷 대상에 넣는다. 다시 계산한 결과는 "그 블록의 올바른 빛"이라 Paper 의 Starlight 재계산 결과와 같아야 한다(검증 V4).
  플러그인이 다시 계산을 부르지 않으면 낡은 빛 그대로(결정 D2). 낡은 상태에서 이어지는 증분 전파는 바닐라 엔진과 Starlight 사이에서도 다르므로 칸 단위 일치를 약속하지 않는다(가정).
- **클라이언트**: `new ClientboundLevelChunkWithLightPacket(chunk, lightEngine, ...)` shim 은 "그 청크를 다시 보내라" 표지만 들고, `connection.send` 될 때 주인 region 에서 대기 거울을 먼저 들여온 뒤 그 플레이어에게 `Region.resendChunk` 와 같은 길로 보낸다(패킷 자체는 N3a 몫, 3.9).
- **저장**: `setUnsaved(true)` → `markDirty`. `setLightCorrect`·`mustNotSave` 는 받기만 하고 Furnace 저장 동작과 맞춘다(검증 항목). 저장·언로드 직전에는 대기 거울을 들여온 뒤 판단한다(플러그인이 저장 표시를 했으면 그 내용이 저장된다 — 바닐라와 같다).

### 3.9 다른 묶음에 기대는 것
- **N3a**: `ChunkMap.getVisibleChunkIfPresent`(리플렉션)·`ChunkHolder`·`chunkMap.getPlayers`·청크 패킷 보내기, `MinecraftServer.execute`.
- 티켓: `addRegionTicket`·`addTicketAtLevel`·`removeTicketAtLevel`·`TicketType.create` → Furnace 청크 티켓(플러그인당 한도 결정 11-08 그대로).
- 레지스트리·값: `Block.BLOCK_STATE_REGISTRY`(IdMapper), `Registry.asHolderIdMap`, `registryOrThrow(BIOME)`(PR #347 R10), Paper 표면(`moonrise$getEntityLookup`, `MCUtil.MAIN_EXECUTOR`, `ChunkHolderManager.UNLOAD_COOLDOWN`).
- FAWE 가 실제로 편집하려면 이 밖에 `SharedConstants`·`DataComponentPatch.CODEC` 등 어댑터 선택·초기화 기호가 먼저 풀려야 한다(기동 로그의 없음 330 대부분은 N2b 밖). **N2b 의 완료 기준은 "필드 수준 쓰기 계약"이고, FAWE 실제 편집은 별도 통합 단계(N2b-6)다.**

## 4. 대안 비교

| 안 | 내용 | 장점 | 단점 | 판단 |
|---|---|---|---|---|
| **A. 거울 + 들여오기(이 문서)** | 3장 | region 규칙 그대로, 플러그인 순서에 기대지 않음(세 겹), 바닐라 부수 효과를 정확히 흉내 낼 자리가 한 곳(들여오기) | 객체 정체성·변환 규칙 추가, 들여오기 창(≤1틱) 경쟁, W3 미지원 | **추천** |
| B. 서버 섹션을 바닐라 배치로 바꿔 그대로 노출 | Furnace `ChunkSection` 자체를 shim 이 넘겨줌 | 창 없음, 복사 없음 | 다른 스레드가 살아 있는 섹션을 고침 = 절대 규칙 4 위반·잠금 필요, scratch·COW·`lightFrozen` 최적화와 충돌, 서버 전반 재작성 | 기각 |
| C. 플러그인 스레드가 편집 동안 region 을 빌림 | `RegionBorrow` 를 비동기 스레드로 넓힘 | 쓰기 순서가 정확 | FAWE 의 무거운 계산 동안 region 틱 정지(MSPT), 여러 스레드·여러 region 교착 위험(지금도 전역만 빌린다), 바닐라 객체 → Furnace 변환은 어차피 필요 | 기각(전역 스레드는 기존 빌림 그대로) |
| D. 맞춤 지점만(`setUnsaved`·청크 보내기) | 계획 검토 원안 | 단순 | FAWE 호출 순서에 기댐 = 사실상 FAWE 맞춤, 저장 표시를 안 하는 플러그인은 영영 안 들어감 | A 의 한 겹으로 흡수 |
| E. FAWE 전용 어댑터를 Furnace 가 제공 | 결정 카드 A 안 | 빠름 | 11-11 도미니코가 B(범용)를 고름, 저격 금지 원칙 | 기각 |
| F. 지금처럼 등급 C(던짐) | — | 비용 없음 | FAWE·WorldEdit 계 블록 편집 불가 유지 | 기각 |

## 5. 바닐라와 같은지 검증하는 방법

| # | 무엇 | 어떻게 | 단계 |
|---|---|---|---|
| V1 | 자료 구조 의미(섹션·팔레트·`BitStorage`·`Heightmap` 생성자·`setBlockState` 개수·`setRawData`·바이옴 컨테이너) | **바닐라 jar 를 부트스트랩한 정답기**: 시험이 `main` 의 번들러 jar 에서 바닐라 `LevelChunkSection`(dvj)·`PalettedContainer`(dvq)·`Heightmap` 을 직접 만들어 같은 입력을 넣고, Furnace shim 결과와 칸·개수·`isRandomlyTicking`·`hasOnlyAir`·하이트맵 raw 를 대조(서버 없이 순수 자료 구조라 가능, E4c-2 지도 표시 값에 쓴 방법). 무작위 입력 수천 개 | N2b-1·2 |
| V2 | 부수 효과 없음 | Furnace 시험 서버에서 W1·W2 뒤 단언: 패킷 0, 빛 배열 그대로, 하이트맵 그대로(고정 모드), dirty 그대로, POI·블록 엔티티 그대로, 이웃 블록 그대로. 바닐라 소스에서 끌어낸 기대값 | N2b-2·3 |
| V3 | Furnace 전용 캐시 빠짐 없음 | 무효화 호출을 하나씩 지우는 **돌연변이 시험**(d11 의 mutation runner 방식) — 하나라도 지워도 시험이 실패해야 한다. 들여오기 뒤 청크 직렬화 바이트 = 같은 블록을 디스크에서 읽어 들인 청크의 직렬화 바이트 | N2b-0·3 |
| V4 | 다시 계산한 빛 | 들여오기 + relight 뒤의 빛 = 같은 블록 배치를 새로 로드해 `lightChunk` 한 결과(Furnace 빛 엔진은 이미 바닐라와 대조됨). 실제 Paper 와는 V6 | N2b-5 |
| V5 | 동시성 | 비동기 스레드 여럿이 거울을 쓰는 동안 region 틱·나누기·합치기·언로드·저장. 단언: 서버 쪽 소유 예외 0, 찢어진 들여오기 0, 대입 뒤 1틱 안 반영, 언로드된 거울 쓰기는 월드에 안 닿음 | N2b-3·4 |
| V6 | 실제 플러그인 결과 | 테스트 노트북: 같은 시드 월드에서 **Paper 1.21.1 + FAWE 2.16.2** 와 **Furnace + FAWE 2.16.2** 로 같은 명령(`//set`·`//replace`·`//copy`+`//paste`·`//undo`·`//regen` 제외, 작은 것과 64³)을 돌린 뒤 region 파일을 칸 단위로 비교(블록·바이옴·하이트맵·빛·블록 엔티티 NBT). 빠른 블록 놓기 표본 플러그인(섹션 `setBlockState` + `setUnsaved` + 청크 패킷)과 squaremap 읽기도 같은 방식. Paper jar 는 이 컨테이너에서 못 받으므로 도미니코 컴퓨터·노트북에서 | N2b-6 |

## 6. 단계별 구현 계획

각 단계는 PR 하나, 독립 검토 뒤 병합. 단계 사이에 이미 있는 기능은 깨지지 않는다(N2b 기호는 그 단계 전까지 계속 함정).

| 단계 | 내용 | 끝 기준 | 모델·노력치 제안 |
|---|---|---|---|
| **N2b-0 조사** | Furnace 의 블록 파생 캐시 전수 목록과 무효화 지점, 여러 플러그인(FAWE·WorldEdit·squaremap·Denizen + 빠른 블록 놓기 라이브러리 표본)의 필드·배열 수준 기호 표(`nms-source-survey.py` 확장), V1 정답기 뼈대 | 목록·표·정답기 시험 1개 | Sonnet high |
| **N2b-1 떨어진 객체** | 생성자들(`LevelChunkSection` 2종, `PalettedContainer` 공개·Paper 5인자, `Strategy`·`Configuration`, 팔레트 4종, `SimpleBitStorage`·`ZeroBitStorage`, `IdMapper`), 진짜 필드, 안정 정체성 캐시 | V1 통과, 서버 쓰기 없음 | Sonnet high |
| **N2b-2 W1 + 고정 하이트맵** | 다리 메서드(`setSectionStateRawNow`·`sectionCountsNow`/`setSectionCountsNow`·`setHeightmapRawNow`), 주인 스레드 원시 쓰기, 고정 하이트맵(바닐라 `Heightmap.update` 포팅), 개수 필드(음수·유체 개수 분리) | V1·V2 | Opus high |
| **N2b-3 거울·들여오기** | `importSectionNow`, 쓰기 장벽(NmsLinker: Field·VarHandle·GETFIELD/PUTFIELD), 맞춤 지점, 훑기, 저장·언로드·나누기 훅 | V2·V3·V5 | Opus xhigh(동시성) |
| **N2b-4 다른 스레드** | 비 region 스레드 읽기 스냅샷·판 번호, W1 대기열(D1 에 따라) | V5 | Opus high |
| **N2b-5 빛·클라이언트** | `DataLayer`·`LevelLightEngine` 표면, `starlight$serverRelightChunks`·`checkBlock`, 청크 패킷 shim(N3a 와 함께), `setLightCorrect`·`mustNotSave` | V4 | Opus high |
| **N2b-6 통합** | FAWE 실제 jar 로 없는 기호 재측정 → 나머지 표면(어댑터 선택·초기화 기호) 묶음, V6 Paper 대조 | V6 차이 0(또는 문서화된 가정만) | Opus high + 노트북 실측 |

N2b-0·1 은 N3a 와 파일이 겹치지 않아 병행할 수 있다. N2b-5 의 패킷 부분과 N2b-6 은 N3a 뒤.

## 7. 위험

| # | 위험 | 영향 | 줄이는 법 |
|---|---|---|---|
| R1 | Furnace 전용 블록 파생 캐시를 하나라도 빠뜨림 | 바닐라와 조용히 다른 결과(가장 위험) | N2b-0 전수 목록, 들여오기를 디스크 로드와 같은 무효화 한 곳으로 모음, V3 돌연변이 시험·직렬화 바이트 동치 |
| R2 | 들여오기 창(≤1틱) 동안 게임 쓰기를 덮어씀 | 드문 경쟁에서 블록 하나 차이 | Paper 도 같은 성질의 경쟁 — 가정으로 문서화, 필요 시 재적용(3.7) |
| R3 | W3 원시 배열 쓰기를 쓰는 플러그인 | 쓰기가 사라짐 | 지원 안 함 문서화 + 맞춤 지점 해시 경고, 사용처가 나오면 그 배열만 살아 있는 사본 고려 |
| R4 | 쓰기 장벽 바이트코드 변환의 부작용 | 검증기 오류·성능 | 서술자로 좁게 거름, NmsLinker 기존 시험 틀(스택 모양 유지), 비용 측정(바뀐 호출 자리만) |
| R5 | 거울 메모리(청크 하나 수십 KB) | 큰 FAWE 작업에서 힙 | 약한 참조·지연 실체화(필드를 읽은 섹션만), 언로드 때 버림, 작업 중 측정 |
| R6 | 잘못된 입력(팔레트 null·길이 틀림)을 거부 | 바닐라는 받아들여 나중에 깨짐 → 관측 차이 | 거부 + 경고, 문서화(세계를 깨는 쪽으로 같아질 이유는 없다고 봄 — 검토에서 토론) |
| R7 | 다른 스레드 읽기의 블로킹 건너가기 비용 | FAWE 처리량 | 청크 단위 한 번에 스냅샷, 판 번호로 재사용, 측정 뒤 필요하면 공개 스냅샷(불변) 도입 |
| R8 | 개수 필드 의미 차이(Furnace `tickingCount` 는 블록+유체 합, -1=모름) | 무작위 틱 대상 차이 | 블록·유체 개수 분리, "모름"은 별도 표시, 필드 대입 값은 음수 포함 그대로 |
| R9 | 낡은 빛·하이트맵이 이후 증분 갱신에서 바닐라와 다르게 진화 | 빛은 엔진이 달라 원래 약속 못 함 | 하이트맵은 고정 모드로 정확히, 빛은 가정으로 문서화(D2) |
| R10 | N3a·기타 기호가 늦으면 FAWE 실측이 늦음 | 완료 확인 지연 | N2b 완료 기준을 계약(V1~V5)으로, FAWE 는 N2b-6 통합으로 분리 |

## 8. 도미니코 결정 (요약, 상세는 user-todo 항목)
- **D1** region 이 아닌 스레드의 섹션 메서드 쓰기: A 거울 + 들여오기 대기열(Paper 처럼 동작, 추천) / B 예외(엄격).
- **D2** 플러그인이 빛을 다시 계산하지 않을 때: A 바닐라·Paper 처럼 낡은 빛 그대로(추천) / B Furnace 가 들여온 섹션의 빛을 자동 계산(끄는 키, 바닐라와 다름).
- **D3** 들여오기 뒤 저장 표시: A 바닐라처럼 플러그인이 `setUnsaved` 해야 저장(추천) / B 들여오면 자동 저장 표시(데이터 잃음 방지, 바닐라와 다름).
