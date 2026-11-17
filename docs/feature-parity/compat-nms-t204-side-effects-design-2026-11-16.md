# T-204 설계 — FAWE NativeAccess·부수 효과 묶음 (2026-11-16)

과제 정의: `docs/roadmap/optimization-tasks.md` T-204, 근거 `compat-real-plugin-gaps-2026-11-16.md` 2장 묶음 D.
앞 단계: N2b 설계(`compat-nms-n2b-design-2026-11-14.md` — 거울·일지, 결정 D1~D4), N2b-4(거울 일지), T-198(블록 엔티티 일지·기다리는 왕복), T-205(checkBlock).
대조 자료: FAWE 2.16.2 소스(태그 `2.16.2`, `worldedit-core`·`worldedit-bukkit`·`adapter-1_21`)와 Modrinth 배포 jar(`FastAsyncWorldEdit-Paper-2.16.2.jar`, sha512 앞 16자 `319f6c961b01206d`)의 `javap`,
Paper 1.21.1 청크 시스템은 Moonrise `mc/1.21.1` 가지(`RegionizedPlayerChunkLoader`·`GenerationChunkHolderMixin`). 바닐라 1.21.1 동작은 서버의 기존 포팅(`ChunkLevel.setBlock` 등)을 기준으로 삼는다.

원칙: 관측 결과가 바닐라·Paper 와 같아야 하고, 특정 플러그인 저격 금지(FAWE 는 검증 대상), 임시 구현 금지.

## 1. 무엇을 하나 — 심볼과 쓰는 곳

FAWE 2.16.2 `v1_21_R1` 에서 이 묶음의 심볼을 부르는 곳은 네 군데다(`javap -c` 로 확인).

| 쓰는 곳 | 언제 | 부르는 NMS |
|---|---|---|
| `PaperweightFaweWorldNativeAccess`(FAWE 어댑터의 WNA) | **한 칸 쓰기 길**: 다른 플러그인이 WorldEdit API 로 `BukkitWorld#setBlock`·`applySideEffects` 를 부를 때, FAWE 가 "빠른 놓기"를 끈 편집(`fast-placement: false`·`EditSessionBuilder#forceWNA`) | `LevelChunk#setBlockState(3)`·`getFullStatus`·`getSections`, `FullChunkStatus#isOrAfter`, `Level#sendBlockUpdated/blockUpdated/updateNeighbourForOutputSignal/onBlockStateChange/getCraftServer/getBlockEntity`, `ServerChunkCache#blockChanged`, `BlockState#handleNeighborChanged/hasAnalogOutputSignal/onPlace/triggerEvent/updateIndirectNeighbourShapes`, `Block#updateFromNeighbourShapes`, `LevelLightEngine#checkBlock`(T-205 에서 있음) |
| `PaperweightPlacementStateProcessor` + `PaperweightFaweMutableBlockPlaceContext` + `PaperweightLevelProxy` | 큐 편집에서 NEIGHBORS 부수 효과를 켰을 때(`//perf neighbors on`), `//fixblocks` | `Block#getStateForPlacement(BlockPlaceContext)`, `BlockPlaceContext` 생성자, `BlockState#canBeReplaced(BlockPlaceContext)`, `BlockHitResult`, `IdMapper#getId` |
| `PaperweightPostProcessor` | 큐 편집 뒤 유체 틱 예약(설정 `experimental.allow-tick-fluids`, 기본 꺼짐) | `ServerLevel#scheduleTick(BlockPos, Fluid, int)`, `Fluid#getTickDelay(LevelReader)` |
| `PaperweightFaweAdapter#canPlaceAt` | WorldEdit 의 놓기 검사(FAWE 는 `hasPostProcess` 로 부른다 — FAWE 쪽 버그지만 Paper 에서의 결과가 정답) | `BlockState#hasPostProcess(BlockGetter, BlockPos)` |

