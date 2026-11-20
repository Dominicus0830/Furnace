# T-211 FAWE 서버 월드 면 설계 (2026-11-17)

로드맵 T-211: NMS shim 점검(`compat-nms-shim-audit-2026-11-17.md` 3장 A)의 첫 묶음. FAWE 의 `//forest`·`//feature`·`//structure` 와
그 둘레의 `ServerLevel`·`Level` 멤버를 Paper 와 같은 결과로 만든다. 특정 플러그인 맞춤이 아니라 Paper 레벨의 일반 규칙(나무 생성 기록,
`BlockStateListPopulator`)을 그대로 옮긴다.

근거: Paper 1.21.1 빌드 133 서버 jar(공유 폴더의 paperclip 에서 `piston-data` 바닐라 jar 에 패치를 적용해 만든 것 — sha256 이 paperclip 의
기대값 `020b6831…` 와 같다)를 읽기 전용으로 풀어 읽었다. 풀어 낸 소스는 커밋하지 않는다. FAWE 는 2.16.2 소스(`raw.githubusercontent`).

## 1. FAWE 2.16.2 가 하는 일

`PaperweightFaweAdapter#generateTree/generateFeature/generateStructure`(모두 `TaskManager.sync` — Furnace 에서는 전역 스레드의 Bukkit 동기 작업):

```
FaweBlockStateListPopulator populator = new FaweBlockStateListPopulator(serverLevel);   // CraftBukkit BlockStateListPopulator 상속
serverLevel.captureTreeGeneration = true; serverLevel.captureBlockStates = true;      // preCaptureStates
placedFeature.place(populator, generator, serverLevel.random, pos)                     // 나무: PLACED_FEATURE, //feature: CONFIGURED_FEATURE
placed = populator.getList() + serverLevel.capturedBlockStates.values()                // CraftBlockState 목록
finally: 두 표시 false, capturedBlockStates.clear()                                    // postCaptureBlockStates
각 상태: editSession.setBlock(getPosition(), getBlockData()); populator.getBlockEntity(pos) 가 있으면 saveWithId 를 타일로
```

- `TreeType` 목록 = 밑 feature 가 `TreeFeature`·`CoralTreeFeature` 인 placed feature(이미 N2b-6 에서 레지스트리 shim 이 답한다).
- `FaweBlockStateListPopulator` 는 `setBlock(3)`·`setBlock(4)`·`getBlockState`·`removeBlock`·`destroyBlock` 등을 **원래 레벨로 넘긴다**.
  그래서 populator 자신의 목록은 비고, 쓰기는 모두 레벨의 나무 생성 기록(`capturedBlockStates`)에 쌓인다.

## 2. Paper 1.21.1 의 규칙 (jar 에서 읽음)

`Level`:
- 필드 `captureBlockStates`, `captureTreeGeneration`(boolean), `capturedBlockStates`(`LinkedHashMap<BlockPos, CraftBlockState>`), `capturedTileEntities`(`LinkedHashMap<BlockPos, BlockEntity>`).
- `setBlock(pos, state, flags, depth)`: `captureTreeGeneration` 이면 ① 지금 상태(`getBlockState` — 기록 우선)가 부술 수 없는 블록(Paper
  `Block#isDestroyable`: 기반암·끝 차원문 틀·끝 차원문·끝 관문·명령 블록 3종·방벽·구조물 블록·직소)이면 **false**, ② 아니면 그 칸 기록이 없을 때
  `CapturedBlockState.getTreeBlockState(this, pos, flags)` 를 만들어 넣고(처음 쓴 차례) `setData(state)`·`setFlag(flags)` 뒤 true. 월드는 안 바뀐다.
  `captureBlockStates` 만 켜져 있으면 옛 상태를 기록하고 실제로 쓰되 이웃 갱신(`notifyAndUpdatePhysics`)을 하지 않는다.
- `getBlockState`·`getBlockStateIfLoaded`: `captureTreeGeneration` 이면 기록이 먼저. **`getFluidState`·`getHeight`·`getBlockEntity` 는 기록을 보지 않는다**
  (`getBlockEntity` 는 `capturedTileEntities` → 실제 청크의 IMMEDIATE).

`BlockStateListPopulator extends DummyGeneratorAccess implements WorldGenLevel`:
- 자기 표(`dataMap`·`entityMap`·`list`)가 먼저, 없으면 감싼 레벨. `setBlock(3)` 은 표에 넣고 새 블록 엔티티(`newBlockEntity`)를 만든다.
- 유체·하이트맵·빛·차원·레지스트리는 감싼 레벨. `DummyGeneratorAccess` 는 예약 틱·레벨 이벤트·게임 이벤트를 버리고, 나머지 대부분은
  `UnsupportedOperationException("Not supported yet.")`, `setBlock(4)`·`destroyBlock` 은 false.

결과적으로 FAWE 가 Paper 에서 보는 것(감싼 레벨이 기록 중인 서버 레벨일 때):

| 읽기·쓰기 | Paper 의 답 |
|---|---|
| 블록 상태 | 월드 + 이번 배치가 쓴 것(기록) |
| 블록 쓰기 | 기록에 쌓임. 부술 수 없는 블록 칸은 거절(false) |
| 유체 | **실제 월드**(이번 배치가 쓴 것을 모른다) |
| 하이트맵 | **실제 월드** |
| 블록 엔티티 | **실제 월드**(보통 없음) — 그래서 벌집 벌·상자 전리품·스포너 몹 칸은 `instanceof` 가 실패해 **난수도 쓰지 않는다** |
| 예약 틱·엔티티 추가 | 버려짐 |

## 3. Furnace 설계

### 3.1 서버 쪽 — 지형 생성 코드를 "레벨을 통한 쓰기"로 돌리기

이미 있는 `LiveFeatures`(묘목·뼛가루·`World#generateTree`)의 사본 격자를 쓴다: 그 칸 주인 region 스레드(전역 스레드는 region 빌림 구간)에서
실제 청크를 처음 닿을 때 복사하고, 월드젠 코드(`FeatureWorldView`)를 사본에 돌린다. 새로 더하는 **쓰기 통로(sink) 모드**:

