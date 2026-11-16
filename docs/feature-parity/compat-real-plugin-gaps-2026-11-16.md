# 실서버 4종 시험에서 나온 구멍 처리 결과 (2026-11-16)

원본: 데스크탑 실서버 시험 보고서(`furnace/reviews/2026-11-16-desktop-real-plugin-test.md`, FAWE 2.16.2·LuckPerms 5.5.91·ProtocolLib 5.4.0·Vault 1.7.3 동시, 호환 실패 0).
이 문서는 보고서 TASK 제안 5가지를 어떻게 처리했는지와, 남은 "미지원 NMS 심볼 137개"를 기능별로 나눠 각 TASK 에 배정한 표다.
측정은 클라우드에서 **실제 FAWE 2.16.2 jar**(Modrinth 공식 배포)와 PaperMC paperclip 1.21.1-133 을 직접 돌려 확인했다(데스크탑 불필요).

## 1. 항목별 결과

| # | 보고서 항목 | 결과 | PR |
|---|---|---|---|
| 1 | FAWE `LegacyMapper` "Unknown block/item" 30줄 | **고침** — `DataFixers` shim 이 서버 데이터 픽서(1452~3955)로 올린다. 실제 FAWE 로 기동해 **0줄** 확인 | #436 (T-199) |
| 2a | 리플렉션 경고 `MinecraftServer#nextTickTime`·`ServerPlayer#playerConnection` | **오탐이었다** — Paper 1.21.1 에도 없는 이름(`nextTickTimeNanos`·`connection` 만 있다, javap 로 확인). Paper 에서도 같은 예외가 나므로 경고하지 않게 고침. 진짜 구멍(Paper 에 있고 shim 에 없는 이름)은 그대로 WARN | #437 |
| 2b | 미지원 NMS 심볼 137개 | 아래 2장 — 기능별로 분류, 기존 TASK 에 배정, 새 TASK(T-204) 한 묶음 | 이 문서 |
| 3 | `/version <플러그인>` | **고침** — Bukkit `VersionCommand` 와 같은 찾기·출력 | #435 |
| 4 | ProtocolLib `/packet names` | 별도 스레드가 Paper·Furnace 17개 명령 비교로 "문제 없음" 결론(오진). 이 스레드는 하지 않음 | — |
| 5 | starlight relight 무시 | 3장 — 계획만 정리해 T-197(N2b-5, P0)로 넘긴다 | 이 문서 |

## 2. 미지원 심볼 137개 분류 (FAWE 2.16.2 `impl/fawe/v1_21_R1`, `javap` 로 호출 위치 확인)

"쓰이는 곳"은 FAWE 클래스·메서드다. **호출될 때만** `UnsupportedNmsCall` 이 나고, 일반 `//set`·`//replace`·`//copy`·`//paste`·`//undo`(시험한 범위)는 이 경로를 타지 않는다.

