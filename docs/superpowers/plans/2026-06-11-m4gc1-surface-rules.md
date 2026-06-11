# M4g-c1 Surface Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 바닐라 1.21.1 표층 블록(grass/dirt/sand/bedrock 등)을 동일 시드에서 비트일치로 생성한다.

**Architecture:** `noise_settings/overworld.json`의 `surface_rule` JSON 트리를 파싱해 `SurfaceRule` 봉인 인터페이스 트리로 빌드. noise fill 완료 후 `SurfaceSystem.buildSurface()` 별도 패스로 컬럼 top-down 순회하며 블록 교체. oracle `surface` 서브커맨드로 바닐라 전체 블록 state ID fixture 덤프 → `SurfaceParityTest` 비트 비교.

**Tech Stack:** Java 21, Gson, JUnit 5, 기존 NoiseRouter/NoiseChunk/WorldgenData/XoroshiroRandomSource 패턴

---

## 파일 맵

**신규 생성:**
- `furnace-server/src/main/java/org/domi/furnace/server/world/gen/surface/SurfaceRule.java`
- `furnace-server/src/main/java/org/domi/furnace/server/world/gen/surface/SurfaceRuleParser.java`
- `furnace-server/src/main/java/org/domi/furnace/server/world/gen/surface/SurfaceContext.java`
- `furnace-server/src/main/java/org/domi/furnace/server/world/gen/surface/SurfaceSystem.java`
- `furnace-server/src/main/resources/worldgen/1.21.1/biome_temperatures.json`
- `furnace-server/src/test/java/org/domi/furnace/server/world/gen/surface/SurfaceRuleParserTest.java`
- `furnace-server/src/test/java/org/domi/furnace/server/world/gen/SurfaceParityTest.java`

**기존 수정:**
- `furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseRouter.java` — `initialDensityWithoutJaggedness` 필드 추가
- `furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseChunk.java` — `getPreliminarySurfaceLevel(quartX, quartZ)` 추가
- `furnace-server/src/main/java/org/domi/furnace/server/world/gen/WorldgenData.java` — `biomeTemperature(name)` 추가
- `furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseWorldSource.java` — `SurfaceSystem.buildSurface()` 호출 추가
- `worldgen-oracle/src/main/java/org/domi/oracle/OracleMain.java` — `surface` 서브커맨드 추가
- `worldgen-oracle/src/main/java/org/domi/oracle/FixtureFormat.java` — surface magic 상수 추가

---

### Task 1: NoiseRouter — initialDensityWithoutJaggedness

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseRouter.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/gen/NoiseRouterTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`NoiseRouterTest.java`에 추가:
```java
@Test
void initialDensityWithoutJaggednessNotNull() {
    WorldgenData data = WorldgenData.load("worldgen/1.21.1");
    NoiseSettings settings = NoiseSettings.fromJson(data.noiseSettings("minecraft:overworld"));
    NoiseRouter router = NoiseRouter.build(data, settings, 0L);
    assertNotNull(router.initialDensityWithoutJaggedness());
}
```

- [ ] **Step 2: 테스트 실패 확인**

```
./gradlew :furnace-server:test --tests "*.NoiseRouterTest.initialDensityWithoutJaggednessNotNull"
```
Expected: FAIL — `initialDensityWithoutJaggedness() 메서드 없음`

- [ ] **Step 3: NoiseRouter.java 수정**

```java
// NoiseRouter.java — 필드 추가
private final DensityFunction initialDensityWithoutJaggedness;

// 생성자 파라미터 추가
private NoiseRouter(DensityFunction finalDensity,
                    DensityFunction initialDensityWithoutJaggedness,
                    DensityFunction temperature, DensityFunction humidity,
                    DensityFunction continents, DensityFunction erosion,
                    DensityFunction depth, DensityFunction ridges) {
    this.finalDensity = finalDensity;
    this.initialDensityWithoutJaggedness = initialDensityWithoutJaggedness;
    this.temperature  = temperature;
    this.humidity     = humidity;
    this.continents   = continents;
    this.erosion      = erosion;
    this.depth        = depth;
    this.ridges       = ridges;
}

// build() 수정 — parser.parseElement 호출 추가
return new NoiseRouter(
    parser.parseElement(routerJson.get("final_density")),
    parser.parseElement(routerJson.get("initial_density_without_jaggedness")),
    parser.parseElement(routerJson.get("temperature")),
    parser.parseElement(routerJson.get("vegetation")),
    parser.parseElement(routerJson.get("continents")),
    parser.parseElement(routerJson.get("erosion")),
    parser.parseElement(routerJson.get("depth")),
    parser.parseElement(routerJson.get("ridges"))
);

// getter 추가
public DensityFunction initialDensityWithoutJaggedness() { return initialDensityWithoutJaggedness; }
```

- [ ] **Step 4: 테스트 통과 확인**

```
./gradlew :furnace-server:test --tests "*.NoiseRouterTest.initialDensityWithoutJaggednessNotNull"
```
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseRouter.java
git add furnace-server/src/test/java/org/domi/furnace/server/world/gen/NoiseRouterTest.java
git commit -m "feat(worldgen): NoiseRouter add initialDensityWithoutJaggedness field"
```

---

### Task 2: NoiseChunk — getPreliminarySurfaceLevel

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseChunk.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/gen/NoiseChunkTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`NoiseChunkTest.java`에 추가:
```java
@Test
void preliminarySurfaceLevelInRange() {
    WorldgenData data = WorldgenData.load("worldgen/1.21.1");
    NoiseSettings settings = NoiseSettings.fromJson(data.noiseSettings("minecraft:overworld"));
    NoiseRouter router = NoiseRouter.build(data, settings, 0L);
    NoiseChunk nc = new NoiseChunk(0, 0, router, settings);

    int level = nc.getPreliminarySurfaceLevel(0, 0);
    // overworld seed=0 origin: surface should be roughly sea level range
    assertTrue(level >= -64 && level <= 320,
        "preliminary surface level out of range: " + level);
}
```

- [ ] **Step 2: 테스트 실패 확인**

```
./gradlew :furnace-server:test --tests "*.NoiseChunkTest.preliminarySurfaceLevelInRange"
```
Expected: FAIL — `getPreliminarySurfaceLevel() 메서드 없음`

- [ ] **Step 3: NoiseChunk.java 수정**

`NoiseChunk` 생성자에서 `initialDensityWithoutJaggedness`를 Cache2D로 래핑해 저장:
```java
// 필드 추가 (wrappedFinalDensity 선언 아래)
private final DensityFunction wrappedInitialDensity; // for preliminarySurfaceLevel

