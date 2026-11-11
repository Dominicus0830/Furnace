// 노이즈 원시 함수 성능 경로가 바닐라 식과 비트까지 같은지 무작위·경계값으로 대조한다.
package org.domi.furnace.server.world.gen.noise;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class NoiseFastPathParityTest {

    private static void same(double expected, double actual, String what) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual),
                () -> what + ": " + expected + " vs " + actual);
    }

    @Test
    void sampleAndLerpMatchesByteTablePath() {
        SplittableRandom r = new SplittableRandom(20261111L);
        for (int n = 0; n < 40; n++) {
            ImprovedNoise noise = new ImprovedNoise(new XoroshiroRandomSource(r.nextLong()));
            for (int i = 0; i < 20000; i++) {
                int gx = r.nextInt(), gy = i % 3 == 0 ? r.nextInt(-300, 300) : r.nextInt(), gz = r.nextInt();
                double dx = r.nextDouble(), dz = r.nextDouble(), dy = r.nextDouble();
                double wy = i % 2 == 0 ? dy : dy - r.nextDouble() * 4;   // yScale 경로의 '이상한' y
                if (i % 97 == 0) { dx = 0.0; dy = -0.0; wy = -0.0; dz = 1.0 - 0x1p-53; }
                same(noise.sampleAndLerpReference(gx, gy, gz, dx, wy, dz, dy),
                        noiseSample(noise, gx, gy, gz, dx, wy, dz, dy), "sampleAndLerp");
            }
        }
    }

    private static double noiseSample(ImprovedNoise n, int gx, int gy, int gz, double dx, double wy, double dz, double dy) {
        return n.sampleAndLerp(gx, gy, gz, dx, wy, dz, dy);
    }

    /** 바닐라 ImprovedNoise.noise 식 그대로(옛 표 경로로 표본). */
    private static double reference(ImprovedNoise n, double x, double y, double z, double yScale, double yMax) {
        double d = x + n.xo, d1 = y + n.yo, d2 = z + n.zo;
        int floor = org.domi.furnace.server.world.gen.MathHelper.floor(d);
        int floor1 = org.domi.furnace.server.world.gen.MathHelper.floor(d1);
        int floor2 = org.domi.furnace.server.world.gen.MathHelper.floor(d2);
        double d3 = d - floor, d4 = d1 - floor1, d5 = d2 - floor2;
        double d7;
        if (yScale != 0.0) {
            double d6 = yMax >= 0.0 && yMax < d4 ? yMax : d4;
            d7 = org.domi.furnace.server.world.gen.MathHelper.floor(d6 / yScale + 1.0E-7F) * yScale;
        } else {
            d7 = 0.0;
        }
        return n.sampleAndLerpReference(floor, floor1, floor2, d3, d4 - d7, d5, d4);
    }

    @Test
    void wrapMatchesVanillaFormula() {
        SplittableRandom r = new SplittableRandom(7L);
        double[] edges = {0.0, -0.0, 8388608.0, -8388608.0, Math.nextDown(8388608.0), -Math.nextDown(8388608.0),
                16777216.0, -16777216.0, Math.nextDown(16777216.0), Math.nextUp(16777216.0), 33554432.0, -33554432.0,
                Double.MIN_VALUE, -Double.MIN_VALUE, 1e300, -1e300, 5e15, -5e15, 0.5, -0.5};
        for (double e : edges) same(PerlinNoise.wrapReference(e), PerlinNoise.wrap(e), "wrap " + e);
        for (int i = 0; i < 2_000_000; i++) {
            double mag = Math.pow(2, r.nextInt(-30, 60));
            double v = (r.nextDouble() * 2 - 1) * mag;
            same(PerlinNoise.wrapReference(v), PerlinNoise.wrap(v), "wrap " + v);
        }
    }

    @Test
    void wholeNoiseMatchesReferenceAtWorldCoordinates() {
        // 바닐라 식으로 다시 짠 noise(x,y,z,yScale,yMax) 와 실제 경로를 넓은 좌표에서 대조(yScale 경로 포함)
        SplittableRandom r = new SplittableRandom(99L);
        for (int n = 0; n < 10; n++) {
            ImprovedNoise noise = new ImprovedNoise(new XoroshiroRandomSource(r.nextLong()));
            for (int i = 0; i < 50000; i++) {
                double x = (r.nextDouble() * 2 - 1) * Math.pow(2, r.nextInt(0, 26));
                double y = (r.nextDouble() * 2 - 1) * Math.pow(2, r.nextInt(0, 12));
                double z = (r.nextDouble() * 2 - 1) * Math.pow(2, r.nextInt(0, 26));
                double ys = i % 2 == 0 ? 0.0 : r.nextDouble() * 4, ym = i % 2 == 0 ? 0.0 : (r.nextDouble() * 2 - 1) * 8;
                same(reference(noise, x, y, z, ys, ym), noise.noise(x, y, z, ys, ym), "noise");
            }
        }
    }
}
