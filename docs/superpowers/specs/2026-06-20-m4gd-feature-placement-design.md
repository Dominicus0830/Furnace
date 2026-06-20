# M4g-d Feature Placement Design

## Overview

Implement vanilla-compatible feature decoration for Furnace worldgen.
Goal: 0-mismatch parity with oracle on full-chunk block state IDs, post-carvers phase.

Pipeline position: noise → surface → carvers → **features** → (light, full)

Scope: ore blobs, springs, disk patches, geodes, vegetation, trees, freeze/snow.
Structures (jigsaw/stronghold) deferred to M4g-e.

---

## MC 1.21.1 Feature Pipeline

### Feature Steps

`GenerationStep.Decoration` enum, **11 values** (ordinal 0-10) — vanilla 소스 확인 완료:

| ordinal | name | 주요 내용 |
|---------|------|---------|
| 0 | RAW_GENERATION | 없음 (오버월드) |
| 1 | LAKES | 물/용암 호수 |
| 2 | LOCAL_MODIFICATIONS | Amethyst geode, 부딪힌 블록 |
| 3 | UNDERGROUND_STRUCTURES | 없음 (오버월드) |
| 4 | SURFACE_STRUCTURES | 사막 우물, 화석 |
| 5 | STRONGHOLDS | (구조물 선행) |
| 6 | UNDERGROUND_ORES | **모든 광석 blob** |
| 7 | UNDERGROUND_DECORATION | glow_lichen, extra_gold 등 |
| 8 | FLUID_SPRINGS | 물/용암 spring |
| 9 | VEGETAL_DECORATION | 나무, 꽃, 풀, 버섯 |
| 10 | TOP_LAYER_MODIFICATION | FreezeTopLayer (눈/얼음) |

### Feature Gathering (바이옴 기반) — 확인 완료

바닐라는 **3×3 chunk 이웃** 전체 섹션에서 unique 바이옴 수집:
```java
// ChunkGenerator.applyBiomeDecoration 실제 코드
ChunkPos.rangeClosed(sectionPos.chunk(), 1)  // 반경 1 = 9 chunk
    .forEach(chunkPos -> {
        for (LevelChunkSection section : level.getChunk(chunkPos).getSections())
            section.getBiomes().getAll(set::add);
    });
set.retainAll(this.biomeSource.possibleBiomes());
```

**Furnace 구현 방침 (최적화 우선):**
- 이웃 청크 **블록 풀 생성 불필요** — 바이옴 quart만 필요
- `BiomeSampler.sampleChunk(neighborCx, neighborCz, nc)` 호출로 biomeId 배열만 추출
- 단, nc(NoiseChunk)는 이웃 청크용 새로 생성 — biomeIds 배열 128B 메모리만 사용
- 결과 biomeId set → 캐시 불필요 (청크당 1회 실행)

### FeatureSorter — 전역 인덱스 (핵심)

바닐라 `FeatureSorter.buildFeaturesPerStep()`:
- **모든 바이옴**의 placed_feature를 step별로 위상 정렬(DFS)
- 각 PlacedFeature에 **전역 고유 인덱스** 부여 (첫 등장 순서 기반)
- step별 `StepFeatureData` = 해당 step에 속하는 feature의 정렬된 리스트

`setFeatureSeed(decorationSeed, i3, stepOrdinal)` 에서 `i3` =
`stepFeatureData.indexMapping().applyAsInt(placedFeature)` =
**해당 step의 StepFeatureData.features() 리스트 내 위치**.
(running counter 아님, step별 독립, 전체 바이옴 통합 정렬 기준)

---

## Data Loading

### 필요 JSON

```
worldgen/1.21.1/
├── worldgen/biome/*.json           ← "features": [[...], [...], ...]  (11개 배열)
├── worldgen/placed_feature/*.json  ← {"feature": ..., "placement": [...]}
└── worldgen/configured_feature/*.json  ← {"type": "...", "config": {...}}
```

`WorldgenData` 확장: `biome(String id)`, `placedFeature(String id)`, `configuredFeature(String id)`.

