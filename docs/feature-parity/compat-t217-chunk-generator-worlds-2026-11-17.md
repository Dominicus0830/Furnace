# T-217 Bukkit 청크 생성기(ChunkGenerator)로 만드는 월드 설계 (2026-11-17)

로드맵 T-217. 범위는 플러그인이 `WorldCreator#generator(ChunkGenerator)`(또는 bukkit.yml `worlds.<이름>.generator`)로 만드는 **추가 월드**의
청크 생성·`BlockPopulator`·고정 스폰과, 그 월드를 저장·다시 여는 규칙이다. T-210(`compat-t210-biome-provider-worlds-2026-11-17.md`)의
바이옴 공급자 월드 규칙을 이어받는다. Furnace 쪽 바탕은 W3 월드젠 단계 훅(`ChunkGenerationHooks`, `docs/api/worldgen-hooks.md`)이다 —
훅 자리(바닐라 단계 뒤, 바닐라 단계 끄기, 구조물 높이)가 CraftBukkit `CustomChunkGenerator` 와 같은 자리로 만들어져 있다.

근거: Paper 1.21.1 패치(`33ff3dff`)와 Paper 저장소에 합쳐진 CraftBukkit 원문(`ca5499c7`, 2024-11-24 — 1.21.3 시점. 1.21.1 패치의
`CustomChunkGenerator` 문맥 줄이 이 원문과 같아 1.21.1 에도 같은 것으로 본다). 동작 비교용으로만 읽고 코드는 옮기지 않았다.

## 1. Paper 1.21.1 이 하는 일

### 1.1 만들기 (`CraftServer#createWorld`)
- 생성기가 없으면 bukkit.yml `worlds.<이름>.generator`("플러그인[:id]" → `Plugin#getDefaultWorldGenerator(이름, id)`, 못 찾으면 콘솔 오류 뒤 없음).
  공급자가 없으면 bukkit.yml `worlds.<이름>.biome-provider` 도 같은 규칙.
- 공급자가 없고 생성기가 있으면 `generator.getDefaultBiomeProvider(worldInfo)`(`CraftWorldInfo` — 아직 World 가 아니다). 그 공급자는 T-210 과 같은 길.
- `ServerLevel` 생성자: 생성기가 있으면 바닐라 생성기(공급자를 붙인 것)를 `CustomChunkGenerator(레벨, 바닐라 생성기, 플러그인 생성기)` 로 감싼다.
- `initWorld`: `world.getPopulators().addAll(generator.getDefaultPopulators(world))` → WorldBorder → `WorldInitEvent` → 새 월드면
  `setInitialSpawn`: `generator.getFixedSpawnLocation(world, new Random(seed))` 가 null 이 아니면 그 칸·yaw 를 스폰으로 하고 바닐라 스폰 찾기를
  하지 않는다(다른 월드의 Location 이면 `IllegalStateException`). `canSpawn` 은 1.21.1 에서 부르지 않는다.
- 생성기·공급자는 어디에도 저장되지 않는다. 재시작 뒤 플러그인(또는 bukkit.yml)이 다시 주지 않으면 그 월드의 새 청크는 바닐라다(경고 없음).

### 1.2 청크 생성 (`CustomChunkGenerator`)
모든 `shouldGenerate*`·`generate*` 는 생성 스레드에서 불린다. `Random` 은 `RandomSourceWrapper.RandomWrapper(WorldgenRandom(LegacyRandomSource(0)))`
— `java.util.Random` 을 넓혀 `nextInt()`·`nextInt(n)`·`nextInt(a,b)`(= `a + nextInt(b-a)`, java.util 과 다름)·`nextLong`·`nextBoolean`·`nextFloat`·
`nextDouble`·`nextGaussian`·`setSeed` 를 레거시 난수원으로 돌리고, 나머지(`next(bits)`·`ints()` 등)는 `java.util.Random` 자신의 상태(씨앗 0)로 돈다.