// 생성자 — wrappedFinalDensity 초기화 직후 추가
this.wrappedInitialDensity = router.initialDensityWithoutJaggedness().mapAll(this::wrap);
```

`getPreliminarySurfaceLevel` 메서드 추가 (stoneMask() 아래):
```java
/**
 * quart 좌표(quartX, quartZ)에서 initialDensityWithoutJaggedness > 0 인
 * 최상단 셀 Y 블록 좌표를 반환한다. 없으면 minY - 1.
 *
 * 바닐라 SurfaceSystem 이 above_preliminary_surface 조건에 사용.
 * quartX = blockX >> 2, quartZ = blockZ >> 2.
 */
public int getPreliminarySurfaceLevel(int quartX, int quartZ) {
    // 셀 Y 위→아래 순회
    for (int cellY = cellNoiseMinY + cellCountY; cellY >= cellNoiseMinY; cellY--) {
        int blockY = cellY * cellHeight;
        int bx = quartX * 4;
        int bz = quartZ * 4;
        double density = wrappedInitialDensity.compute(
            new DensityFunction.SinglePointContext(bx, blockY, bz));
        if (density > 0.390625) {
            return blockY;
        }
    }
    return cellNoiseMinY * cellHeight - 1; // minY - 1 sentinel
}
```

> **주의:** 0.390625 threshold = 바닐라 `SurfaceSystem.hasPreliminarySurface`의 기준값.
> 바닐라 디컴파일 소스 `SurfaceSystem.java` 확인 후 일치 여부 검증 필수.
> 틀리면 `above_preliminary_surface` 조건 전체가 어긋남.

- [ ] **Step 4: 테스트 통과 확인**

```
./gradlew :furnace-server:test --tests "*.NoiseChunkTest.preliminarySurfaceLevelInRange"
```
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseChunk.java
git add furnace-server/src/test/java/org/domi/furnace/server/world/gen/NoiseChunkTest.java
git commit -m "feat(worldgen): NoiseChunk.getPreliminarySurfaceLevel for surface rules"
```

---

### Task 3: biome_temperatures.json + WorldgenData

**Files:**
- Create: `furnace-server/src/main/resources/worldgen/1.21.1/biome_temperatures.json`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/WorldgenData.java`

- [ ] **Step 1: biome_temperatures.json 생성**

```json
{
  "minecraft:badlands": 2.0,
  "minecraft:bamboo_jungle": 0.95,
  "minecraft:beach": 0.8,
  "minecraft:birch_forest": 0.6,
  "minecraft:cherry_grove": 0.5,
  "minecraft:cold_ocean": 0.5,
  "minecraft:dark_forest": 0.7,
  "minecraft:deep_cold_ocean": 0.5,
  "minecraft:deep_dark": 0.8,
  "minecraft:deep_frozen_ocean": 0.5,
  "minecraft:deep_lukewarm_ocean": 0.5,
  "minecraft:deep_ocean": 0.5,
  "minecraft:desert": 2.0,
  "minecraft:dripstone_caves": 0.8,
  "minecraft:eroded_badlands": 2.0,
  "minecraft:flower_forest": 0.7,
  "minecraft:forest": 0.7,
  "minecraft:frozen_ocean": 0.0,
  "minecraft:frozen_peaks": -0.7,
  "minecraft:frozen_river": 0.0,
  "minecraft:grove": -0.2,
  "minecraft:ice_spikes": 0.0,
  "minecraft:jagged_peaks": -0.7,
  "minecraft:jungle": 0.95,
  "minecraft:lukewarm_ocean": 0.5,
  "minecraft:lush_caves": 0.5,
  "minecraft:mangrove_swamp": 0.8,
  "minecraft:meadow": 0.5,
  "minecraft:mushroom_fields": 0.9,
  "minecraft:ocean": 0.5,
  "minecraft:old_growth_birch_forest": 0.6,
  "minecraft:old_growth_pine_taiga": 0.3,
  "minecraft:old_growth_spruce_taiga": 0.25,
  "minecraft:plains": 0.8,
  "minecraft:river": 0.5,
  "minecraft:savanna": 2.0,
  "minecraft:savanna_plateau": 2.0,
  "minecraft:snowy_beach": 0.05,
  "minecraft:snowy_plains": 0.0,
  "minecraft:snowy_slopes": -0.3,
  "minecraft:snowy_taiga": -0.5,
  "minecraft:sparse_jungle": 0.95,
  "minecraft:stony_peaks": 1.0,
  "minecraft:stony_shore": 0.2,
  "minecraft:sunflower_plains": 0.8,
  "minecraft:swamp": 0.8,
  "minecraft:taiga": 0.25,
  "minecraft:warm_ocean": 0.5,
  "minecraft:windswept_forest": 0.2,
  "minecraft:windswept_gravelly_hills": 0.2,
  "minecraft:windswept_hills": 0.2,
  "minecraft:windswept_savanna": 2.0,
  "minecraft:wooded_badlands": 2.0
}
```

> **검증 필요:** 바닐라 디컴파일 소스의 biome JSON 파일과 대조해 temperature 값 확인.
> 특히 cold 경계(< 0.15)에 있는 biome: snowy_beach(0.05), old_growth_spruce_taiga(0.25) 확인.

- [ ] **Step 2: WorldgenData에 biomeTemperature() 추가**

```java
// WorldgenData.java — biome_temperatures 로드용 필드 + 메서드 추가
private Map<String, Float> biomeTemperatures; // lazy

/**
 * 바이옴 base temperature를 반환한다 (없으면 0.5f fallback).
 * surface_rule의 temperature 조건에서 사용.
 */
public float biomeTemperature(String biomeName) {
    if (biomeTemperatures == null) {
        biomeTemperatures = loadBiomeTemperatures();
    }
    return biomeTemperatures.getOrDefault(biomeName, 0.5f);
}

private Map<String, Float> loadBiomeTemperatures() {
    JsonElement el = loadResource(basePath + "/biome_temperatures.json");
    if (el == null) throw new IllegalStateException("biome_temperatures.json not found");
    Map<String, Float> map = new HashMap<>();
    for (var entry : el.getAsJsonObject().entrySet()) {
        map.put(entry.getKey(), entry.getValue().getAsFloat());
    }
    return map;
}
```

`HashMap` import 추가: `import java.util.HashMap; import java.util.Map;`

- [ ] **Step 3: 테스트 작성 + 통과 확인**

`WorldgenData` 기존 테스트 파일에 추가:
```java
@Test
void biomeTemperatureKnownValues() {
    WorldgenData data = WorldgenData.load("worldgen/1.21.1");
    assertEquals(0.0f, data.biomeTemperature("minecraft:frozen_ocean"), 1e-6f);
    assertEquals(2.0f, data.biomeTemperature("minecraft:desert"), 1e-6f);
    assertEquals(0.5f, data.biomeTemperature("minecraft:unknown_biome"), 1e-6f); // fallback
}
```

```
./gradlew :furnace-server:test --tests "*.WorldgenDataTest*"
```
Expected: PASS

- [ ] **Step 4: 커밋**

```bash
git add furnace-server/src/main/resources/worldgen/1.21.1/biome_temperatures.json
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/WorldgenData.java
git add furnace-server/src/test/java/org/domi/furnace/server/world/gen/WorldgenDataTest.java
git commit -m "feat(worldgen): biome_temperatures.json + WorldgenData.biomeTemperature()"
```

---

### Task 4: SurfaceRule sealed interface

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/surface/SurfaceRule.java`

