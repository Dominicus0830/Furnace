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
   해당 없음). 유체 읽기 자리마다 원천을 고르게 바꾸는 일은 후속(T-218).
2. 플러그인 레벨의 블록 엔티티 내용을 바꾸는 길이 없다(3.1) — 바꿔야 할 때는 오류로 알린다. 보통 FAWE 에서는 그 칸에 블록 엔티티가 없어 안 생긴다.
3. 로드 안 된·다른 region 청크에 닿으면 오류(Paper 는 불러온다).

## 6. 1단계 구현 메모 (2026-11-17)

- 다리: `Internals#features()` → `FeatureInternals#placeNow(월드, placed?, id, x, y, z, java.util.Random, LevelSink)`. 통로가 null 이면 서버 레벨에 바로
  (`LiveFeatures.placeThrough` 의 반영 판 — 사본에 돌리고 flag 대로 옮긴다), 아니면 통로 판. 서버 쪽 `ChunkLevel#placeFeatureThrough`.
- 오류 종류: 구현 안 된 지형지물·플러그인 레벨 블록 엔티티 내용 바꾸기 → 서버 `UnsupportedOperationException` → 호환 `UnsupportedNmsCall`.
  닿지 못한 청크 → `IllegalStateException`(`LiveFeatures.Unreachable` — 호환 `Level` 의 "로드 안 된 칸은 IllegalStateException" 과 같은 종류).
  플러그인 레벨이 던진 예외는 서버 코드 너머로 그대로 나른다(서버가 던진 것과 섞지 않는다).
- 기록 적용 범위: 전역 스레드이거나 `NmsFeatures` 배치 안(스레드별 깊이). 기록 표는 `Collections.synchronizedMap(LinkedHashMap)`(필드 형은 Paper 그대로 `Map`).
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
  닿지 못함), 호환 `NmsFeaturePlaceTest`(FAWE populator 꼴 — 전역 스레드 빌림 구간에서 기록, 평범한 populator, 서버 레벨 바로, 다른 스레드의 기록 표시,
  부술 수 없는 칸, 정직한 실패).