같은 묶음으로 분류됐지만 쓰는 곳이 다른 과제의 것: `ServerLevel#destroyBlock`·`ChunkPos#rangeClosed`·`LevelChunk#setBiome(4)`(`FaweBlockStateListPopulator`·`PaperweightServerLevelDelegateProxy`·`generateStructure` — T-203 의 `//regen`·지형지물 길), `ServerLevel#updateNeighborsAt/sendBlockUpdated/onBlockStateChange/updateNeighbourForOutputSignal`·`LevelChunk#setBlockState(4)`·`BlockState#canSurvive/updateNeighbourShapes`(WorldEdit 원판 어댑터 `ext.fawe.v1_21_R1.PaperweightWorldNativeAccess`·`PaperweightAdapter#canPlaceAt` — FAWE 어댑터가 올라오면 쓰이지 않는다). 이 문서는 **이름이 같은 메서드는 함께 만든다**(같은 서버 진입점이라 비용이 거의 없고, 다른 플러그인도 쓴다). `destroyBlock`·`setBiome(4)`·`rangeClosed` 는 T-203 에 남긴다(그 길의 나머지 표면 없이는 시험할 수 없다).

### 1.1 FAWE WNA 의 실제 흐름(`WorldNativeAccess#setBlock` → `markAndNotifyBlock`)
1. `getChunk` → `getBlockState` → (VALIDATION 이면 `Block.updateFromNeighbourShapes`) → `setBlockState`.
   - **주 스레드**(`Fawe.isMainThread`)면 `LevelChunk#setBlockState(pos, state, UPDATE 부수 효과)` 를 바로 부른다.
   - 아니면 바꿀 칸을 모아 두었다가 `TaskManager.sync` 로 주 스레드에서 `setBlockState` 하고 청크를 통째로 다시 보낸다.
2. 블록 엔티티 NBT 가 있으면 `Level#getBlockEntity` + `loadWithComponents`(T-198).
3. LIGHTING 이면 `checkBlock`(T-205).
4. `markAndNotifyBlock`: 지금 칸이 새 상태가 **아니면 바로 끝**. 맞으면 `getFullStatus().isOrAfter(BLOCK_TICKING)` 일 때 ENTITY_AI 면 `sendBlockUpdated(pos, old, new, 3)`, 아니고 NETWORK 면 `blockChanged(pos)`. NEIGHBORS 면 `notifyNeighbors`(EVENTS 면 `Level#blockUpdated`, 아니면 6방향 `handleNeighborChanged` 직접 + `hasAnalogOutputSignal` 이면 `updateNeighbourForOutputSignal`)와 `updateNeighbors`(`updateIndirectNeighbourShapes` → EVENTS 면 `BlockPhysicsEvent` → `triggerEvent(level, pos, 2, 512)` → `updateIndirectNeighbourShapes`). POI_UPDATE 면 `onBlockStateChange`.

비주 스레드에서는 1에서 칸이 아직 안 바뀌어 4의 첫 검사에서 끝난다(Paper 도 같다 — 비동기 읽기가 옛 상태를 본다). 그러므로 **부수 효과 호출은 사실상 주 스레드(= Furnace 에서 그 칸의 주인 region, 또는 Bukkit 동기 작업의 전역 빌림 구간)에서 온다**. 예외는 같은 상태를 다시 놓는 경우(옛 상태 == 새 상태)뿐이다.

## 2. 의미 — 서버 진입점

서버는 이미 바닐라 `Level.setBlock` 을 포팅해 이 부수 효과를 모두 가지고 있다(`ChunkLevel.setBlock`). NativeAccess 는 그것을 **쪼개서** 부른다. 쪼갠 조각마다 서버의 같은 코드에 잇는다(새 바닐라 포팅은 없다).