- [ ] **Step 1: SurfaceRule.java 작성**

```java
// surface/SurfaceRule.java — surface_rule JSON 노드 표현
package org.domi.furnace.server.world.gen.surface;

import java.util.List;
import java.util.Set;

public sealed interface SurfaceRule {

    // ── Rule 타입 ─────────────────────────────────────────────────────────

    record Sequence(List<SurfaceRule> rules) implements SurfaceRule {}

    record IfTrue(SurfaceCondition condition, SurfaceRule then) implements SurfaceRule {}

    record Block(int blockStateId) implements SurfaceRule {}

    // ── Condition 타입 ────────────────────────────────────────────────────

    sealed interface SurfaceCondition {

        record VerticalGradient(
            String randomName,
            YOffset trueAtAndBelow,
            YOffset falseAtAndAbove
        ) implements SurfaceCondition {}

        record AbovePreliminarySurface() implements SurfaceCondition {}

        record StoneDepth(
            int offset,
            boolean addSurfaceDepth,
            int secondaryDepthRange,
            SurfaceType surfaceType
        ) implements SurfaceCondition {}

        record BiomeIs(Set<String> biomes) implements SurfaceCondition {}

        record YAbove(
            YOffset anchor,
            int surfaceDepthMultiplier,
            boolean addStoneDepth
        ) implements SurfaceCondition {}

        record Water(
            int offset,
            int surfaceDepthMultiplier,
            boolean addStoneDepth
        ) implements SurfaceCondition {}

        record NoiseThreshold(
            String noiseId,
            double minInclusive,
            double maxExclusive
        ) implements SurfaceCondition {}

        record Temperature() implements SurfaceCondition {}

        record Steep() implements SurfaceCondition {}

        record Not(SurfaceCondition inner) implements SurfaceCondition {}

        record Hole() implements SurfaceCondition {}
    }

    // ── YOffset 앵커 타입 ─────────────────────────────────────────────────

    sealed interface YOffset {
        record Absolute(int y) implements YOffset {}
        record AboveBottom(int offset) implements YOffset {}
        record BelowTop(int offset) implements YOffset {}
    }

    // ── SurfaceType ────────────────────────────────────────────────────────

    enum SurfaceType { FLOOR, CEILING }
}
```

- [ ] **Step 2: 컴파일 확인**

```
./gradlew :furnace-server:compileJava
```
Expected: 오류 없음

- [ ] **Step 3: 커밋**

```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/surface/SurfaceRule.java
git commit -m "feat(worldgen): SurfaceRule sealed interface — all rule/condition types"
```

---

### Task 5: SurfaceRuleParser + 단위 테스트

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/surface/SurfaceRuleParser.java`
- Create: `furnace-server/src/test/java/org/domi/furnace/server/world/gen/surface/SurfaceRuleParserTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
// surface/SurfaceRuleParserTest.java
package org.domi.furnace.server.world.gen.surface;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.domi.furnace.server.world.gen.WorldgenData;
import org.domi.furnace.server.world.BlockRegistry;

class SurfaceRuleParserTest {

    @Test
    void parsesOverworldSurfaceRule() {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        SurfaceRuleParser parser = new SurfaceRuleParser(reg);
        var noiseSettingsJson = data.noiseSettings("minecraft:overworld");
        SurfaceRule rule = parser.parse(noiseSettingsJson.get("surface_rule").getAsJsonObject());
        assertInstanceOf(SurfaceRule.Sequence.class, rule);
        var seq = (SurfaceRule.Sequence) rule;
        assertFalse(seq.rules().isEmpty());
    }

