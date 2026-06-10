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

    public List<BiomeEntry> entries() { return entries; }

    // -- 로드 & 샘플링 --

    /** biome_parameters.json 에서 BiomeSampler 를 빌드한다. 레지스트리에 없는 바이옴(예: pale_garden)은 건너뛴다. */
    public static BiomeSampler load(WorldgenData data, RegistrySet registries) {
        JsonArray arr = data.biomeParameters();
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
        return new BiomeSampler(entries);
    }

    /** quarter-block 좌표에서 최근접 바이옴 레지스트리 ID 를 반환한다. */
    public int sample(int worldQuartX, int absQuartY, int worldQuartZ, NoiseRouter router) {
        int bx = worldQuartX * 4;
        int by = -64 + absQuartY * 4;
        int bz = worldQuartZ * 4;
        DensityFunction.SinglePointContext ctx = new DensityFunction.SinglePointContext(bx, by, bz);

        long qt = quantize((float) router.temperature().compute(ctx));
        long qh = quantize((float) router.humidity().compute(ctx));
        long qc = quantize((float) router.continents().compute(ctx));
        long qe = quantize((float) router.erosion().compute(ctx));
        long qd = quantize((float) router.depth().compute(ctx));
        long qw = quantize((float) router.ridges().compute(ctx));
        long[] target = {qt, qh, qc, qe, qd, qw};

        if (entries.isEmpty()) return 0;
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

    /** 청크(cx,cz) 의 모든 1536개 바이옴 ID(96 qy × 4 qz × 4 qx)를 반환한다. */
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
}