| NMS | 바닐라 의미 | 서버 진입점(`RegionLevel`/`ChunkLevel`) |
|---|---|---|
| `Level#sendBlockUpdated(pos, old, new, flags)` | `ServerLevel`: `ChunkSource.blockChanged(pos)` + 충돌 모양이 바뀌면 길찾기 다시(`navigatingMobs`) | `markChanged` + `updateNavigations`(지금 private — `setBlock` 의 그 부분을 메서드로 뽑는다) |
| `ServerChunkCache#blockChanged(pos)` | `ChunkHolder.blockChanged` — 틱 끝 방송 목록에 넣는다 | `sendBlockUpdated(Pos)`(= `markChanged`) |
| `Level#blockUpdated(pos, block)`, `ServerLevel#updateNeighborsAt(pos, block)` | `updateNeighborsAt` → `NeighborUpdater.updateNeighborsAtExceptFromFacing`(CraftBukkit `BlockPhysicsEvent` 포함) | `updateNeighborsAt(pos, block)` — 서버 갱신기가 플러그인 `BlockPhysicsEvent` 를 이미 낸다 |
| `Level#updateNeighbourForOutputSignal(pos, block)` | 비교기 알림 | 같은 이름 |
| `Level#onBlockStateChange(pos, old, new)` | `ServerLevel`: POI 종류가 바뀌면 POI 지우기·더하기 | `poiManager.onBlockStateChange`(서버 `setBlock` 과 같은 자리 — 바닐라의 `server.execute` 미룸은 서버도 하지 않는다, 기존 가정 그대로) |
| `BlockState#onPlace(level, pos, old, moved)` | 블록 놓기 반응 | `BlockBehaviors.of(s).onPlace` |
| `BlockState#handleNeighborChanged(level, pos, block, from, moved)` | `Block.neighborChanged` **직접**(갱신기·`BlockPhysicsEvent` 를 거치지 않는다) | `BlockBehaviors.of(s).neighborChanged` |
| `BlockState#updateNeighbourShapes(level, pos, flags, depth)` | 6방향 `updateOrDestroy` | `B.updateNeighbourShapes` |
| `BlockState#updateIndirectNeighbourShapes(level, pos, flags, depth)` | 레드스톤 선만 재정의 | `BlockBehaviors.of(s).updateIndirectNeighbourShapes` |
| `BlockState#triggerEvent(level, pos, type, data)` → boolean | 블록 이벤트 처리(소리 블록·피스톤·상자·종…) — FAWE 는 `(2, 512)` 로 부른다(Paper 에서도 그 값으로 블록별 반응이 난다) | `BlockBehaviors.of(s).triggerEvent` |
| `BlockState#hasAnalogOutputSignal()` | 상태 속성 | 고정 표(`BlockStateInternals`) — 아무 스레드 |
| `Block#updateFromNeighbourShapes(state, level, pos)` → state | 6방향(서·동·북·남·아래·위) `updateShape` 를 차례로 — `updateShape` 는 틱 예약 같은 부수 효과가 있다(물 잠긴 블록의 유체 틱) | 서버의 `updateShape` 를 같은 순서로(`ShapeUpdater` 는 생성용 읽기 전용이라 쓰지 않는다 — 부수 효과가 빠진다) |
| `LevelChunk#setBlockState(pos, state, moved, doPlace)` | Paper: `doPlace` 가 거짓이면 `onPlace` 를 건너뛴다 | `setBlockStateInChunk` 에 `doPlace` 인자 |
| `Level#getCraftServer()` | CraftServer | 호환 `Bukkit.getServer()` |
| `LevelChunk#getFullStatus()`·`FullChunkStatus` | 청크 홀더의 티켓 레벨 → INACCESSIBLE/FULL/BLOCK_TICKING/ENTITY_TICKING | 4장 |
| `ServerLevel#scheduleTick(pos, Fluid, delay)`·`(pos, Block, delay)` | 유체·블록 예약 틱 | `scheduleFluidTick`·`scheduleTick`(5장) |
| `Fluid#getTickDelay(LevelReader)` | 물 5, 용암 30(초고온 차원 10), 빈 유체 0 | 차원 표(`ultraWarm`) — 아무 스레드 |
| `Block#getStateForPlacement(BlockPlaceContext)`, `BlockState#canBeReplaced(BlockPlaceContext)`·`canSurvive`·`hasPostProcess` | 놓기 상태 계산(읽기만) | 서버 `BlockBehavior` 의 같은 메서드 + 읽기 전용 레벨 다리(6장) |

## 3. 스레드 규칙

N2b 결정 D1·D4(다른 스레드의 쓰기는 거울 일지로 주인이 재생, 다른 region 스레드도 같은 규칙)와 T-198 의 규칙(값을 돌려줘야 하는 다른 스레드 호출은 기다리는 왕복, region·전역 스레드는 교착을 피해 `ThreadOwnershipException`)을 그대로 넓힌다.