    @Test
    void parsesBedrockCondition() {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var parser = new SurfaceRuleParser(reg);
        var noiseSettingsJson = data.noiseSettings("minecraft:overworld");
        SurfaceRule rule = parser.parse(noiseSettingsJson.get("surface_rule").getAsJsonObject());
        // First rule is bedrock floor
        var seq = (SurfaceRule.Sequence) rule;
        var first = (SurfaceRule.IfTrue) seq.rules().get(0);
        assertInstanceOf(SurfaceRule.SurfaceCondition.VerticalGradient.class, first.condition());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```
./gradlew :furnace-server:test --tests "*.SurfaceRuleParserTest"
```
Expected: FAIL — `SurfaceRuleParser 없음`

- [ ] **Step 3: SurfaceRuleParser.java 작성**

```java
// surface/SurfaceRuleParser.java
package org.domi.furnace.server.world.gen.surface;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.domi.furnace.server.world.BlockRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SurfaceRuleParser {

    private final BlockRegistry reg;

    public SurfaceRuleParser(BlockRegistry reg) {
        this.reg = reg;
    }

    public SurfaceRule parse(JsonObject json) {
        String type = json.get("type").getAsString();
        return switch (type) {
            case "minecraft:sequence"  -> parseSequence(json);
            case "minecraft:condition" -> parseCondition(json);
            case "minecraft:block"     -> parseBlock(json);
            default -> throw new IllegalArgumentException("Unknown surface rule type: " + type);
        };
    }

    private SurfaceRule.Sequence parseSequence(JsonObject json) {
        JsonArray arr = json.getAsJsonArray("sequence");
        List<SurfaceRule> rules = new ArrayList<>(arr.size());
        for (JsonElement el : arr) rules.add(parse(el.getAsJsonObject()));
        return new SurfaceRule.Sequence(rules);
    }

    private SurfaceRule.IfTrue parseCondition(JsonObject json) {
        SurfaceRule.SurfaceCondition cond = parseConditionNode(json.getAsJsonObject("if_true"));
        SurfaceRule then = parse(json.getAsJsonObject("then_run"));
        return new SurfaceRule.IfTrue(cond, then);
    }

    private SurfaceRule.Block parseBlock(JsonObject json) {
        String blockName = json.getAsJsonObject("result_state").get("Name").getAsString();
        int id = reg.defaultStateId(blockName);
        return new SurfaceRule.Block(id);
    }

    private SurfaceRule.SurfaceCondition parseConditionNode(JsonObject json) {
        String type = json.get("type").getAsString();
        return switch (type) {
            case "minecraft:vertical_gradient"      -> parseVerticalGradient(json);
            case "minecraft:above_preliminary_surface" -> new SurfaceRule.SurfaceCondition.AbovePreliminarySurface();
            case "minecraft:stone_depth"            -> parseStoneDepth(json);
            case "minecraft:biome"                  -> parseBiome(json);
            case "minecraft:y_above"                -> parseYAbove(json);
            case "minecraft:water"                  -> parseWater(json);
            case "minecraft:noise_threshold"        -> parseNoiseThreshold(json);
            case "minecraft:temperature"            -> new SurfaceRule.SurfaceCondition.Temperature();
            case "minecraft:steep"                  -> new SurfaceRule.SurfaceCondition.Steep();
            case "minecraft:not"                    -> new SurfaceRule.SurfaceCondition.Not(
                                                           parseConditionNode(json.getAsJsonObject("invert")));
            case "minecraft:hole"                   -> new SurfaceRule.SurfaceCondition.Hole();
            default -> throw new IllegalArgumentException("Unknown condition type: " + type);
        };
    }

    private SurfaceRule.SurfaceCondition.VerticalGradient parseVerticalGradient(JsonObject json) {
        String name = json.get("random_name").getAsString();
        SurfaceRule.YOffset trueBelow   = parseYOffset(json.getAsJsonObject("true_at_and_below"));
        SurfaceRule.YOffset falseAbove  = parseYOffset(json.getAsJsonObject("false_at_and_above"));
        return new SurfaceRule.SurfaceCondition.VerticalGradient(name, trueBelow, falseAbove);
    }

    private SurfaceRule.SurfaceCondition.StoneDepth parseStoneDepth(JsonObject json) {
        int offset             = json.get("offset").getAsInt();
        boolean addSurfDepth   = json.get("add_surface_depth").getAsBoolean();
        int secondaryRange     = json.get("secondary_depth_range").getAsInt();
        String typeStr         = json.get("surface_type").getAsString();
        SurfaceRule.SurfaceType t = typeStr.equals("ceiling")
            ? SurfaceRule.SurfaceType.CEILING
            : SurfaceRule.SurfaceType.FLOOR;
        return new SurfaceRule.SurfaceCondition.StoneDepth(offset, addSurfDepth, secondaryRange, t);
    }

    private SurfaceRule.SurfaceCondition.BiomeIs parseBiome(JsonObject json) {
        Set<String> biomes = new HashSet<>();
        for (JsonElement el : json.getAsJsonArray("biome_is")) {
            biomes.add(el.getAsString());
        }
        return new SurfaceRule.SurfaceCondition.BiomeIs(biomes);
    }

    private SurfaceRule.SurfaceCondition.YAbove parseYAbove(JsonObject json) {
        SurfaceRule.YOffset anchor = parseYOffset(json.getAsJsonObject("anchor"));
        int mult        = json.get("surface_depth_multiplier").getAsInt();
        boolean addStone = json.get("add_stone_depth").getAsBoolean();
        return new SurfaceRule.SurfaceCondition.YAbove(anchor, mult, addStone);
    }

    private SurfaceRule.SurfaceCondition.Water parseWater(JsonObject json) {
        int offset  = json.get("offset").getAsInt();
        int mult    = json.get("surface_depth_multiplier").getAsInt();
        boolean add = json.get("add_stone_depth").getAsBoolean();
        return new SurfaceRule.SurfaceCondition.Water(offset, mult, add);
    }

    private SurfaceRule.SurfaceCondition.NoiseThreshold parseNoiseThreshold(JsonObject json) {
        // 바닐라 1.21.1 surface_rule JSON: "noise" 필드가 string ID인지 inline object인지 확인 필요.
        // 실제 overworld.json 확인 후 파싱 방식 조정. string ID 가정:
        JsonElement noiseEl = json.get("noise");
        String noiseId = noiseEl.isJsonObject()
            ? noiseEl.getAsJsonObject().get("type").getAsString()  // inline NoiseParameters — TODO 확인
            : noiseEl.getAsString();                               // string registry ID
        double min = json.get("min_threshold").getAsDouble();
        double max = json.get("max_threshold").getAsDouble();
        return new SurfaceRule.SurfaceCondition.NoiseThreshold(noiseId, min, max);
    }

    private SurfaceRule.YOffset parseYOffset(JsonObject json) {
        if (json.has("absolute"))     return new SurfaceRule.YOffset.Absolute(json.get("absolute").getAsInt());
        if (json.has("above_bottom")) return new SurfaceRule.YOffset.AboveBottom(json.get("above_bottom").getAsInt());
        if (json.has("below_top"))    return new SurfaceRule.YOffset.BelowTop(json.get("below_top").getAsInt());
        throw new IllegalArgumentException("Unknown YOffset format: " + json);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```
./gradlew :furnace-server:test --tests "*.SurfaceRuleParserTest"
```
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/surface/SurfaceRuleParser.java
git add furnace-server/src/test/java/org/domi/furnace/server/world/gen/surface/SurfaceRuleParserTest.java
git commit -m "feat(worldgen): SurfaceRuleParser JSON→tree + unit test"
```

---

### Task 6: SurfaceContext + SurfaceSystem

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/surface/SurfaceContext.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/surface/SurfaceSystem.java`

- [ ] **Step 1: SurfaceContext.java 작성**

```java
// surface/SurfaceContext.java
package org.domi.furnace.server.world.gen.surface;

import org.domi.furnace.server.world.gen.WorldgenData;
import org.domi.furnace.server.world.gen.noise.NormalNoise;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;

import java.util.Map;

/** 단일 블록 평가 시 SurfaceRule 트리에 전달되는 상태 컨텍스트. */
public final class SurfaceContext {

    // 현재 위치
    public int blockX, blockY, blockZ;

    // 바이옴
    public String biomeName;

    // 표면 깊이 (컬럼 스캔 결과)
    public int stoneDepthAbove;   // 현재 y 위로 solid 블록 수 (top-down 루프 실시간)
    public int stoneDepthBelow;   // 현재 y 아래로 solid 블록 수 (컬럼 pre-scan)
    public int surfaceY;          // 컬럼 최상단 solid y
    public int waterY;            // 컬럼 최상단 water y (-1 = 없음)
    public int floorY;            // 컬럼 최하단 solid y

    // preliminary surface level (quart 단위 해상도)
    public int preliminarySurfaceLevel;

    // surface depth: add_surface_depth=true 조건에 사용 (minecraft:surface noise 기반)
    public int surfaceDepth;

    // 설정
    public int minY;
    public int maxY;
    public int seaLevel;

    // noise/random 접근
    public Map<String, NormalNoise> noises;
    public XoroshiroRandomSource.XoroshiroPositionalRandomFactory randomFactory;
    public WorldgenData worldgenData;
}
```

- [ ] **Step 2: SurfaceSystem.java 작성**

```java
// surface/SurfaceSystem.java
package org.domi.furnace.server.world.gen.surface;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.gen.NoiseChunk;
import org.domi.furnace.server.world.gen.NoiseSettings;
import org.domi.furnace.server.world.gen.WorldgenData;
import org.domi.furnace.server.world.gen.noise.NormalNoise;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;
import org.domi.furnace.server.world.gen.df.DensityFunctionParser;

import java.util.HashMap;
import java.util.Map;

public final class SurfaceSystem {

    private final SurfaceRule rule;
    private final NoiseSettings settings;
    private final WorldgenData worldgenData;
    private final int airId;

    // 시드 기반 random factory (바닐라 RandomState.random 동일 시딩)
    private final XoroshiroRandomSource.XoroshiroPositionalRandomFactory randomFactory;

    // NormalNoise 캐시 (noise_threshold + surfaceDepth용)
    private final Map<String, NormalNoise> noiseCache = new HashMap<>();

    // minecraft:surface noise — surface depth 계산용
    private final NormalNoise surfaceNoise;

    private SurfaceSystem(SurfaceRule rule, NoiseSettings settings,
                          WorldgenData worldgenData, BlockRegistry reg, long seed) {
        this.rule         = rule;
        this.settings     = settings;
        this.worldgenData = worldgenData;
        this.airId        = ChunkSection.AIR;

        // 바닐라 RandomState와 동일한 시딩: new XoroshiroRandomSource(seed).forkPositional()
        this.randomFactory = new XoroshiroRandomSource(seed).forkPositional();

        // minecraft:surface noise (add_surface_depth 조건용)
        this.surfaceNoise = buildNoise("minecraft:surface");
    }

    public static SurfaceSystem create(WorldgenData data, NoiseSettings settings,
                                       BlockRegistry reg, long seed) {
        JsonObject noiseSettingsJson = data.noiseSettings("minecraft:overworld");
        SurfaceRule rule = new SurfaceRuleParser(reg).parse(
            noiseSettingsJson.get("surface_rule").getAsJsonObject());
        return new SurfaceSystem(rule, settings, data, reg, seed);
    }

    private NormalNoise buildNoise(String noiseId) {
        return noiseCache.computeIfAbsent(noiseId, id -> {
            XoroshiroRandomSource rng = randomFactory.fromHashOf(id);
            JsonElement params = worldgenData.noiseParams(id);
            JsonObject p = params.getAsJsonObject();
            int firstOctave = p.get("firstOctave").getAsInt();
            var amps = p.getAsJsonArray("amplitudes");
            double[] amplitudes = new double[amps.size()];
            for (int i = 0; i < amps.size(); i++) amplitudes[i] = amps.get(i).getAsDouble();
            return NormalNoise.create(rng, new NormalNoise.NoiseParameters(firstOctave, amplitudes));
        });
    }

    /**
     * noise fill 완료된 ChunkColumn에 surface rules를 적용한다.
     * biomeNames[lz*16+lx] = 해당 컬럼의 biome name (quart y=surface 기준).
     */
    public void buildSurface(ChunkColumn col, int[] biomeIds, String[] biomeNames,
                             NoiseChunk nc, int chunkX, int chunkZ) {
        int minY    = settings.minY();
        int height  = settings.height();
        int maxY    = minY + height - 1;
        int seaLevel = settings.seaLevel();

        SurfaceContext ctx = new SurfaceContext();
        ctx.minY          = minY;
        ctx.maxY          = maxY;
        ctx.seaLevel      = seaLevel;
        ctx.noises        = noiseCache;
        ctx.randomFactory = randomFactory;
        ctx.worldgenData  = worldgenData;

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int worldX = chunkX * 16 + lx;
                int worldZ = chunkZ * 16 + lz;

                // biome name for this column (use surface quart y)
                int surfaceQuartY = 0;
                ctx.biomeName = biomeNames[lz * 16 + lx];

                // preliminary surface level (quart resolution)
                ctx.preliminarySurfaceLevel = nc.getPreliminarySurfaceLevel(
                    (worldX >> 2), (worldZ >> 2));

                // surface depth from minecraft:surface noise
                ctx.surfaceDepth = (int)(surfaceNoise.getValue(worldX, 0, worldZ) * 2.75 + 3.0
                    + randomFactory.at(worldX, 0, worldZ).nextDouble() * 0.25);

                // pre-scan: surfaceY, waterY, floorY
                ctx.surfaceY = minY;
                ctx.waterY   = -1;
                ctx.floorY   = minY;
                boolean foundSurface = false;
                boolean foundFloor   = false;
                for (int y = maxY; y >= minY; y--) {
                    int state = col.getBlock(lx, y, lz);
                    boolean solid = (state != airId);
                    boolean water = (state != airId && !solid); // water check below
                    // Distinguish water from truly solid
                    int stoneId = col.getBlock(lx, y, lz); // reuse
                    // solid = any non-air block (stone, water, lava all count as "filled")
                    if (state != ChunkSection.AIR) {
                        if (!foundSurface) { ctx.surfaceY = y; foundSurface = true; }
                        ctx.floorY = y;
                    }
                }
                // waterY: highest y that contains water state id
                // Note: this needs the water block id — pass it or check against reg
                // For now: waterY stays -1 (refined later for water condition)

                ctx.blockX = worldX;
                ctx.blockZ = worldZ;

                // top-down loop with stoneDepthAbove tracking
                ctx.stoneDepthAbove = 0;
                for (int y = maxY; y >= minY; y--) {
                    ctx.blockY = y;
                    int current = col.getBlock(lx, y, lz);
                    boolean isSolid = (current != ChunkSection.AIR);

                    // update stoneDepthAbove: count consecutive solid blocks from surface down
                    if (isSolid) {
                        ctx.stoneDepthAbove++;
                    } else {
                        ctx.stoneDepthAbove = 0;
                    }

                    Integer result = eval(rule, ctx);
                    if (result != null) {
                        col.setBlock(lx, y, lz, result);
                    }
                }
            }
        }
    }