| 단계(바닐라 순서) | 묻기(씨앗) | 바닐라 처리 | 플러그인 호출(씨앗) |
|---|---|---|---|
| 구조물 시작점 | `shouldGenerateStructures`(`getSeed(x,"should-structures".hashCode(),z) ^ seed`) | false 면 그 청크가 시작점인 구조물 없음 | — |
| 노이즈 | `shouldGenerateNoise`(`"should-noise"`) | false 면 빈 청크(바이옴은 그대로) | `generateNoise`(`x*341873128712 + z*132897987541`) |
| 표면 | `shouldGenerateSurface`(`"should-surface"`) | 표면 규칙(기반암 포함) | `generateSurface`, 새 난수로 `generateBedrock`(같은 씨앗), 옛 API `generateChunkData` |
| 카버 | `shouldGenerateCaves`(`"should-caves"`) | 동굴·협곡 | `generateCaves`(`setDecorationSeed(seed,0,0)` = `setSeed(seed)`) |
| 장식 | `shouldGenerateDecorations`(`"should-decoration"`) | **구조물 조각과 지형지물 모두**(`addVanillaDecorations`) | 그 뒤 월드의 `BlockPopulator` 들(아래) |
| 처음 동물 | `shouldGenerateMobs`(`"should-mobs"`) | `spawnOriginalMobs`(노이즈 생성기만 — 평지는 비어 있다) | — |
| 구조물 높이 | — | — | `getBaseHeight(world, 난수(청크 좌표 씨앗), x, z, 높이맵)` — `UnsupportedOperationException` 이면 다시 묻지 않고 바닐라 |

- `getSeed(x,y,z)` 는 바닐라 `Mth.getSeed`. 모든 묻기는 같은 청크에 다시 물어도 같은 답(씨앗이 좌표·시드만의 함수).
- `ChunkData`(`CraftChunkData`): 청크 안 x·z 0..15, y 는 `[world.getMinHeight, world.getMaxHeight)`. 밖이면 쓰기는 무시, 읽기는 공기.
  `setRegion` 은 최대값 제외·잘라 맞춤. 블록 엔티티 블록이면 빈 블록 엔티티를 만든다. `getBiome(x,y,z)` 는 `getNoiseBiome(x>>2, y>>2, z>>2)`(높이는 잘라 맞춤).
  호출이 끝나면 링크를 끊어 더 쓰면 `IllegalStateException`. 1.21.1 `ChunkData` 에 `getHeight` 는 없다(뒤 판에서 생김).
- 옛 API `generateChunkData(World, Random, x, z, BiomeGrid)`: 처음 `UnsupportedOperationException`(기본)이면 다시 부르지 않는다. 난수는 생성기 하나가
  공유하는 `java.util.Random` 을 `x*341873128712 + z*132897987541` 로 다시 씨앗(`isParallelCapable()` 이 false 면 생성기 단위 동기화).
  `BiomeGrid` 는 청크 바이옴을 바로 읽고 쓴다. 돌려준 `OldCraftChunkData`(`createChunkData(world)` 로만 — 다른 구현이면 `IllegalArgumentException`)의
  **만들어진 섹션은 통째로 바꿔 끼운다**(쓰지 않은 칸은 공기, 바이옴은 옛 섹션 것을 옮긴다). 블록 엔티티 블록에는 빈 블록 엔티티.
- `BlockPopulator`: 생성기와 무관하게 **모든 월드**의 `getPopulators()`(만들 때 `getDefaultPopulators` 를 넣은 가변 목록)가 장식 단계에서 돈다
  (`ChunkGenerator#applyBiomeDecoration`, 바닐라 장식을 끈 청크도). 난수는 목록의 항목마다 새 `WorldgenRandom(LegacyRandomSource(seed))` 에
  `setDecorationSeed(seed, 청크x, 청크z)`(블록 좌표가 아니라 청크 좌표). 영역(`CraftLimitedRegion`)은 원점 청크 ±1 청크(버퍼 16칸, 높이 전체,
  최대값 제외) — 밖이면 `IllegalArgumentException`. 끝나면 링크를 끊는다. 옛 `populate(World, Random, Chunk)` 는 청크가 처음 올라갈 때
  주 스레드에서 따로 돈다(`LevelChunk#loadCallback`, `ChunkPopulateEvent` 와 함께).
