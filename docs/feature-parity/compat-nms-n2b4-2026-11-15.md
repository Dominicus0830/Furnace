# NMS 어댑터 N2b-4 — 거울과 들여오기 (2026-11-15)

설계: `compat-nms-n2b-design-2026-11-14.md` 3.2~3.5·3.8·6장 N2b-4 줄. 앞 단계: `compat-nms-n2b1-2026-11-14.md`(PR #365), `compat-nms-n2b2-2026-11-14.md`(PR #373),
`compat-nms-n2b3-2026-11-14.md`(PR #382). 결정: 도미니코 11-14 D1~D4 모두 A. 이 단계에 걸리는 것은 **D1**(다른 스레드의 메서드 쓰기 = 칸 일지, 주인이 1틱 안에 재생),
**D2**(섹션 교체 때 빛 계산 안 함), **D3**(저장 표시 없이 들여온 섹션은 저장하지 않음 + 내려갈 때 경고 한 줄), **D4**(다른 region 스레드 = 비동기 스레드 규칙).

## 1. 무엇이 생겼나

플러그인이 NMS 청크 객체에 **아무 스레드에서나** 쓴 것이 바닐라·Paper 와 같은 결과로 서버 청크에 들어간다. 쓰는 길은 네 가지다.

| 쓰기 | 예 | 주인 region 스레드 | 그 밖의 스레드(FAWE 일꾼·비동기·다른 region) |
|---|---|---|---|
| W1 메서드 쓰기 | `LevelChunkSection#setBlockState`, 블록·바이옴 `PalettedContainer#set/getAndSet`, `LevelChunk#setBlockState`, `Heightmap#setRawData/update`, `setUnsaved` | 서버에 바로(N2b-2 그대로) | **칸 일지**에 쌓고 주인이 그 칸만 재생(보통 같은 틱, 늦어도 틱 끝). 쓴 스레드는 자기 쓰기를 곧바로 읽는다(덧칠). 섹션 개수 필드는 바닐라처럼 바로 ±1 |
| W2 배열·필드 대입 | `sections[i] = new LevelChunkSection(..)`(AASTORE·VarHandle CAS), `biomes` 필드 대입, `PalettedContainer.data` 대입·`read`, 개수 필드 `setShort` | 그 칸 읽기·`setUnsaved`·Bukkit 진입에서 맞춘다 | 틱 끝 **훑기**가 바뀐 구성 요소만 들여온다. 쓰기 장벽(아래)이 알리면 틱을 기다리지 않는다 |
| 읽기 | `getBlockState`, 섹션·컨테이너 읽기, `PalettedContainer.data` 리플렉션 읽기 | 살아 있는 값(그 칸이 맞춰지지 않았으면 먼저 맞춤) | 공개 스냅샷(N2b-3) + 자기 쓰기 덧칠. `data` 필드는 읽는 순간 바닐라 배치로 실체화 |
| W3 제자리 배열 | `BitStorage.getRaw()[k] = ..`, `Heightmap.getRawData()[k] = ..` | **미지원**(설계 3.3) — 사본이라 서버에 닿지 않는다 | 같음 |

들여오기는 바닐라에 없는 경로라 바닐라가 하지 않는 일은 하지 않는다: **저장 표시·빛·하늘빛 원천·하이트맵 재계산·POI·블록 엔티티·패킷·이웃 갱신 없음**(하이트맵은 고정 모드로 낡은 값을 든다 — N2b-2). 플러그인이 `setUnsaved(true)` 를 부르면 그 앞의 쓰기를 먼저 들여온 뒤 저장 표시를 세운다.

### FAWE 2.16.2 흐름(`PaperweightGetBlocks.call`)이 어떻게 지나가나
1. `clearCounts(existing)` — 개수 필드 셋 `setShort(0)`: 장벽 알림 → 주인이 개수만 들여온다(서버 섹션이 무작위 틱 대상에서 빠진다 — Paper 와 같은 의도).
2. `newChunkSection(.., existing.getBiomes())` — 기존 섹션 data 를 리플렉션으로 읽는다: `Field.get(data)` 앞 장벽이 붙은 보기의 `data` 를 그 순간 내용으로 실체화한다.
3. `setSectionAtomic` — worldedit-core `ReflectionUtils.compareAndSet`(Object[] VarHandle, "minecraft" 문자열 없는 클래스): 장벽(이름 힌트 게이트 밖)이 알림 → 주인이 새 섹션을 들여오고 **그 객체를 그 자리의 보기로 붙인다**(정체성 유지 — FAWE 의 다음 CAS 기대값). 빠진 옛 섹션은 그 순간 내용으로 굳혀 떼어 낸다.
4. `setBiomesToChunkSection` — `biomes` 필드 대입: 바이옴만 들여오고 대입한 컨테이너를 보기로 붙인다. 빠진 옛 컨테이너는 옛 내용으로 굳는다.
5. `setHeightmapToGet` — 살아 있는 하이트맵 `setRawData`: 일지(HM_RAW), 주인이 순서대로 재생.
6. `nmsChunk.setUnsaved(true)` — 일지(UNSAVED): 앞선 쓰기 재생·들여오기 뒤 저장 표시.

## 2. 구조

### 2.1 서버 쪽(`ChunkColumn`·`Region`·`World`)
- **단일 진입점 `ChunkColumn.importSection(index, states, biomes, counts)`**(설계 R9): null 인 구성 요소는 그대로. 블록·개수를 바꾸면 먼저 하이트맵 고정. `ownSection`(COW·빛 작업 얼림 끊기) → 블록 교체(개수를 안 주면 바닐라 필드 값 = 옛 블록의 개수 그대로 — 바닐라는 컨테이너 data 를 갈아도 다시 세지 않는다) → 바이옴 교체 → `touch()`. 저장 표시는 세우지 않고 `importedUnsaved` 만(D3).
- **대기 표시**: 컬럼에 `mirrorPending`(volatile, CAS 0→1 을 이긴 쪽만 주인 작업을 보낸다), `mirrorHeld`(대기 중에만 거울을 강하게 쥔다 — 설계 R14), 월드 전체 `World.mirrorPendingChunks`(0 이면 맞춤 지점 비용이 volatile 읽기 하나).
- shim 칸(`ShimRef`)이 shim 과 그 거울을 **약한 참조로 함께** 쥔다. 다른 로드의 거울이 부탁하면 거절(그 거울은 죽음 — 설계 N2b-1 검토 R3).
- **맞춤 지점**: ① 다른 스레드의 부탁 작업(`Region.syncMirrorTask`), ② Bukkit 진입(`WorldHandle.ownedRegion` → `World.syncMirrorIfPending`; 다리의 주인 읽기·쓰기도 이 길), ③ 틱 끝 공개 직전(`publishReadViews` — 거울 있는 청크는 대기가 없어도 훑는다 = 정식 경로), ④ 저장 전(`saveDirtyChunks` → `syncAllMirrors`), ⑤ 내리기 직전(`unloadMirror`).
- **D3 경고**: 내릴 때 맞춘 뒤 `importedUnsaved && !dirty` 면 `[NMS] 청크 x, z (차원) 가 저장 표시 없이 플러그인이 바꾼 섹션을 든 채 내려간다 — … LevelChunk#setUnsaved(true) …` 한 줄(서버 전체 처음 20건, 그 뒤 1000건마다). 저장하면(`clearDirty`) 표시가 내려간다. N2b-2 원시 쓰기(주인 스레드 섹션 쓰기)도 같은 표시를 세운다.
- 재생성(`inheritLoadToken`): 대기 표시·거울을 새 컬럼에 잇고 내용 판(`modCount`)을 옛 것보다 크게.
- 맞추기 예외는 틱을 깨지 않고 `[NMS] 거울 맞추기 실패` 경고(처음 5건은 쌓임까지, 그 뒤 100건마다).

### 2.2 다리(`ChunkInternals`, 불안정 내부 API) 새 메서드
`attachChunkShim(.., ChunkMirror)`, `requestMirrorSync`, `importSectionNow`(상태 번호·바이옴 번호·길이 검사 뒤 `importSection`), `contentStampNow`, `ChunkView.contentStamp()`, 인터페이스 `ChunkMirror { sync(); published(view); unloaded(); }`.

### 2.3 호환 쪽 — 청크 거울 `FurnaceChunkMirror`(shim 하나에 하나, 플러그인에 보이지 않음)
- **기준(placed)**: 자리마다 Furnace 가 마지막으로 놓은 섹션, 그 섹션의 `biomes` 값, 컨테이너의 `data`, 개수 필드 기대값(`expected`, 3×n).
- **sync()**(주인만 — 다른 스레드가 부르면 아무것도 안 함): 잠금 안에서 일지 떼어 내기와 훑기 계획을 한 순간에 → 일지 재생(바닐라 원시 의미) → 계획 실행. **재생이 훑기보다 먼저**: 배열에서 빠진 옛 섹션에 쓴 칸은 새 섹션이 덮는다(Paper 에서도 교체된 섹션에 쓴 것은 남지 않는다).
- **구성 요소 단위**(설계 R3): 배열 칸이 바뀌면 블록·바이옴·개수 모두, `data` 만 바뀌면 블록, `biomes` 필드면 바이옴, 바이옴 컨테이너 `data` 면 바이옴, 개수 필드만이면 개수만. 개수 들여오기는 일지 ±1 과 섞이지 않게 `expected += 들여온 값 - 계획 때 기대값`.
- **검사**: 팔레트 항목 null·서버 레지스트리에 없는 값·저장소 칸 수 틀림이면 그 섹션을 들여오지 않고 (거울, 원인)마다 한 번 `[NMS] 청크 x, z (차원) 거울: 섹션 i 의 내용을 들여오지 않는다: …` 경고(Configuration 은 보지 않는다 — 설계 R13a).
- **붙이기·떼어 내기**: 들여온 플러그인 섹션·컨테이너는 그 자리의 보기가 된다(공급원 `FurnaceSectionSource` — 칸 읽기·쓰기가 청크 shim 으로). 빠진 옛 것은 들여오기 **전에** 그 순간 내용으로 굳힌다(떨어진 객체 — 다시 넣으면 옛 내용이 들어간다).
- **개수 필드**: 붙은 섹션의 필드는 서버 바닐라 개수의 거울이다 — shim 을 만들 때 서버 값, 다른 스레드의 일지 쓰기마다 바닐라 ±1, 틱 끝 공개 뒤 필드가 기대값 그대로면(플러그인 대입 없음) 공개 판 값으로 CAS.
- **덧칠**: 다른 스레드의 칸·바이옴 쓰기를 그 스레드(와 다른 비주인 스레드)가 곧바로 읽게. 재생돼 공개된 순번까지 지운다.
- **`data` 실체화**: 붙은 보기의 `data` 는 평소 쓰이지 않는다(null). `Field.get` 장벽이 그 순간 내용으로 바닐라 배치 data 를 만들고(팔레트 크기 바꿈 처리기 = 그 컨테이너), 내용 판(주인: 컬럼 `modCount`, 다른 스레드: 공개 판의 `contentStamp`, 덧칠이 있으면 늘 다시)이 같으면 앞서 만든 것을 그대로 준다. 플러그인이 바꾼 `data` 는 건드리지 않는다(CAS).
- **잠금**: Furnace 전용 객체 하나(설계 R12 — 플러그인이 볼 수 있는 청크·섹션·컨테이너의 모니터는 쓰지 않는다). 잠금 안에서 기다리거나 다리로 다른 스레드를 부르지 않는다.
- **내린 청크**: 주인이 맞춘 뒤 거울을 죽이고 붙은 섹션을 그 순간 내용으로 굳혀 떼어 낸다 — 옛 shim 의 읽기는 그 내용, 쓰기·`setUnsaved`·하이트맵 쓰기는 월드(같은 좌표의 새 로드 포함)에 닿지 않는다(바닐라: 죽은 `LevelChunk`).

### 2.4 쓰기 장벽(`NmsClassTransformer`, 설계 3.3-3 — 지연을 줄이는 최적화, 정확성은 훑기가 보장)
- `java/lang/invoke/VarHandle` 의 배열 칸 쓰기(`compareAndSet`·`weakCompareAndSet*`·`compareAndExchange*`·`set*`·`getAndSet*`) 중 서술자가 `([Ljava/lang/Object;I..)` 인 것 → `NmsArrays.*`: **NMS 이름 힌트 게이트 밖**(모든 플러그인 클래스). 원소 형이 `net/minecraft/` 인 서술자도 Object 꼴로 바꿔 돌리고 반환 형은 `CHECKCAST`. 원래 연산 뒤 그 배열이 `LevelChunkSection[]` 이고 거울 배열이면 알림. 다른 원소 형(예: `String[]`)은 바꾸지 않는다(`withInvokeExactBehavior` 핸들 보호).
- 이름 힌트가 있는 클래스의 `Field.get` 앞(`NmsReflect.beforeFieldGet` — `PalettedContainer.data` 실체화)과 `Field.set`·`setShort`·`setInt`·`setByte`·`setChar`·`setBoolean`·`setFloat` 뒤(`NmsReflect.afterFieldSet` — 섹션·컨테이너면 알림). 원래 호출은 플러그인 메서드에 그대로 남긴다(호출자 민감성 유지).
- AASTORE·`Unsafe`·`MethodHandle` setter·`findVarHandle` 은 장벽 없음 — 틱 끝 훑기(주인 스레드면 그 칸 읽기·`setUnsaved`)가 잡는다.

## 3. 시험

- 서버 `ChunkColumnImportSectionTest`(8): 블록만 → 개수 그대로·바닐라 hasOnlyAir 참·Furnace 비공기 수 1·하이트맵 고정·저장 표시 없음·`importedUnsaved`·`modCount`·직렬화 캐시 버림, 블록+개수(음수 포함), 개수만 → 무작위 틱 마스크·저장할 변경 아님, 바이옴만 → 하이트맵 고정 안 함, COW 사본·빛 작업 얼림 보호, 공개 판(바뀐 섹션만 새 사본·내용 판 오름), 원시 쓰기의 `importedUnsaved`, 길이 검사.
- 서버 `ChunkFieldCensusTest`: 새 필드 분류(`mirrorPending`·`mirrorHeld`·`mirrorSyncing`·`importedUnsaved`, `MIRROR_PENDING`).
- 호환 `NmsMirrorTest`(11): 실제로 변환한 플러그인 클래스(FAWE 꼴 `ReflectionUtils` + `Adapter`)를 시험 서버에서 돌린다.
  다른 스레드 섹션 `setBlockState`(옛 상태·자기 쓰기 읽기·개수 필드 +1·**틱 없이** 재생·저장 표시 없음·되돌림), 다른 스레드 `LevelChunk#setBlockState`(하이트맵·저장 표시는 바닐라대로),
  FAWE 꼴 교체(개수 0 → 새 섹션 → Object[] CAS → 틱 없이 들여옴·정체성 유지·새 섹션이 보기·옛 섹션 굳음), NMS 원소 형 VarHandle `compareAndExchange`,
  `biomes` 필드 대입(들여옴·대입 컨테이너가 보기·옛 컨테이너 되돌리면 옛 내용), `data` 리플렉션 읽기(실체화·같은 판이면 같은 객체·바뀌면 새 객체),
  알림 없는 AASTORE(주인 작업 한 바퀴로는 안 들어오고 틱 끝에), 주인 스레드 AASTORE(그 칸 읽기·`setUnsaved` 맞춤 지점), 내린 거울(굳음·월드에 안 닿음·다른 스레드의 `sync`/`unloaded` 무시),
  **동시 쓰기**(스레드 4 × 256칸, 쓰는 사이 틱 4번 — 교착·예외 없음, 개수 필드 = 서버 개수), 변환기 바이트코드(장벽 호출 위치·개수, `normalize` 경계).
- 바꾼 기존 시험: `NmsOwnerRawWriteTest.offThreadRawWritesThrow` 삭제(다른 스레드 쓰기는 이제 예외가 아니라 일지 — `NmsMirrorTest`), `NmsChunkSymbolsTest` 주석(따로 만드는 하이트맵은 N2b-6).
- 안정성: `NmsMirrorTest`·`NmsOwnerRawWriteTest`·`NmsSnapshotReadTest`·`NmsChunkShimsTest` 를 5번 연달아 다시 돌려 모두 통과.

## 4. 남은 것·가정

1. **W3(제자리 배열 쓰기) 미지원** — 설계 3.3 그대로. Starlight `getDataLayerData` 가 사본이면 Paper 에서도 효과가 없다(가정, N2b-6 V6 에서 확인).
2. **블록 엔티티·빛 일지 없음** — shim 에 블록 엔티티 객체(`getBlockEntity`·`blockEntities` 맵)와 빛 표면(`DataLayer`·`LevelLightEngine`)이 아직 없어 쓸 길이 없다. 빛은 N2b-5, 블록 엔티티는 그 shim 이 생길 때 같은 일지에 넣는다.
3. **GETFIELD/PUTFIELD 장벽 없음** — 바닐라 1.21.1 의 해당 필드(`states`·`biomes`·개수·`data`·`sections`)는 private/protected 라 플러그인 바이트코드가 직접 닿지 못한다(리플렉션은 `Field` 장벽, 그 밖은 훑기).
4. **주인 스레드의 알림 없는 W2 뒤 같은 틱 Bukkit 읽기**: 장벽 없는 대입(AASTORE)은 대기 표시를 세우지 않아 Bukkit 진입이 맞추지 않는다 — 그 칸을 NMS 로 읽거나 `setUnsaved` 를 부르면 맞춰지고, 늦어도 틱 끝. FAWE 는 CAS(장벽)라 해당 없음.
5. **다른 스레드의 하이트맵 읽기는 덧칠 없음** — 자기 `setRawData` 를 재생·공개 전까지 공개 판으로 읽는다(`update` 돌려줌은 false). FAWE 는 하이트맵을 쓰기만 한다.
6. **알림 없는 대입을 한 거울이 틱 끝 전에 GC 되면**(플러그인이 shim·배열을 모두 놓음) 그 대입은 사라진다. 대기 표시(`mirrorHeld`)는 알림이 있을 때만 거울을 붙잡는다. 바닐라에서는 청크가 붙잡으므로 다르지만, 장벽 없는 대입 + 즉시 놓기 조합을 쓰는 플러그인은 찾지 못했다(가정).
7. N2b-3 검토 R3 의 "거두는 순간 μs 틈"은 그대로(2ms 다시 보기 완화). 슬롯 CAS 로 닫는 것은 필요해지면.
8. 개수 필드는 주인 스레드의 쓰기 뒤 틱 끝에 새로 고친다 — 같은 틱 안에 다른 스레드가 필드를 리플렉션으로 읽으면 한 틱 옛 값(Paper 의 경쟁 읽기와 같은 성질).
9. 따로 만드는 `Heightmap` 생성자·월드젠 하이트맵 쓰기는 여전히 덫(N2b-6 — FAWE 는 쓰지 않는다).
10. 대량 편집 비용(FAWE 64³)·Paper 대조는 N2b-6 노트북 실측(V6).
11. D3 경고는 청크를 내릴 때(`evictIdleChunks`)만 낸다. 서버를 끌 때는 거울을 맞춘 뒤(`saveDirtyChunks → syncAllMirrors`) 저장 표시가 있는 청크만 저장하고 경고는 없다(저장 결과는 바닐라와 같다).
