# NMS shim 5번 묶음 — squaremap 이 타는 길의 구멍 39개 (2026-11-20)

squaremap(지도 그리기 플러그인, Modrinth 1.21.1 용 1.3.2)이 부르는데 Furnace 의 Mojang 이름 shim 에 없던 심볼 39행을 채웠다. 1~4번 묶음(`compat-nms-batches-2-4-2026-11-20.md`)과 같은 방식: 서명은 바닐라 1.21.1 에 맞추고, Furnace 연결은 서버 다리(`ChunkInternals`) 한 곳에, 아직 없는 것은 진짜와 같은 오류(`UnsupportedNmsCall`)로 둔다. 바닐라 클래스를 통째로 옮기지 않았다.

## 계측

`./gradlew -PskipOracle=true :furnace-compat-paper:nmsShimAudit -PauditJars=<squaremap jar 폴더>` → 없는 심볼 39행(없음(Paper 에 있음) 33, 표 모름 5, 서명 다름 1). 작업 뒤 같은 명령 결과: **0행**. squaremap 은 서버 로드 때 쓰는 Paper 길(`PaperChunkSnapshotProvider`·`PaperRegionFileDirectoryResolver`)과 공통 길(색·바이옴·지도 네트워크)을 모두 부르므로 두 길의 심볼을 모두 채웠다.

## 한 것

| 묶음 | 내용 |
|---|---|
| 월드 폴더 | `LevelResource`(상수 9개와 `getId`), `MinecraftServer#getWorldPath`(기본 월드 폴더 + 자원 이름, `ROOT` 는 "."를 붙인 경로 — 바닐라와 같다), `DimensionType#getStorageFolder`(차원 키 → 오버월드 = 월드 폴더, 엔드 DIM1, 네더 DIM-1, 그 밖 dimensions/<이름공간>/<경로>), `LevelStorageSource#getStorageFolder`(차원 줄기 키 판), `ServerLevel#getTypeKey`(월드 환경으로 OVERWORLD·NETHER·END) |
| 레벨 | `Level#getSharedSpawnPos`(경계 안이면 서버 월드 스폰, 밖이면 바닐라처럼 경계 중심 칸의 MOTION_BLOCKING), `WorldBorder#getCenterX·getCenterZ·getSize`, `DimensionType#hasCeiling`, `CommonLevelAccessor`(레지스트리 묶음 입구, `LevelAccessor` 가 잇는다) |
| 바이옴 | `Biome#getBaseTemperature`·`getSpecialEffects`, `BiomeSpecialEffects`(안개·물·물속 안개·하늘 색, 잎·풀 색 고정값, 풀 색 보정), `GrassColorModifier`(NONE·DARK_FOREST·SWAMP, 식은 바닐라 그대로, swamp 의 잡음은 서버 `BiomeClimate` 의 같은 `BIOME_INFO_NOISE`). 값은 플러그인 바이옴 정의가 먼저, 없으면 서버의 바닐라 바이옴 JSON 에서 읽는다 |
| 청크 | `ImposterProtoChunk`, `BelowZeroRetrogen`(값 클래스), `ChunkAccess#getBelowZeroRetrogen`(늘 null), `ChunkHolder#getLatestChunk`, `Heightmap#primeHeightmaps`, `BuiltInRegistries#CHUNK_STATUS`(+`Registries#CHUNK_STATUS`, 기본값 empty) |
| 블록 종류 | `StainedGlassBlock`·`CropBlock`(밀·당근·감자·비트·횃불꽃 작물, `AGE`)·`StemBlock`(호박·수박 줄기, `AGE`) — 서버 블록 표가 이 블록들을 그 클래스로 만든다(플러그인의 `instanceof`). `BlockState#getMapColor` 는 서버 블록 표의 지도 색 |
| 지도 | `MapItemSavedData` 의 `centerX`·`centerZ`·`dimension`·`scale`·`mapView` 필드, `Level#getMapData`(서버 전체 지도에서 읽은 겉모양 사본), `CraftMapView#getRenderers`·`CraftMapRenderer`(바닐라 그리기 표시 하나) |
| 글·명령 | `ComponentSerialization#CODEC`(진짜 DataFixerUpper 코덱, JSON 모양으로 어떤 ops 와도), `ComponentUtils#fromMessage`, `DimensionArgument#dimension()` |
| 접속자 | `CraftPlayer#displayName()`(Adventure 글), `CraftPlayer#getPersistentDataContainer` 의 반환형 `CraftPersistentDataContainer`, `Entity#isInvisible`(접속자는 투명 효과) |
| 수 | `Mth#ceillog2`·`log2`·`smallestEncompassingPowerOfTwo`(바닐라 드브루인 표) |