    /** rule 평가. 배치할 blockStateId 반환, null = 아무것도 배치 안 함. */
    private Integer eval(SurfaceRule rule, SurfaceContext ctx) {
        return switch (rule) {
            case SurfaceRule.Sequence seq -> {
                for (SurfaceRule r : seq.rules()) {
                    Integer res = eval(r, ctx);
                    if (res != null) yield res;
                }
                yield null;
            }
            case SurfaceRule.IfTrue ifTrue -> {
                if (evalCondition(ifTrue.condition(), ctx)) yield eval(ifTrue.then(), ctx);
                yield null;
            }
            case SurfaceRule.Block block -> block.blockStateId();
        };
    }

    private boolean evalCondition(SurfaceRule.SurfaceCondition cond, SurfaceContext ctx) {
        return switch (cond) {
            case SurfaceRule.SurfaceCondition.VerticalGradient vg -> evalVerticalGradient(vg, ctx);
            case SurfaceRule.SurfaceCondition.AbovePreliminarySurface __ ->
                ctx.blockY >= ctx.preliminarySurfaceLevel;
            case SurfaceRule.SurfaceCondition.StoneDepth sd -> evalStoneDepth(sd, ctx);
            case SurfaceRule.SurfaceCondition.BiomeIs bi -> bi.biomes().contains(ctx.biomeName);
            case SurfaceRule.SurfaceCondition.YAbove ya -> evalYAbove(ya, ctx);
            case SurfaceRule.SurfaceCondition.Water w -> evalWater(w, ctx);
            case SurfaceRule.SurfaceCondition.NoiseThreshold nt -> evalNoiseThreshold(nt, ctx);
            case SurfaceRule.SurfaceCondition.Temperature __ ->
                ctx.worldgenData.biomeTemperature(ctx.biomeName) < 0.15f;
            case SurfaceRule.SurfaceCondition.Steep __ -> evalSteep(ctx);
            case SurfaceRule.SurfaceCondition.Not n -> !evalCondition(n.inner(), ctx);
            case SurfaceRule.SurfaceCondition.Hole __ ->
                ctx.surfaceY < ctx.seaLevel; // 표면이 해수면 아래 = hole
        };
    }

