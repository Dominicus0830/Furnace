// 바닐라 CanyonWorldCarver 비트정확 포팅 — per-Y widthFactors + updateVerticalRadius
package org.domi.furnace.server.world.gen.carver;

import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.Aquifer;
import org.domi.furnace.server.world.gen.NoiseSettings;

public final class CanyonWorldCarver extends WorldCarver {

    public static volatile boolean debugCarveParams = false;

    // SectionPos.sectionToBlockCoord(getRange()*2-1) = (4*2-1)*16 = 112
    private static final int BASE_MAX_STEP = 112;

    @Override
    public boolean isStartChunk(CarverConfig config, LegacyRandom random) {
        return random.nextFloat() <= config.probability();
    }

    @Override
    public boolean carve(ChunkColumn col, CarverConfig config, int chunkX, int chunkZ,
                          NoiseSettings settings, Aquifer aquifer, BlockRegistry reg,
                          CarvingMask mask, LegacyRandom random, ChunkPos originChunk) {
        CarverConfig.Canyon canyon = (CarverConfig.Canyon) config;

        int lavaId = reg.defaultStateId("minecraft:lava");

        double x      = originChunk.getBlockX(random.nextInt(16));
        int    startY = canyon.y().sample(random, settings.minY(), settings.height());
        double y      = startY;
        double z      = originChunk.getBlockZ(random.nextInt(16));

        float yaw      = random.nextFloat() * 6.2831855f;
        float vertRot  = canyon.verticalRotation().sample(random);
        float yScale   = (float) canyon.yScale().sample(random); // constant 3.0, no advance
        float initThick = canyon.shapeThickness().sample(random);

        int maxStep = (int)(BASE_MAX_STEP * canyon.shapeDistanceFactor().sample(random));
        long tunnelSeed = random.nextLong();

        if (debugCarveParams) {
            System.out.printf("DBG_CYN origin(%d,%d) x=%.1f y=%.1f z=%.1f yaw=%.6f vertRot=%.6f yScale=%.4f initThick=%.6f maxStep=%d tunnelSeed=%d%n",
                originChunk.x(), originChunk.z(), x, y, z, yaw, vertRot, yScale, initThick, maxStep, tunnelSeed);
            // Print widthFactors at key low-y indices (by=2 → idx=65, by=3 → idx=66)
            LegacyRandom wfCheck = new LegacyRandom(tunnelSeed);
            float[] wfArr = initWidthFactors(settings.height(), canyon, wfCheck);
            System.out.printf("DBG_WF wf[65]=%.6f wf[66]=%.6f (by=2 idx=65, by=3 idx=66, minY=%d)%n",
                wfArr[65], wfArr[66], settings.minY());
        }

        doCarve(col, canyon, chunkX, chunkZ, settings, aquifer, reg,
            tunnelSeed, x, y, z, initThick, yaw, vertRot, 0, maxStep, yScale, mask, lavaId);

        return true;
    }

    public static volatile boolean debugSteps = false;

    private void doCarve(
            ChunkColumn col, CarverConfig.Canyon config,
            int chunkX, int chunkZ, NoiseSettings settings,
            Aquifer aquifer, BlockRegistry reg,
            long seed, double x, double y, double z,
            float thickness, float yaw, float pitch,
            int startStep, int maxStep, double yScale,
            CarvingMask mask, int lavaId) {

        LegacyRandom br = new LegacyRandom(seed);
        float[] widthFactors = initWidthFactors(settings.height(), config, br);

        float yawDir = 0, pitchDir = 0;

        final float[] wf = widthFactors;
        final int minY   = settings.minY();
        SkipChecker skipChecker = (relX, relY, relZ, by) -> {
            int idx = by - minY - 1;
            if (idx < 0 || idx >= wf.length) return true;
            return (relX * relX + relZ * relZ) * wf[idx] + relY * relY / 6.0 >= 1.0;
        };

        for (int step = startStep; step < maxStep; step++) {
            double hDistBase = 1.5 + Mth.sin((float) Math.PI * step / maxStep) * thickness;
            // sample hDist/vDist BEFORE position update, ALWAYS (even skipped) — matches vanilla order
            double hDist = hDistBase * config.shapeHorizontalRadiusFactor().sample(br);
            double vDist = updateVerticalRadius(config, br, hDistBase * yScale, maxStep, step);

            float cosPitch = Mth.cos(pitch);
            x += Mth.cos(yaw) * cosPitch;
            y += Mth.sin(pitch);
            z += Mth.sin(yaw) * cosPitch;

            pitch    *= 0.7f;
            pitch    += pitchDir * 0.05f;
            yaw      += yawDir  * 0.05f;
            pitchDir *= 0.8f;
            yawDir   *= 0.5f;

            pitchDir += (br.nextFloat() - br.nextFloat()) * br.nextFloat() * 2.0f;
            yawDir   += (br.nextFloat() - br.nextFloat()) * br.nextFloat() * 4.0f;

            boolean skipped = br.nextInt(4) == 0;
            if (debugSteps) System.out.printf("DBG_STEP step=%d x=%.4f y=%.4f z=%.4f hDist=%.4f vDist=%.4f skip=%b%n", step, x, y, z, hDist, vDist, skipped);
            if (skipped) continue;

            if (!canReach(chunkX, chunkZ, x, z, step, maxStep, thickness)) {
                if (debugSteps) System.out.printf("DBG_EXIT canReach=false step=%d x=%.4f z=%.4f remaining=%d%n", step, x, z, maxStep - step);
                return;
            }

            if (debugSteps && chunkX == 0 && chunkZ == 0) {
                int dbgYMin = Math.max(settings.minY() + 1, (int)Math.floor(y - vDist) - 1);
                int dbgYMax = Math.min(settings.minY() + settings.height() - 1 - 7, (int)Math.floor(y + vDist) + 1);
                if (dbgYMin <= 5) System.out.printf("DBG_YRANGE step=%d x=%.2f y=%.4f z=%.2f vDist=%.4f yMin=%d yMax=%d lowestReach=%d%n",
                    step, x, y, z, vDist, dbgYMin, dbgYMax, dbgYMin + 1);
            }

            carveEllipsoid(col, chunkX, chunkZ, config, settings, aquifer, reg,
                x, y, z, hDist, vDist, mask, skipChecker, lavaId);
        }
        if (debugSteps) System.out.printf("DBG_DONE canyon doCarve finished step loop%n");
    }

    private float[] initWidthFactors(int genDepth, CarverConfig.Canyon config, LegacyRandom random) {
        float[] floats = new float[genDepth];
        float f = 1.0f;
        for (int i = 0; i < genDepth; i++) {
            if (i == 0 || random.nextInt(config.shapeWidthSmoothness()) == 0) {
                f = 1.0f + random.nextFloat() * random.nextFloat();
            }
            floats[i] = f * f;
        }
        return floats;
    }

    private double updateVerticalRadius(CarverConfig.Canyon config, LegacyRandom random,
                                         double verticalRadius, int maxStep, int step) {
        float f  = 1.0f - Math.abs(0.5f - (float)step / (float)maxStep) * 2.0f;
        float f1 = config.shapeVerticalRadiusDefaultFactor() + config.shapeVerticalRadiusCenterFactor() * f;
        float rb = 0.75f + random.nextFloat() * 0.25f;
        return (double)f1 * verticalRadius * (double)rb;
    }
}
