// 바닐라 CaveWorldCarver 비트정확 포팅 — worm tunnel + room 알고리즘
package org.domi.furnace.server.world.gen.carver;

import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.Aquifer;
import org.domi.furnace.server.world.gen.NoiseSettings;

public final class CaveWorldCarver extends WorldCarver {

    // SectionPos.sectionToBlockCoord(getRange()*2-1) = (4*2-1)*16 = 112
    private static final int MAX_STEP = 112;

    @Override
    public boolean isStartChunk(CarverConfig config, LegacyRandom random) {
        return random.nextFloat() <= config.probability();
    }

    // DEBUG flag for origin(0,-1) cave tracing
    public static volatile boolean debugOrigin01 = false;

    @Override
    public boolean carve(ChunkColumn col, CarverConfig config, int chunkX, int chunkZ,
                          NoiseSettings settings, Aquifer aquifer, BlockRegistry reg,
                          CarvingMask mask, LegacyRandom random, ChunkPos originChunk) {
        CarverConfig.Cave cave = (CarverConfig.Cave) config;

        boolean dbg = debugOrigin01;
        int a     = random.nextInt(15) + 1;
        int b     = random.nextInt(a)  + 1;
        int count = random.nextInt(b);
        if (dbg) {
            int sx16_ = -999; // peek at next int without consuming
            System.out.printf("DBG_CAVE origin(%d,%d) a=%d b=%d count=%d%n",
                originChunk.x(), originChunk.z(), a, b, count);
        }

        int lavaId = reg.defaultStateId("minecraft:lava");

        for (int i = 0; i < count; i++) {
            double startX    = originChunk.getBlockX(random.nextInt(16));
            double startY    = cave.y().sample(random, settings.minY(), settings.height());
            double startZ    = originChunk.getBlockZ(random.nextInt(16));
            float hRadMult   = cave.horizontalRadiusMultiplier().sample(random);
            float vRadMult   = cave.verticalRadiusMultiplier().sample(random);
            float floorLevel = cave.floorLevel().sample(random);
            if (dbg) System.out.printf("DBG_CAVE i=%d startX=%.1f startY=%.1f startZ=%.1f hRad=%.4f vRad=%.4f floor=%.4f%n",
                i, startX, startY, startZ, hRadMult, vRadMult, floorLevel);

            SkipChecker skipChecker = (relX, relY, relZ, y) ->
                relY <= floorLevel || relX * relX + relY * relY + relZ * relZ >= 1.0;

            int tunnelCount = 1;
            if (random.nextInt(4) == 0) {
                float yScale    = cave.yScale().sample(random);
                float roomThick = 1.0f + random.nextFloat() * 6.0f;
                createRoom(col, cave, chunkX, chunkZ, settings, aquifer, reg, mask,
                    startX, startY, startZ, roomThick, yScale, lavaId, skipChecker);
                tunnelCount += random.nextInt(4);
            }

            for (int t = 0; t < tunnelCount; t++) {
                float yaw       = random.nextFloat() * 6.2831855f;
                float pitch     = (random.nextFloat() - 0.5f) / 4.0f;
                float thickness = getThickness(random);
                int maxStep     = MAX_STEP - random.nextInt(MAX_STEP / 4);
                long tunnelSeed = random.nextLong();

                if (dbg && i == 1) System.out.printf("DBG_CAVE i=1 t=%d yaw=%.6f pitch=%.6f thick=%.6f maxStep=%d seed=%d%n",
                    t, yaw, pitch, thickness, maxStep, tunnelSeed);

                createTunnel(col, cave, chunkX, chunkZ, settings, aquifer, reg,
                    tunnelSeed, startX, startY, startZ,
                    hRadMult, vRadMult, thickness, yaw, pitch,
                    0, maxStep, 1.0f, mask, lavaId, skipChecker);
            }
        }
        return true;
    }

    private float getThickness(LegacyRandom r) {
        float t = r.nextFloat() * 2.0f + r.nextFloat();
        if (r.nextInt(10) == 0) {
            t *= r.nextFloat() * r.nextFloat() * 3.0f + 1.0f;
        }
        return t;
    }