    private boolean evalVerticalGradient(SurfaceRule.SurfaceCondition.VerticalGradient vg,
                                          SurfaceContext ctx) {
        int trueY  = resolveY(vg.trueAtAndBelow(), ctx);
        int falseY = resolveY(vg.falseAtAndAbove(), ctx);
        if (ctx.blockY <= trueY)  return true;
        if (ctx.blockY >= falseY) return false;
        double gradient = (double)(ctx.blockY - trueY) / (falseY - trueY); // 0→1
        XoroshiroRandomSource rng = randomFactory
            .fromHashOf(vg.randomName())
            .at(ctx.blockX, ctx.blockY, ctx.blockZ);
        return rng.nextFloat() < (1.0 - gradient); // true when closer to trueY
    }

    private boolean evalStoneDepth(SurfaceRule.SurfaceCondition.StoneDepth sd,
                                    SurfaceContext ctx) {
        int depth = switch (sd.surfaceType()) {
            case FLOOR   -> ctx.stoneDepthAbove;
            case CEILING -> ctx.stoneDepthBelow;
        };
        int maxDepth = sd.offset() + 1;
        if (sd.addSurfaceDepth()) maxDepth += ctx.surfaceDepth;
        // secondaryDepthRange: additional noise-based range (currently not used in common rules)
        return depth <= maxDepth;
    }

    private boolean evalYAbove(SurfaceRule.SurfaceCondition.YAbove ya, SurfaceContext ctx) {
        int anchorY = resolveY(ya.anchor(), ctx);
        if (ya.addStoneDepth()) anchorY += ctx.stoneDepthAbove;
        return ctx.blockY >= anchorY + ya.surfaceDepthMultiplier() * ctx.surfaceDepth;
    }

    private boolean evalWater(SurfaceRule.SurfaceCondition.Water w, SurfaceContext ctx) {
        // water condition: is there water above (within offset range)?
        // waterY = highest water y in column (-1 if none)
        if (ctx.waterY == -1) return w.offset() < 0;
        int checkY = ctx.blockY + w.offset();
        return ctx.waterY >= checkY;
    }

    private boolean evalNoiseThreshold(SurfaceRule.SurfaceCondition.NoiseThreshold nt,
                                        SurfaceContext ctx) {
        NormalNoise noise = buildNoise(nt.noiseId());
        double v = noise.getValue(ctx.blockX, 0, ctx.blockZ);
        return v >= nt.minInclusive() && v < nt.maxExclusive();
    }

    private boolean evalSteep(SurfaceContext ctx) {
        // 인접 컬럼 surfaceY 차이 > 4 — 현재 컬럼 surfaceY 와 비교
        // 청크 경계는 청크 내부만 비교 (바닐라 동일)
        // SurfaceSystem은 청크 단위 호출이므로 인접 컬럼 surfaceY가 필요.
        // TODO: buildSurface에서 surfaceY[16][16] 배열을 pre-build해 SurfaceContext에 전달.
        // 현재는 false fallback (steep 비교 컬럼 데이터 미구현).
        return false; // 구현 보류 — parity 통과 후 보완
    }

    private int resolveY(SurfaceRule.YOffset offset, SurfaceContext ctx) {
        return switch (offset) {
            case SurfaceRule.YOffset.Absolute a    -> a.y();
            case SurfaceRule.YOffset.AboveBottom ab -> ctx.minY + ab.offset();
            case SurfaceRule.YOffset.BelowTop bt    -> ctx.maxY - bt.offset();
        };
    }
}
```

> **주의:** `steep` 조건은 인접 컬럼 `surfaceY` 필요. `buildSurface`에서 16×16 surfaceY 배열 pre-build 후 `SurfaceContext`에 추가하는 보완 필요. parity 실패 시 이 부분 먼저 확인.

> **주의:** `stoneDepthBelow`는 현재 구현에서 미계산. CEILING 타입 stone_depth 조건 사용 시 pre-scan 추가 필요.

> **주의:** `waterY` 계산은 물 block state id 필요. `SurfaceSystem.create()`에서 `waterId = reg.defaultStateId("minecraft:water")` 저장 후 pre-scan에서 체크.

- [ ] **Step 3: 컴파일 확인**

```
./gradlew :furnace-server:compileJava
```
Expected: 오류 없음 (미구현 부분은 false/null 반환)

- [ ] **Step 4: 커밋**

```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/surface/
git commit -m "feat(worldgen): SurfaceContext + SurfaceSystem.buildSurface() initial implementation"
```

---

### Task 7: NoiseWorldSource 연결

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseWorldSource.java`

- [ ] **Step 1: biomeNames 배열 빌드 헬퍼 추가 + SurfaceSystem 생성 + generate() 연결**

```java
// NoiseWorldSource.java — 필드 추가
private final SurfaceSystem surfaceSystem;
private final String[] registryNames; // biomeId → biome name 역매핑

// overworld() 팩토리 수정
// RegistrySet에서 biome id → name 역매핑 생성
String[] biomeNames = new String[registries.size("minecraft:worldgen/biome")];
for (int i = 0; i < biomeNames.length; i++) {
    biomeNames[i] = registries.nameAt("minecraft:worldgen/biome", i);
}
SurfaceSystem surfaceSystem = SurfaceSystem.create(data, settings, reg, seed);

return new NoiseWorldSource(router, settings, biomeSampler, surfaceSystem,
                             biomeNames, stoneId, waterId, lavaId);
```