- `World#getGenerator()` 는 그 생성기, `WorldCreator.copy(world)` 도 그것을 옮긴다.

## 2. Furnace 설계

### 2.1 API (furnace-api) — W3 훅을 넓힌다

- `WorldCreator.Builder#generationHooksObject(ChunkGenerationHooks)`: 키 대신 **훅 객체**를 직접 단다(T-210 공급자처럼). 이름(`ChunkGenerationHooks#name()`,
  기본 클래스 이름)은 level.dat 설명서 `generation_hooks_object` 에 남는다. 객체는 저장할 수 없어 다시 열 때는 공급자와 같은 규칙(2.4).
  키 훅과 객체 훅은 함께 쓸 수 없다(`IllegalArgumentException`). `World#generationHooksObject()` 는 단 객체(없으면 null).
- `ChunkGenerationHooks` 에 기본 메서드를 더한다(이미 있는 훅은 그대로 돈다):
  - `prepare(BiomeProvider.Context)` — 서버가 생성기에 훅을 걸기 직전(월드 스레드, 이미 있는 월드는 level.dat 시드).
  - `name()` — 설명서 표시.
  - `attached(String levelKey)` — 월드가 목록에 오른 직후(새 월드든 이미 있는 월드든, 틱·청크 전) 월드 스레드에서 한 번. Bukkit 다리가 기본
    BlockPopulator 를 다는 자리(CraftBukkit `initWorld`).
  - `fixedSpawn(String levelKey)` → `Location`(null 이면 바닐라 스폰 찾기). 새 월드에서만, `attached` 뒤·스폰 찾기 전에 월드 스레드에서.
- `GenerationStage` 에 두 단계를 더한다(바닐라 순서 끝에 붙여 이미 있는 순번은 그대로):
  - `DECORATION` — 장식 단계 전체(구조물 조각 + 지형지물). 끄면 둘 다 없다(Bukkit `shouldGenerateDecorations=false` 와 같다).
    지금의 `FEATURES` 는 그대로 "지형지물만"이다(이미 쓰는 훅의 뜻을 바꾸지 않는다). `FeaturePlacer` 주석의 "CraftBukkit 과 같다"는 틀린 말이라 고친다.
  - `MOBS` — 청크 생성 때 처음 놓는 동물(바닐라 `spawnOriginalMobs`, 노이즈 생성기만).
- `GenerationChunk#setBiome(x, y, z, 바이옴)` — 4칸 단위로 그 칸의 바이옴을 바꾼다(옛 API `BiomeGrid` 용). 장식 단계는 청크에 저장된 바이옴을 읽는다(바닐라와 같다).
- `DecorationRegion#height(GenerationHeightmap, x, z)` — `GenerationHeightmap` 에 `WORLD_SURFACE`·`MOTION_BLOCKING`·
  `MOTION_BLOCKING_NO_LEAVES`·`OCEAN_FLOOR` 를 더한다(장식 단계 영역의 높이맵. 값은 바닐라 `WorldGenRegion#getHeight` 와 같은 "첫 빈 칸 y").
- `World#addChunkDecorator(ChunkDecorator)`·`removeChunkDecorator` — 생성기와 무관하게 그 월드의 장식 단계 끝(바닐라 장식·키/객체 훅의
  `decorate` 뒤)에 도는 일(Bukkit `getPopulators`). 바닐라 장식을 끈 청크도 돈다. 디버그 월드는 돌지 않는다(바닐라 `DebugLevelSource` 가
  장식을 덮어써 Paper 도 안 돈다).

### 2.2 서버
- `GenHooks` 는 키 조회 대신 객체를 쥘 수 있다(`GenHooks.attachObject` — 월드가 사는 동안 세게 쥔다. 임시 월드 `freshCopy` 도 같은 객체).
- 노이즈(보통·평지)·`SuperflatWorldSource` 의 장식 경로: `vanilla(DECORATION)` 이 false 면 구조물 조각·지형지물을 건너뛰고, 그다음 훅 `decorate`,
  그다음 월드 장식 목록. `vanilla(MOBS)` 가 false 면 `freshlyGenerated` 를 세우지 않는다.