기존 `data.noiseSettings()` 패턴 그대로 `data.jsonResource(path)` 로 로드.

---

## Seeding System — 확인 완료

### setDecorationSeed

바닐라 소스 그대로 — `| 1L` 있음, XZ는 `+` 합산 후 `^` levelSeed:

```java
// WorldgenRandom에 추가
public long setDecorationSeed(long worldSeed, int blockX, int blockZ) {
    setSeed(worldSeed);
    long a = nextLong() | 1L;   // | 1L 유지 (carver와 다름)
    long b = nextLong() | 1L;
    long decorSeed = ((long) blockX * a + (long) blockZ * b) ^ worldSeed;
    setSeed(decorSeed);
    return decorSeed;
}
```

carver(`setLargeFeatureSeed`)와 비교:
- carver: `(x*a) ^ (z*b) ^ seed` — `| 1L` 없음, XOR만
- decoration: `(x*a + z*b) ^ seed` — `| 1L` 있음, + 후 XOR

### setFeatureSeed

```java
// 바닐라 공식 그대로
public void setFeatureSeed(long decorationSeed, int featureIndex, int stepOrdinal) {
    setSeed(decorationSeed + featureIndex + 10000L * stepOrdinal);
}
```

`featureIndex` = 해당 step의 **FeatureSorter 정렬 리스트 내 위치** (아래 FeatureSorter §).

### 호출 위치

```
blockX = cx * 16  (chunk의 minBlockX)
blockZ = cz * 16  (chunk의 minBlockZ)
```

---

## Architecture

### FeatureSorter (최적화 핵심 — init 1회)

NoiseWorldSource 생성 시 **1회** 빌드, 이후 모든 청크에서 재사용:

```java
public final class FeatureSorter {
    // step별 sorted PlacedFeature 리스트 + index lookup
    private final List<StepFeatureData> featuresPerStep; // 11개

    public record StepFeatureData(
        List<PlacedFeature> features,                // 정렬된 전체 feature 리스트
        Reference2IntMap<PlacedFeature> indexMap     // feature → 리스트 내 위치 (IDENTITY, O(1))
    ) {}

    // 모든 바이옴을 고려해 위상 정렬 → featuresPerStep 구성
    public static FeatureSorter build(BiomeFeatureData allBiomes) { ... }
}
```

> **[리뷰 반영 — interning 필수]** 바닐라 `StepFeatureData`의 indexMapping은
> `Util.createIndexIdentityLookup` = **레퍼런스 동일성(identity) 맵**이다 (equals 아님).
> 동작 전제: placed_feature id 1개당 인스턴스 **딱 1개**, 모든 바이옴이 그 단일
> 인스턴스를 공유. 따라서 `indexMap`은 `Object2IntOpenHashMap`(equals)이 아니라
> **`Reference2IntOpenHashMap`(identity)** 로 둔다.
> **선행 요구**: `WorldgenData`가 placed_feature를 id 키로 **인터닝**해 단일 인스턴스를
> 캐시하고, biome·sorter·collect 전부 그 인스턴스를 참조해야 한다. 바이옴마다 JSON
> 재파싱해 별개 인스턴스를 만들면 identity 룩업이 전부 부재 → 조용히 feature 누락.

바닐라 위상 정렬 알고리즘 (DFS):
1. 각 바이옴의 feature 목록 순서로 "A → B" 의존 엣지 추가
2. DFS 역순 = 실행 순서
3. 사이클 존재 시 IllegalStateException

**최적화 포인트:**
- `indexMap` = `Object2IntOpenHashMap` (fastutil, O(1) lookup)
- 위상 정렬은 전체 바이옴 데이터 로드 후 1회 — per-chunk 비용 없음

### FeatureWorldView

Feature 배치는 이웃 청크 블록에도 쓸 수 있다(나무 가지, 대형 광맥 등).

