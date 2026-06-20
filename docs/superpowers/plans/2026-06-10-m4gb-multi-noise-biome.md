# M4g-b Multi-Noise Biome Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 바닐라 1.21.1과 시드 비트일치하는 multi-noise 바이옴 배치 구현 — 6개 기후 DF 평가 → Climate 거리 공식으로 최근접 바이옴 선택 → ChunkSection.biomes[] 채움.

**Architecture:** Oracle이 OverworldBiomeBuilder에서 biome_parameters.json(quantized long 파라미터 목록)을 추출 → BiomeSampler가 로드해 linear search → NoiseRouter에 6개 기후 DF 추가 → NoiseWorldSource.generate()에서 quart 단위 바이옴 채움 → Oracle 확장으로 biome fixture 덤프 → BiomeSamplerParityTest가 1536 quart 전부 비트비교.

**Tech Stack:** Java 21, Gson, JUnit 5, Vanilla remapped JAR (oracle), `registries/1.21.1.txt` (바이옴 registry 순서)

---

## 파일 구조

**신규:**
- `furnace-server/src/main/resources/worldgen/1.21.1/biome_parameters.json` — Oracle 1회 생성 후 커밋. Climate ParameterPoint 목록(quantized long). T1에서 생성.
- `furnace-server/src/main/java/org/domi/furnace/server/world/gen/BiomeSampler.java` — load/sample/sampleChunk + Climate 수학 함수.
- `furnace-server/src/test/java/org/domi/furnace/server/world/gen/BiomeSamplerTest.java` — Climate 수학 단위 테스트.
- `furnace-server/src/test/java/org/domi/furnace/server/world/gen/BiomeSamplerParityTest.java` — Fixture 비트비교.

**수정:**
- `furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseRouter.java` — 6개 기후 DF 필드 추가.
- `furnace-server/src/main/java/org/domi/furnace/server/world/gen/WorldgenData.java` — biomeParameters() 메서드 추가.
- `furnace-server/src/main/java/org/domi/furnace/server/world/ChunkSection.java` — setBiome() 추가.
- `furnace-server/src/main/java/org/domi/furnace/server/world/ChunkColumn.java` — setBiome(quartX, quartY, quartZ, id) 추가.
- `furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseWorldSource.java` — BiomeSampler 보유, RegistrySet 파라미터 추가, generate() 바이옴 채움.
- `furnace-server/src/main/java/org/domi/furnace/server/FurnaceMain.java` — overworld() 호출에 registries 추가.
- `worldgen-oracle/src/main/java/org/domi/oracle/OracleMain.java` — params 서브커맨드(T1) + biome:cx:cz 서브커맨드(T6).

---

### Task 1: Oracle — biome_parameters.json 추출

**Background:** OverworldBiomeBuilder는 ~1000줄 Java이므로 포팅 대신 Oracle로 파라미터를 추출한다. `MultiNoiseBiomeSourceParameterList.Preset.knownPresets().get(OVERWORLD).values()`가 전체 Climate.ParameterPoint 목록을 반환한다. min/max 필드는 이미 quantized long이다.

**Files:**
- Modify: `worldgen-oracle/src/main/java/org/domi/oracle/OracleMain.java`
- Output (commit): `furnace-server/src/main/resources/worldgen/1.21.1/biome_parameters.json`

- [ ] **Step 1: OracleMain에 dumpBiomeParams 메서드 추가**

```java
// OracleMain.main() 루프의 args[i] 처리 switch에 추가:
} else if (arg.equals("params")) {
    Path mainResDir = Paths.get("furnace-server", "src", "main", "resources", "worldgen", "1.21.1");
    dumpBiomeParams(mainResDir);
}
```

```java
// OracleMain 클래스 안 새 메서드:
private static void dumpBiomeParams(Path outDir) throws Exception {
    Climate.ParameterList<ResourceKey<Biome>> paramList =
        MultiNoiseBiomeSourceParameterList.Preset.knownPresets()
            .get(MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD);

    StringBuilder sb = new StringBuilder("[");
    boolean first = true;
    for (com.mojang.datafixers.util.Pair<Climate.ParameterPoint, ResourceKey<Biome>> pair
            : paramList.values()) {
        Climate.ParameterPoint pp = pair.getFirst();
        String biomeName = pair.getSecond().location().toString();
        if (!first) sb.append(",");
        sb.append("\n  {")
          .append("\"biome\":\"").append(biomeName).append("\"")
          .append(",\"temperature\":[").append(pp.temperature().min()).append(",").append(pp.temperature().max()).append("]")
          .append(",\"humidity\":[").append(pp.humidity().min()).append(",").append(pp.humidity().max()).append("]")
          .append(",\"continentalness\":[").append(pp.continentalness().min()).append(",").append(pp.continentalness().max()).append("]")
          .append(",\"erosion\":[").append(pp.erosion().min()).append(",").append(pp.erosion().max()).append("]")
          .append(",\"depth\":[").append(pp.depth().min()).append(",").append(pp.depth().max()).append("]")
          .append(",\"weirdness\":[").append(pp.weirdness().min()).append(",").append(pp.weirdness().max()).append("]")
          .append(",\"offset\":").append(pp.offset())
          .append("}");
        first = false;
    }
    sb.append("\n]\n");

    Files.createDirectories(outDir);
    Path out = outDir.resolve("biome_parameters.json");
    java.nio.file.Files.writeString(out, sb.toString());
    System.out.println("Wrote " + out + " (" + paramList.values().size() + " entries)");
}
```

