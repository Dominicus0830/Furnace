// 바닐라 Climate 거리 공식으로 multi-noise 최근접 바이옴을 찾는 샘플러
package org.domi.furnace.server.world.gen;

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
}
