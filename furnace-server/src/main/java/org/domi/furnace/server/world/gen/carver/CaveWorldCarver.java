// 바닐라 CaveWorldCarver 비트정확 포팅 — worm tunnel + room 알고리즘
package org.domi.furnace.server.world.gen.carver;

import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.Aquifer;
import org.domi.furnace.server.world.gen.NoiseSettings;

public final class CaveWorldCarver extends WorldCarver {

    private static final int MAX_STEP = 31; // 16*2-1

    @Override
    public boolean isStartChunk(CarverConfig config, LegacyRandom random) {
        return random.nextFloat() < config.probability();
    }

    @Override
    public boolean carve(ChunkColumn col, CarverConfig config, int chunkX, int chunkZ,
                          NoiseSettings settings, Aquifer aquifer, BlockRegistry reg,
                          CarvingMask mask, LegacyRandom random, ChunkPos originChunk) {
        CarverConfig.Cave cave = (CarverConfig.Cave) config;

        int a     = random.nextInt(15) + 1;
        int b     = random.nextInt(a)  + 1;
        int count = random.nextInt(b);

        int lavaId = reg.defaultStateId("minecraft:lava");

        for (int i = 0; i < count; i++) {
            double startX    = originChunk.getBlockX(random.nextInt(16));
            double startY    = cave.y().sample(random, settings.minY(), settings.height());
            double startZ    = originChunk.getBlockZ(random.nextInt(16));
            float hRadMult   = cave.horizontalRadiusMultiplier().sample(random);
            float vRadMult   = cave.verticalRadiusMultiplier().sample(random);
            float floorLevel = cave.floorLevel().sample(random);

            SkipChecker skipChecker = (relX, relY, relZ) ->
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

                createTunnel(col, cave, chunkX, chunkZ, settings, aquifer, reg,
                    random.nextLong(), startX, startY, startZ,
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
        double hDist = 1.5 + thickness;
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

        LegacyRandom br = new LegacyRandom(worldSeed);
        int splitStep = br.nextInt(maxStep / 2) + maxStep / 4;
        boolean isThin = br.nextInt(6) == 0;
        float yawDir = 0, pitchDir = 0;

        for (int step = startStep; step < maxStep; step++) {
            float sinStep = (float) Math.sin((float) step * Math.PI / maxStep);
            double hDist  = 1.5 + sinStep * thickness;
            double vDist  = hDist * yScale;

            float cosPitch = (float) Math.cos(pitch);
            x += Math.cos(yaw) * cosPitch;
            y += Math.sin(pitch);
            z += Math.sin(yaw) * cosPitch;

            pitch    *= isThin ? 0.92f : 0.7f;
            pitch    += pitchDir * 0.1f;
            yaw      += yawDir * 0.1f;
            pitchDir *= 0.9f;
            yawDir   *= 0.75f;

            pitchDir += (br.nextFloat() - br.nextFloat()) * br.nextFloat() * 2.0f;
            yawDir   += (br.nextFloat() - br.nextFloat()) * br.nextFloat() * 4.0f;

            if (step == splitStep && thickness > 1.0f) {
                createTunnel(col, config, chunkX, chunkZ, settings, aquifer, reg,
                    br.nextLong(), x, y, z, hRadMult, vRadMult,
                    br.nextFloat() * 0.5f + 0.5f,
                    yaw - (float)(Math.PI / 2), pitch / 3.0f,
                    step, maxStep, 1.0f, mask, lavaId, skipChecker);
                createTunnel(col, config, chunkX, chunkZ, settings, aquifer, reg,
                    br.nextLong(), x, y, z, hRadMult, vRadMult,
                    br.nextFloat() * 0.5f + 0.5f,
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