`RegistrySet`에 `nameAt(registry, id)` 메서드 필요 — 없으면 추가:
```java
// RegistrySet.java — 추가
public String nameAt(String registry, int id) {
    // entries에서 id 위치의 name 반환 (역방향 조회)
    return getEntry(registry, id).name(); // 기존 구조에 맞게 구현
}
public int size(String registry) { ... }
```

`generate()` 끝에 추가:
```java
// 표층 규칙 적용 (noise fill + biome fill 이후)
String[] colBiomeNames = buildBiomeNames(biomeIds);
surfaceSystem.buildSurface(col, biomeIds, colBiomeNames, nc, chunkX, chunkZ);
```

`buildBiomeNames` 헬퍼:
```java
private String[] buildBiomeNames(int[] biomeIds) {
    // biomeIds = 96 qy × 4 qz × 4 qx — 컬럼(lx,lz)의 surface quart y(최상단 고체 quart)의 biome 사용
    String[] names = new String[256]; // 16×16 컬럼
    for (int lz = 0; lz < 16; lz++) {
        for (int lx = 0; lx < 16; lx++) {
            int qx = lx >> 2;
            int qz = lz >> 2;
            // surface quart y: 대략 quart y=72 (y=288~292 부근) 기준 — 단순화: qy=72
            int qy = 72; // TODO: 정확한 surface quart y 계산
            int biomeId = biomeIds[qy * 16 + qz * 4 + qx];
            names[lz * 16 + lx] = registryNames[biomeId];
        }
    }
    return names;
}
```

> **주의:** biome name → surface quart y 선택은 parity 결과에 영향. 바닐라는 block y 기준 quart y를 사용. 정확한 값은 parity 테스트 실패 시 조정.

- [ ] **Step 2: RegistrySet.nameAt + size 추가 (없는 경우)**

`RegistrySet` 기존 구현 확인 후 필요한 메서드만 추가.

- [ ] **Step 3: 전체 테스트 통과 확인**

```
./gradlew :furnace-server:test
```
Expected: 기존 테스트 전부 PASS (새 코드가 기존 테스트를 깨지 않는지 확인)

- [ ] **Step 4: 커밋**

```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseWorldSource.java
git add furnace-server/src/main/java/org/domi/furnace/server/net/registry/RegistrySet.java
git commit -m "feat(worldgen): wire SurfaceSystem into NoiseWorldSource.generate()"
```

---

### Task 8: Oracle surface 서브커맨드 + FixtureFormat

**Files:**
- Modify: `worldgen-oracle/src/main/java/org/domi/oracle/FixtureFormat.java`
- Modify: `worldgen-oracle/src/main/java/org/domi/oracle/OracleMain.java`

- [ ] **Step 1: FixtureFormat.java 수정 — surface 상수 추가**

```java
// FixtureFormat.java 기존 내용 유지, 추가:

// SURFACE fixture "surface_<seed>_<cx>_<cz>.bin": little-endian.
//   magic int32 = 0x53524643 ("SRFC"); long seed; int32 cx; int32 cz;
//   then HEIGHT*16*16 short (block state id), index = (y-MIN_Y)*256 + localZ*16 + localX
public static final int SURFACE_MAGIC = 0x53524643;
```

- [ ] **Step 2: OracleMain.java — surface 서브커맨드 분기 추가**

`main()` 의 arg 처리 루프에 추가:
```java
} else if (arg.startsWith("surface:")) {
    String[] parts = arg.substring("surface:".length()).split(":");
    int cx = Integer.parseInt(parts[0]);
    int cz = Integer.parseInt(parts[1]);
    dumpSurfaceChunk(seed, cx, cz, generator, randomState, noAquiferSettings,
                     fluidPicker, frozenBiomeRegistry, registries, outDir);
}
```

`dumpSurfaceChunk` 메서드 추가:
```java
private static void dumpSurfaceChunk(
    long seed, int cx, int cz,
    NoiseBasedChunkGenerator generator,
    RandomState randomState,
    NoiseGeneratorSettings settings,
    Aquifer.FluidPicker fluidPicker,
    Registry<Biome> biomeRegistry,
    HolderLookup.Provider registryAccess,
    Path outDir
) throws Exception {
    ChunkPos chunkPos = new ChunkPos(cx, cz);
    LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(FixtureFormat.MIN_Y, FixtureFormat.HEIGHT);
    ProtoChunk chunk = new ProtoChunk(chunkPos, UpgradeData.EMPTY, heightAccessor, biomeRegistry, null);

    // Pre-cache NoiseChunk (StructureManager 불필요하게 만들기)
    Beardifier emptyBeardifier = new Beardifier(
        new ObjectArrayList<Beardifier.Rigid>().iterator(),
        new ObjectArrayList<JigsawJunction>().iterator()
    );
    chunk.getOrCreateNoiseChunk(ca ->
        NoiseChunk.forChunk(ca, randomState, emptyBeardifier, settings, fluidPicker, Blender.empty())
    );

    // 1. noise fill (stone/water/lava/air)
    generator.fillFromNoise(Blender.empty(), randomState, null, chunk).join();

    // 2. surface rules (NoiseChunk already cached → null StructureManager safe)
    generator.buildSurface(randomState, null, (net.minecraft.core.RegistryAccess) registryAccess, chunk);

    // 3. 전체 블록 state id 덤프
    int totalBlocks = FixtureFormat.HEIGHT * 16 * 16;
    int headerSize  = 4 + 8 + 4 + 4; // magic + seed + cx + cz
    ByteBuffer buf  = ByteBuffer.allocate(headerSize + totalBlocks * 2).order(ByteOrder.LITTLE_ENDIAN);
    buf.putInt(FixtureFormat.SURFACE_MAGIC);
    buf.putLong(seed);
    buf.putInt(cx);
    buf.putInt(cz);

    for (int y = FixtureFormat.MIN_Y; y < FixtureFormat.MIN_Y + FixtureFormat.HEIGHT; y++) {
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int worldX = cx * 16 + localX;
                int worldZ = cz * 16 + localZ;
                BlockState state = chunk.getBlockState(new net.minecraft.core.BlockPos(worldX, y, worldZ));
                short id = (short) net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(state);
                buf.putShort(id);
            }
        }
    }

    Path out = outDir.resolve("surface_" + seed + "_" + cx + "_" + cz + ".bin");
    try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
        fos.write(buf.array());
    }
    System.out.printf("Wrote %s (%d bytes)%n", out, buf.capacity());
}
```

> **주의:** `(net.minecraft.core.RegistryAccess) registryAccess` 캐스트가 실패하면 `VanillaRegistries.createLookup()`이 반환하는 타입 확인. 1.21.x에서 `RegistryAccess.Frozen` 구현체인지 확인 후 적절히 처리.

> **주의:** `generator.buildSurface(randomState, null, registryAccess, chunk)` — `null` StructureManager가 NPE를 낸다면 Beardifier-only mock 생성 필요. 바닐라 소스에서 `buildSurface`가 null-safe한지 확인.