```java
public final class FeatureWorldView {
    private final ChunkColumn center;
    private final ChunkColumn[] neighbors; // [0..8], lazy init, null = 미생성
    private final NoiseWorldSource source;
    private final int cx, cz;

    // 이웃 청크 lazy 생성 — 실제로 쓰기가 발생할 때만 생성
    private ChunkColumn getOrCreateNeighbor(int nx, int nz) {
        int idx = (nz - cz + 1) * 3 + (nx - cx + 1);
        if (idx == 4) return center; // 자신
        if (neighbors[idx] == null)
            neighbors[idx] = source.generateWithCarvers(nx, nz); // carvers까지만
        return neighbors[idx];
    }

    public void setBlock(int worldX, int worldY, int worldZ, int stateId) {
        int nx = Math.floorDiv(worldX, 16);
        int nz = Math.floorDiv(worldZ, 16);
        if (Math.abs(nx - cx) > 1 || Math.abs(nz - cz) > 1) return; // 범위 밖 무시
        int lx = worldX - nx * 16; int lz = worldZ - nz * 16;
        getOrCreateNeighbor(nx, nz).setBlock(lx, worldY, lz, stateId);
    }
}
```

**최적화 포인트:**
- 이웃 쓰기는 드묾 → lazy init으로 일반 케이스 비용 0
- 이웃 청크가 생성되면 features 단계도 추후 호출 시 재활용 가능 (캐시 설계 여지)

### FeaturePlacer

```java
public final class FeaturePlacer {
    private final FeatureSorter sorter;        // init 시 1회 빌드
    private final BiomeFeatureData biomeData;  // biome → step → feature list (raw)
    private final long seed;

    public void decorate(FeatureWorldView view, Set<String> biomeSet, int cx, int cz) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandom(0L));
        long decorSeed = random.setDecorationSeed(seed, cx * 16, cz * 16);

        for (int step = 0; step < 11; step++) {
            FeatureSorter.StepFeatureData stepData = sorter.featuresPerStep().get(step);
            // 이 step에 등장하는 feature index 집합 (biome set 기준)
            IntSortedSet featureIndices = collectIndices(biomeSet, step, stepData);

            WorldgenRandom featRandom = new WorldgenRandom(new LegacyRandom(0L));
            for (int idx : featureIndices) { // 이미 정렬된 int set
                PlacedFeature feature = stepData.features().get(idx);
                featRandom.setFeatureSeed(decorSeed, idx, step);
                feature.place(view, featRandom, cx * 16, cz * 16);
            }
        }
    }

    // 이 step에서 biomeSet에 속하는 feature들의 index 수집 (정렬 유지)
    private IntSortedSet collectIndices(Set<String> biomes, int step,
                                        FeatureSorter.StepFeatureData data) {
        IntSortedSet result = new IntRBTreeSet(); // fastutil, 자동 정렬
        for (String biome : biomes) {
            List<PlacedFeature> features = biomeData.featuresForStep(biome, step);
            for (PlacedFeature f : features) {
                int idx = data.indexMap().getInt(f); // identity 룩업
                // 부재(-1)는 interning 깨짐 신호 → 조용히 버리지 말고 즉시 실패
                if (idx < 0) throw new IllegalStateException(
                    "PlacedFeature not interned: " + f + " (step " + step + ")");
                result.add(idx);
            }
        }
        return result;
    }
}
```

**최적화 포인트:**
- `IntRBTreeSet` (fastutil): 중복 제거 + 자동 정렬, boxing 없음
- `Object2IntOpenHashMap` indexMap으로 feature → index O(1)
- step당 biome 순회 = biome 수 × feature 수 — 청크 내 unique biome은 통상 2~5개

### Feature Interface

```java
public sealed interface ConfiguredFeature
    permits OreFeature, SpringFeature, DiskFeature, LakeFeature,
            GeodeFeature, TreeFeature, VegetationPatchFeature,
            SimpleBlockFeature, FreezeTopLayerFeature {

    boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z);
}
```

### PlacedFeature

