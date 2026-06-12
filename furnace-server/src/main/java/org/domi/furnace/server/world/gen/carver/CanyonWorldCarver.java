// 바닐라 CanyonWorldCarver 비트정확 포팅 — 협곡 알고리즘 (Catmull-Rom width spline)
package org.domi.furnace.server.world.gen.carver;

import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.Aquifer;
import org.domi.furnace.server.world.gen.NoiseSettings;

public final class CanyonWorldCarver extends WorldCarver {

    @Override
    public boolean isStartChunk(CarverConfig config, LegacyRandom random) {
        return random.nextFloat() < config.probability();
    }

    @Override
    public boolean carve(ChunkColumn col, CarverConfig config, int chunkX, int chunkZ,
                          NoiseSettings settings, Aquifer aquifer, BlockRegistry reg,
                          CarvingMask mask, LegacyRandom random, ChunkPos originChunk) {
        CarverConfig.Canyon canyon = (CarverConfig.Canyon) config;

        int lavaId = reg.defaultStateId("minecraft:lava");

        double x = originChunk.getBlockX(random.nextInt(16));
        int    startY = canyon.y().sample(random, settings.minY(), settings.height());
        double y = startY;
        double z = originChunk.getBlockZ(random.nextInt(16));

        float yaw          = random.nextFloat() * 6.2831855f;
        float vertRot      = canyon.verticalRotation().sample(random);
        float yScale       = canyon.yScale().sample(random); // 3.0f
        float initThickness = canyon.shapeThickness().sample(random);
        float distanceFactor = canyon.shapeDistanceFactor().sample(random);

        int maxStep = (int)(240.0f * distanceFactor);

        float[] widths = {initThickness, initThickness, initThickness, initThickness};

        float yawDir = 0, pitchDir = 0;

        SkipChecker skipChecker = (relX, relY, relZ) -> relX * relX + relZ * relZ >= 1.0;

        for (int step = 0; step < maxStep; step++) {
            float w = canyon.shapeHorizontalRadiusFactor().sample(random);
            widths[0] = widths[1];
            widths[1] = widths[2];
            widths[2] = widths[3];
            widths[3] = w;

            float hWidth = catmullRomSpline(widths, 0.25f);
            float vWidth = hWidth * yScale;

            float cosPitch = (float) Math.cos(vertRot);
            x += Math.cos(yaw) * cosPitch;
            y += Math.sin(vertRot);
            z += Math.sin(yaw) * cosPitch;

            vertRot  *= 0.7f;
            vertRot  += pitchDir * 0.05f;
            yaw      += yawDir * 0.05f;
            pitchDir *= 0.8f;
            yawDir   *= 0.5f;

            pitchDir += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0f;
            yawDir   += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0f;

            if (!canReach(chunkX, chunkZ, x, z, step, maxStep, initThickness)) return true;

            carveEllipsoid(col, chunkX, chunkZ, canyon, settings, aquifer, reg,
                x, y, z, hWidth, vWidth, mask, skipChecker, lavaId);
        }
        return true;
    }

    private static float catmullRomSpline(float[] p, float t) {
        float t2 = t * t, t3 = t2 * t;
        return 0.5f * (
            2 * p[1] +
            (-p[0] + p[2]) * t +
            (2 * p[0] - 5 * p[1] + 4 * p[2] - p[3]) * t2 +
            (-p[0] + 3 * p[1] - 3 * p[2] + p[3]) * t3
        );
    }
}