서버 다리 추가(`ChunkInternals`): `hasCeiling`, `borderCenterAndSize`, `mapInfo`, `biomeInfo`, `biomeInfoNoise`, `isPlayerInvisible`. 서버 코드 하나: `BiomeClimate#biomeInfoNoise`(기존 비공개 잡음의 읽기 입구).

대역 라이브러리(컴파일·시험 전용, jar 에 넣지 않음): `src/brigadier`(Message·ArgumentType 등 네 모양), `src/adventure`(Component·ComponentSerializer·GsonComponentSerializer). 시험은 진짜 adventure(Maven Central)를 쓴다.

## Paper 와 다른 점·가정

- `DimensionArgument#parse` 는 `UnsupportedNmsCall`: Furnace 에 Brigadier 명령 분배기가 없다. `dimension()` 은 명령 라이브러리가 "차원 인자" 표시로 건네는 값으로만 쓰인다.
- `ComponentUtils#fromMessage` 는 늘 글자 그대로의 글이다(shim `Component` 는 Brigadier `Message` 를 잇지 않는다).
- 바이옴 값은 만들 때 한 번 읽는다 — 같은 키를 플러그인이 다시 정의해도 이 값은 그대로(가정: 바이옴 정의는 서버를 다시 켜야 바뀐다). 데이터 팩이 바닐라 바이옴을 덮어쓴 값은 읽지 않는다(내장 바닐라 JSON 기준 — 가정).
- `Biome.ClimateSettings` 는 record 가 아닌 클래스다. 이유: squaremap 이 이 값의 "두 번째 float 접근자"를 리플렉션으로 불러 강수량을 읽는데 선언 순서는 JVM 마다 달라질 수 있어, 들고 있는 하위 클래스가 float 접근자 둘을 모두 강수량으로 답하게 했다(시험 `downfallIsReachable…`).
- `ChunkAccess#getBelowZeroRetrogen` 은 늘 null, `ImposterProtoChunk` 는 서버가 만들어 주지 않는다(서버 청크는 늘 `LevelChunk`) — 플러그인의 `instanceof ImposterProtoChunk` 는 늘 거짓. 낡은 1.18 이전 청크를 서버가 어떻게 올리는지는 이 길에서 확인하지 않았다(가정).
- `MapItemSavedData` 는 읽을 때의 겉모양 사본이다(색 바이트·표시·추적 없음). `Level#getMapData` 는 서버 전체 지도를 본다(바닐라는 오버월드 저장소).
- `getWorldPath` 는 첫 월드(기본 월드)의 폴더 기준이다.
- `StainedGlassBlock` 은 바닐라의 `TransparentBlock`·`BeaconBeamBlock` 중간 클래스를 두지 않고 `Block` 을 바로 잇는다.
- `Level#getSharedSpawnPos` 의 경계 밖 길은 바닐라 `getHeightmapPos` 와 같지만 그 청크가 안 불러와져 있으면 `Level#getHeight` 규칙(바닥 높이)을 따른다.
- 시험 서버·측정은 실제 squaremap 을 돌리지 않았다 — 서명 감사 0행과 shim 단위 시험까지다(실제 접속 시험은 사용자 확인 항목).