```java
public record PlacedFeature(ConfiguredFeature feature, List<PlacementModifier> placement) {
    public void place(FeatureWorldView view, WorldgenRandom random, int blockX, int blockZ) {
        // placement modifier 체인으로 위치 스트림 생성 후 각 위치에 feature.place()
        Stream<int[]> positions = Stream.of(new int[]{blockX, 0, blockZ});
        for (PlacementModifier mod : placement)
            positions = positions.flatMap(pos -> mod.filter(view, random, pos));
        positions.forEach(pos -> feature.place(view, random, pos[0], pos[1], pos[2]));
    }
}
```

> **[리뷰 반영 — Stream 게으름(laziness) 보존 필수]** 위 코드는 겉보기 2단계
> ("위치 다 만들고 → 다 배치")지만, **반드시 게으른 Java Stream으로 유지**해야 한다.
> 단일 공유 `random`을 modifier 체인과 `feature.place`가 함께 소비하며, 게으른
> flatMap+forEach는 시작점 하나당 **깊이우선으로 modifier-random과 feature-random을
> 교차 소비**한다 (바닐라 `PlacedFeature.placeWithContext` 그대로).
> **절대 금지**: `positions`를 `List`로 모아(eager) "전체 modifier 소비 후 전체 배치"로
> 바꾸기 → random 소비 순서 역전 → 전 청크 parity 붕괴.
> 또한 각 `PlacementModifier.filter`는 random 소비 **시점**(호출 즉시 sample vs
> 원소당)을 바닐라 해당 modifier와 동일하게 복제해야 한다 — modifier별 단위테스트로 검증.

### PlacementModifier (주요 구현 대상)

| Modifier | 역할 |
|----------|------|
| `CountPlacement` | n회 반복 |
| `RarityFilterPlacement` | 1/n 확률 필터 |
| `HeightRangePlacement` | Y 범위 균등/삼각 샘플 |
| `InSquarePlacement` | XZ +0..15 오프셋 |
| `SurfaceRelativeThresholdFilter` | 표면 기준 Y 필터 |
| `BiomeFilter` | 해당 위치 바이옴 검사 |
| `CountOnEveryLayerPlacement` | 섹션 레이어당 count |

> placement modifier 체인이 결정론적이어야 함. 각 modifier는 random을 정확히 소비.
> Stream lazy evaluation → 위치 생성 중 random 소비 순서 바닐라와 동일해야 함.

---

## Feature Implementations (우선순위)

### Priority 1: OreFeature (`minecraft:ore`, `minecraft:scattered_ore`)

광석 blob — 가장 많이 보임. step 6(UNDERGROUND_ORES) 핵심.

```java
// OreConfig: targetBlock, size, discard_chance_on_air_exposure
// 알고리즘: size개의 타원형 blob을 랜덤 중심 근방에 생성
// replaceable 조건: target block list에 포함되면 교체
// discard_chance: 블록 루프 안에서 random 소비 — 순서 중요
```

### Priority 2: SpringFeature (`minecraft:spring_feature`)

물/용암 spring. step 8(FLUID_SPRINGS). 구조 단순.
조건: 인접 6면 중 rock 4+ 면, target fluid 미설치. 단일 블록.

### Priority 3: DiskFeature (`minecraft:disk`)

원형 disk: gravel/clay/sand 패치. step 7.

### Priority 4: GeodeFeature (`minecraft:geode`)

Amethyst geode. step 2(LOCAL_MODIFICATIONS). 복잡하나 독립적.

### Priority 5: FreezeTopLayerFeature (`minecraft:freeze_top_layer`)

눈/얼음 레이어. step 10. 단순. SurfaceY 의존.

### Priority 6: 식생 (VegetationPatchFeature, TreeFeature)

가장 복잡. TreeFeature = 줄기 + 가지 + 잎 알고리즘 여러 종.
1차 parity는 지하 광석만으로도 대부분 달성 가능.

---

## Oracle Extension

### dumpFeatureChunk (추가)

`arg: "feature:cx:cz"` → `feature_{seed}_{cx}_{cz}.bin`