- `GenerationChunk#setBiome` 는 섹션 바이옴과 장식 단계가 쓰는 바이옴 배열을 함께 바꾼다.
- `WorldManager`: 새 월드면 `fixedSpawn` 을 먼저 묻고(목록에 올린 뒤), 없을 때만 바닐라 스폰 찾기. 다른 월드 이름의 Location 이면
  `IllegalStateException`("Cannot set spawn point for …", Paper 문구).

### 2.3 호환 (furnace-compat-paper)
- `org.bukkit.generator.ChunkGenerator` 를 Paper 1.21.1 표면 전부로(`generate*`·`shouldGenerate*`(두 꼴)·`getBaseHeight`·`getDefaultPopulators`·
  `getFixedSpawnLocation`·`canSpawn`·`isParallelCapable`·`shouldGenerateBedrock`·`BiomeGrid`·옛 `generateChunkData`·`createChunkData`·`ChunkData`).
  `BlockPopulator`, `LimitedRegion`, `RegionAccessor`(이 다리가 쓰는 부분), `Server#createChunkData`.
- `BukkitChunkGenerators.Adapted implements ChunkGenerationHooks`: 1.2 표 그대로 — 묻기는 그 씨앗의 레거시 난수로, `generate*` 는
  `ChunkData` 를 `GenerationChunk` 위에 얹은 것(`CompatChunkData`, 호출 뒤 닫힘)으로 부른다. 옛 API 는 `OldChunkData`(섹션별 블록 배열)를 받아
  만들어진 섹션의 4096칸을 모두 쓴다. 넘기는 `WorldInfo` 는 월드가 목록에 오른 뒤에는 그 Bukkit World(CraftBukkit 처럼 `(World) worldInfo` 가 된다),
  그 전에는 `LateWorldInfo`.
- 월드마다 장식 하나(`ChunkDecorator`)를 걸어 그 월드의 `getPopulators()` 목록(복사 후 쓰기 목록)을 돈다 — 목록이 비면 아무것도 안 한다.
  `LimitedRegion` 은 `DecorationRegion` 위에: 블록 읽기·쓰기(`getType`·`getBlockData`·`setType`·`setBlockData`), 바이옴 읽기,
  `getHighestBlockYAt`(높이맵 꼴 포함), `isInRegion`·`getBuffer`·`getCenter*`. 영역 밖은 Paper 문구의 `IllegalArgumentException`, populate 뒤에는
  `IllegalStateException`.
- 옛 `populate(World, Random, Chunk)` 만 덮어쓴 BlockPopulator 는 클래스마다 콘솔 경고 한 번(아직 부르지 않는다 — T-218).
- `CraftServer#createWorld`: 생성기가 있으면 위 객체 훅을 달고, 생성기의 기본 공급자는 T-210 공급자 길로. Furnace 에는 bukkit.yml 이 없어
  `worlds.<이름>.generator`·`biome-provider` 는 읽지 않는다(Paper 에서 bukkit.yml 에 그 월드 항목이 없을 때와 같다 — 결정 G2).
  `CraftWorld#getGenerator`·`getPopulators`.

### 2.4 저장·다시 열기 (T-210 E1 과 같은 규칙)
- level.dat `furnace:world` 에 `generation_hooks_object`(생성기 클래스 이름). 객체는 저장하지 않는다.
- Furnace 가 스스로 여는 길(자동 불러오기·`/worlds load`·저장된 설명서 그대로)은 표시가 있는데 객체가 없으면 열지 않는다
  (`NeedsGenerationHooksException`, 콘솔 한 줄) — 자동 불러오기가 플러그인보다 먼저 바닐라로 열면 플러그인이 나중에 생성기를 줘도
  "이미 있는 월드" 가 돌아와 생성기가 영영 안 붙는다.
- Bukkit `createWorld`: 생성기가 있으면 그것으로 연다. 없으면 Paper 처럼 바닐라로 열고 표시를 지운다(콘솔 경고 한 번).
- Furnace API: `toBuilder().generationHooksObject(객체)` 로 다시 붙이고, `generationHooksObject(null)` 이면 표시를 지운다.

