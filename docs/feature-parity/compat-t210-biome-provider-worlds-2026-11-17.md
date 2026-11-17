# T-210 바이옴 공급자로 만드는 보통 월드 설계 (2026-11-17)

로드맵 T-210. 범위는 `WorldCreator#biomeProvider`(Bukkit)·`WorldCreator.Builder#biomeProvider`(Furnace)로 만드는 **저장되는 월드**와,
T-203 결정 D3(평지 월드 `//regen <바이옴>` 오류)이다. 바이옴 공급자 길 자체(`furnace-api` `worldgen.BiomeProvider`, 서버 `ProvidedBiomes`,
호환 `BukkitBiomeProviders`)는 T-203 3단계(#487, `compat-nms-t203-regen-design-2026-11-17.md` 2.4)에서 생겼다.
Bukkit `ChunkGenerator`(`WorldCreator#generator`) 다리는 표면이 따로 커서 T-211 로 나눈다(그때까지 지금처럼 `UnsupportedFeature`).

## 1. Paper 1.21.1 이 하는 일

- `CraftServer#createWorld`: 생성기(`creator.generator()`)가 있고 공급자가 없으면 `generator.getDefaultBiomeProvider(worldInfo)`. 공급자는
  `ServerLevel` 생성자(CraftBukkit 패치)로 간다.
- `ServerLevel` 생성자: 공급자가 있으면 `CustomWorldChunkManager(이 CraftWorld, 공급자, 바이옴 레지스트리)` 를 바이옴 원천으로,
  생성기가 `NoiseBasedChunkGenerator` 면 `new NoiseBasedChunkGenerator(원천, 설정)`, `FlatLevelSource` 면 `new FlatLevelSource(평지 설정, 원천)`.
  그 밖의 생성기(디버그)는 공급자를 쓰지 않는다. 오버월드·네더·엔드 모두 같다.
- 평지 + 공급자(`FlatLevelSource(settings, 원천)`):
  - 청크 바이옴은 `fillBiomesFromNoise(원천, randomState.sampler())`. 평지의 `RandomState` 는 `NoiseGeneratorSettings.dummy()`(라우터가 모두
    `DensityFunctions.zero()`)라 **기후 값·최소·최대가 모두 0** 이다(`ChunkMap` 생성자, Paper 는 `CustomChunkGenerator` 를 벗겨 같은 분기).
  - 지형지물: `generationSettingsGetter = adjustGenerationSettings` — 평지 설정의 바이옴이면 층·호수·features 규칙, **다른 바이옴이면 그 바이옴의
    지형지물 그대로**. 지형지물 순서표는 원천의 `possibleBiomes()`(공급자 `getBiomes`).
  - 구조물: `ChunkGeneratorStructureState.createForFlat(…, 원천, structure_overrides)` — 원천의 바이옴 목록으로 세트를 거르고 바이옴 검사도 원천.
  - 카버·표면 규칙 없음(평지 그대로). 스폰은 공급자와 무관.
- `CraftWorld#getBiomeProvider()` 는 만들 때 준 공급자. 공급자는 어디에도 저장되지 않는다: 재시작 뒤에는 플러그인이 같은 공급자로
  `createWorld` 를 다시 불러야 하고, 공급자 없이 열면 새 청크는 바닐라 바이옴이다(경고 없음).
- 평지 `generatorSettings`(JSON)는 `DedicatedServerProperties.WorldDimensionData` 로 읽는다 — `server.properties` `generator-settings` 와
  같은 코덱(층이 없으면 기본 프리셋: 기반암·흙 2·잔디, 평원, **요새·마을**; `structure_overrides` 가 없으면 레지스트리의 모든 구조물 세트).

## 2. Furnace 설계

### 2.1 서버 생성기

- `NoiseWorldSource` 의 공개 생성 함수가 공급자를 받는다(`create(…, provider)`·`flat(…, provider)`). 노이즈 생성기는 T-203 3단계 그대로.
- **평지 + 공급자**(지금은 `UnsupportedOperationException`): `ProvidedBiomes` 에 "기후 0" 판(라우터 없음 — 값·최소·최대 0)을 두고,
  - `generateFlat` 의 청크 바이옴을 공급자로(바닐라 채우기 순서), `noiseBiome`·`/locate biome` 도 공급자 길(고정 바이옴 지름길은 공급자가 없을 때만),
  - 지형지물 순서표는 공급자 목록, 바이옴별 단계는 이미 있는 `FlatLayers#generationSteps`(평지 바이옴이 아니면 바이옴 JSON),
  - 구조물은 공급자 목록으로 거른다(노이즈와 같은 `StructureEngine(possible)` 길), 고정 바이옴 지름길(`fixedBiome`)은 공급자면 쓰지 않는다.
- 디버그·빈 월드(VOID)·높이가 다른 평지(아래 2.2)에 공급자를 주면 `IllegalArgumentException`(이유 문구). Paper 는 디버그에서 공급자를 조용히
  버리지만 Bukkit 으로는 디버그 월드를 만들 길이 없고, Furnace API 에서는 조용히 버리는 것보다 거절이 맞다.

### 2.2 추가 평지 월드를 바닐라 평지 생성기로

지금 추가 월드의 FLAT 은 층만 쌓는 `SuperflatWorldSource`(구조물·지형지물·호수 없음)다. 이 생성기로는 공급자가 정한 다른 바이옴의 지형지물을
놓을 수 없어(위 1장) D3 를 Paper 와 같게 못 고친다. 그래서

- 오버월드 높이(-64..320) 타입의 FLAT 추가 월드는 주 월드 `level-type=flat` 과 같은 `NoiseWorldSource.flat` 을 쓴다. 설정은 **그 월드
  level.dat 의 평지 설정**(바닐라와 같은 진실 — Paper 에서 가져온 평지 월드도 그 설정 그대로)을 읽는다(`flatSpecOf`). 모르는 바이옴은
  바닐라처럼 "Unknown biome, defaulting to plains".
- 새 월드의 평지 설정은 지금처럼 설명서의 층·바이옴에 지형지물·호수 끔·구조물 없음(Furnace API·`/worlds create … flat` 의 뜻 그대로 —
  이미 있는 월드도 level.dat 이 그렇게 적혀 있어 블록이 바뀌지 않는다. 다른 점: 움직임을 막지 않는 층은 바닐라처럼 `fill_layer` 지형지물로 놓인다 — 결과 블록은 같다).
- Furnace API 에 `flatGeneratorSettings(JSON)` 을 더한다: 값이 있으면 바닐라 `generator-settings` 와 같은 코덱(`SuperflatWorldSource.settingsNbt`)
  으로 읽어 level.dat 에 적는다. 호환 `createWorld(FLAT)` 은 Paper 처럼 `generatorSettings`(비면 `{}`)를 그대로 넘긴다 → **기본 프리셋은
  마을·요새가 생긴다**(Paper 와 같게 — 지금까지는 없었다).
- 높이가 다른 타입의 FLAT·VOID 는 지금처럼 `SuperflatWorldSource`(공급자 거절).

### 2.3 공급자에게 월드 정보 주기

Bukkit 공급자는 `getBiomes(WorldInfo)`·`getBiome(WorldInfo, …)` 로 월드 이름·UID·환경·시드·높이를 받는다. Furnace 는 생성기를 만들 때
(`CraftWorld` 가 생기기 전) `getBiomes` 를 부르므로, 서버가 생성기를 만들기 직전 `BiomeProvider#prepare(Context)`(이름·레벨 키·환경·시드·
최저·최고 높이)를 부른다. 호환 쪽 `LateWorldInfo` 는 이 값으로 채우고, UID 는 `CraftWorld#getUID` 와 같은 식(레벨 키에서)이라 월드가 생긴 뒤와 같다.
이미 있는 월드의 시드는 level.dat 것을 준다(설명서의 시드를 쓰지 않는다).

### 2.4 저장·다시 열기 규칙

- level.dat 설명서(`furnace:world`)에 `biome_provider`(공급자 이름 — Bukkit 이면 공급자 클래스 이름)를 남긴다. 공급자 객체는 저장할 수 없다.
- **Furnace 가 스스로 여는 길**(`worlds.autoload`, `/worlds load`, 저장된 설명서 그대로의 `createWorld`)은 공급자 표시가 있는데 공급자가 없으면
  열지 않는다(`NeedsBiomeProviderException`, 자동 불러오기는 경고 한 줄). 자동 불러오기는 플러그인 onEnable 앞이라, 여기서 바닐라 바이옴으로
  열면 플러그인이 나중에 공급자와 함께 `createWorld` 를 불러도 Paper 규칙대로 "이미 있는 월드"가 돌아와 공급자가 영영 안 붙는다. — 결정 E1.
- **Bukkit `createWorld`**(Paper 규칙): 공급자를 주면 그것으로 연다(저장된 생성기·구조물 설정 + 공급자). 주지 않으면 Paper 처럼 바닐라 바이옴으로
  열고 표시를 지운다(콘솔 경고 한 번 — 새 청크 경계가 생긴다는 것만 알린다. Paper 는 경고가 없다).
- Furnace API: `toBuilder().biomeProvider(p)` 로 공급자를 다시 붙이고, `biomeProvider(null)` 이면 표시를 지우고 바닐라로 연다.
- 이미 올라온 월드를 같은 이름으로 다시 만들면(공급자가 다른 객체여도) 그 월드 — Bukkit 은 Paper 그대로, Furnace API 는 다른 공급자 객체를 주면
  "다른 설명서" 예외(지금 규칙과 같은 뜻).
- `CraftWorld#getBiomeProvider()` 는 만들 때 준 Bukkit 공급자(Furnace API `World#biomeProvider()` 를 벗긴 것), `WorldCreator.copy(world)` 도 그것을 옮긴다.

### 2.5 스레드

공급자는 생성 스레드 여러 개에서 동시에 불린다(Paper 의 월드젠 워커와 같다 — 상태를 바꾸지 말 것). `prepare` 와 `biomes` 는 월드 스레드에서
생성기를 만들 때 한 번. 서버 생성기는 공급자 객체를 세게 쥐고(월드가 사는 동안), 내리면 같이 놓는다. 임시 월드(T-203)의 약한 참조 규칙은 그대로.

## 3. 같음의 기준

- 같은 시드·같은 공급자면 공급자 월드 청크의 바이옴·블록 = Paper `createWorld(…biomeProvider)` 결과(노이즈: T-203 4단계 대조와 같은 길).
- 평지 + 공급자: 바이옴은 기후 0 으로 부른 공급자 답, 지형지물은 평지 바이옴이면 평지 규칙·다른 바이옴이면 그 바이옴 지형지물, 구조물은 그 설정.
- 공급자를 Paper 와 다른 때에 부르는 것(받아들인 차이, T-203 #487 검토 4장과 같다): `getBiomes` 는 생성기를 만들 때 한 번(Paper 는 처음 쓸 때).

## 4. 단계

| 단계 | 내용 | 시험 |
|---|---|---|
| 1 | 서버: 생성기 공급자 인자, 평지 + 공급자(기후 0), 추가 FLAT → 바닐라 평지 생성기(level.dat 설정), `prepare` | 평지 + 단일 바이옴 공급자 = 그 바이옴 평지 설정 월드와 같은 바이옴·지형지물 판정, 기후 0, 이미 있는 추가 평지 월드 블록 그대로 |
| 2 | API·저장 규칙: `biomeProvider`·`flatGeneratorSettings`, `biome_provider` 표시, 자동 불러오기 거절 | 재시작 시뮬레이션: 표시 남음 → load 거절 → 공급자 주면 열림 → 공급자 없이 Bukkit 이면 열리고 표시 지움 |
| 3 | 호환: `createWorld` 공급자·평지 JSON 그대로, `getBiomeProvider`, `WorldInfo` | Bukkit 공급자 월드 청크 바이옴 = 공급자 답, `getBiomes` 의 WorldInfo 값, 기본 평지에 마을 구조물 시작점 |
| 4 | D3: 평지 월드 `//regen <바이옴>` | NmsRegenTest 평지 판(주 평지·추가 평지) |
