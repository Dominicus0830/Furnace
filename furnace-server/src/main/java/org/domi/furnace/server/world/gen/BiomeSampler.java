// 바닐라 Climate 거리 공식으로 multi-noise 최근접 바이옴을 찾는 샘플러
package org.domi.furnace.server.world.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.gen.df.DensityFunction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BiomeSampler {

    /** 단일 바이옴 climate 파라미터 항목. paramsMin/Max 인덱스: 0=temp 1=hum 2=cont 3=ero 4=depth 5=weird */
    public record BiomeEntry(long[] paramsMin, long[] paramsMax, long offset, int registryId) {}

    private final List<BiomeEntry> entries;

    // depth(d=4) 범위별 그룹핑 — vanilla grid 는 소수의 depth 밴드를 재사용한다.
    // 같은 그룹의 entry 는 임의 target 에서 동일한 depthDist² 를 가지므로
    // 그룹 best = min constPart(나머지 5차원 거리² + offset²) 로 환원된다.
    private final long[] groupDepthMin;   // [g]
    private final long[] groupDepthMax;   // [g]
    private final int[][] groupEntryIdx;  // [g][k] → entries index

    private final ClimateRTree rtree;
    /** 바이옴을 채울 섹션 수(오버월드 24, 네더 16). */
    private final int sectionCount;
    /** TheEndBiomeSource 모드: [the_end, end_highlands, end_midlands, small_end_islands, end_barrens] 레지스트리 id (null 이면 multi-noise). */
    private int[] endBiomes;
    private DensityFunction endErosion;

    private BiomeSampler(List<BiomeEntry> entries) {
        this(entries, 24);
    }

    private BiomeSampler(List<BiomeEntry> entries, int sectionCount) {
        this.sectionCount = sectionCount;
        this.entries = entries;
        List<ClimateRTree.Leaf> leaves = new ArrayList<>(entries.size());
        for (BiomeEntry e : entries) {
            long[] mn = new long[7];
            long[] mx = new long[7];
            System.arraycopy(e.paramsMin(), 0, mn, 0, 6);
            System.arraycopy(e.paramsMax(), 0, mx, 0, 6);
            mn[6] = e.offset();
            mx[6] = e.offset();
            leaves.add(new ClimateRTree.Leaf(mn, mx, e.registryId()));
        }
        this.rtree = entries.isEmpty() ? null : new ClimateRTree(leaves);

        Map<Long, List<Integer>> byDepth = new HashMap<>();
        List<Long> keyOrder = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            BiomeEntry e = entries.get(i);
            long key = (e.paramsMin()[4] << 32) | (e.paramsMax()[4] & 0xFFFFFFFFL);
            List<Integer> list = byDepth.get(key);
            if (list == null) {
                list = new ArrayList<>();
                byDepth.put(key, list);
                keyOrder.add(key);
            }
            list.add(i);
        }
        int g = keyOrder.size();
        this.groupDepthMin = new long[g];
        this.groupDepthMax = new long[g];
        this.groupEntryIdx = new int[g][];
        for (int gi = 0; gi < g; gi++) {
            long key = keyOrder.get(gi);
            this.groupDepthMin[gi] = key >> 32;
            this.groupDepthMax[gi] = (int) key; // sign-extend low 32 bits
            List<Integer> list = byDepth.get(key);
            int[] arr = new int[list.size()];
            for (int k = 0; k < arr.length; k++) arr[k] = list.get(k);
            this.groupEntryIdx[gi] = arr;
        }
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

    /** fitness 계산 중 부분합이 cutoff 이상이면 즉시 반환 (lower-bound pruning). */
    static long fitnessWithCutoff(long[] paramsMin, long[] paramsMax, long offset, long[] target, long cutoff) {
        long f = offset * offset;
        if (f >= cutoff) return f;
        for (int i = 0; i < 6; i++) {
            long d = paramDistance(paramsMin[i], paramsMax[i], target[i]);
            f += d * d;
            if (f >= cutoff) return f;
        }
        return f;
    }

    public List<BiomeEntry> entries() { return entries; }

    // -- 로드 & 샘플링 --

    /** biome_parameters.json 에서 BiomeSampler 를 빌드한다. 레지스트리에 없는 바이옴(예: pale_garden)은 건너뛴다. */
    public static BiomeSampler load(WorldgenData data, RegistrySet registries) {
        return load(data.biomeParameters(), registries, 24);
    }

    /** 파라미터 목록(biome_parameters*.json 형식)과 섹션 수로 빌드 — 네더 프리셋 등. */
    public static BiomeSampler load(JsonArray arr, RegistrySet registries, int sectionCount) {
        List<BiomeEntry> entries = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            String biomeName = obj.get("biome").getAsString();
            int id = registries.indexOf("minecraft:worldgen/biome", biomeName);
            if (id < 0) continue;  // skip biomes not in registry (e.g. pale_garden from 1.21.4 jar)
            long[] min = new long[6];
            long[] max = new long[6];
            String[] keys = {"temperature", "humidity", "continentalness", "erosion", "depth", "weirdness"};
            for (int i = 0; i < 6; i++) {
                JsonArray range = obj.get(keys[i]).getAsJsonArray();
                min[i] = range.get(0).getAsLong();
                max[i] = range.get(1).getAsLong();
            }
            long offset = obj.get("offset").getAsLong();
            entries.add(new BiomeEntry(min, max, offset, id));
        }
        return new BiomeSampler(entries, sectionCount);
    }

    /** 바닐라 TheEndBiomeSource — 중앙 섬(청크 거리² ≤ 4096) 은 the_end, 그 밖은 섹션 중심 erosion(end_islands) 값으로 4종. */
    public static BiomeSampler theEnd(RegistrySet registries, NoiseRouter router, int sectionCount) {
        BiomeSampler b = new BiomeSampler(new ArrayList<>(), sectionCount);
        String[] names = {"minecraft:the_end", "minecraft:end_highlands", "minecraft:end_midlands", "minecraft:small_end_islands", "minecraft:end_barrens"};
        b.endBiomes = new int[5];
        for (int i = 0; i < 5; i++) b.endBiomes[i] = registries.indexOf("minecraft:worldgen/biome", names[i]);
        b.endErosion = router.erosion();
        return b;
    }

    private int endBiome(int blockX, int blockY, int blockZ) {
        int sx = blockX >> 4, sz = blockZ >> 4;
        if ((long) sx * sx + (long) sz * sz <= 4096L) return endBiomes[0];
        double e = endErosion.compute(new DensityFunction.SinglePointContext((sx * 2 + 1) * 8, blockY, (sz * 2 + 1) * 8));
        if (e > 0.25) return endBiomes[1];
        if (e >= -0.0625) return endBiomes[2];
        return e < -0.21875 ? endBiomes[3] : endBiomes[4];
    }

    /** quarter-block 좌표에서 최근접 바이옴 레지스트리 ID 를 반환한다. */
    public int sample(int worldQuartX, int absQuartY, int worldQuartZ, NoiseRouter router) {
        return sampleQuart(worldQuartX, absQuartY - 16, worldQuartZ, router);
    }

    /** Vanilla noise-biome quart 좌표에서 최근접 바이옴 레지스트리 ID 를 반환한다. */
    public int sampleQuart(int worldQuartX, int worldQuartY, int worldQuartZ, NoiseRouter router) {
        if (endBiomes != null) return endBiome(worldQuartX * 4, worldQuartY * 4, worldQuartZ * 4);
        int bx = worldQuartX * 4;
        int by = worldQuartY * 4;
        int bz = worldQuartZ * 4;
        DensityFunction.SinglePointContext ctx = new DensityFunction.SinglePointContext(bx, by, bz);

        long[] target = {
            quantize((float) router.temperature().compute(ctx)),
            quantize((float) router.humidity().compute(ctx)),
            quantize((float) router.continents().compute(ctx)),
            quantize((float) router.erosion().compute(ctx)),
            quantize((float) router.depth().compute(ctx)),
            quantize((float) router.ridges().compute(ctx))
        };
        return findBestEntry(target);
    }

    /**
     * 청크(cx,cz) 의 모든 1536개 바이옴 ID(96 qy × 4 qz × 4 qx)를 반환한다.
     *
     * 컬럼 최적화: depth(d=4) 제외 5개 차원은 FlatCache 로 Y-불변.
     * 컬럼(qx,qz)당 1 pass 로 depth 그룹별 min constPart 를 구하면
     * qy당 연산은 그룹 수(소수) 비교로 환원된다. 결과는 linear 검색과 비트일치:
     *   min over entries(constPart_i + depthDist_g²) = min over groups(minConst_g + depthDist_g²)
     * 타이브레이크(min fitness → min entry index)도 그룹 내/간 min-idx 추적으로 보존.
     */
    public int[] sampleChunk(int cx, int cz, NoiseChunk noiseChunk) {
        int[] result = new int[sectionCount * 64];
        if (endBiomes != null) {
            // 청크 안 모든 쿼트가 같은 섹션 xz 라 한 번만 계산(erosion 은 y 불변).
            java.util.Arrays.fill(result, endBiome(cx * 16, 0, cz * 16));
            return result;
        }
        if (rtree == null) return result;

        // 바닐라 ChunkAccess.fillBiomesFromNoise 순서: 섹션을 아래에서 위로, 섹션 안에서 x 바깥 · y 가운데 · z 안쪽.
        // R-tree는 직전 결과 leaf를 탐색 출발점으로 쓰므로(거리 동률 시 직전 leaf 우선) 이 순서를 지켜야 동률이 같아진다.
        // 청크 첫 쿼리의 직전 leaf는 바닐라에선 스레드 이력이라 알 수 없어 null로 시작한다.
        long[] target = new long[6];
        ClimateRTree.Leaf last = null;
        for (int section = 0; section < sectionCount; section++) {
            for (int qx = 0; qx < 4; qx++) {
                for (int dy = 0; dy < 4; dy++) {
                    int qy = section * 4 + dy;
                    for (int qz = 0; qz < 4; qz++) {
                        noiseChunk.fillTarget(qx, qy, qz, target);
                        last = rtree.search(target, last);
                        result[qy * 16 + qz * 4 + qx] = last.value;
                    }
                }
            }
        }
        return result;
    }

    private int findBestEntry(long[] target) {
        if (rtree == null) return 0;
        return rtree.search(target, null).value;
    }
}
