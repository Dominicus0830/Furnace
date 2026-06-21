// 바닐라 SimplexNoise 비트정확 포팅 — Biome.BIOME_INFO_NOISE(식생 분포 noise) 기반
package org.domi.furnace.server.world.gen.noise;

import org.domi.furnace.server.world.gen.carver.LegacyRandom;

public final class SimplexNoise {

    private static final int[][] GRADIENT = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
        {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
        {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}
    };
    private static final double SQRT_3 = Math.sqrt(3.0);
    private static final double F2 = 0.5 * (SQRT_3 - 1.0);
    private static final double G2 = (3.0 - SQRT_3) / 6.0;

    private final int[] p = new int[512];
    public final double xo;
    public final double yo;
    public final double zo;

    public SimplexNoise(LegacyRandom random) {
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        int i = 0;
        while (i < 256) {
            this.p[i] = i++;
        }
        for (int ix = 0; ix < 256; ix++) {
            int randomInt = random.nextInt(256 - ix);
            int i1 = this.p[ix];
            this.p[ix] = this.p[randomInt + ix];
            this.p[randomInt + ix] = i1;
        }
    }

    private int p(int index) {
        return this.p[index & 0xFF];
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    private static double dot(int[] gradient, double x, double y, double z) {
        return gradient[0] * x + gradient[1] * y + gradient[2] * z;
    }

    private double getCornerNoise3D(int gradientIndex, double x, double y, double z, double offset) {
        double d = offset - x * x - y * y - z * z;
        double d1;
        if (d < 0.0) {
            d1 = 0.0;
        } else {
            d *= d;
            d1 = d * d * dot(GRADIENT[gradientIndex], x, y, z);
        }
        return d1;
    }

    public double getValue(double x, double y) {
        double d = (x + y) * F2;
        int floor = floor(x + d);
        int floor1 = floor(y + d);
        double d1 = (floor + floor1) * G2;
        double d2 = floor - d1;
        double d3 = floor1 - d1;
        double d4 = x - d2;
        double d5 = y - d3;
        int i;
        int i1;
        if (d4 > d5) {
            i = 1;
            i1 = 0;
        } else {
            i = 0;
            i1 = 1;
        }
        double d6 = d4 - i + G2;
        double d7 = d5 - i1 + G2;
        double d8 = d4 - 1.0 + 2.0 * G2;
        double d9 = d5 - 1.0 + 2.0 * G2;
        int i2 = floor & 0xFF;
        int i3 = floor1 & 0xFF;
        int i4 = this.p(i2 + this.p(i3)) % 12;
        int i5 = this.p(i2 + i + this.p(i3 + i1)) % 12;
        int i6 = this.p(i2 + 1 + this.p(i3 + 1)) % 12;
        double cornerNoise3D = this.getCornerNoise3D(i4, d4, d5, 0.0, 0.5);
        double cornerNoise3D1 = this.getCornerNoise3D(i5, d6, d7, 0.0, 0.5);
        double cornerNoise3D2 = this.getCornerNoise3D(i6, d8, d9, 0.0, 0.5);
        return 70.0 * (cornerNoise3D + cornerNoise3D1 + cornerNoise3D2);
    }
}