- 사본에 쓰기 전에 `sink.setBlock(x, y, z, 상태, flags)` 를 부른다. 거짓이면 사본에도 쓰지 않는다(Paper 의 거절이 다음 읽기에 그대로 보인다).
  쓰는 차례·같은 칸 다시 쓰기·flags 모두 지형 생성 코드 그대로다.
- 블록 상태 읽기 = 사본(월드 + 받아들인 쓰기). 하이트맵 읽기 = **실제 청크**(사본 말고). 블록 엔티티 있음 = `sink.blockEntityType(x, y, z)` —
  지형 생성 코드가 바닐라 `getBlockEntity … instanceof` 자리에서 묻는다(벌집 장식, 보너스 상자, 던전 상자·스포너). 기대한 종류면 그 블록 엔티티
  내용을 바꿔야 하는데 플러그인 레벨의 블록 엔티티 객체를 바꾸는 길은 아직 없다 → `UnsupportedOperationException`(호환 계층이
  `UnsupportedNmsCall` 로). 아니면 바닐라처럼 그 단계를 건너뛰고 **난수를 쓰지 않는다**. 통로가 없는 기존 길(월드젠·`/place`·묘목)은 지금처럼 늘 있음.
- 나무 가장자리 모양 갱신(`StructureTemplate.updateShapeAtEdge(level, 3, …)`)은 각 나무 feature 끝에서 바로, 실제 블록 동작의 `updateShape` 로
  돈다. 그 레벨은 사본 읽기 + 실제 월드 유체 + 예약 틱 버림(`DummyGeneratorAccess`) + 쓰기는 통로(flag 2)다.
- 생성 NBT(상자 전리품 표 등)·엔티티·유체 예약 틱은 통로 모드에서 옮기지 않는다(Paper 와 같이 버려진다).
- 범위: 원점 청크 ±2(처음 닿을 때만 복사하므로 비용은 닿은 청크만큼). 로드 안 된 청크·다른 region 청크·범위 밖에 닿으면 Paper 는 불러와 계속하지만
  Furnace 는 하지 않는다 → 결과를 꾸미지 않고 오류로 끝낸다(지금 `World#generateTree` 는 false — 그 길은 그대로 둔다).
- 다리: `Internals#features()` → 새 `FeatureInternals`(불안정, 호환 계층용).

### 3.2 호환 쪽

- `Level`: Paper 필드 넷과 2장의 `setBlock`·`getBlockState`·`getBlockStateIfLoaded` 규칙. 기록 상태는 새 `org.bukkit.craftbukkit.block.CraftBlockState`
  (Paper 형 — Furnace 의 블록 상태 스냅숏 `CompatBlockState` 의 상위 클래스로 끼운다)·`CapturedBlockState`. `captureBlockStates` 만 켠 쓰기(실제로 쓰되
  이웃 갱신 없음)는 서버에 그 길이 없어 `UnsupportedNmsCall`.
  - 스레드: 표시는 월드마다 하나인 Paper 필드라 누가 켰는지 알 수 없다. Furnace 는 region 스레드가 여럿이므로 **기록은 전역 스레드이거나 이 shim 의
    지형 배치(3.1 통로) 안에서 부른 쓰기에만** 적용한다 — 다른 region 스레드의 플러그인이 같은 틈에 NMS `setBlock` 을 불러도 기록에 빨려 들지 않는다.
- `WorldGenLevel`(+`ServerLevelAccessor` 자리), `org.bukkit.craftbukkit.util.DummyGeneratorAccess`·`BlockStateListPopulator`(Paper 그대로).
- `ConfiguredFeature#place`·`PlacedFeature#place`(`placeWithBiomeCheck` 는 없음 — 플러그인이 부를 일이 없다):
  - 레벨이 서버 레벨 자신이고 기록 중이 아니면 바닐라 런타임 배치(`/place feature` 와 같은 길: 사본에 돌리고 flag 대로 실제 월드에 반영, 블록 엔티티 포함).
  - 그 밖(populator, 기록 중인 서버 레벨)은 3.1 통로 모드: 통로 = 그 레벨 객체의 `setBlock(pos, state, flags)`, 블록 엔티티 = 그 객체의 `getBlockEntity(pos)`.
    그래서 FAWE populator 는 Paper 처럼 기록에, 평범한 populator 는 자기 표에 쌓인다. 레벨의 `getLevel()` 이 Furnace 서버 레벨이 아니면 `UnsupportedNmsCall`.
  - 생성기 인자는 그 레벨 월드의 생성기여야 한다(다르면 `UnsupportedNmsCall`). 난수는 받은 `RandomSource` 를 그대로 쓴다(`java.util.Random` 어댑터로 서버
    지형 생성 난수에 넘겨 뽑을 때마다 그 원천에서 뽑는다 — Paper 와 같은 수열, 원천 상태도 같이 나아간다).
  - 구현 안 된 feature(Noop)가 하위에 있으면 `UnsupportedNmsCall`(규칙 3 — 다른 것으로 바꿔 놓지 않는다). 바이옴 거르기가 든 placed feature 는
    바닐라처럼 `IllegalStateException`("Tried to biome check an unregistered feature…").

## 4. 단계

| 단계 | 내용 | 확인 |
|---|---|---|
| 1 | 2·3장: 기록·populator·`ConfiguredFeature/PlacedFeature#place` — `//forest`·`//feature` | FAWE 꼴 시험 클래스(실제 populator 상속·기록 표시·`CraftBlockState` 목록), 같은 난수로 바닐라 runtime 결과 대조, 거절·블록 엔티티 없음·난수 안 씀 |
| 2 | `Structure#generate`·`StructureStart`(`isValid`·`getBoundingBox`·`placeInChunk`)·`BoundingBox`·`structureManager()`·`getStructureManager()`·`ChunkGenerator#getBiomeSource`·`randomState()` — `//structure` | 2장 규칙(템플릿 블록 엔티티 칸의 방벽 쓰기가 기록에서는 거절되는 것 등)을 Paper jar 로 확인한 뒤 |
| 3 | 나머지 멤버: `getBiomeManager`·`getChunkIfLoadedImmediately`·`getFluidIfLoaded`·`destroyBlock(4)`·`getShade`·`enabledFeatures`·`getCurrentDifficultyAt`·`getEntities`·`addFreshEntity(…, SpawnReason)`·`addFreshEntityWithPassengers`·`getSharedSpawnPos`·`getTypeKey`·`paperConfig` | 멤버마다 Paper jar 원문 |