파이프라인: fillFromNoise → buildSurface → carvers → **applyBiomeDecoration**

`applyBiomeDecoration`은 `WorldGenLevel` 필요. Oracle 구현:

```java
// 접근: ProtoChunk 기반 WorldGenRegion 또는 단순 WorldGenLevel stub
// Paper 내부 SimpleWorldGenRegion이 9청크 지원 — import 가능 여부 확인 필요
// 대안: applyBiomeDecoration 로직을 직접 펼쳐서 WorldGenLevel stub 최소화
```

> **[리뷰 반영 — oracle 충실도 = 최대 리스크]** fixture가 parity의 ground truth라
> oracle이 틀리면 Furnace가 **틀린 정답을 쫓는다**. `applyBiomeDecoration`이 요구하는 것:
> - `registryAccess()` → STRUCTURE + PLACED_FEATURE 레지스트리
> - `getChunk(x,z)` 9개 이웃, biome 섹션 채워진 ChunkAccess
> - `getSeed`, `setCurrentlyGenerating`
> - BiomeFilter modifier → `level.getBiome(pos)`
> - 각 feature → getBlockState/setBlock/getHeight
>
> 손으로 깎은 stub은 biome 룩업·height accessor 한 군데만 어긋나도 fixture 오염.
> **방침 확정**: stub 새로 짜지 말고 **실제 `WorldGenRegion`(9-chunk ProtoChunk) 재사용**.
> **oracle 자가검증 선행**: fixture 박제 전, 같은 seed로 vanilla 서버가 실제 생성한
> 청크 dump vs oracle dump를 최소 1개 대조해 oracle 자체를 먼저 검증한다.

### Fixture 포맷

```
magic  : 0x46454154 ("FEAT")
seed   : int64 le
cx     : int32 le
cz     : int32 le
blocks : HEIGHT * 16 * 16 * int16  (carver fixture와 동일 레이아웃, uint16 state id)
```

---

## Parity Test

`FeatureParityTest.java`:
```java
@Test void featureChunk_seed0_cx0_cz0() throws Exception { ... }
@Test void featureChunk_seed0_cx0_cz5() throws Exception { ... }
@Test void featureChunk_seed123456789_cx0_cz0() throws Exception { ... }
```

- Fixture: `worldgen/fixtures/feature_{seed}_{cx}_{cz}.bin`
- 비교: Furnace `generateWithFeatures()` vs oracle uint16 state ID
- 3 fixture 0 mismatch 달성 전까지 반복

---

## Integration with NoiseWorldSource

```java
public ChunkColumn generateWithFeatures(int chunkX, int chunkZ) {
    ChunkColumn col = generate(chunkX, chunkZ);
    NoiseChunk nc = new NoiseChunk(chunkX, chunkZ, router, settings);
    applyCarvers(col, chunkX, chunkZ, nc);

    // 3×3 이웃 biome 수집 (block 생성 없이 biome quart만)
    Set<String> biomeSet = collectNeighborBiomes(chunkX, chunkZ);

    FeatureWorldView view = new FeatureWorldView(col, this, chunkX, chunkZ);
    featurePlacer.decorate(view, biomeSet, chunkX, chunkZ);
    col.clearDirty();
    return col;
}

private Set<String> collectNeighborBiomes(int cx, int cz) {
    Set<String> result = new ObjectOpenHashSet<>();
    for (int dx = -1; dx <= 1; dx++) {
        for (int dz = -1; dz <= 1; dz++) {
            int ncx = cx + dx, ncz = cz + dz;
            NoiseChunk nc = new NoiseChunk(ncx, ncz, router, settings);
            int[] ids = biomeSampler.sampleChunk(ncx, ncz, nc);
            for (int id : ids) {
                if (id >= 0 && id < registryBiomeNames.length)
                    result.add(registryBiomeNames[id]);
            }
        }
    }
    return result;
}
```