    private void createRoom(ChunkColumn col, CarverConfig.Cave config, int chunkX, int chunkZ,
                             NoiseSettings settings, Aquifer aquifer, BlockRegistry reg,
                             CarvingMask mask,
                             double x, double y, double z, float thickness, float yScale,
                             int lavaId, SkipChecker skipChecker) {
        double hDist = 1.5 + Mth.sin((float)(Math.PI / 2)) * thickness;
        double vDist = hDist * yScale;
        carveEllipsoid(col, chunkX, chunkZ, config, settings, aquifer, reg,
            x + 1.0, y, z, hDist, vDist, mask, skipChecker, lavaId);
    }

    private void createTunnel(
            ChunkColumn col, CarverConfig.Cave config, int chunkX, int chunkZ,
            NoiseSettings settings, Aquifer aquifer, BlockRegistry reg,
            long worldSeed, double x, double y, double z,
            float hRadMult, float vRadMult,
            float thickness, float yaw, float pitch,
            int startStep, int maxStep, float yScale,
            CarvingMask mask, int lavaId, SkipChecker skipChecker) {

        boolean dbgTunnel = debugOrigin01 && worldSeed == 5586391736412127207L && startStep == 0;

        LegacyRandom br = new LegacyRandom(worldSeed);
        int splitStep = br.nextInt(maxStep / 2) + maxStep / 4;
        boolean isThin = br.nextInt(6) == 0;
        if (dbgTunnel) System.out.printf("DBG_TUN init: splitStep=%d isThin=%b%n", splitStep, isThin);
        float yawDir = 0, pitchDir = 0;

        for (int step = startStep; step < maxStep; step++) {
            float sinStep = Mth.sin((float) Math.PI * step / maxStep);
            double hDist  = 1.5 + sinStep * thickness;
            double vDist  = hDist * yScale;

            float cosPitch = Mth.cos(pitch);
            x += Mth.cos(yaw) * cosPitch;
            y += Mth.sin(pitch);
            z += Mth.sin(yaw) * cosPitch;

            pitch    *= isThin ? 0.92f : 0.7f;
            pitch    += pitchDir * 0.1f;
            yaw      += yawDir * 0.1f;
            pitchDir *= 0.9f;
            yawDir   *= 0.75f;

            pitchDir += (br.nextFloat() - br.nextFloat()) * br.nextFloat() * 2.0f;
            yawDir   += (br.nextFloat() - br.nextFloat()) * br.nextFloat() * 4.0f;

            if (dbgTunnel && (step < 5 || step >= 44)) System.out.printf("DBG_TUN step=%d x=%.4f y=%.4f z=%.4f pitch=%.4f hDist=%.4f skip=%s%n",
                step, x, y, z, pitch, hDist, "?");

            if (step == splitStep && thickness > 1.0f) {
                long bSeed1 = br.nextLong();
                float bThick1 = br.nextFloat() * 0.5f + 0.5f;
                long bSeed2 = br.nextLong();
                float bThick2 = br.nextFloat() * 0.5f + 0.5f;
                if (dbgTunnel) System.out.printf("DBG_TUN SPLIT step=%d pos=(%.4f,%.4f,%.4f) yaw=%.4f bSeed1=%d bThick1=%.4f bSeed2=%d bThick2=%.4f%n",
                    step, x, y, z, yaw, bSeed1, bThick1, bSeed2, bThick2);
                createTunnel(col, config, chunkX, chunkZ, settings, aquifer, reg,
                    bSeed1, x, y, z, hRadMult, vRadMult, bThick1,
                    yaw - (float)(Math.PI / 2), pitch / 3.0f,
                    step, maxStep, 1.0f, mask, lavaId, skipChecker);
                createTunnel(col, config, chunkX, chunkZ, settings, aquifer, reg,
                    bSeed2, x, y, z, hRadMult, vRadMult, bThick2,
                    yaw + (float)(Math.PI / 2), pitch / 3.0f,
                    step, maxStep, 1.0f, mask, lavaId, skipChecker);
                return;
            }

            if (br.nextInt(4) == 0) continue;

            if (!canReach(chunkX, chunkZ, x, z, step, maxStep, thickness)) return;

            carveEllipsoid(col, chunkX, chunkZ, config, settings, aquifer, reg,
                x, y, z,
                hDist * hRadMult, vDist * vRadMult,
                mask, skipChecker, lavaId);
        }
    }
}