### 2.5 스레드
- 묻기·`generate*`·`getBaseHeight`·`BlockPopulator#populate` 는 생성 스레드 여러 개에서 동시에(Paper 의 월드젠 워커와 같다). 옛 API 는
  `isParallelCapable()` 이 false 면 생성기 단위로 하나씩.
- `getDefaultBiomeProvider`·`getDefaultPopulators`·`getFixedSpawnLocation` 은 `createWorld` 를 부른 스레드/월드 스레드에서 월드를 만들 때 한 번.

## 3. 같음의 기준
- 같은 시드·같은 생성기면 청크 블록·바이옴 = Paper(바닐라 단계를 켠 생성기는 바닐라 단계 결과가 그대로라 난수 순서까지 같다).
- 플러그인이 받는 `Random` 의 수열이 Paper 와 같다(씨앗·래퍼 규칙) — 같은 플러그인 코드가 같은 블록을 놓는다.
- 받아들인 차이:
  - 묻기를 Paper 와 다른 횟수로 할 수 있다(Furnace 는 같은 청크를 여러 번 만들 수 있다 — 답은 같다).
  - 옛 API 섹션 교체 뒤의 `*_WG` 높이맵: Paper 는 섹션을 바꿔 끼워 갱신하지 않고, Furnace 는 표면 단계 뒤 블록에서 다시 만든다. `*_WG` 높이맵으로
    놓는 지형지물(바다 바닥 몇 가지)을 켠 옛 API 월드에서만 갈릴 수 있다.
  - 아직 없는 것(후속 T-218): 옛 `BlockPopulator#populate(World, Random, Chunk)`·`ChunkPopulateEvent`, `LimitedRegion` 의 엔티티·블록 상태
    (`getBlockState`·`getTileEntities`)·`setBiome`·`getComputedBiome`·`generateTree`·`scheduleBlockUpdate`(부르면 `UnsupportedFeature` — H5 원칙),
    Bukkit `World` 가 `RegionAccessor` 를 넓히는 것, `ChunkGenerator#canSpawn` 의 기본 본문(Paper 1.21.1 서버는 부르지 않는다 — 플러그인이
    `super.canSpawn` 을 직접 부를 때만 갈린다), `ChunkData` 의 `MaterialData` 판·`getData`(이 호환 계층에 옛 데이터 표가 없다).
  - 기본 세 월드의 bukkit.yml 생성기: Furnace 는 플러그인보다 먼저 기본 월드를 만들고 bukkit.yml 도 없어 이번에 하지 않는다(결정 G2, 후속 T-219).
  - `getFixedSpawnLocation` 은 WorldInitEvent 보다 먼저 불린다(Paper 는 WorldInitEvent 뒤). Furnace 는 WorldInitEvent 를 월드가 다 올라온 뒤
    전역 스레드에서 보낸다(T-210 부터 같은 순서).
  - `shouldGenerate*` 에서 `nextGaussian` 을 홀수 번 부르면 Paper 는 같은 청크 `generate*` 의 첫 `nextGaussian` 에 남은 값을 주지만 Furnace 는 새 난수다
    (묻기와 만들기가 서로 다른 훅 호출이다).
  - 플러그인 코드가 던진 예외: Paper 는 그 청크 생성이 실패하고, Furnace 는 W3 규칙대로 기록하고 그 단계의 훅만 건너뛴다.

## 4. 단계

| 단계 | 내용 | 시험 |
|---|---|---|
| 1 | API·서버: 객체 훅·`prepare`·`fixedSpawn`·`DECORATION`·`MOBS`·`setBiome`·장식 목록·저장 규칙 | 객체 훅 월드 만들기·다시 열기 거절, DECORATION 끄면 구조물 조각도 없음, MOBS, setBiome 이 장식 바이옴에 반영, 장식 목록 순서, 고정 스폰 |
| 2 | 호환: ChunkGenerator·ChunkData·옛 API·BlockPopulator·LimitedRegion·createWorld·bukkit.yml | 빈(void) 생성기·층 생성기 청크 블록, 묻기 난수 = Paper 식, 옛 API 섹션 교체·BiomeGrid, 장식 순서·난수, 고정 스폰, 재시작 규칙 |
