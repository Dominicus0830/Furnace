// 바닐라 비트일치 노이즈 지형 WorldSource. final_density>0=stone, else lava(y<-54)/water(y<63)/air
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
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

    private NoiseWorldSource(NoiseRouter router, NoiseSettings settings, BiomeSampler biomeSampler,
                              SurfaceSystem surfaceSystem, String[] registryBiomeNames,
                              int stoneId, int waterId, int lavaId) {
        this.router             = router;
        this.settings           = settings;
        this.biomeSampler       = biomeSampler;
        this.surfaceSystem      = surfaceSystem;
        this.registryBiomeNames = registryBiomeNames;
        this.stoneId            = stoneId;
        this.waterId            = waterId;
        this.lavaId             = lavaId;
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
        SurfaceSystem surfaceSystem = SurfaceSystem.create(data, settings, reg, seed);

        return new NoiseWorldSource(router, settings, biomeSampler, surfaceSystem,
                                    registryBiomeNames, stoneId, waterId, lavaId);
    }

    @Override
    public ChunkColumn generate(int chunkX, int chunkZ) {
        NoiseChunk nc = new NoiseChunk(chunkX, chunkZ, router, settings);
        boolean[] stone = nc.stoneMask();

        int minY      = settings.minY();       // -64
        int height    = settings.height();     // 384
        int seaLevel  = settings.seaLevel();   // 63
        int lavaFloor = settings.lavaFloorY(); // -54

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
                // 표면 높이 계산을 위해 위에서 아래로 한 번 스캔 (top-down, topmost non-air).
                int surfaceHeight = 0; // 0 = all-air sentinel
                for (int y = minY + height - 1; y >= minY; y--) {
                    int idx = (y - minY) * 256 + lz * 16 + lx;
                    int state;
                    if (stone[idx]) {
                        state = stoneId;
                    } else if (y < lavaFloor) {
                        state = lavaId;
                    } else if (y < seaLevel) {
                        state = waterId;
                    } else {
                        state = ChunkSection.AIR;
                    }

                    if (state != ChunkSection.AIR && surfaceHeight == 0) {
                        // y - minY + 1 mirrors SuperflatWorldSource convention
                        surfaceHeight = (y - minY) + 1;
                    }

                    col.setBlock(lx, y, lz, state);
                }
                col.setSurface(lx, lz, surfaceHeight);
            }
        }

        String[] colBiomeNames = buildBiomeNames(biomeIds, registryBiomeNames);
        surfaceSystem.buildSurface(col, biomeIds, colBiomeNames, nc, chunkX, chunkZ);

        return col;
    }

    private static String[] buildBiomeNames(int[] biomeIds, String[] registryBiomeNames) {
        String[] names = new String[256]; // 16×16
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int qx = lx >> 2;
                int qz = lz >> 2;
                // 표면 근처 쿼트 y(qy=72 ≈ y=288)에서 바이옴 id 조회
                int qy = 72;
                int biomeId = biomeIds[qy * 16 + qz * 4 + qx];
                names[lz * 16 + lx] = biomeId >= 0 && biomeId < registryBiomeNames.length
                    ? registryBiomeNames[biomeId] : "minecraft:plains";
            }
        }
        return names;
    }
}