| 부르는 스레드 | 값이 없는 부수 효과(`sendBlockUpdated`·`blockChanged`·`blockUpdated`·`updateNeighborsAt`·`updateNeighbourForOutputSignal`·`onBlockStateChange`·`onPlace`·`handleNeighborChanged`·`updateNeighbourShapes`·`updateIndirectNeighbourShapes`·`scheduleTick`) | 값을 돌려주는 부수 효과(`triggerEvent`·`updateFromNeighbourShapes`)와 주인 상태 읽기(`getFullStatus`) | 읽기만 하는 계산(`getStateForPlacement`·`canBeReplaced`·`canSurvive`·`hasPostProcess`) |
|---|---|---|---|
| 그 칸의 주인 region(전역 빌림 포함) | 원점 둘레 3×3 청크 중 들여오지 않은 쓰기가 있는 거울을 먼저 맞추고(이 스레드가 가진 청크만) 서버에 바로 | 바로 | 서버 레벨에서 바로 |
| 그 밖 | **주인 region 작업**(기다리지 않음) — 주인이 먼저 원점 둘레 3×3 청크 중 들여오지 않은 쓰기가 있는 거울을 맞추고(간접 모양 갱신이 닿는 범위 — 그 스레드가 이웃 청크에 앞서 쓴 것도 효과보다 먼저, 검토 #468) 서버에 한다(보통 같은 틱, 늦어도 다음 틱) | 틱 스레드가 아닌 스레드: 기다리는 왕복(`submitAwaited`, 최대 10초 — T-198 과 같은 문·예산). region·전역 스레드: `ThreadOwnershipException`(교착) | 그 NMS 레벨의 읽기(다른 스레드면 공개 스냅샷 + 자기 쓰기, N2b-3)로 서버 계산을 돌린다 — 기다리지 않는다 |

- 값이 없는 부수 효과를 주인 작업으로 넘기면 바닐라·Paper 보다 최대 한 틱 늦게 일어난다. Paper 에서 이 호출을 비주 스레드에서 하는 것은 경쟁 쓰기(서버를 깰 수 있다)이고, 순서(그 스레드의 앞선 쓰기 뒤)는 지킨다 — 다만 맞추기가 그 스레드가 효과 **뒤에** 쓴 것까지 들여올 수 있다(경쟁 호출이라 Paper 에서도 정해지지 않은 순서). N2b D1 과 같은 성질로 가정에 적는다. FAWE 의 실제 흐름은 1.1 대로 주 스레드라 이 길을 타지 않는다.
- 처음 안은 거울 일지 한 줄(`EFFECT`)이었으나, 일지 재생은 섹션 교체(훑기) 들여오기보다 먼저라 "쓰기 뒤 효과" 순서가 깨질 수 있고 이웃 청크 거울도 따로 맞춰야 해서, 맞추기를 끝낸 뒤 효과를 하는 주인 작업으로 정했다(2단계).
- 원점 청크가 로드돼 있지 않으면: 값이 있는 조각은 **부르는 순간** 부른 스레드에서 `IllegalStateException`(`Level#setBlock` 과 같은 문구 — 동기로 불러오지 않는다). 값이 없는 조각은 아무것도 하지 않는다(바닐라도 `sendBlockUpdated`·`blockChanged` 는 청크가 없으면 아무것도 안 한다. 내려간 원점의 로드된 이웃에 알리는 `updateNeighborsAt` 만 다르다 — 가정). 주인 작업이 도는 순간 내려가 있으면 조용히 버린다.
- `getFullStatus` 는 상태(정수 하나)를 공개 스냅샷에 얹어 기다리지 않게 할 수도 있으나(최대 1틱 낡음), FAWE 는 주 스레드에서만 부르고(1.1) 다른 스레드에서 부를 일이 드물어 값 호출 규칙(기다리는 왕복)으로 둔다 — 낡은 값으로 블록 변경을 보낼지 고르는 일을 피한다.
- 보낸 사이 그 칸의 주인이 바뀌면(region 나뉨) 받은 스레드에서 다시 확인해 한 번 다시 보낸다(`NmsOwner` — T-198 블록 엔티티 저장도 이 문을 쓴다). 값 없는 조각의 주인 작업도 같다(검토 #472 — 두 번 다 주인이 아니면 플러그인·조각마다 한 번 경고).
- 맞춤이 실제로 필요한 경우(검토 #472): 다른 스레드 쓰기의 맞춤 작업은 주인 대기열에서 효과 작업보다 앞서 있어 넘긴 효과에는 원점 청크 맞춤으로 충분하다. 둘레 맞춤이 필요한 것은 주인 스레드가 **자기 작업 안에서** 다른 스레드의 이웃 청크 쓰기를 본(기다린) 뒤 바로 효과를 부를 때다 — 그 쓰기의 맞춤 작업이 아직 돌지 않았다(`NmsSideEffectsTest.neighborChunkSyncedBeforeEffect`, 둘레 맞춤을 빼면 실패). 효과가 둘레 3×3 밖 청크까지 번지면(레드스톤 선 전파, 깊이 511 모양 갱신) 그 청크는 맞추지 않는다 — 한계(가정: 그 청크의 맞춤 작업은 주인 대기열에서 곧 돈다).
- 이웃 갱신은 다른 청크로 번진다. 주인 region 이 서버 `setBlock` 과 똑같이 처리한다(region 경계 처리도 서버 그대로).
- 이웃 쪽 칸은 서버 `setBlock` 과 같이 로드되지 않은 칸을 건드리지 않는다(`hasChunkAt` 검사).

## 4. FullChunkStatus — 바닐라 티켓 레벨

`LevelChunk#getFullStatus()` 는 청크 홀더의 티켓 레벨(바닐라 `ChunkLevel.fullStatus`: ≤31 ENTITY_TICKING, 32 BLOCK_TICKING, 33 FULL, 그 밖 INACCESSIBLE)이다. Furnace 는 바닐라 티켓 규칙으로 청크를 돌리므로 그 값을 그대로 낸다.

| 티켓(서버 쪽 근거) | 가운데 레벨 | ENTITY_TICKING | BLOCK_TICKING | FULL |
|---|---|---|---|---|
| 플레이어 시야(바닐라 `PlayerTicketTracker`, 시야 거리 2~32, 관전자는 `spectatorsGenerateChunks` 때만) | 31(시야 안 모든 청크) | 체비셰프 ≤ 시야 | 시야 + 1 | 시야 + 2 |
| `/forceload`·플러그인 ticking 티켓 | 31 | 그 청크 | 둘레 1 | 둘레 2 |
| 플러그인 붙잡기 티켓(`pluginHeld`, NMS `addRegionTicket` 의 load 반경 포함) | ≤33 | — | — | 그 청크 |
| 포털·드래곤·순간 이동 티켓(`addTicket`, 레벨 33 − 거리) | 33 − 거리 | 거리 − 2 안(`entityTicketed`) | 거리 − 1 안(`blockTicketed`) | 거리 안(`tickets`) |
| 스폰 청크 | 바닐라 START | `spawnTicketWithin(…, 2)` | `(…, 1)` | `(…, 0)` |

가장 높은 단계를 준다.

알려진 어긋남(검토 #468): NMS `addRegionTicket`·`addTicketAtLevel` 의 레벨 32 티켓은 Furnace 에서 "붙잡기"(`pluginHeld` — `NmsTickets` 참고)라 FULL 로 보인다(바닐라 BLOCK_TICKING). 레벨 ≤31 은 tick 티켓으로 옮겨져 맞다. Paper `CHUNK_RELIGHT`(서버 `relight.pinned`)는 Paper 에서도 레벨이 FULL 이 되지 않는 내부 티켓이라 상태 계산에 넣지 않는다(의도). 서버에서 `Region.fullChunkStatus(cx, cz)` 로 계산한다(그 region 의 플레이어 목록을 읽으므로 주인 스레드 — 3장 규칙).

**Paper 와 다른 점(위임 결정, `docs/user-todo/entries/2026-11-16-t204-side-effects.md`)**: Paper 1.21.1(Moonrise `RegionizedPlayerChunkLoader`)은 플레이어 티켓을 시뮬레이션 거리 안 31(ENTITY_TICKING), 불러오기 거리 안 33(FULL)으로 건다 — 시뮬레이션 거리 + 1 만 BLOCK_TICKING 이고 그 밖 시야 안은 FULL 이다. Furnace 는 바닐라 티켓 규칙으로 실제로 청크를 돌리므로(엔티티·블록 틱 판정이 바닐라), **서버가 실제로 쓰는 바닐라 값**을 낸다. FAWE 에서 보이는 차이: 주 스레드 WNA 편집이 시뮬레이션 거리 + 2 ~ 시야 + 1 사이 청크에서 Paper 는 블록 변경 패킷을 보내지 않고(클라이언트가 다시 받을 때까지 옛 블록을 본다), Furnace 는 보낸다(바닐라와 같다). 월드 결과(블록·틱)는 같다.

## 5. 유체·블록 예약 틱

`ServerLevel#scheduleTick(pos, Fluid, delay)` → `scheduleFluidTick(pos, 유체 종류, delay)`, `(pos, Block, delay)` → `scheduleTick(pos, 블록, delay, NORMAL)`, 우선순위 인자 판도 같다. 바닐라처럼 순번(`subTickCount`)은 그 칸 청크가 없어도 하나 쓴다(서버 그대로). 비주인 스레드는 3장대로 주인 작업(원점 청크만 맞춘다) — 지연은 주인이 받은 틱의 시각부터 센다(최대 1틱 늦다, 가정). 우선순위는 `TickPriority` shim(-3..3, 바닐라 값)이고 유체 예약도 우선순위를 받는다(서버 `scheduleFluidTick(…, priority)` 를 더했다 — 서버 유체 코드는 늘 NORMAL).
`Fluid#getTickDelay`: 서버 유체 표와 같은 값(물 5, 용암 초고온 10·아니면 30, 빈 유체 0). `LavaFluid` shim 이 차원의 `ultraWarm` 을 다리에서 읽는다(서버 월드가 아닌 레벨은 차원을 몰라 UnsupportedNmsCall — Paper 의 FAWE 프록시는 null 필드라 NullPointerException).

## 6. 놓기 상태 — 읽기 전용 레벨 다리

FAWE `PaperweightLevelProxy` 는 `ServerLevel` 을 상속하고 `Unsafe.allocateInstance` 로 만든다(생성자 없음 — Furnace shim 의 필드는 null). `getBlockState`·`getFluidState`·`getBlockEntity`·높이만 재정의해 **편집 중인 FAWE 내용**을 읽게 한다. `getStateForPlacement` 는 그 레벨에서 읽어야 Paper 와 같은 답이 나온다.

- 서버 `BlockBehavior.getStateForPlacement(PlaceContext)`·`canBeReplaced(state, PlaceContext)`·`canSurvive(state, BlockLevel, Pos)` 를 그대로 쓰고, `BlockLevel` 자리에 **NMS 레벨 객체의 가상 메서드로 읽는 읽기 전용 다리**를 넣는다(`getBlockState`·`getFluidState` = 그 객체의 메서드 → 플러그인이 재정의했으면 그것이 불린다). 신호(`hasNeighborSignal` 등)·면 단단함은 서버의 같은 계산을 그 다리의 읽기로 한다. 쓰기·소리·엔티티 등 읽기 밖의 요청은 `UnsupportedNmsCall`(놓기 계산은 바닐라에서도 읽기만 한다 — 시험으로 블록 전수 확인).
- `PlaceContext` 자리는 NMS `BlockPlaceContext` 의 가상 메서드(`getClickedPos`·`getClickedFace`·`getClickLocation`·`isInside`·`getItemInHand`·`getPlayer`·`getHand`·`getLevel`·`replacingClickedOnBlock`·`canPlace`·`getNearestLookingDirection(s)`·`getHorizontalDirection`·`isSecondaryUseActive`·`getRotation`)를 부르는 하위 클래스 — FAWE 의 가변 문맥처럼 재정의한 값이 그대로 들어간다.
- 진짜 Furnace 레벨(정확히 `ServerLevel` 클래스, 재정의 없음)이면: 주인 스레드는 서버 레벨로 바로(블록 엔티티의 실행 중 값까지 같다), 다른 스레드는 같은 다리(공개 스냅샷 읽기)로 기다리지 않고.
- 문맥 값은 **쓰일 때 묻는다.** FAWE 가변 문맥은 부모 생성자가 `canBeReplaced(this)` 를 부르는 순간 클릭 칸·방향 필드가 아직 null 이다 — 바닐라 블록 코드가 묻지 않는 값(공기의 canBeReplaced 는 손에 든 것만 본다)을 미리 읽으면 Paper 에 없는 NullPointerException 이 난다. 레벨도 처음 쓰일 때 고른다.
- 블록 엔티티(비교기 신호 등): 그 레벨 `getBlockEntity` 의 저장 꼴로 그 칸 상태의 떨어진 서버 블록 엔티티를 만든다(FAWE 프록시와 같은 방식 — 실행 중 값인 상자 연 수 등은 비어 있다).
- 월드 경계(침대): 그 레벨의 `getWorldBorder().isWithinBounds(pos)` — FAWE 프록시는 실제 월드 경계를 돌려준다. `WorldBorder` shim(판정만)과 `Level#getWorldBorder` 를 더했다.
- 읽기 밖으로 나가는 바닐라 놓기 계산은 둘뿐이다(블록 전수 시험으로 확인): 덩굴·켈프 나이 난수(`level.random`)와 산호 블록의 예약 틱(물이 없으면 `scheduleTick` + `getRandom`). 플러그인 레벨에서는 UnsupportedNmsCall — Paper 의 FAWE 프록시는 null 필드라 NullPointerException 이다(가정 — V5 1회차에서 Paper 는 F5~F7 에서 예외 없이 끝났다. 재실측에서 Furnace 쪽 처리를 확인). 진짜 Furnace 레벨은 서버 난수·예약 그대로.
- 놓는 플레이어가 있는 문맥은 아직 없다(UnsupportedNmsCall — 바닐라 생성자가 `canBeReplaced` 를 불러 만들 때 난다). 플레이어 없는 바닐라 문맥은 서버 `PlaceContext` 와 같다(시험 — 플레이어가 필요한 방향 질의는 둘 다 NullPointerException).
- `hasPostProcess(level, pos)`: 바닐라 `Properties.hasPostProcess(Blocks::always)` 를 거는 블록은 1.21.1 jar 의 `Blocks` 에서 셋뿐이다(마그마 블록·갈색·빨간 버섯 — 람다 `dga.a(dtc,dcc,jd)` = `always`). 서버 블록 표 플래그(`POST_PROCESS`)로 읽는다 — 위치를 보지 않는다.

## 7. 검증

| # | 무엇 | 어떻게 |
|---|---|---|
| V1 | 쪼갠 호출 = 서버 `setBlock` | 시험 서버에서 같은 칸을 (a) `Level#setBlock(pos, s, 3)` 와 (b) FAWE WNA 순서(`setBlockState` → `sendBlockUpdated` → `blockUpdated` → `updateIndirectNeighbourShapes`·`triggerEvent`·`updateIndirectNeighbourShapes` → `onBlockStateChange`)로 바꾼 두 세계를 몇 틱 돌려 블록·예약 틱·방송 목록·POI 가 같은지(물·용암·문·레드스톤 선·비교기·관측기·소리 블록·피스톤·POI 블록). 단 FAWE 순서는 `updateNeighbourShapes` 대신 `triggerEvent(2, 512)` 라 모양 갱신이 빠진다 — 그 차이는 Paper 에서도 같으므로, 대조는 "바닐라 원시 조각을 같은 순서로 부른 결과"와 한다 |
| V2 | 스레드 규칙 | 비주인 스레드 부수 효과가 같은 청크 앞선 쓰기 뒤에 재생되는지, 값 호출이 기다리는 왕복·region 스레드 예외인지, 내린 청크에 닿지 않는지 |
| V3 | FullChunkStatus | 플레이어 시야·`/forceload`·플러그인 티켓·포털 티켓 경계 청크에서 바닐라 표(4장) 값 |
| V4 | 놓기 상태 | 블록 전수 × 면 6 × 클릭 높이 2 × 장면 6(열린 바닥·물 안·이어지는 이웃·신호·천장·비교기 블록 엔티티 신호)에서 FAWE 가변 문맥 꼴(시험용 사본) + 프록시 레벨 길·서버 월드 길 = 서버 블록 동작 직접. canSurvive 는 전 상태. `NmsPlacementTest` — 블록 엔티티·유체·방향·경계 읽기를 일부러 틀리게 한 변이 4종이 모두 실패한다 |
| V5 | 실제 FAWE | **장면 `tools/compat/fawe-paper/scenario_t204.py`(README "T-204 놓기 상태·부작용 장면"), 데스크탑에서 잰다(클라우드에서 PaperMC 를 받을 수 없음). 1회차(11-17, `context-notes/2026-11-17-t204-v5-fawe-desktop.md`): FAWE 문맥·프록시가 NMS shim 상속 껍데기에 막혀 Furnace 만 실패 → 상속해도 되는 shim(`@NmsInheritable`, #480) 고침 뒤 재실측: 예외 0, FAWE 응답 Paper 와 같음, 블록은 Furnace 두 번 중 한 번 0차이·한 번 레드스톤 가루 1칸(실행마다 갈림 — 원인 미확인 후속).** F1~F4 FAWE `//set` 으로 울타리·계단·레드스톤 가루·판유리·담을 날것으로 쓴 뒤 `//fixblocks`(PlacementStateProcessor), F5~F7 덩굴·켈프·물 없는 산호 블록 `//fixblocks`(읽기 밖 요청 — Paper NPE·Furnace UnsupportedNmsCall 을 FAWE 가 같게 처리하는지), N1 `//perf neighbors on` 뒤 `//set`(WNA 이웃 갱신), N2 `//perf update on`·`neighbors on` 뒤 공중 물 `//set`(예약 유체 틱, 흐름이 끝난 모양). Paper 와 anvil-diff 블록 0차이 + MARK 구간 예외 비교. 처음 계획의 플러그인 표본 한 칸 쓰기는 FAWE `//perf` 편집이 같은 WNA 길을 타므로 뺐다 |

## 8. 단계(PR)

| 단계 | 내용 | 크기 |
|---|---|---|
| 1 | 이 설계 + `FullChunkStatus`·`LevelChunk#getFullStatus`(서버 `Region.fullChunkStatus`) | S |
| 2 | WNA 부수 효과(2장 위 표 `sendBlockUpdated`~`setBlockState(4)`·`getCraftServer`), 다른 스레드는 3×3 거울을 맞추는 주인 작업, 값 호출 기다리는 왕복, V1·V2 | M |
| 3 | 예약 틱·`getTickDelay`, 놓기 상태 다리(`BlockPlaceContext`·`getStateForPlacement`·`canBeReplaced`·`canSurvive`·`hasPostProcess`), V4 | M |
| 4 | V5 실측(시나리오·결과 문서) — 시나리오 올림, 데스크탑 결과 대기 | S |

각 단계는 독립 검토 승인 뒤 병합(동시성 — 2·3).

## 9. 가정·한계
- 부수 효과 조각은 서버의 `Level.setBlock` 포팅과 같은 코드를 부르므로, 그 포팅이 바닐라와 다른 곳(예: `onBlockStateChange` 를 `server.execute` 로 미루지 않음, `sendBlockUpdated` 의 BLOCK_TICKING 조건을 보지 않음)은 그대로 이어진다(기존 가정).
- 비주인 스레드 부수 효과는 최대 한 틱 늦다(3장). Paper 에서는 경쟁 호출이다.
- FullChunkStatus 는 바닐라 값(4장 — Paper 와 다른 것은 위임 결정). NMS 레벨 32 티켓은 FULL 로 보인다(4장 알려진 어긋남).
- `ChunkHolder#getFullStatus` 는 로드가 끝난 청크만 단계를 준다(바닐라는 홀더 레벨이라 불러오는 중에도 FULL 일 수 있다).
- 다른 스레드의 값 없는 부수 효과에서 원점이 내려가 있으면 아무것도 하지 않는다 — 바닐라는 내려간 원점의 로드된 이웃에도 `updateNeighborsAt` 을 알린다(드묾).
- `destroyBlock`·`setBiome(4)`·`rangeClosed` 는 T-203.
- V5: `//fixblocks` 뒤 레드스톤 가루 한 칸의 연결이 Furnace 에서만 실행마다 갈린다(2번 중 1번, 원인 미확인 — 가정: 앞선 FAWE 쓰기가 보이는 시점). 후속 과제.
- 놓기 다리: 플러그인 레벨의 난수·예약 틱은 UnsupportedNmsCall(Paper 는 FAWE 프록시에서 NullPointerException — 6장), 플레이어 있는 문맥 없음, 플러그인 레벨의 블록 엔티티는 저장 꼴에서 만든다(실행 중 값 없음 — FAWE 프록시와 같다).