**최적화 고려:**
- `collectNeighborBiomes` = NoiseChunk 9개 생성. 각 ~2ms → 합계 ~18ms 추가.
- 미래: biome quart-only 샘플링 경로 분리 (NoiseChunk 없이 기후 DF만 평가) → ~1ms×9.
- 현재는 correctness 우선, 병목 확인 후 최적화.

---

## File Map

| Action | Path |
|--------|------|
| Modify | `world/gen/carver/WorldgenRandom.java` — `setDecorationSeed`, `setFeatureSeed` 추가 |
| Create | `world/gen/feature/FeatureSorter.java` |
| Create | `world/gen/feature/FeatureWorldView.java` |
| Create | `world/gen/feature/PlacementModifier.java` |
| Create | `world/gen/feature/PlacedFeature.java` |
| Create | `world/gen/feature/ConfiguredFeature.java` |
| Create | `world/gen/feature/BiomeFeatureData.java` |
| Create | `world/gen/feature/FeaturePlacer.java` |
| Create | `world/gen/feature/OreFeature.java` |
| Create | `world/gen/feature/SpringFeature.java` |
| Create | `world/gen/feature/DiskFeature.java` |
| Create | `world/gen/feature/FreezeTopLayerFeature.java` |
| Modify | `world/gen/NoiseWorldSource.java` — `generateWithFeatures()`, `collectNeighborBiomes()` |
| Modify | `worldgen-oracle/OracleMain.java` — `feature:cx:cz` 추가 |
| Create | `test/.../FeatureParityTest.java` |
| Create | `test/resources/worldgen/fixtures/feature_*.bin` (3 fixtures) |

전체 패키지: `org.domi.furnace.server.world.gen.feature.*`

---

## Known Traps

- **FeatureSorter DFS 순서**: 바닐라와 동일한 위상 정렬 필요. `FeatureSorter.java` 소스
  그대로 포팅 (`Graph.depthFirstSearch` 로직 포함). 순서 틀리면 모든 seeding 불일치.
- **setDecorationSeed 공식**: `((x*a) + (z*b)) ^ seed`. 연산자 우선순위 주의
  (`*` > `+` > `^`). Java에서 `x*a + z*b ^ seed`는 `(x*a + z*b) ^ seed` — 맞음.
  C/Kotlin 등 다른 언어 포팅 시 괄호 명시.
- **이웃 청크 biome 수집**: 3×3 범위. `collectNeighborBiomes`가 center 청크 nc를
  2번 생성하지 않도록 주의 (dx=0,dz=0 케이스 재사용).
- **OreFeature discard_chance** (소스 확인 완료 — `OreFeature.java:178,182`): 배치 아님,
  **블록 루프 안 인라인·단락**. 후보 블록마다 `target.test(state, random)` 먼저 →
  `&&` 단락 → `shouldSkipAirCheck(random, discardChance)`. test가 거짓이면 discard random
  안 씀. `shouldSkipAirCheck`는 `discardChance > 0`일 때만 `nextFloat()` 소비
  (`chance <= 0`이면 nextFloat 전에 return). 이 인라인·단락 순서 그대로 포팅.
- **이웃 청크 쓰기 lazy init**: 나무/geode가 이웃에 쓸 경우 lazy 생성. 생성된 이웃 청크는
  features 미적용 상태(carvers까지만) — 이웃 청크 feature 적용은 그 청크가 자체 decoration
  호출될 때 처리됨 (바닐라 동일 방식).
- **Oracle WorldGenLevel**: `applyBiomeDecoration` 시그니처가 `WorldGenLevel` 요구.
  `ProtoChunk` + stub `WorldGenLevel` 구현 or Paper internals 활용.
- **Geode step 2**: LOCAL_MODIFICATIONS가 UNDERGROUND_ORES(step 6)보다 먼저 → geode
  내부에 광석 blob이 나중에 덮어씀. 바닐라 동일 순서.
- **collectNeighborBiomes 비용**: 9×NoiseChunk 생성 ≈ 18ms 추가. 현 청크 총 ~32ms
  예상. 병목 확인 후 biome-only 경로 최적화 가능.