## 5. 알려진 차이·가정 (1단계)

1. **유체 읽기**: Paper 의 populator 는 유체를 실제 월드에서 읽어 이번 배치가 쓴 칸의 유체를 모른다. Furnace 지형 생성 코드는 유체를 블록 상태에서
   얻으므로 사본(쓴 것 포함)을 본다. 같은 칸을 쓰고 나서 그 칸의 유체를 읽는 feature 에서만 갈린다(나무는 잎·뿌리가 쓰기 전에 그 칸 유체를 읽어
   해당 없음). 유체 읽기 자리마다 원천을 고르게 바꾸는 일은 후속(T-220).
2. 플러그인 레벨의 블록 엔티티 내용을 바꾸는 길이 없다(3.1) — 바꿔야 할 때는 오류로 알린다. 보통 FAWE 에서는 그 칸에 블록 엔티티가 없어 안 생긴다.
3. 로드 안 된·다른 region 청크에 닿으면 오류(Paper 는 불러온다).
4. 쓰기 플래그는 맨 위 지형지물 하나로 정한다(나무면 19, 아니면 3 — `LiveFeatures.liveFlags`). 바닐라는 지형지물마다 자기 플래그라 나무를 고르는
   지형지물(`random_selector` 등)이 맨 위면 나무 블록도 3 으로 실린다. FAWE 는 플래그를 쓰지 않아 블록 결과는 같고, `CraftBlockState#getFlag` 값과
   서버 레벨에 바로 놓을 때의 이웃 갱신만 다를 수 있다(기존 묘목·`/place` 길과 같은 제약 — 후속에서 지형지물마다 플래그를 싣게 한다).
5. 광석 줄기(`OreFeature`, 흩뿌린 광석 제외)는 바닐라가 `BulkSectionAccess` 로 청크 섹션에 바로 쓴다 — Paper 도 플러그인 레벨의 `setBlock` 을 거치지 않는다
   (평범한 populator 는 `getChunk` 에서 예외, FAWE populator 는 기록 없이 실제 월드 섹션에 바로). 통로로 흉내 내지 않고 `UnsupportedNmsCall`. 서버 레벨에 바로 놓기는 된다.
6. 높이 밖 쓰기: Paper 나무 기록은 높이 검사 전에 기록하지만 서버 지형 생성 코드는 높이 밖 쓰기를 통로에 넘기지 않는다(바닐라 지형지물이 높이 밖에 쓰는 일은 거의 없다).
7. `getBlockState` 를 바꾼 플러그인 레벨: 블록 읽기는 플러그인 레벨에 묻지 않고 사본(실제 월드 + 받아들인 쓰기)으로 답한다. FAWE populator(실제 월드 기록으로 돌림)와
   평범한 populator 는 이것과 같다.
8. 나무 가장자리 모양 갱신(`SinkShapeLevel`)의 유체는 실제 월드에서 읽는다(Paper `DummyGeneratorAccess` 를 감싼 populator 와 같다 — 1 의 원천 문제는 T-220).


## 6. 1단계 구현 메모 (2026-11-17)

- 다리: `Internals#features()` → `FeatureInternals#placeNow(월드, placed?, id, x, y, z, java.util.Random, LevelSink)`. 통로가 null 이면 서버 레벨에 바로
  (`LiveFeatures.placeThrough` 의 반영 판 — 사본에 돌리고 flag 대로 옮긴다), 아니면 통로 판. 서버 쪽 `ChunkLevel#placeFeatureThrough`.
- 오류 종류: 구현 안 된 지형지물·플러그인 레벨 블록 엔티티 내용 바꾸기 → 서버 `UnsupportedOperationException` → 호환 `UnsupportedNmsCall`.
  닿지 못한 청크 → `IllegalStateException`(`LiveFeatures.Unreachable` — 호환 `Level` 의 "로드 안 된 칸은 IllegalStateException" 과 같은 종류).
  플러그인 레벨이 던진 예외는 서버 코드 너머로 그대로 나른다(서버가 던진 것과 섞지 않는다).