| 묶음 | 심볼 | 쓰이는 곳 | 맡을 TASK |
|---|---|---|---|
| A 블록 엔티티 | `LevelChunk#getBlockEntity(2)`·`ChunkAccess#getBlockEntity`·`Level/ServerLevel#getBlockEntity`·`ServerLevel#removeBlockEntity`·`BlockEntity#loadWithComponents/setChanged`·`BeaconBlockEntity#playSound`·`CraftBlockEntityState#getTileEntity`·`ClientboundBlockEntityDataPacket`(생성자·create) | `PaperweightGetBlocks#tile/internalCall`(상자·표지판 편집), `PaperweightFaweWorldNativeAccess#updateTileEntity` | **T-198**(P0) |
| B 빛 | `ThreadedLevelLightEngine#checkBlock/getLayerListener`·`LevelLightEngine#checkBlock/queueSectionData`·`LayerLightEventListener`·`ChunkSource#getLightEngine`·`ServerLevel#getLightEngine/getRawBrightness` | `PaperweightGetBlocks#getSkyLight/getEmittedLight/fillLightNibble/removeSectionLighting`, NativeAccess `updateLightingForBlock` | **T-197**(P0) |
| C 아이템 구성 요소 | `RegistryAccess$Frozen#createSerializationContext`·`ItemStack#applyComponents/getComponentsPatch/getDescriptionId` | `PaperweightFaweAdapter#adapt(BaseItemStack)`(`//wand`·브러시) | **T-200** + T-191 |
| D 이웃 갱신·부수효과 | `Level#blockUpdated/sendBlockUpdated/onBlockStateChange/updateNeighbourForOutputSignal/getCraftServer`, `ServerLevel#sendBlockUpdated/onBlockStateChange/updateNeighborsAt/updateNeighbourForOutputSignal/destroyBlock/scheduleTick`, `BlockState#onPlace/handleNeighborChanged/triggerEvent/updateNeighbourShapes/updateIndirectNeighbourShapes/hasAnalogOutputSignal/hasPostProcess/canBeReplaced/canSurvive`, `Block#updateFromNeighbourShapes/getStateForPlacement`, `BlockPlaceContext`, `Fluid#getTickDelay`, `ServerChunkCache#blockChanged`, `FullChunkStatus`·`LevelChunk#getFullStatus`, `LevelChunk#setBlockState(4)·setBiome(4)`, `ChunkPos#rangeClosed` | NativeAccess(`markBlockChanged/notifyBlockUpdate/notifyNeighbors/updateNeighbors/updateBlock/isChunkTicking`: 한 칸씩 쓰는 길·부수 효과 켠 편집), `PaperweightPostProcessor`(물·용암 틱 예약), `PaperweightPlacementStateProcessor`(놓기 상태) | **T-204 신규**(아래) |
| E 엔티티 복사·붙이기 | `Entity#load/save/setUUID/absMoveTo/discard/spawnReason/generation`, `EntityType#create`, `ServerLevel#addFreshEntity(+WithPassengers)/getEntities`, `PersistentEntitySectionManager`·`LevelEntityGetter`, `LevelChunk$EntityCreationType`, `ServerPlayer#<init>`(가짜 플레이어), `InteractionResult*`·`UseOnContext`·`Item#use`·`ItemStack#useOn`·`BlockState#useItemOn` | `PaperweightGetBlocks#internalCall`(엔티티 붙이기), `PaperweightFakePlayer`(`simulateItemUse`) | **T-203** |
| F regen·구조물·지형지물 | `ServerLevel#<init>(15)` 와 `ServerLevel` 상속 두 곳, `ChunkGenerator`·`NoiseBasedChunkGenerator`·`LevelStem`·`LevelSettings`·`WorldOptions`·`WorldData`·`PrimaryLevelData`·`LevelStorageSource(+Access)`·`DimensionDataStorage`·`ChunkProgressListener`, `ServerChunkCache#close/getDataStorage/getGenerator/pollTask/randomState`, `Structure#generate(10)`·`StructureStart`·`BoundingBox`, `ConfiguredFeature#place`·`PlacedFeature#place`·`WorldGenLevel`, `ServerLevel` 의 읽기 위임(`getBiomeManager/getRandom/getSeaLevel/…` — `FaweBlockStateListPopulator`·`PaperweightLevelProxy`), `captureBlockStates` 계열 필드, `MinecraftServer#getWorldData`, `Util`·`ThreadingDetector`·`BlockableEventLoop`·`Clearable`·`DyeColor`·`GsonHelper`·`StringUtil`, `CraftBlock`·`CraftBlockState`·`BlockStateListPopulator` | `//regen`, `//forest`·`//feature`·`generateTree/Feature/Structure`, 임시 월드 | **T-203**(L, 기능별로 쪼갬) |

분류 근거: `missing.txt` 는 `NmsCompatReport.scan("FAWE", fawe.jar, NmsSymbols.RUNTIME).missing()` 로 뽑았다(137줄, 위 묶음을 다 합치면 137). 이번 PR 들로 줄어드는 것은 없다(2a 는 심볼 보고가 아니라 리플렉션 경고).

