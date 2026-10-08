// 바닐라 PerlinSimplexNoise(옥타브 중 last<=0 인 경우) 포팅 — Biome 온도 보정/frozen modifier 에서 사용
package org.domi.furnace.server.world.gen.noise;

import java.util.List;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;

public final class PerlinSimplexNoise {

    private final SimplexNoise[] noiseLevels;
    private final double highestFreqInputFactor;
    private final double highestFreqValueFactor;

    public PerlinSimplexNoise(LegacyRandom random, List<Integer> octaves) {
        int first = octaves.stream().min(Integer::compare).orElseThrow();
        int last = octaves.stream().max(Integer::compare).orElseThrow();
        int j = -first;
        int k = last;
        int l = j + k + 1;
        if (l < 1) throw new IllegalArgumentException("Total number of octaves needs to be >= 1");
        if (k > 0) throw new UnsupportedOperationException("양의 옥타브는 미지원(바닐라 사용처 없음)");
        SimplexNoise zero = new SimplexNoise(random);
        this.noiseLevels = new SimplexNoise[l];
        if (k >= 0 && k < l && octaves.contains(0)) noiseLevels[k] = zero;
        for (int m = k + 1; m < l; m++) {
            if (octaves.contains(k - m)) {
                noiseLevels[m] = new SimplexNoise(random);
            } else {
                for (int i = 0; i < 262; i++) random.nextInt();   // consumeCount(262): LegacyRandom은 호출당 1회 상태 전진
            }
        }
        this.highestFreqInputFactor = Math.pow(2.0, k);
        this.highestFreqValueFactor = 1.0 / (Math.pow(2.0, l) - 1.0);
    }

    public double getValue(double x, double y, boolean useOffsets) {
        double sum = 0.0;
        double in = highestFreqInputFactor;
        double out = highestFreqValueFactor;
        for (SimplexNoise noise : noiseLevels) {
            if (noise != null) {
                sum += noise.getValue(x * in + (useOffsets ? noise.xo : 0.0), y * in + (useOffsets ? noise.yo : 0.0)) * out;
            }
            in /= 2.0;
            out *= 2.0;
        }
        return sum;
    }
}
