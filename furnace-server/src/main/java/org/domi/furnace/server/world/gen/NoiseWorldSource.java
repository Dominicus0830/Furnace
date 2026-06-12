// 바닐라 비트일치 노이즈 지형 WorldSource. generateBlockIds + Aquifer 기반 블록 결정
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.gen.carver.CarvingMask;
import org.domi.furnace.server.world.gen.carver.OverworldCarvers;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.surface.SurfaceSystem;

/**
 * NoiseChunk 밀도 엔진을 사용해 바닐라 1.21.1 오버월드 지형을 생성하는 WorldSource.
 *
 * 블록 결정 규칙 (바닐라 NoiseBasedChunkGenerator.doFill 동일):
 *   final_density > 0  → stone (defaultBlock)
 *   else y < lavaFloorY(-54) → lava
 *   else y < seaLevel(63)    → water (defaultFluid)
 *   else                     → air
 *
 * router/settings/ids 는 생성 시 한 번만 빌드하며 generate() 는 thread-safe.
 */
public final class NoiseWorldSource implements WorldSource {

    private final NoiseRouter router;
    private final NoiseSettings settings;
    private final BiomeSampler biomeSampler;
    private final SurfaceSystem surfaceSystem;
    private final String[] registryBiomeNames;
    private final int stoneId;
    private final int waterId;
    private final int lavaId;
    private final long seed;
    private final BlockRegistry reg;
    private final OverworldCarvers overworldCarvers;

    private NoiseWorldSource(NoiseRouter router, NoiseSettings settings, BiomeSampler biomeSampler,
                              SurfaceSystem surfaceSystem, String[] registryBiomeNames,
                              int stoneId, int waterId, int lavaId,
                              long seed, BlockRegistry reg, OverworldCarvers overworldCarvers) {
        this.router             = router;
        this.settings           = settings;
        this.biomeSampler       = biomeSampler;
        this.surfaceSystem      = surfaceSystem;
        this.registryBiomeNames = registryBiomeNames;
        this.stoneId            = stoneId;
        this.waterId            = waterId;
        this.lavaId             = lavaId;
        this.seed               = seed;
        this.reg                = reg;
        this.overworldCarvers   = overworldCarvers;
    }

    /**
     * 오버월드 NoiseWorldSource 를 생성한다.
     *
     * @param seed       월드 시드
     * @param reg        블록 레지스트리 (state id 해석용)
     * @param registries 바이옴 레지스트리 (BiomeSampler 로드용)
     */
    public static NoiseWorldSource overworld(long seed, BlockRegistry reg, RegistrySet registries) {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        NoiseSettings settings = NoiseSettings.fromJson(data.noiseSettings("minecraft:overworld"));
        NoiseRouter router = NoiseRouter.build(data, settings, seed);

        int stoneId = reg.defaultStateId(settings.defaultBlock());  // minecraft:stone
        int waterId = reg.defaultStateId(settings.defaultFluid());  // minecraft:water
        int lavaId  = reg.defaultStateId("minecraft:lava");

        BiomeSampler biomeSampler = BiomeSampler.load(data, registries);

        java.util.List<String> biomeEntries = registries.entries("minecraft:worldgen/biome");
        String[] registryBiomeNames = biomeEntries.toArray(new String[0]);
        SurfaceSystem surfaceSystem = SurfaceSystem.create(data, settings, reg, seed, registryBiomeNames);

        OverworldCarvers overworldCarvers = OverworldCarvers.load();

        return new NoiseWorldSource(router, settings, biomeSampler, surfaceSystem,
                                    registryBiomeNames, stoneId, waterId, lavaId,
                                    seed, reg, overworldCarvers);
    }

    @Override
    public ChunkColumn generate(int chunkX, int chunkZ) {
        int minY      = settings.minY();       // -64
        int height    = settings.height();     // 384
        int seaLevel  = settings.seaLevel();   // 63
        int lavaFloor = settings.lavaFloorY(); // -54

        NoiseChunk nc = new NoiseChunk(chunkX, chunkZ, router, settings);
        Aquifer aquifer = settings.aquifersEnabled()
            ? new NoiseBasedAquifer(nc, chunkX, chunkZ, router, settings, router.aquiferRandom(), stoneId, waterId, lavaId)
            : new DisabledAquifer(seaLevel, lavaFloor, waterId, lavaId);
        int[] blockIds = nc.generateBlockIds(stoneId, aquifer);

        ChunkColumn col = new ChunkColumn(0);

        int[] biomeIds = biomeSampler.sampleChunk(chunkX, chunkZ, nc);
        for (int qy = 0; qy < 96; qy++) {
            for (int qz = 0; qz < 4; qz++) {
                for (int qx = 0; qx < 4; qx++) {
                    col.setBiome(qx, qy, qz, biomeIds[qy * 16 + qz * 4 + qx]);
                }
            }
        }

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int surfaceHeight = 0;
                for (int y = minY + height - 1; y >= minY; y--) {
                    int idx = (y - minY) * 256 + lz * 16 + lx;
                    int state = blockIds[idx];

                    if (state != ChunkSection.AIR && surfaceHeight == 0) {
                        surfaceHeight = (y - minY) + 1;
                    }

                    col.setBlock(lx, y, lz, state);
                }
                col.setSurface(lx, lz, surfaceHeight);
            }
        }

        surfaceSystem.buildSurface(col, biomeIds, nc, chunkX, chunkZ);

        return col;
    }

    private void applyCarvers(ChunkColumn col, int chunkX, int chunkZ, NoiseChunk nc) {
        CarvingMask mask = new CarvingMask(settings.minY(), settings.height());
        Aquifer aquifer = settings.aquifersEnabled()
            ? new NoiseBasedAquifer(nc, chunkX, chunkZ, router, settings, router.aquiferRandom(), stoneId, waterId, lavaId)
            : new DisabledAquifer(settings.seaLevel(), settings.lavaFloorY(), waterId, lavaId);

        WorldgenRandom worldgenRandom = new WorldgenRandom();

        for (int originX = chunkX - 8; originX <= chunkX + 8; originX++) {
            for (int originZ = chunkZ - 8; originZ <= chunkZ + 8; originZ++) {
                org.domi.furnace.server.world.gen.carver.WorldCarver.ChunkPos originPos =
                    new org.domi.furnace.server.world.gen.carver.WorldCarver.ChunkPos(originX, originZ);
                for (OverworldCarvers.Entry entry : overworldCarvers.entries()) {
                    worldgenRandom.setLargeFeatureSeed(this.seed, originX, originZ);
                    if (entry.carver().isStartChunk(entry.config(), worldgenRandom)) {
                        entry.carver().carve(col, entry.config(), chunkX, chunkZ,
                            settings, aquifer, reg, mask, worldgenRandom, originPos);
                    }
                }
            }
        }
    }

    public ChunkColumn generateWithCarvers(int chunkX, int chunkZ) {
        ChunkColumn col = generate(chunkX, chunkZ);
        NoiseChunk nc = new NoiseChunk(chunkX, chunkZ, router, settings);
        applyCarvers(col, chunkX, chunkZ, nc);
        return col;
    }
}