### T-204(신규, P1) — NativeAccess·부수 효과 묶음(D)
FAWE 가 블록을 한 칸씩 쓰거나(`BukkitWorld#setBlock`·다른 플러그인이 WorldEdit API 로 부를 때) 부수 효과(이웃 갱신·물 틱·놓기 상태)를 켠 편집을 할 때 쓰는 길이다. 설계 요점:
- 서버에는 이미 `Level#setBlock` 의 플래그(이웃 1·클라이언트 2·모양 16…)와 `setBlockNow` 가 있다. NativeAccess 는 같은 일을 **쪼개서** 부른다(`setBlockState` → `onPlace` → `sendBlockUpdated` → 이웃 갱신). 쪼갠 호출을 바닐라 순서·바닐라 의미로 서버 쪽 진입점에 이어야 한다. 이웃·모양 갱신은 주인 region 스레드 규칙(스케줄러 규칙)을 따라야 하므로 N2b-4 의 거울 일지처럼 **다른 스레드는 일지에 쌓고 주인이 재생**하는 모양이 유력하다 — 설계는 Opus high 가 문서로 먼저 낸다.
- `FullChunkStatus`/`getFullStatus` 는 쉬운 부분(청크가 FULL/BLOCK_TICKING/ENTITY_TICKING 인지는 Furnace 청크 티켓 수준에서 바로 나온다) — 첫 작은 PR 로 떼어낼 수 있다.
- 증명: FAWE 부수 효과를 켠 편집(물 `//set`·문·레드스톤)이 Paper 와 같은 틱 진행(유체 틱 예약·이웃 갱신)을 보이는지 같은 시나리오로 대조(N2b-6 시나리오 + 물 흐름 N틱 뒤 칸 수).
- 크기 L, 모델 Opus high, 선행 T-198(블록 엔티티 쓰기 길을 같이 쓴다).

## 3. (5) starlight relight — T-197 로 넘기는 계획

**왜 이 스레드에서 안 하나**: 설계는 이미 있다(`compat-nms-n2b-design-2026-11-14.md` 5장 빛·6장 N2b-5, 결정 D2). 구현은 Furnace 빛 엔진(`LevelLight.lightChunk` + 가장자리 + 하늘빛 원천 `lowestSource` 다시 세기)을 주인 region 에서 돌리고 거울 일지·저장 표시·빛 패킷과 이어야 해서 L 크기·동시성이다(T-197 은 이미 P0, Opus high). 작은 PR 로 쪼개 이 스레드에서 서둘러 넣으면 "임시로 하고 나중에 고치기"가 된다.

**javap 로 확인한 호출 모양(구현 때 그대로 맞출 것)**:
1. `PaperweightStarlightRelighter#invokeRelight(Set<ChunkPos>, Consumer<ChunkPos>, IntConsumer)` 는 **region 스레드가 아닌 곳**에서 불린다: 먼저 청크마다 `CraftWorld#getChunkAtAsync(x, z)` 로 로드를 확보한 뒤 `serverLevel.getChunkSource().getLightEngine().starlight$serverRelightChunks(chunks, chunkCallback, onComplete)` 를 부른다. 그러므로 다시 계산은 "주인 region 으로 넘기고 비동기 완료"여야 한다(지금처럼 호출 스레드에서 콜백을 바로 부르면 안 된다).
2. `FAWE_TICKET`(`TicketType`)·`LIGHT_LEVEL`: 호출하는 동안 청크를 붙들어 둔다(Furnace 청크 티켓 `NmsTickets` 로 이미 이어짐).
3. 청크 콜백 안에서 FAWE 는 `DELAY_PACKET_SENDING`(기본 켬)이면 모아서 `PaperweightPlatformAdapter#sendChunk` 로 **청크 통째 다시 보내기**를 한다(이미 구현된 청크 패킷 shim). 그러므로 새 빛은 **콜백 전에** 이미 청크 상태에 반영돼 있어야 한다(콜백은 다시 계산이 끝난 청크마다).
4. 현재 shim(`ThreadedLevelLightEngine#starlight$serverRelightChunks`)은 빛을 건드리지 않고 콜백·완료만 부르며 처음 한 번 경고한다(`compat.nms-relight-unsupported`). 이 시그니처·콜백 순서는 유지한다.

**측정 사실**: 노트북 N2b-6 시나리오(편집 41청크)에서 하늘빛 126,006칸·블록빛 3,151칸이 Paper 와 다르다(보고서 3장). 데스크탑 시험은 평지 낮이라 빛 어긋남을 못 봤다 — 지하·밤 발광 블록에서 보이므로 **도미니코 직접 확인 항목**(아래 user-todo)으로 올린다.

## 4. 한계·가정
- 심볼 137개는 정적 분석(상수 풀) 기준이라 실제로 부르는 것은 일부다. 묶음 D·E·F 의 "쓰이는 곳" 은 `javap` 로 FAWE 클래스에서 확인했고, 각 기능의 실제 실패 여부는 해당 TASK 의 시험에서 본다.
- Paper 멤버 이름표(#437)는 이름만 담고 오버로드를 구분하지 않는다(있으면 "있다").