- [ ] **Step 3: oracle 컴파일 확인**

```
./gradlew :worldgen-oracle:compileJava
```
Expected: PASS

- [ ] **Step 4: 커밋**

```bash
git add worldgen-oracle/src/main/java/org/domi/oracle/OracleMain.java
git add worldgen-oracle/src/main/java/org/domi/oracle/FixtureFormat.java
git commit -m "feat(oracle): surface block state fixture dump subcommand"
```

---

### Task 9: Fixture 3개 덤프 + 커밋

**Files:**
- Create: `furnace-server/src/test/resources/worldgen/fixtures/surface_0_0_0.bin`
- Create: `furnace-server/src/test/resources/worldgen/fixtures/surface_0_5_5.bin`
- Create: `furnace-server/src/test/resources/worldgen/fixtures/surface_123456789_0_0.bin`

- [ ] **Step 1: fixture 덤프**

```
./gradlew :worldgen-oracle:run --args="0 surface:0:0 surface:5:5"
./gradlew :worldgen-oracle:run --args="123456789 surface:0:0"
```

Expected output:
```
Wrote furnace-server/src/test/resources/worldgen/fixtures/surface_0_0_0.bin (196628 bytes)
Wrote furnace-server/src/test/resources/worldgen/fixtures/surface_0_5_5.bin (196628 bytes)
Wrote furnace-server/src/test/resources/worldgen/fixtures/surface_123456789_0_0.bin (196628 bytes)
```

- [ ] **Step 2: 파일 크기 검증**

```
ls -la furnace-server/src/test/resources/worldgen/fixtures/surface_*.bin
```
Expected: 각 196628 bytes (20 header + 98304 shorts × 2 = 196628)

- [ ] **Step 3: 커밋**

```bash
git add furnace-server/src/test/resources/worldgen/fixtures/surface_*.bin
git commit -m "test(worldgen): commit 3 surface block-state fixtures (seed0 0:0+5:5, seed123456789 0:0)"
```

---

### Task 10: SurfaceParityTest + 0 mismatch 달성

**Files:**
- Create: `furnace-server/src/test/java/org/domi/furnace/server/world/gen/SurfaceParityTest.java`

- [ ] **Step 1: SurfaceParityTest.java 작성**

```java
// SurfaceParityTest.java
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;

class SurfaceParityTest {

    private void assertSurfaceParity(long seed, int cx, int cz) throws Exception {
        WorldgenData data     = WorldgenData.load("worldgen/1.21.1");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        BlockRegistry reg      = BlockRegistry.load("blocks/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(seed, reg, registries);

        ChunkColumn col = source.generate(cx, cz);

        // Load fixture
        String fixtureName = "/worldgen/fixtures/surface_" + seed + "_" + cx + "_" + cz + ".bin";
        byte[] fx;
        try (InputStream in = getClass().getResourceAsStream(fixtureName)) {
            assertNotNull(in, "fixture not found: " + fixtureName);
            fx = in.readAllBytes();
        }

        // Parse header
        ByteBuffer header = ByteBuffer.wrap(fx, 0, 20).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x53524643, header.getInt(), "magic mismatch");
        // skip seed (8), cx (4), cz (4)
        header.getLong(); header.getInt(); header.getInt();

        // Compare block by block
        int minY   = -64;
        int height = 384;
        int mismatch = 0;
        String firstMismatch = null;
        int pos = 20;

        for (int y = minY; y < minY + height; y++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int expected = ByteBuffer.wrap(fx, pos, 2).order(ByteOrder.LITTLE_ENDIAN)
                        .getShort() & 0xFFFF;
                    pos += 2;
                    int got = col.getBlock(lx, y, lz);
                    if (expected != got) {
                        mismatch++;
                        if (firstMismatch == null) {
                            firstMismatch = String.format("lx=%d y=%d lz=%d expected=%d got=%d",
                                lx, y, lz, expected, got);
                        }
                    }
                }
            }
        }

        System.out.printf("seed=%d cx=%d cz=%d: %d mismatches%n", seed, cx, cz, mismatch);
        assertEquals(0, mismatch,
            "seed=" + seed + " cx=" + cx + " cz=" + cz
            + " mismatches=" + mismatch + " first=" + firstMismatch);
    }

    @Test void seed0_spawn() throws Exception { assertSurfaceParity(0L, 0, 0); }
    @Test void seed0_far()   throws Exception { assertSurfaceParity(0L, 5, 5); }
    @Test void seedB()       throws Exception { assertSurfaceParity(123456789L, 0, 0); }
}
```

- [ ] **Step 2: 테스트 실행 — 초기 mismatch 수 확인**

```
./gradlew :furnace-server:test --tests "*.SurfaceParityTest"
```
Expected: FAIL with mismatch count and first mismatch location.
초기 실행 시 mismatch 많을 것 — 어떤 블록/조건이 틀렸는지 출력으로 진단.

- [ ] **Step 3: mismatch 진단 + SurfaceSystem 수정**

진단 순서:
1. `firstMismatch` 좌표의 블록 확인 — bedrock인지, grass/dirt인지, stone인지.
2. bedrock 불일치: `vertical_gradient` random 시딩 오류. `randomFactory.fromHashOf(name).at(x,y,z)` 순서 확인.
3. grass/dirt 불일치: `stone_depth` 계산 오류 or `above_preliminary_surface` threshold 오류.
4. stone→surface block 미교체: `preliminary surface level` 계산 값 확인 (`getPreliminarySurfaceLevel`에서 0.390625 임계값 검증).
5. `SurfaceSystem.buildSurface()`의 `stoneDepthAbove` 계산: top-down 루프에서 reset 조건 확인.
6. `surfaceDepth` 계산: 바닐라 소스 `SurfaceSystem.getSurfaceDepth` 공식 검증.

각 오류마다 수정 후 재실행.

- [ ] **Step 4: 0 mismatch 달성 확인**

```
./gradlew :furnace-server:test --tests "*.SurfaceParityTest"
```
Expected: 3개 테스트 전부 PASS

- [ ] **Step 5: 전체 테스트 통과 확인**

```
./gradlew :furnace-server:test
```
Expected: ALL PASS

- [ ] **Step 6: ChunkGenBenchmark 실행 — buildSurface 성능 측정**

```
./gradlew :furnace-server:test --tests "*.ChunkGenBenchmark"
```
buildSurface 단계 시간 출력 확인.

- [ ] **Step 7: 커밋**

```bash
git add furnace-server/src/test/java/org/domi/furnace/server/world/gen/SurfaceParityTest.java
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/surface/
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseWorldSource.java
git commit -m "feat(worldgen): M4g-c1 surface rules — 3-fixture 0-mismatch parity"
```
