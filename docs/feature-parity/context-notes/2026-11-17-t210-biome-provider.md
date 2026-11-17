## 2026-11-17 바이옴 공급자로 만드는 보통 월드 (T-210)

설계: `docs/feature-parity/compat-t210-biome-provider-worlds-2026-11-17.md`. 결정: `docs/user-todo/entries/2026-11-17-t210-biome-provider.md`.

### 바닐라·Paper 대조 (기억에 의존한 부분은 "가정")
- CraftBukkit `ServerLevel` 생성자: 공급자가 있으면 `NoiseBasedChunkGenerator`·`FlatLevelSource` 둘만 `CustomWorldChunkManager` 로 바이옴 원천을 바꾼다(디버그는 그대로). 오버월드·네더·엔드 모두.
- 평지의 `RandomState` 는 `NoiseGeneratorSettings.dummy()`(`NoiseRouterData.none()` — 모두 `DensityFunctions.zero()`): 공급자가 받는 기후 값·최소·최대가 모두 0. Paper 는 `ChunkMap` 에서 `CustomChunkGenerator` 를 벗겨 같은 분기를 탄다.
- `FlatLevelGeneratorSettings.adjustGenerationSettings(바이옴)`: 평지 설정의 바이옴이 아니면 그 바이옴의 생성 설정 그대로 — 공급자가 고른 다른 바이옴에는 그 바이옴 지형지물(구조물 단계 3·4 포함)이 놓인다.
- Paper `createWorld(FLAT)` 의 `generatorSettings` 는 `DedicatedServerProperties.WorldDimensionData`(server.properties `generator-settings` 와 같은 코덱) — 비면 `{}` → 기본 프리셋(요새·마을).

### 함정·고친 것
- **`UniformHeight`·`TrapezoidHeight` 빈 범위**: 바닐라는 `min > max` 면 경고("Empty height range") 뒤 `min` 을 돌려주고 난수를 쓰지 않는다. Furnace 는 `nextInt(음수)` 로 생성 스레드가 죽었다. 평지 생성 문맥(minY 0)에서 오버월드 바이옴 지형지물(사막 등의 광석 `absolute` 음수 범위)을 놓을 때 생긴다 — 공급자 평지뿐 아니라 `features: true` 평지(평지 바이옴의 지형지물)에서도 같은 길이었다. `BiasedToBottom`·`VeryBiasedToBottom` 은 이미 바닐라처럼 막혀 있었다.
- 추가 평지 월드는 전에 `SuperflatWorldSource`(층만)였다. 오버월드 높이면 이제 주 월드 `level-type=flat` 과 같은 `NoiseWorldSource.flat` + level.dat 평지 설정. 이미 있는 Furnace 추가 평지는 level.dat 에 "구조물 없음·지형지물 끔"이 적혀 있어 블록이 그대로다(움직임을 막지 않는 층은 `fill_layer` 로 놓여 결과 같음).
- `/locate biome` 의 평지 지름길(제자리)은 공급자가 없을 때만(`flatFixedBiome()`).
- `WorldInfo#getUID` 는 `CraftWorld#getUID` 와 같은 식(레벨 키) — 월드가 생기기 전 `getBiomes` 에서도 같은 값.

### 받아들인 차이
- `getBiomes` 를 생성기를 만들 때 한 번 부른다(Paper 는 처음 쓸 때). 공급자 없이 Bukkit `createWorld` 로 다시 열면 Paper 와 같이 열되 콘솔 경고 한 번(Paper 는 없음).
- Furnace 가 스스로 여는 길(자동 불러오기·`/worlds load`)은 공급자 표시가 있으면 열지 않는다(Paper 에는 이 길이 없다 — 결정 E1).
- Bukkit `ChunkGenerator` 는 T-211.