필요 import 추가:
```java
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
```
(이미 일부 import 있을 수 있음 — 중복 추가 금지)

- [ ] **Step 2: 빌드 확인**

```
./gradlew :worldgen-oracle:compileJava
```
Expected: BUILD SUCCESSFUL (컴파일 에러 없음)

- [ ] **Step 3: biome_parameters.json 생성**

```
./gradlew :worldgen-oracle:run --args="0 params"
```
Expected 출력:
```
Wrote furnace-server/src/main/resources/worldgen/1.21.1/biome_parameters.json (N entries)
Oracle done.
```
N > 100 이어야 함 (OverworldBiomeBuilder는 바이옴당 여러 climate range 생성 → 총 ~160+ entries).

- [ ] **Step 4: 파일 내용 스팟체크**

```
head -10 furnace-server/src/main/resources/worldgen/1.21.1/biome_parameters.json
```
Expected: 배열 시작(`[`), 첫 entry에 biome name, temperature/humidity 등 6개 파라미터, offset.

- [ ] **Step 5: 커밋**

```bash
git add worldgen-oracle/src/main/java/org/domi/oracle/OracleMain.java \
        furnace-server/src/main/resources/worldgen/1.21.1/biome_parameters.json
git commit -m "feat(oracle): extract overworld biome climate parameters to JSON"
```

---

### Task 2: BiomeSampler — Climate 수학 함수 + 단위 테스트

