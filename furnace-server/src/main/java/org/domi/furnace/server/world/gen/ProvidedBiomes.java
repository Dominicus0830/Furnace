// 플러그인 바이옴 공급자를 생성기에 잇는다(T-203 3단계) — CraftBukkit CustomWorldChunkManager 와 같은 자리: 칸마다 공급자에 묻고(기후 값을 함께),
// 나올 수 있는 바이옴 목록(지형지물 순서)도 공급자 것. 공급자가 틀린 바이옴을 주면 바닐라처럼 IllegalArgumentException("Cannot use the biome …").
package org.domi.furnace.server.world.gen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.domi.furnace.api.worldgen.BiomeProvider;
import org.domi.furnace.api.worldgen.Climate;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.gen.df.DensityFunction;

public final class ProvidedBiomes {

    private final BiomeProvider provider;
    private final Map<String, Integer> ids;
    private final List<String> possible;
    private final int[] possibleIds;
    private final double[] min = new double[6];
    private final double[] max = new double[6];
    private final DensityFunction[] climate;

    ProvidedBiomes(BiomeProvider provider, RegistrySet registries, NoiseRouter router) {
        this.provider = provider;
        List<String> all = registries.entries("minecraft:worldgen/biome");
        Map<String, Integer> m = new HashMap<>(all.size() * 2);
        for (int i = 0; i < all.size(); i++) m.put(all.get(i), i);
        this.ids = m;
        List<String> listed = provider.biomes();
        if (listed == null) throw new IllegalArgumentException("BiomeProvider#biomes returned null");
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String b : listed) {
            if (b == null || !m.containsKey(b)) throw new IllegalArgumentException("Cannot use the biome " + b);
            distinct.add(b);
        }
        this.possible = List.copyOf(new ArrayList<>(distinct));
        this.possibleIds = possible.stream().mapToInt(m::get).toArray();
        this.climate = new DensityFunction[] {router.temperature(), router.humidity(), router.continents(), router.erosion(), router.depth(), router.ridges()};
        for (int i = 0; i < 6; i++) {
            min[i] = climate[i].minValue();
            max[i] = climate[i].maxValue();
        }
    }

    public BiomeProvider provider() { return provider; }

    /** 나올 수 있는 바이옴 키(공급자 순서, 겹침 없음). */
    List<String> possible() { return possible; }

    int[] possibleIds() { return possibleIds; }

    /** 블록 좌표 칸의 바이옴 레지스트리 번호. */
    int at(int x, int y, int z, Point climate) {
        String b = provider.biome(x, y, z, climate);
        Integer id = b == null ? null : ids.get(b);
        if (id == null) throw new IllegalArgumentException("Cannot use the biome " + b);
        return id;
    }

    /** 처음 읽을 때 기후 값을 계산하는 칸 하나. 한 번의 공급자 호출 동안만 쓴다. */
    abstract class Point implements Climate {
        private long[] target;

        abstract void fill(long[] out);

        @Override
        public double value(Parameter p) {
            long[] t = target;
            if (t == null) {
                t = new long[6];
                fill(t);
                target = t;
            }
            return (float) t[p.ordinal()] / 10000.0F;   // Climate#unquantizeCoord
        }

        @Override
        public double min(Parameter p) { return min[p.ordinal()]; }

        @Override
        public double max(Parameter p) { return max[p.ordinal()]; }
    }

    /** 밀도 함수를 그 점에서 바로 계산(BiomeSource#getNoiseBiome(…, Climate.Sampler)). */
    Point direct(int x, int y, int z) {
        return new Point() {
            @Override
            void fill(long[] out) {
                var ctx = new DensityFunction.SinglePointContext(x, y, z);
                for (int i = 0; i < 6; i++) out[i] = BiomeSampler.quantize((float) climate[i].compute(ctx));
            }
        };
    }

    /** 청크 채우기: 노이즈 청크의 기후 캐시(바닐라 NoiseChunk#cachedClimateSampler). */
    Point cached(NoiseChunk chunk, int localQX, int absQY, int localQZ) {
        return new Point() {
            @Override
            void fill(long[] out) { chunk.fillTarget(localQX, absQY, localQZ, out); }
        };
    }
}
