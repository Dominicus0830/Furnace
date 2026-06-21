// 바닐라 ImprovedNoise(단일 옥타브 Perlin) 비트정확 포팅
package org.domi.furnace.server.world.gen.noise;

import org.domi.furnace.server.world.gen.MathHelper;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;

/**
 * 바닐라 net.minecraft.world.level.levelgen.synth.ImprovedNoise 를 비트정확하게 포팅.
 * 생성자 RNG 소비 순서: xo(nextDouble), yo(nextDouble), zo(nextDouble), 그 다음 Fisher-Yates 셔플 256회.
 * GRADIENT 테이블은 SimplexNoise.GRADIENT(16엔트리) 인라인.
 */
public final class ImprovedNoise {

    // SimplexNoise.GRADIENT(16 entries) - int[][3]
    private static final int[][] GRADIENT = {
        { 1,  1,  0},
        {-1,  1,  0},
        { 1, -1,  0},
        {-1, -1,  0},
        { 1,  0,  1},
        {-1,  0,  1},
        { 1,  0, -1},
        {-1,  0, -1},
        { 0,  1,  1},
        { 0, -1,  1},
        { 0,  1, -1},
        { 0, -1, -1},
        { 1,  1,  0},
        { 0, -1,  1},
        {-1,  1,  0},
        { 0, -1, -1}
    };

    private final byte[] p;
    public final double xo;
    public final double yo;
    public final double zo;

    public ImprovedNoise(XoroshiroRandomSource random) {
        // RNG 소비 순서 바닐라와 동일: 오프셋 3개 → Fisher-Yates 셔플 256회
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        this.p = new byte[256];

        for (int i = 0; i < 256; i++) {
            this.p[i] = (byte) i;
        }

        for (int i = 0; i < 256; i++) {
            int randomInt = random.nextInt(256 - i);
            byte b = this.p[i];
            this.p[i] = this.p[i + randomInt];
            this.p[i + randomInt] = b;
        }
    }

    /** LegacyRandomSource 경로 — state provider noise(LegacyNormalNoise)용. 소비순서 동일. */
    public ImprovedNoise(LegacyRandom random) {
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        this.p = new byte[256];

        for (int i = 0; i < 256; i++) {
            this.p[i] = (byte) i;
        }

        for (int i = 0; i < 256; i++) {
            int randomInt = random.nextInt(256 - i);
            byte b = this.p[i];
            this.p[i] = this.p[i + randomInt];
            this.p[i + randomInt] = b;
        }
    }

    public double noise(double x, double y, double z) {
        return this.noise(x, y, z, 0.0, 0.0);
    }

    public double noise(double x, double y, double z, double yScale, double yMax) {
        double d  = x + this.xo;
        double d1 = y + this.yo;
        double d2 = z + this.zo;
        int floor  = MathHelper.floor(d);
        int floor1 = MathHelper.floor(d1);
        int floor2 = MathHelper.floor(d2);
        double d3 = d  - floor;
        double d4 = d1 - floor1;
        double d5 = d2 - floor2;
        double d7;
        if (yScale != 0.0) {
            double d6;
            if (yMax >= 0.0 && yMax < d4) {
                d6 = yMax;
            } else {
                d6 = d4;
            }
            d7 = MathHelper.floor(d6 / yScale + 1.0E-7F) * yScale;
        } else {
            d7 = 0.0;
        }
        return this.sampleAndLerp(floor, floor1, floor2, d3, d4 - d7, d5, d4);
    }

    private static double gradDot(int gradIndex, double xFactor, double yFactor, double zFactor) {
        int[] g = GRADIENT[gradIndex & 15];
        return g[0] * xFactor + g[1] * yFactor + g[2] * zFactor;
    }

    private int p(int index) {
        return this.p[index & 0xFF] & 0xFF;
    }

    private double sampleAndLerp(int gridX, int gridY, int gridZ,
                                  double deltaX, double weirdDeltaY, double deltaZ, double deltaY) {
        int i  = this.p(gridX);
        int i1 = this.p(gridX + 1);
        int i2 = this.p(i  + gridY);
        int i3 = this.p(i  + gridY + 1);
        int i4 = this.p(i1 + gridY);
        int i5 = this.p(i1 + gridY + 1);
        double d  = gradDot(this.p(i2 + gridZ),     deltaX,        weirdDeltaY,        deltaZ);
        double d1 = gradDot(this.p(i4 + gridZ),     deltaX - 1.0,  weirdDeltaY,        deltaZ);
        double d2 = gradDot(this.p(i3 + gridZ),     deltaX,        weirdDeltaY - 1.0,  deltaZ);
        double d3 = gradDot(this.p(i5 + gridZ),     deltaX - 1.0,  weirdDeltaY - 1.0,  deltaZ);
        double d4 = gradDot(this.p(i2 + gridZ + 1), deltaX,        weirdDeltaY,        deltaZ - 1.0);
        double d5 = gradDot(this.p(i4 + gridZ + 1), deltaX - 1.0,  weirdDeltaY,        deltaZ - 1.0);
        double d6 = gradDot(this.p(i3 + gridZ + 1), deltaX,        weirdDeltaY - 1.0,  deltaZ - 1.0);
        double d7 = gradDot(this.p(i5 + gridZ + 1), deltaX - 1.0,  weirdDeltaY - 1.0,  deltaZ - 1.0);
        double d8  = MathHelper.smoothstep(deltaX);
        double d9  = MathHelper.smoothstep(deltaY);
        double d10 = MathHelper.smoothstep(deltaZ);
        return MathHelper.lerp3(d8, d9, d10, d, d1, d2, d3, d4, d5, d6, d7);
    }
}