**Background:** vanilla Climate.java 비트일치 수학 함수 3개: `quantize(float)`, `paramDistance(min,max,v)`, `fitness(...)`. 모두 package-private static.

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/BiomeSampler.java`
- Create: `furnace-server/src/test/java/org/domi/furnace/server/world/gen/BiomeSamplerTest.java`

- [ ] **Step 1: 실패하는 단위 테스트 작성**

```java
// BiomeSamplerTest.java
// 바닐라 Climate.java 수학 함수 단위 검증
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BiomeSamplerTest {

    @Test void quantize_positive() { assertEquals(9000L, BiomeSampler.quantize(0.9f)); }
    @Test void quantize_negative() { assertEquals(-9000L, BiomeSampler.quantize(-0.9f)); }
    @Test void quantize_zero() { assertEquals(0L, BiomeSampler.quantize(0f)); }
    @Test void quantize_max() { assertEquals(10000L, BiomeSampler.quantize(1.0f)); }

    @Test void paramDistance_in_range() { assertEquals(0L, BiomeSampler.paramDistance(5000L, 9000L, 7000L)); }
    @Test void paramDistance_at_min() { assertEquals(0L, BiomeSampler.paramDistance(5000L, 9000L, 5000L)); }
    @Test void paramDistance_at_max() { assertEquals(0L, BiomeSampler.paramDistance(5000L, 9000L, 9000L)); }
    @Test void paramDistance_above() { assertEquals(2000L, BiomeSampler.paramDistance(5000L, 9000L, 11000L)); }
    @Test void paramDistance_below() { assertEquals(2000L, BiomeSampler.paramDistance(5000L, 9000L, 3000L)); }
    @Test void paramDistance_point_match() { assertEquals(0L, BiomeSampler.paramDistance(7000L, 7000L, 7000L)); }

    @Test void fitness_all_in_range_no_offset() {
        long[] mn = {-1000, -1000, -1000, -1000, -1000, -1000};
        long[] mx = { 1000,  1000,  1000,  1000,  1000,  1000};
        long[] t  = {    0,     0,     0,     0,     0,     0};
        assertEquals(0L, BiomeSampler.fitness(mn, mx, 0L, t));
    }

    @Test void fitness_with_offset() {
        long[] mn = {-1000, -1000, -1000, -1000, -1000, -1000};
        long[] mx = { 1000,  1000,  1000,  1000,  1000,  1000};
        long[] t  = {    0,     0,     0,     0,     0,     0};
        // sq(10000) = 100_000_000
        assertEquals(100_000_000L, BiomeSampler.fitness(mn, mx, 10000L, t));
    }

    @Test void fitness_one_param_out_of_range() {
        long[] mn = {-1000, -1000, -1000, -1000, -1000, -1000};
        long[] mx = { 1000,  1000,  1000,  1000,  1000,  1000};
        long[] t  = { 3000,     0,     0,     0,     0,     0}; // temperature 2000 above max
        // sq(2000) = 4_000_000
        assertEquals(4_000_000L, BiomeSampler.fitness(mn, mx, 0L, t));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```
./gradlew :furnace-server:test --tests "*.BiomeSamplerTest"
```
Expected: FAILED (BiomeSampler 없음)

- [ ] **Step 3: BiomeSampler.java 골격 + 수학 함수 구현**

```java
// 바닐라 Climate 거리 공식으로 multi-noise 최근접 바이옴을 찾는 샘플러
package org.domi.furnace.server.world.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.gen.df.DensityFunction;

import java.util.ArrayList;
import java.util.List;

public final class BiomeSampler {

    /** 단일 바이옴 climate 파라미터 항목. paramsMin/Max 인덱스: 0=temp 1=hum 2=cont 3=ero 4=depth 5=weird */
    public record BiomeEntry(long[] paramsMin, long[] paramsMax, long offset, int registryId) {}

    private final List<BiomeEntry> entries;

    private BiomeSampler(List<BiomeEntry> entries) {
        this.entries = entries;
    }

    /** biome_parameters.json 로드 + RegistrySet으로 biome name → ID 변환. */
    public static BiomeSampler load(WorldgenData data, RegistrySet registries) {
        JsonArray arr = data.biomeParameters();
        List<BiomeEntry> entries = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            String biomeName = obj.get("biome").getAsString();
            int id = registries.indexOf("minecraft:worldgen/biome", biomeName);
            if (id < 0) throw new IllegalStateException("Unknown biome in parameters: " + biomeName);
            long[] min = new long[6];
            long[] max = new long[6];
            String[] keys = {"temperature","humidity","continentalness","erosion","depth","weirdness"};
            for (int i = 0; i < 6; i++) {
                JsonArray range = obj.get(keys[i]).getAsJsonArray();
                min[i] = range.get(0).getAsLong();
                max[i] = range.get(1).getAsLong();
            }
            long offset = obj.get("offset").getAsLong();
            entries.add(new BiomeEntry(min, max, offset, id));
        }
        return new BiomeSampler(entries);
    }

    // -- Climate 수학 (vanilla Climate.java 비트일치) --

    /** double → float 캐스트 후 quantize. 바닐라 Climate.quantizeCoord(float) = (long)(f * 10000.0F). */
    static long quantize(float f) { return (long)(f * 10000.0F); }

    /** 바닐라 Climate.Parameter.distance(long point): 0=범위 내, else 거리. */
    static long paramDistance(long min, long max, long v) {
        long l = v - max;
        long l1 = min - v;
        return l > 0L ? l : Math.max(l1, 0L);
    }

    /** 바닐라 ParameterPoint.fitness: sq(distance) 6개 합 + sq(offset). */
    static long fitness(long[] paramsMin, long[] paramsMax, long offset, long[] target) {
        long f = offset * offset;
        for (int i = 0; i < 6; i++) {
            long d = paramDistance(paramsMin[i], paramsMax[i], target[i]);
            f += d * d;
        }
        return f;
    }

    // -- stub for load/sample (Task 3에서 구현) --
    public List<BiomeEntry> entries() { return entries; }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```
./gradlew :furnace-server:test --tests "*.BiomeSamplerTest"
```
Expected: 모든 테스트 PASS

- [ ] **Step 5: 커밋**

```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/BiomeSampler.java \
        furnace-server/src/test/java/org/domi/furnace/server/world/gen/BiomeSamplerTest.java
git commit -m "feat(worldgen): BiomeSampler climate math (quantize/paramDistance/fitness)"
```

---

### Task 3: WorldgenData 확장 + BiomeSampler.sample() 구현

**Background:** WorldgenData에 biomeParameters() 추가, BiomeSampler에 sampleChunk()/sample() 구현. 쿼트 좌표 체계: 내부 `absQuartY` 0..95 (블록 y = -64 + absQuartY*4). 바닐라 쿼트 y = absQuartY - 16. sample()은 router의 6 기후 DF를 SinglePointContext로 평가.

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/WorldgenData.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/BiomeSampler.java`

- [ ] **Step 1: WorldgenData.biomeParameters() 추가**

`WorldgenData.java`의 기존 `public JsonObject noiseSettings(String id)` 메서드 아래에:

```java
/** biome_parameters.json (quantized long 파라미터 배열). */
public JsonArray biomeParameters() {
    JsonElement el = loadResource(basePath + "/biome_parameters.json");
    if (el == null) {
        throw new IllegalStateException("biome_parameters.json 없음: " + basePath + "/biome_parameters.json");
    }
    return el.getAsJsonArray();
}
```

import 추가:
```java
import com.google.gson.JsonArray;
```

- [ ] **Step 2: BiomeSampler.sample() + sampleChunk() 구현**

BiomeSampler.java에 추가 (기존 `entries()` 메서드 뒤):

```java
/**
 * 청크 (cx, cz) 의 1536 quart 바이옴 ID 배열 반환.
 * index = absQuartY * 16 + localQuartZ * 4 + localQuartX
 * absQuartY 0..95, localQuartX/Z 0..3
 */
public int[] sampleChunk(int cx, int cz, NoiseRouter router) {
    int[] result = new int[1536];
    for (int qy = 0; qy < 96; qy++) {
        for (int qz = 0; qz < 4; qz++) {
            for (int qx = 0; qx < 4; qx++) {
                result[qy * 16 + qz * 4 + qx] = sample(cx * 4 + qx, qy, cz * 4 + qz, router);
            }
        }
    }
    return result;
}

/**
 * worldQuartX = 전역 quart x (cx*4+localX), absQuartY 0..95, worldQuartZ = 전역 quart z.
 * 바닐라 Climate.Sampler.sample(quartX, quartY, quartZ) 로직 재현.
 */
public int sample(int worldQuartX, int absQuartY, int worldQuartZ, NoiseRouter router) {
    int bx = worldQuartX * 4;
    int by = -64 + absQuartY * 4;          // MIN_Y + absQuartY*4
    int bz = worldQuartZ * 4;
    DensityFunction.SinglePointContext ctx = new DensityFunction.SinglePointContext(bx, by, bz);

    // double → float 먼저 캐스트 후 quantize (바닐라 Climate.Sampler.sample 동일)
    long qt = quantize((float) router.temperature().compute(ctx));
    long qh = quantize((float) router.humidity().compute(ctx));
    long qc = quantize((float) router.continents().compute(ctx));
    long qe = quantize((float) router.erosion().compute(ctx));
    long qd = quantize((float) router.depth().compute(ctx));
    long qw = quantize((float) router.ridges().compute(ctx));
    long[] target = {qt, qh, qc, qe, qd, qw};

    int bestId = entries.get(0).registryId();
    long bestFitness = Long.MAX_VALUE;
    for (BiomeEntry entry : entries) {
        long f = fitness(entry.paramsMin(), entry.paramsMax(), entry.offset(), target);
        if (f < bestFitness) {
            bestFitness = f;
            bestId = entry.registryId();
        }
    }
    return bestId;
}
```

- [ ] **Step 3: 로드 카운트 테스트 작성**

`BiomeSamplerTest.java`에 추가:

```java
@Test
void load_entry_count() {
    WorldgenData data = WorldgenData.load("worldgen/1.21.1");
    RegistrySet reg = org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt");
    BiomeSampler sampler = BiomeSampler.load(data, reg);
    // OverworldBiomeBuilder는 최소 64개 바이옴 고유값 대비 여러 climate range 생성 → 총 160+ entries
    assertTrue(sampler.entries().size() > 100, "Expected >100 entries, got " + sampler.entries().size());
}
```

- [ ] **Step 4: 테스트 실행**

```
./gradlew :furnace-server:test --tests "*.BiomeSamplerTest"
```
Expected: 모든 테스트 PASS (load_entry_count 포함)

- [ ] **Step 5: 커밋**

```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/WorldgenData.java \
        furnace-server/src/main/java/org/domi/furnace/server/world/gen/BiomeSampler.java \
        furnace-server/src/test/java/org/domi/furnace/server/world/gen/BiomeSamplerTest.java
git commit -m "feat(worldgen): BiomeSampler load() + sampleChunk() multi-noise lookup"
```

---

### Task 4: NoiseRouter — 6개 기후 DF 추가

**Background:** noise_settings/overworld.json의 noise_router에 temperature, vegetation(=humidity), continents, erosion, depth, ridges가 있다. DensityFunctionParser.parseElement()로 파싱. 기존 final_density 파싱 코드 패턴 그대로.

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseRouter.java`

- [ ] **Step 1: 실패하는 테스트 추가**

`NoiseRouterTest.java`의 `settingsAndRouter()` 테스트에 아래 assertion 추가 (메서드 끝, commit 전):

```java
// 6개 기후 DF 비null + 유한값 확인
assertNotNull(r.temperature());
assertNotNull(r.humidity());
assertNotNull(r.continents());
assertNotNull(r.erosion());
assertNotNull(r.depth());
assertNotNull(r.ridges());
double tv = r.temperature().compute(new DensityFunction.SinglePointContext(0, 64, 0));
assertTrue(Double.isFinite(tv), "temperature finite, got " + tv);
```

- [ ] **Step 2: 테스트 실패 확인**

```
./gradlew :furnace-server:test --tests "*.NoiseRouterTest"
```
Expected: FAILED (temperature() 메서드 없음)

- [ ] **Step 3: NoiseRouter.java 수정**

```java
// 바닐라 NoiseRouter의 경량 포트. final_density + 6개 기후 DF 보유.
package org.domi.furnace.server.world.gen;

import com.google.gson.JsonObject;
import org.domi.furnace.server.world.gen.df.DensityFunction;
import org.domi.furnace.server.world.gen.df.DensityFunctionParser;

public final class NoiseRouter {

    private final DensityFunction finalDensity;
    private final DensityFunction temperature;
    private final DensityFunction humidity;   // JSON 필드명: "vegetation"
    private final DensityFunction continents;
    private final DensityFunction erosion;
    private final DensityFunction depth;
    private final DensityFunction ridges;     // JSON 필드명: "ridges" (= weirdness)

    private NoiseRouter(DensityFunction finalDensity,
                        DensityFunction temperature, DensityFunction humidity,
                        DensityFunction continents, DensityFunction erosion,
                        DensityFunction depth, DensityFunction ridges) {
        this.finalDensity = finalDensity;
        this.temperature  = temperature;
        this.humidity     = humidity;
        this.continents   = continents;
        this.erosion      = erosion;
        this.depth        = depth;
        this.ridges       = ridges;
    }

    public static NoiseRouter build(WorldgenData data, NoiseSettings settings, long seed) {
        DensityFunctionParser parser = new DensityFunctionParser(data, seed);
        JsonObject routerJson = data.noiseSettings("minecraft:overworld")
                                    .get("noise_router").getAsJsonObject();
        return new NoiseRouter(
            parser.parseElement(routerJson.get("final_density")),
            parser.parseElement(routerJson.get("temperature")),
            parser.parseElement(routerJson.get("vegetation")),   // humidity
            parser.parseElement(routerJson.get("continents")),
            parser.parseElement(routerJson.get("erosion")),
            parser.parseElement(routerJson.get("depth")),
            parser.parseElement(routerJson.get("ridges"))
        );
    }

    public DensityFunction finalDensity() { return finalDensity; }
    public DensityFunction temperature()  { return temperature;  }
    public DensityFunction humidity()     { return humidity;     }
    public DensityFunction continents()   { return continents;   }
    public DensityFunction erosion()      { return erosion;      }
    public DensityFunction depth()        { return depth;        }
    public DensityFunction ridges()       { return ridges;       }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```
./gradlew :furnace-server:test --tests "*.NoiseRouterTest"
```
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseRouter.java \
        furnace-server/src/test/java/org/domi/furnace/server/world/gen/NoiseRouterTest.java
git commit -m "feat(worldgen): NoiseRouter add 6 climate DFs (temperature/humidity/continents/erosion/depth/ridges)"
```

---

### Task 5: ChunkSection/ChunkColumn setBiome + NoiseWorldSource 바이옴 배선

**Background:**
- `ChunkSection.biomeIndex(x, y, z) = y*16 + z*4 + x` (각 0-3). 현재 getter만 있고 setter 없음.
- `ChunkColumn.setBiome(quartX, quartY, quartZ, id)`: 내부 quartY 0..95 → section = quartY/4, sectionQuartY = quartY%4.
- `NoiseWorldSource.overworld()`: RegistrySet 파라미터 추가, BiomeSampler 초기화.
- `NoiseWorldSource.generate()`: sampleChunk() 결과로 biomes[] 채움.
- `FurnaceMain`: overworld() 호출 업데이트.

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/ChunkSection.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/ChunkColumn.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseWorldSource.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/FurnaceMain.java`

- [ ] **Step 1: ChunkSection.setBiome 추가**

`ChunkSection.java`의 `fillBiome` 메서드 아래에:

```java
public void setBiome(int x, int y, int z, int biomeId) {
    biomes[biomeIndex(x, y, z)] = biomeId;
}
```

- [ ] **Step 2: ChunkColumn.setBiome 추가**

`ChunkColumn.java`의 `setSurface` 메서드 아래에:

```java
/**
 * 청크 내 quart 좌표(quartX,quartY,quartZ)에 바이옴 id 설정.
 * quartX, quartZ 0..3, quartY 0..95 (블록 y = -64 + quartY*4).
 */
public void setBiome(int quartX, int quartY, int quartZ, int biomeId) {
    sections[quartY / 4].setBiome(quartX, quartY % 4, quartZ, biomeId);
}
```

- [ ] **Step 3: 단위 테스트 작성**

기존 `SuperflatWorldSourceTest.java` 옆에 또는 `ChunkColumnTest.java` 파일 생성:

```java
// ChunkColumn.setBiome / getBiome 라운드트립 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChunkColumnBiomeTest {

    @Test void setBiome_roundtrip() {
        ChunkColumn col = new ChunkColumn(0);
        col.setBiome(2, 10, 3, 42);
        // section = 10/4=2, sectionQuartY = 10%4=2, biomeIndex(2,2,3) = 2*16+3*4+2 = 32+12+2=46
        assertEquals(42, col.sections()[2].getBiome(2, 2, 3));
    }

    @Test void setBiome_bottom_quart() {
        ChunkColumn col = new ChunkColumn(5);
        col.setBiome(0, 0, 0, 7);
        assertEquals(7, col.sections()[0].getBiome(0, 0, 0));
    }

    @Test void setBiome_top_quart() {
        ChunkColumn col = new ChunkColumn(0);
        col.setBiome(3, 95, 3, 99);
        // section = 95/4=23, sectionQuartY = 95%4=3, biomeIndex(3,3,3)=3*16+3*4+3=48+12+3=63
        assertEquals(99, col.sections()[23].getBiome(3, 3, 3));
    }
}
```

- [ ] **Step 4: 테스트 실패 확인**

```
./gradlew :furnace-server:test --tests "*.ChunkColumnBiomeTest"
```
Expected: FAILED (setBiome 없음)

- [ ] **Step 5: 테스트 통과 확인**

```
./gradlew :furnace-server:test --tests "*.ChunkColumnBiomeTest"
```
Expected: PASS

- [ ] **Step 6: NoiseWorldSource 수정**

`NoiseWorldSource.java` 전체 교체:

```java
// 바닐라 비트일치 노이즈 지형 + multi-noise 바이옴 WorldSource
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;

public final class NoiseWorldSource implements WorldSource {

    private final NoiseRouter router;
    private final NoiseSettings settings;
    private final BiomeSampler biomeSampler;
    private final int stoneId;
    private final int waterId;
    private final int lavaId;

    private NoiseWorldSource(NoiseRouter router, NoiseSettings settings,
                              BiomeSampler biomeSampler,
                              int stoneId, int waterId, int lavaId) {
        this.router       = router;
        this.settings     = settings;
        this.biomeSampler = biomeSampler;
        this.stoneId      = stoneId;
        this.waterId      = waterId;
        this.lavaId       = lavaId;
    }

    public static NoiseWorldSource overworld(long seed, BlockRegistry reg, RegistrySet registries) {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        NoiseSettings settings = NoiseSettings.fromJson(data.noiseSettings("minecraft:overworld"));
        NoiseRouter router = NoiseRouter.build(data, settings, seed);
        BiomeSampler biomeSampler = BiomeSampler.load(data, registries);

        int stoneId = reg.defaultStateId(settings.defaultBlock());
        int waterId = reg.defaultStateId(settings.defaultFluid());
        int lavaId  = reg.defaultStateId("minecraft:lava");

        return new NoiseWorldSource(router, settings, biomeSampler, stoneId, waterId, lavaId);
    }

    @Override
    public ChunkColumn generate(int chunkX, int chunkZ) {
        // 1. 지형 밀도 마스크
        NoiseChunk nc = new NoiseChunk(chunkX, chunkZ, router, settings);
        boolean[] stone = nc.stoneMask();

        int minY      = settings.minY();
        int height    = settings.height();
        int seaLevel  = settings.seaLevel();
        int lavaFloor = settings.lavaFloorY();

        // 2. 바이옴 샘플링 (quart 단위)
        int[] biomeIds = biomeSampler.sampleChunk(chunkX, chunkZ, router);

        // 3. ChunkColumn 구성 (biomeId=0 placeholder → setBiome으로 덮어씀)
        ChunkColumn col = new ChunkColumn(0);

        // biomes[] 채우기
        for (int qy = 0; qy < 96; qy++) {
            for (int qz = 0; qz < 4; qz++) {
                for (int qx = 0; qx < 4; qx++) {
                    col.setBiome(qx, qy, qz, biomeIds[qy * 16 + qz * 4 + qx]);
                }
            }
        }

        // blocks[] 채우기
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int surfaceHeight = 0;
                for (int y = minY + height - 1; y >= minY; y--) {
                    int idx = (y - minY) * 256 + lz * 16 + lx;
                    int state;
                    if (stone[idx]) {
                        state = stoneId;
                    } else if (y < lavaFloor) {
                        state = lavaId;
                    } else if (y < seaLevel) {
                        state = waterId;
                    } else {
                        state = ChunkSection.AIR;
                    }
                    if (state != ChunkSection.AIR && surfaceHeight == 0) {
                        surfaceHeight = (y - minY) + 1;
                    }
                    col.setBlock(lx, y, lz, state);
                }
                col.setSurface(lx, lz, surfaceHeight);
            }
        }

        return col;
    }
}
```

- [ ] **Step 7: FurnaceMain 업데이트**

`FurnaceMain.java`에서:
```java
// 기존:
var noiseSource = NoiseWorldSource.overworld(0L, blockRegistry);

// 변경:
var noiseSource = NoiseWorldSource.overworld(0L, blockRegistry, registries);
```

- [ ] **Step 8: 기존 parity 테스트 회귀 없음 확인**

```
./gradlew :furnace-server:test --tests "*.NoiseWorldSourceParityTest"
```
Expected: 3 tests PASS (terrain 변경 없음)

- [ ] **Step 9: 전체 빌드**

```
./gradlew build
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: 커밋**

```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/ChunkSection.java \
        furnace-server/src/main/java/org/domi/furnace/server/world/ChunkColumn.java \
        furnace-server/src/test/java/org/domi/furnace/server/world/ChunkColumnBiomeTest.java \
        furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseWorldSource.java \
        furnace-server/src/main/java/org/domi/furnace/server/FurnaceMain.java
git commit -m "feat(worldgen): wire BiomeSampler into NoiseWorldSource; ChunkColumn.setBiome"
```

---

### Task 6: Oracle — biome fixture 덤프

**Background:** oracle이 바닐라 `MultiNoiseBiomeSource.getNoiseBiome(qx, qy, qz, sampler)` 호출 → 바이옴 이름 문자열로 fixture 저장. 파리티 테스트가 문자열 → ID 변환. 픽스처 포맷: 헤더(20B) + 1536 × (int16 nameLen + nameLen bytes UTF-8). 바닐라 쿼트 y = absQuartY - 16 (absQuartY 0..95).

**Files:**
- Modify: `worldgen-oracle/src/main/java/org/domi/oracle/OracleMain.java`
- Output (commit): `furnace-server/src/test/resources/worldgen/fixtures/biome_0_0_0.bin`, `biome_0_5_5.bin`, `biome_123456789_0_0.bin`

- [ ] **Step 1: dumpBiomeChunk 메서드 추가**

```java
// OracleMain.main() 루프의 else if 분기에 추가:
} else if (arg.startsWith("biome:")) {
    String[] parts = arg.substring("biome:".length()).split(":");
    int cx = Integer.parseInt(parts[0]);
    int cz = Integer.parseInt(parts[1]);
    dumpBiomeChunk(seed, cx, cz, biomeSource, randomState, outDir);
}
```

```java
private static void dumpBiomeChunk(
    long seed, int cx, int cz,
    MultiNoiseBiomeSource biomeSource,
    RandomState randomState,
    Path outDir
) throws Exception {
    Climate.Sampler sampler = randomState.sampler();

    // 1536 quart 바이옴 이름 수집
    // absQuartY 0..95 → vanillaQuartY = absQuartY - 16
    // worldQuartX = cx*4 + localQX, worldQuartZ = cz*4 + localQZ
    String[] biomeNames = new String[1536];
    for (int qy = 0; qy < 96; qy++) {
        int vanillaQY = qy - 16;
        for (int qz = 0; qz < 4; qz++) {
            for (int qx = 0; qx < 4; qx++) {
                int worldQX = cx * 4 + qx;
                int worldQZ = cz * 4 + qz;
                Holder<Biome> holder = biomeSource.getNoiseBiome(worldQX, vanillaQY, worldQZ, sampler);
                biomeNames[qy * 16 + qz * 4 + qx] =
                    holder.unwrapKey().orElseThrow().location().toString();
            }
        }
    }

    // 직렬화: 헤더(20B) + 1536×(int16 len + bytes)
    // 먼저 크기 계산
    int bodySize = 0;
    byte[][] nameBytes = new byte[1536][];
    for (int i = 0; i < 1536; i++) {
        nameBytes[i] = biomeNames[i].getBytes(java.nio.charset.StandardCharsets.UTF_8);
        bodySize += 2 + nameBytes[i].length; // int16 + bytes
    }
    ByteBuffer buf = ByteBuffer.allocate(20 + bodySize).order(ByteOrder.LITTLE_ENDIAN);
    buf.putInt(0x42494F4D); // "BIOM"
    buf.putLong(seed);
    buf.putInt(cx);
    buf.putInt(cz);
    for (byte[] nb : nameBytes) {
        buf.putShort((short) nb.length);
        buf.put(nb);
    }

    Path out = outDir.resolve("biome_" + seed + "_" + cx + "_" + cz + ".bin");
    try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
        fos.write(buf.array());
    }
    System.out.printf("Wrote %s (%d bytes, %d quarts)%n", out, buf.capacity(), biomeNames.length);
}
```

- [ ] **Step 2: 컴파일 확인**

```
./gradlew :worldgen-oracle:compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: seed=0 두 청크 픽스처 생성**

```
./gradlew :worldgen-oracle:run --args="0 biome:0:0 biome:5:5"
```
Expected:
```
Wrote furnace-server/src/test/resources/worldgen/fixtures/biome_0_0_0.bin (...bytes, 1536 quarts)
Wrote furnace-server/src/test/resources/worldgen/fixtures/biome_0_5_5.bin (...bytes, 1536 quarts)
Oracle done.
```

- [ ] **Step 4: seed=123456789 픽스처 생성**

```
./gradlew :worldgen-oracle:run --args="123456789 biome:0:0"
```
Expected:
```
Wrote furnace-server/src/test/resources/worldgen/fixtures/biome_123456789_0_0.bin (...bytes, 1536 quarts)
Oracle done.
```

- [ ] **Step 5: 파일 존재 확인**

```bash
ls -la furnace-server/src/test/resources/worldgen/fixtures/biome_*.bin
```
Expected: 3개 파일 존재, 각 ~30-50 KB.

- [ ] **Step 6: 커밋**

```bash
git add worldgen-oracle/src/main/java/org/domi/oracle/OracleMain.java \
        "furnace-server/src/test/resources/worldgen/fixtures/biome_0_0_0.bin" \
        "furnace-server/src/test/resources/worldgen/fixtures/biome_0_5_5.bin" \
        "furnace-server/src/test/resources/worldgen/fixtures/biome_123456789_0_0.bin"
git commit -m "feat(oracle): biome fixture dump + 3 committed fixtures"
```

---

### Task 7: BiomeSamplerParityTest — 3 fixture 비트비교

**Background:** 픽스처는 바이옴 이름 문자열 저장. 테스트가 `RegistrySet.indexOf("minecraft:worldgen/biome", name)`으로 ID 변환 후 BiomeSampler 출력과 비교. 0 mismatch 목표.

**Files:**
- Create: `furnace-server/src/test/java/org/domi/furnace/server/world/gen/BiomeSamplerParityTest.java`

- [ ] **Step 1: 테스트 작성**

```java
// BiomeSampler multi-noise 바이옴 배치 바닐라 fixture 일치 검증
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.domi.furnace.server.net.registry.RegistrySet;

class BiomeSamplerParityTest {

    private void assertBiomeParity(long seed, int cx, int cz) throws Exception {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseSettings settings = NoiseSettings.fromJson(data.noiseSettings("minecraft:overworld"));
        NoiseRouter router = NoiseRouter.build(data, settings, seed);
        BiomeSampler sampler = BiomeSampler.load(data, registries);

        int[] got = sampler.sampleChunk(cx, cz, router);

        // 픽스처 로드
        byte[] fx;
        String fixtureName = "/worldgen/fixtures/biome_" + seed + "_" + cx + "_" + cz + ".bin";
        try (InputStream in = getClass().getResourceAsStream(fixtureName)) {
            assertNotNull(in, "fixture 없음: " + fixtureName);
            fx = in.readAllBytes();
        }

        // 헤더(20B) 파싱
        ByteBuffer header = ByteBuffer.wrap(fx, 0, 20).order(ByteOrder.LITTLE_ENDIAN);
        int magic = header.getInt();
        assertEquals(0x42494F4D, magic, "magic mismatch");

        // body: 1536 × (int16 len + bytes)
        int mismatch = 0;
        String first = null;
        int pos = 20;
        for (int i = 0; i < 1536; i++) {
            int len = ByteBuffer.wrap(fx, pos, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
            pos += 2;
            String biomeName = new String(fx, pos, len, StandardCharsets.UTF_8);
            pos += len;

            int expectedId = registries.indexOf("minecraft:worldgen/biome", biomeName);
            assertTrue(expectedId >= 0, "Unknown biome in fixture: " + biomeName);

            int qy = i / 16;
            int rem = i % 16;
            int qz = rem / 4;
            int qx = rem % 4;

            if (expectedId != got[i]) {
                mismatch++;
                if (first == null) {
                    first = "@quart(" + qx + "," + qy + "," + qz + ") want=" + biomeName
                          + "(id=" + expectedId + ") got=" + got[i];
                }
            }
        }
        assertEquals(0, mismatch, "seed=" + seed + " cx=" + cx + " cz=" + cz
                                + " mismatches=" + mismatch + " " + first);
    }

    @Test void seed0_spawn()  throws Exception { assertBiomeParity(0L, 0, 0); }
    @Test void seed0_far()   throws Exception { assertBiomeParity(0L, 5, 5); }
    @Test void seedB()       throws Exception { assertBiomeParity(123456789L, 0, 0); }
}
```

- [ ] **Step 2: 테스트 실행**

```
./gradlew :furnace-server:test --tests "*.BiomeSamplerParityTest"
```
Expected: 3 tests PASS, mismatch = 0.

만약 실패 시: 출력에서 첫 mismatch quart 좌표 + want/got 확인.
- `got` 바이옴이 일반적으로 틀리거나 같은 바이옴의 다른 ID면 → RegistrySet 순서 불일치 가능성 → registries/1.21.1.txt 첫 몇 줄과 vanilla `frozenBiomeRegistry.byId(0).toString()` 비교.
- 특정 quart만 틀리면 → quantize float→long 캐스트 확인, climate DF 파싱 이슈.

- [ ] **Step 3: 전체 빌드 + 모든 테스트 통과 확인**

```
./gradlew build
```
Expected: BUILD SUCCESSFUL, 모든 테스트 PASS (NoiseWorldSourceParityTest 포함).

- [ ] **Step 4: 커밋**

```bash
git add furnace-server/src/test/java/org/domi/furnace/server/world/gen/BiomeSamplerParityTest.java
git commit -m "test(worldgen): BiomeSamplerParityTest 3-fixture 0-mismatch parity"
```