- 기록 적용 범위: **전역 스레드(Bukkit 동기 작업)에서만**(`NmsFeatures#captureAppliesHere`). `Level#setBlock`·`getBlockState` 와 "서버 레벨에 바로 놓기" 판정이
  같은 규칙을 쓴다 — region 스레드의 NMS 쓰기·지형지물 배치는 전역 스레드가 켠 기록에 섞이지 않는다(검토 #494 지적으로 "배치 안" 조건을 뺐다).
  기록 표는 `Collections.synchronizedMap(LinkedHashMap)`(필드 형은 Paper 그대로 `Map`).
  `captureBlockStates` 만 켠 `setBlock`(이웃 갱신 없이 쓰고 옛 상태 기록)은 서버에 그 길이 없어 `UnsupportedNmsCall`.
- `BlockStateListPopulator#getList` 차례: Paper 는 `list.remove` 뒤 `put` 이라 다시 쓴 칸이 뒤로 간다(나무 기록 `capturedBlockStates` 는 처음 쓴 차례).
  잎 거리 갱신이 잎을 다시 쓰므로 두 목록의 차례는 다르고, 칸·마지막 상태는 같다(`NmsFeaturePlaceTest`).
- `CraftBlockState` 는 추상 클래스로 끼웠다(Bukkit 표면은 `CompatBlockState` 가 구현). populator 가 남기는 블록 엔티티 블록의 상태는 Paper 처럼 형이
  `CraftChest` 등이고 내용은 새 블록 엔티티(빈 내용 — `CraftBlockEntityState.furnaceFresh`).
- `CapturedBlockState#update`·`setBlockState` 로 나무가 놓은 벌집을 반영하면 Paper 는 벌 2~3 마리를 넣는다 — 블록 엔티티 shim 에 벌 넣기가 없어 쓰기 전에 `UnsupportedNmsCall`.
- `ensureCanWrite` 를 바꾼 플러그인 레벨: 바닐라는 하위 지형지물마다 묻는데 서버 지형 생성 코드는 묻지 않으므로 `UnsupportedNmsCall`(바닐라 안에서는
  `WorldGenRegion` 만 바꾼다). 생성기 인자는 그 레벨 월드의 생성기여야 한다.
- 난수: 받은 `RandomSource` 를 `java.util.Random` 어댑터로 넘겨 뽑을 때마다 그 원천에서 뽑는다. 위치별 난수 공장(`forkPositional`)을 쓰는 지형지물은
  `UnsupportedNmsCall`(Paper `RandomSourceWrapper` 길과 같은 제약 — 서버 `JavaRandomWorldgen`).
- 시험: 서버 `PluginLevelFeatureTest`(통로 = Paper 나무 기록과 같은 쓰기·난수, 거절한 칸, 벌집 블록 엔티티 있음/없음, 바로 놓기 = 런타임 길, 바이옴 거르기,
  닿지 못함, 광석), 호환 `NmsFeaturePlaceTest`(FAWE populator 꼴 — 전역 스레드 빌림 구간에서 기록, 평범한 populator, 서버 레벨 바로, 다른 스레드의 기록 표시,
  전역 스레드 기록 중 region 스레드 배치, 부술 수 없는 칸, 광석, 정직한 실패). 남은 시험 공백: 실제 FAWE jar 클래스의 링크, 보너스 상자·던전·엔드 관문의
  블록 엔티티 없음 길, 하이트맵 원천.


## 7. 2단계 설계 — 구조물 (`//structure`) (2026-11-17)

### 7.1 FAWE 2.16.2 가 하는 일 (`PaperweightFaweAdapter#generateStructure`, 전역 스레드)

```
Structure k = registryOrThrow(STRUCTURE).get(id)                     // 없으면 false
populator = new FaweBlockStateListPopulator(serverLevel); preCaptureStates(serverLevel)   // 1단계와 같은 기록
start = k.generate(registryAccess, gen, gen.getBiomeSource(), chunkSource.randomState(), serverLevel.getStructureManager(),
                   serverLevel.getSeed(), new ChunkPos(pt), 0, populator, biome -> true)
!start.isValid() → null
bbox = start.getBoundingBox(); 그 청크 범위(ChunkPos.rangeClosed — x 가 먼저)마다:
  start.placeInChunk(populator, serverLevel.structureManager(), gen, serverLevel.getRandom(),
                     new BoundingBox(청크 minX, minBuildHeight, 청크 minZ, 청크 maxX, maxBuildHeight, 청크 maxZ), 청크)
결과 = populator.getList() + capturedBlockStates.values()
```

배치 난수가 **서버 레벨 난수 자체**다(`serverLevel.getRandom()`). 그래서 조각이 `world.getRandom()` 으로 뽑는 값(사막 피라미드 지하실, 저택 알레이 수,
몹 `finalizeSpawn`)이 배치 난수와 한 수열에서 섞인다.

### 7.2 Paper 1.21.1 의 규칙 (jar 에서 읽음)

`StructureStart#placeInChunk`: 그 청크 상자와 겹치는 조각마다 `postProcess(transformerAccess, …)` — 레벨을 **`TransformerGeneratorAccess`** 로 감싼다
(`AsyncStructureGenerateEvent` 를 부르고 블록·엔티티 변환기를 받는다; Furnace 호환 계층에는 이 이벤트가 없어 변환기는 늘 비어 있다 — 아래는 변환기 없는 Paper).
`afterPlace` 는 감싸지 않은 레벨로.

| 자리 | Paper 동작(변환기 없음) |
|---|---|
| 조각 쓰기 `placeBlock` 등 | 감싸개 `setBlock(pos, s, 2)` → **레벨의 `setBlock(pos, s, flags, 512)`(인자 4개)**. 감싸개면 유체 예약 틱·`markPosForPostprocessing` 을 하지 않고 끝난다 |
| 상자·디스펜서 `createChest`/`createDispenser`, 바다 유적 상자 표시 | `random.nextLong()` 을 **늘** 뽑고(Craft 상태 시드), `placeCraftBlockEntity`: 레벨 4인자 `setBlock(flag 2)` → 유체 예약 틱(버려짐) → `getBlockEntity(pos)` 가 있으면 내용 바꿈 |
| 스포너(요새·요새 성·폐광) `placeCraftSpawner` | `CraftBlockStates.getBlockState(LevelReader…)` 는 레벨에서 `registryAccess()` 만 묻고 **월드 없는** 상태를 만든다 → `setSpawnedType` 은 `isPlaced()` 가 거짓이라 레벨의 `getRandom()` 이 아니라 `RandomSource.create()`(레벨 난수 안 부름 — 검토 #497 에서 바로잡음) → 위와 같은 `placeCraftBlockEntity` |
| 표시 상자 `setCraftLootTable`(난파선·이글루·엔드 시티) | `getBlockEntity(pos)` 가 `RandomizableContainerBlockEntity` 일 때만 `nextLong` 과 내용 바꿈 |
| 템플릿 `StructureTemplate#placeInWorld` | 감싸개를 벗겨 **레벨의 `setBlock` 인자 3개**. NBT 있는 칸은 먼저 방벽을 `flag 20` 으로 쓴다(`WorldGenLevel` 이라 `Clearable` 없음) → 실제 상태를 템플릿 flag(조각 2, 풀 요소 18)로. 쓰기 성공이고 NBT 가 있으면 `getBlockEntity` 가 있을 때만 `LootTableSeed` 뽑기·내용 넣기. 침수는 `placeLiquid`(flag 3). 모양 갱신은 `updateShapeAtEdge(flags)`(쓰기 `flags & ~1`) 뒤 칸마다 `updateFromNeighbourShapes`(쓰기 `flags & ~1 \| 16`)·`blockUpdated` |
| 템플릿 엔티티 | `EntityType.create(nbt, world.getLevel(), true)` → `finalizeEntities`(풀 요소) 이고 `Mob` 이면 `finalizeSpawn(world, …)` → `addFreshEntityWithPassengers`(populator 는 버림) |
| 코드가 만드는 엔티티 | `EntityType.X.create(world.getLevel())` → 몹이면 `finalizeSpawn` → `addFreshEntity`. 늪 오두막 마녀·고양이, 바다 유적 드라운드, 해저 유적 엘더 가디언, 저택 몹은 몹, 엔드 시티 셜커·아이템 액자와 폐광 상자 광산수레는 몹 아님 |
| 표시 `handleDataMarker`·직소 `final_state` | 감싸개로 → 4인자 쓰기(이글루 공기 flag 3, 직소 flag 3, 드라운드 자리 flag 2) |
| 지형지물 풀 요소 | 감싸개를 레벨로 지형지물 `place` → 4인자 쓰기 |
| `afterPlace`(사막 피라미드 의심스러운 모래·저택 조약돌) | 감싸지 않은 레벨에 3인자. 모래는 `getBlockEntity(pos, BRUSHABLE_BLOCK)` 가 있으면 내용 바꿈. 시드 `world.getSeed()` |
| `CappedProcessor` | `world.getLevel().getSeed()` |

FAWE populator 는 3·4인자 쓰기 모두 서버 레벨로 넘겨 나무 기록에 쌓인다(1단계 규칙: 부술 수 없는 블록 칸은 거절). 그래서 Paper+FAWE 에서는
**템플릿의 NBT 칸(상자 등)에 방벽이 남는다** — 방벽을 기록한 뒤 실제 상태 쓰기는 "지금 방벽(부술 수 없음)" 이라 거절된다. 블록 엔티티는 기록이 만들지 않으므로
`getBlockEntity` 는 실제 월드(보통 없음)라 상자 내용·`LootTableSeed` 뽑기도 없다. 평범한 populator 는 4인자 쓰기를 `DummyGeneratorAccess` 가 거절(false)하고,
`getLevel`·`getRandom`·`getSeed` 에서 `UnsupportedOperationException`.

### 7.3 Furnace 설계

서버(1단계 통로의 연장, 구조물용 `LiveStructures`):
- **생성**: `StructureEngine#generateForCommand` 에 바이옴 검사(시작 자리 바이옴 이름 → 호환이 `Holder<Biome>` 로 플러그인 술어를 부른다)를 더한 판.
  시작 청크는 플러그인이 준 것, 참조 수는 보관만. 구현 안 된 구조물·템플릿 없음 → `UnsupportedOperationException`.
- **청크 배치**: `placeInChunk(시작, 청크 상자, 청크, 배치 난수, 레벨 난수, 통로)`. 사본 격자는 청크 상자를 덮고 한 칸 여유(상한 `LiveGrid.MAX_GRID_RADIUS`),
  처음 닿을 때 복사, 닿지 못하면 `Unreachable`. 통로가 없으면 `/place structure` 와 같은 반영(flag 2).
- **통로 세션**: 같은 시작을 같은 플러그인 레벨로 여러 청크에 놓으면 앞 청크에서 받아들인 쓰기가 다음 청크의 읽기에 보여야 한다(Paper 는 기록·populator 표를 읽는다 —
  청크 경계의 모양 갱신 등). 세션이 받아들인 쓰기를 차례대로 들고, 사본을 처음 채울 때 그 청크 몫을 얹는다(실제 월드 + 받아들인 쓰기 = 1단계와 같은 읽기).
  세션은 호환 `StructureStart` 가 레벨마다 하나 든다.
- **쓰기 인자 수**: 통로 쓰기에 "인자 4개(깊이 512) / 3개" 를 싣는다. 조각·표시·직소·지형지물 풀 요소·상자·스포너는 4개, 템플릿 본문·`afterPlace` 는 3개.
- **템플릿(통로)**: NBT 칸 방벽(flag 20) 쓰기, 템플릿 flag, 침수 flag 3, 모양 갱신은 1단계 `SinkShapeLevel` 처럼 실제 블록 동작으로(쓰기 flag 은 위 표), `blockUpdated` 는 통로로.
  NBT 를 넣을 블록 엔티티가 있으면 `UnsupportedOperationException`(1단계와 같은 이유), 없으면 뽑지 않고 건너뛴다.
- **블록 엔티티 자리**: 상자·디스펜서·바다 유적 상자는 늘 뽑고(서버 코드 그대로), 쓴 뒤 그 칸에 블록 엔티티가 있으면 오류·없으면 건너뜀. 표시 상자(`setCraftLootTable`)와
  의심스러운 모래는 블록 엔티티가 있을 때만(있으면 오류). 스포너는 레벨 난수를 부르지 않는다(Paper 는 월드 없는 상태로 `RandomSource.create()` 를 쓴다 — `getRandom()` 이 던지는 평범한 populator 로도 끝난다).
- **레벨 호출**: `getRandom()`(레벨 난수 — 플러그인 레벨이 주는 원천에서 뽑는다; FAWE 는 배치 난수와 같은 원천이라 섞임까지 같다), `getSeed()`·`getLevel().getSeed()`
  (월드 시드와 다르면 오류), `getLevel()`(엔티티를 만들 때). 플러그인 레벨이 던지면 그대로 나른다.
- **엔티티**: 몹이 아니면 `getLevel()` 뒤 통로의 `addFreshEntity`(populator 는 버린다 — Paper 와 같다; 그 밖의 레벨은 오류). **몹 `finalizeSpawn`** 은 레벨 난수로
  종마다 다른 수를 뽑는데(속성 삼각 분포·왼손잡이, 아기·장비·변종 …) 서버에 아직 그 이식이 없다 — 통로·바로 놓기 모두 그 자리에 닿으면
  `UnsupportedOperationException`(규칙 3: 난수 소비가 다른 길을 켜지 않는다). 해당: 마을(동물·주민·골렘 템플릿), 약탈자 전초기지(알레이·골렘),
  보루(피글린·호글린), 늪 오두막, 해저 유적, 저택, 드라운드 표시가 든 바다 유적. 나머지 구조물(이글루 — 템플릿 조각은 `finalizeEntities` 가 꺼져 있어 주민을 만들기만 한다, 사막·정글 사원, 난파선, 묻힌 보물, 폐광, 요새, 네더 요새, 엔드 시티,
  폐허 포털, 네더 화석, 고대 도시, 흔적 폐허, 시련의 회당 등)은 된다. 이식은 후속(T-221 — 서버 월드젠·`/place structure` 의 몹 속성도 같이 고쳐진다).

호환:
- `Structure#generate(…)`: 생성기·바이옴 원천·난수 상태·템플릿 관리자는 그 월드 것이어야 하고 시드는 월드 시드, 높이 접근자는 월드 높이와 같아야 한다(아니면 `UnsupportedNmsCall`).
- `StructureStart`(`INVALID_START`·`isValid`·`getBoundingBox`·`getChunkPos`·`getReferences`·`getStructure`·`placeInChunk`), `levelgen.structure.BoundingBox`(바닐라 그대로),
  `ChunkGenerator#getBiomeSource`·`BiomeSource`, `ServerChunkCache#randomState`·`RandomState`, `ServerLevel#getStructureManager`(`StructureTemplateManager`)·`structureManager()`(`StructureManager`).
- `placeInChunk(level, …)`: 1단계 `NmsFeatures` 와 같은 갈래 — 서버 레벨 자신이고 기록 중이 아니면 바로 놓기, 아니면 통로(통로 = 그 레벨의 `setBlock` 3·4인자,
  `getBlockEntity`, `getRandom`, `getSeed`, `getLevel`, `addFreshEntity`, `blockUpdated`).

## 8. 2단계 구현 메모 (2026-11-17)

- 서버: `world/LiveStructures`(생성 `generate` · 청크 배치 `placeInChunk` · 세션 `Session`), `StructureEngine#generateFor(id, cx, cz, terrain, biomeTest)`,
  `FeatureWorldView` 의 플러그인 배치 표시(`setPluginPlacement`)·쓰기 깊이(`setSinkDepth` — -1 이면 인자 3개)·레벨 난수 부르기·시드·엔티티·`blockUpdated` 통로,
  `TemplatePlacer` 의 통로 갈래(방벽 flag 20, NBT 칸 블록 엔티티 검사, 모양 갱신 flag `flags & ~1`·`flags & ~1 | 16`, 침수 flag 3, 엔티티 `pluginTemplateEntity`).
  API 다리: `FeatureInternals#generateStructure`·`newStructureSession`·`structureSessionWrite`·`placeStructureNow`, `LevelSink` 의 기본 메서드 5개.
- 호환: `NmsStructures`(검사·세션·예외 변환), `StructureStart`·`BoundingBox`·`BiomeSource`·`RandomState`·`StructureTemplateManager`·`StructureManager` shim,
  `NmsFeatures#levelSink`(지형지물·구조물 공용 통로). `LevelAccessor#blockUpdated` 기본(아무것도 안 함)을 바닐라처럼 더했다(서버 `Level` 은 이웃 갱신으로 재정의).
- 세션: 서버 레벨 자신(기록 중)이면 Paper 의 읽기가 기록을 돌려주므로 호출마다 지금 `capturedBlockStates` 에서 새로 채운다. 그 밖의 레벨은 그 레벨이 살아 있는 동안
  하나(약한 참조 표) — FAWE 는 호출마다 새 populator 를 만들고 바로 앞에서 기록을 켜므로 그 populator 의 세션 = 그 호출의 기록이다.
- 통로의 엔티티·`blockUpdated`: 그 레벨이 바닐라 기본(`addFreshEntity` 없음 → 거짓, `blockUpdated` → 아무것도 안 함)이면 Paper 처럼 버리고, 직접 정의한 레벨이면
  `UnsupportedNmsCall`(서버는 플러그인 레벨에 넘길 NMS 엔티티 객체를 만들지 않는다; 기록 중 배치가 실제 월드의 이웃 갱신을 같은 순서로 낼 수 없다).
  서버 레벨의 기록 중 구조물 배치는 템플릿 모양 갱신 뒤 `blockUpdated` 자리에서 이 오류가 난다 — FAWE 는 populator 를 쓰므로 해당하지 않는다.
- 시험: 서버 `PluginLevelStructureTest`(통로 쓰기 갈래·방벽·세션·몹 오류·바로 놓기·닿지 못한 청크), 호환 `NmsStructureTest`(FAWE generateStructure 흐름 그대로 —
  기록에 방벽·눈 블록, 월드 그대로; 시작 값; 서버 레벨에 바로; 마녀 오두막·다른 시드·생성기 없음·DummyGeneratorAccess 의 정직한 실패).
- 검토 #497 반영: 스포너 자리의 레벨 `getRandom()` 부르기를 지웠다(위 7.2 표). 아래 (4)~(6)은 검토 권장 사항이다.
- 알려진 차이·가정: (1) 이글루처럼 놓을 때 높이를 옮기는 조각은 시작의 상자(`getBoundingBox`)가 옮기기 전 값이다 — 바닐라도 같다(시작 상자는 처음 계산값을 둔다).
  (2) 1단계 5장의 유체 읽기 차이(T-220)가 구조물 템플릿의 침수 판정에도 같다. (3) `Structure#generate` 의 `RegistryAccess` 는 서버 것만 받는다(다른 레지스트리의 구조물은 없다).
  (4) Paper 는 템플릿 NBT 칸마다 방벽 앞·모양 갱신 뒤, 그리고 `setCraftBlock` 에서 쓰기 결과와 상관없이 `getBlockEntity` 를 부른다. Furnace 는 쓰기가 받아들여진
  뒤에만 묻는다 — FAWE·평범한 populator 는 부작용이 없어 결과가 같고, `getBlockEntity` 가 던지는 레벨이면 Paper 는 방벽을 쓰기 전에 멈춘다(가정: 그런 레벨은 드물다).
  (5) Paper 는 청크 상자와 겹치는 조각이 있으면 `AsyncStructureGenerateEvent` 를 부른다(`CraftStructureTransformer`). 호환 계층에 그 이벤트가 없어 부르지 않는다.
  (6) 노이즈 생성기가 없는 월드(평지·빈 월드·Bukkit 생성기 월드)에서는 구조물 생성이 `UnsupportedNmsCall` 이다. Paper 는 이런 월드에서도 `//structure` 가 된다 — 로드맵 T-223.

## 9. 3단계 설계 — 서버 레벨 나머지 멤버 (2026-11-17)

### 9.1 범위

점검 보고서(`compat-nms-shim-audit-full-run-2026-11-17-raw.md`)의 FAWE 없는 멤버 가운데 레벨 쪽 7개. FAWE 2.16.2 에서 이것들을 부르는 곳은
`FaweBlockStateListPopulator`(그 레벨 메서드를 서버 레벨로 넘기는 재정의)와 `PaperweightAdapter#clearContainerBlockContents`(`Clearable`) 뿐이다.
1·2단계의 배치는 서버 코드가 돌리므로 populator 의 이 재정의를 부르지 않는다 — 다른 플러그인·바닐라 꼴 코드가 부를 때 Paper 와 같게 하는 것이 목적이다.

| 멤버 | Paper 1.21.1 (jar 에서 읽음) | Furnace |
|---|---|---|
| `ServerLevel#getShade(Direction, boolean)` | `1.0F` | 같다 |
| `Level#getChunkIfLoadedImmediately(int, int)`(final, `LevelReader` 에 추상) | `chunkSource.getChunkAtIfLoadedImmediately` | 같다(이미 있는 `ServerChunkCache` 메서드 — 로드된 청크, 아무 스레드) |
| `LevelReader#enabledFeatures()` → `FeatureFlagSet` | `server.getWorldData().enabledFeatures()` | 서버의 켜진 플래그(`data/FeatureFlags`)를 바닐라 `FeatureFlags.REGISTRY.fromNames` 로 |
| `LevelReader#getBiomeManager()` → `BiomeManager`, `getBiome(pos)`, `getNoiseBiome(qx, qy, qz)` | 레벨이 만들 때 `new BiomeManager(this, obfuscateSeed(seed))`; `getNoiseBiome` 은 BIOMES 이상 청크가 있으면 그 청크, 없으면 `getUncachedNoiseBiome` | `BiomeManager` 바닐라 그대로 이식(확대 지터), 원천은 그 레벨의 `getNoiseBiome`(로드된 청크 = 공개본, 아니면 생성기) |
| `LevelAccessor#getCurrentDifficultyAt(pos)` → `DifficultyInstance` | 청크가 있으면 달 밝기·머문 시간을 넣어 `new DifficultyInstance(난이도, 낮 시간, 머문 시간, 달)` | 서버 `ChunkLevel#getCurrentDifficultyAt` 값(그 칸 주인 스레드 — `NmsOwner`). 청크가 없으면 머문 시간 0·달 0 |
| `LevelWriter#destroyBlock(pos, drop, entity, depth)`(+ 2·3인자 기본) | 아래 9.2 | 서버 `ChunkLevel#destroyBlock`(바닐라) |
| `net.minecraft.world.Clearable` | 담는 블록 엔티티(`BaseContainerBlockEntity` 계열·모닥불·조각된 책장·장식 단지·주크박스·독서대)가 구현 | 같은 클래스에 구현, `clearContent()` 는 서버 `Clearables.tryClear`(그 칸 주인 스레드) |

그 밖: 엔티티 멤버(`addFreshEntity(Entity, SpawnReason)`·`addFreshEntityWithPassengers`·`getEntities()`/`LevelEntityGetter`·`Entity.generation`·
`spawnReason`)는 엔티티 쓰기 작업(T-212)으로 넘긴다. `paperConfig()` 는 FAWE `//regen` 의 리플렉션 필드 복사에서만 쓰이고(필드가 없으면 건너뛴다),
`MinecraftServer#getWorldData` 는 FAWE 가 아닌 WorldEdit `//regen` 길에서만 쓰여 이번 범위가 아니다.

### 9.2 `destroyBlock` — Paper 와 바닐라가 다른 곳

Paper `Level#destroyBlock`: 공기면 거짓 → **경험치를 먼저 뽑는다**(`getExpDrop(state, level, pos, EMPTY, true)` — 광석은 레벨 난수, `drop` 이 거짓이어도)
→ `BlockDestroyEvent` 듣는 쪽이 있으면 부르고(취소·효과 블록·효과 끄기·떨굼·경험치를 바꿀 수 있다) → 불이 아니면 레벨 이벤트 2001 → 떨굼이면
`dropResources(…, 경험치 없음)` 뒤 뽑아 둔 경험치 → `setBlock(유체 상태, 3, depth)` → 성공이면 `BLOCK_DESTROY` 게임 이벤트.
바닐라는 경험치를 `dropResources` 안(`spawnAfterBreak`, 떨굼 뒤)에서만 뽑는다. Furnace 서버는 바닐라 순서다(바닐라가 정답 — 절대 규칙 2, T-204 의
`getFullStatus` 결정과 같은 기준). 그래서 경험치를 주는 블록을 부술 때 레벨 난수를 쓰는 차례가 Paper 와 다르고, `drop` 이 거짓이면 Paper 만 난수를 쓴다.
`BlockDestroyEvent` 는 Furnace 서버 어디에서도 부르지 않는다(물·덩굴 등이 블록을 부수는 모든 길 공통) — 로드맵에 따로 둔다.
부수는 엔티티: 플레이어는 서버 `PlayerActor`, 그 밖의 엔티티는 서버의 엔티티 부수기(`destroyBlockByEntity` — 전리품의 this 엔티티는 비움, 서버의 기존 차이)로.
그 칸 주인 스레드에서 돌린다(`NmsOwner`). 부수는 엔티티가 그 스레드의 것이 아니면 `UnsupportedNmsCall`.

### 9.3 `Clearable` — 서버 쪽 빈칸

서버 `Clearables.tryClear`(`/setblock`·`/fill`·`/clone`·빠른 블록 쓰기 공용)는 주크박스·독서대를 빠뜨렸고 장식 단지의 전리품을 풀지 않았다. 바닐라 `clearContent`:
담는 블록 엔티티는 칸을 모두 비움(전리품 표는 그대로), 주크박스는 `removeTheItem` → `setTheItem(EMPTY)`(has_record 끄기 flag 2·`block_change`·재생 멈춤 1011),
독서대는 `setBook(EMPTY)`(쪽 0·저장 표시 — `has_book` 상태는 그대로), 장식 단지는 `removeTheItem` → `unpackLootTable(null)` 뒤 비움, 모닥불·조각된 책장은 칸 비움.
`tryClear(be, level)` 로 레벨을 받아 이 셋을 바닐라대로 고친다. 이 고침은 명령 길도 바꾼다: 지금까지 `/setblock` 으로 음반이 든 주크박스를 바꾸면
`onRemove` 가 음반을 떨궜는데 바닐라는 먼저 비워 떨구지 않는다.
호환: 월드의 블록 엔티티 보기는 그 칸 주인 스레드에서 서버 블록 엔티티에(`NmsOwner` — 저장과 같은 문), 플러그인이 만든 떨어진 블록 엔티티는 `UnsupportedNmsCall`
(내용을 NBT 로만 들고 있어 주크박스·단지의 레벨 동작을 같게 낼 수 없다; FAWE 는 월드의 것만 비운다).

### 9.4 기타

- `FeatureFlagSet`·`FeatureFlag`·`FeatureFlagUniverse`·`FeatureFlagRegistry`·`FeatureFlags` 는 바닐라 그대로(코덱은 빼고). `hashCode` 는 fastutil `HashCommon.mix` 와 같은 식.
- `BiomeManager.obfuscateSeed` 는 바닐라(구아바 `sha256().hashLong(seed).asLong()`)와 같은 값 — 8바이트 리틀 엔디언 SHA-256 앞 8바이트(서버 `BiomeZoom` 과 같다).
- `DummyGeneratorAccess` 에 Paper 처럼 `getCurrentDifficultyAt`·`getBiomeManager`·`enabledFeatures`·`getChunkIfLoadedImmediately` 를 둔다(모두 "Not supported yet.").
- `getShade` 는 `ServerLevel` 에만 둔다(바닐라는 `BlockAndTintGetter` 에 추상 — shim 에 그 인터페이스가 없다).

## 10. 3단계 구현 메모 (2026-11-17)

- API 다리(`ChunkInternals`, 기본 메서드 4개): `enabledFeatureFlags()`(아무 스레드), `currentDifficultyNow`(청크가 있으면 주인 스레드),
  `destroyBlockNow`(주인 스레드, 부수는 엔티티는 `entityNow` 로 — 플레이어는 `PlayerActor`, 그 밖은 `destroyBlockByEntity` 에 깊이를 더한 겹메서드),
  `clearBlockEntityContentNow`(주인 스레드, 아직 만들지 않은 생성 블록 엔티티는 바닐라 `getBlockEntity` 처럼 만든 뒤).
- 서버: `Clearables.tryClear(be, level)`·`isClearable` — 명령(`/setblock`·`/fill`·`/clone`)·빠른 블록 쓰기와 같은 코드.
- 호환: `LevelReader`(`NoiseBiomeSource` 상속, `getChunkIfLoadedImmediately`·`getBiomeManager`·`getBiome`·`getNoiseBiome`·`getUncachedNoiseBiome`·`enabledFeatures`),
  `LevelAccessor#getCurrentDifficultyAt`, `LevelWriter#destroyBlock`(2·3인자 기본 + 4인자), `Level` 구현, `ServerLevel#getShade`, `DummyGeneratorAccess` 의 Paper 거절 4개.
  새 shim: `Clearable`·`DifficultyInstance`·`flag.*`·`BiomeManager`·`LinearCongruentialGenerator`. 블록 엔티티 생성기(`tools/compat/gen-nms-block-entities.py`)가
  바닐라 `Clearable` 클래스 6개에 `implements Clearable`·`clearContent()` 를 붙인다(하위 클래스는 물려받는다).
- `getCurrentDifficultyAt`·`clearContent` 는 그 칸 주인 스레드(`NmsOwner` — 틱 스레드가 아니면 기다리는 왕복, 남의 region·전역 스레드는 ThreadOwnershipException,
  빌림 구간이면 된다), `destroyBlock` 은 `setBlock` 과 같이 주인 스레드(빌림 포함)에서만.
- 시험: 호환 `NmsLevelSurfaceTest`(7개), 서버 `ClearablesTest`(5개).
- 알려진 차이·가정: (1) 9.2 의 `destroyBlock` 경험치 난수 순서·`BlockDestroyEvent`(T-224). (2) `getNoiseBiome` 은 BIOMES 단계의 생성 중 청크를 보지 않는다 —
  로드된 청크만 보고 나머지는 생성기 값이다(생성 중 청크의 바이옴은 생성기 값과 같으므로 결과는 같다, 가정: 생성 뒤 바이옴을 바꾸는 단계는 없다).
  (3) 지워졌거나 내려간 청크의 블록 엔티티 보기·떨어진 블록 엔티티의 `clearContent` 는 `UnsupportedNmsCall`. (4) `FeatureFlagRegistry#codec` 은 없다.
