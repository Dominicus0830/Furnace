// 청크 생성 단계별 시간 측정 — 어느 단계가 병목인지 파악
package org.domi.furnace.server.world.gen;

import org.junit.jupiter.api.Test;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.gen.surface.SurfaceSystem;

class ChunkGenBenchmark {

    @Test
    void benchmarkChunkGenPhases() throws Exception {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        NoiseSettings settings = NoiseSettings.fromJson(data.noiseSettings("minecraft:overworld"));

        // warm up JIT
        for (int i = 0; i < 3; i++) {
            NoiseRouter r = NoiseRouter.build(data, settings, 0L);
            NoiseChunk nc = new NoiseChunk(i, i, r, settings);
            nc.stoneMask();
        }

        // BiomeSampler + SearchTree 빌드는 서버 시작 시 1회 — per-chunk 측정에서 제외
        org.domi.furnace.server.net.registry.RegistrySet reg =
            org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt");
        BiomeSampler sampler = BiomeSampler.load(data, reg);

        int SAMPLES = 8;
        long[] constructMs = new long[SAMPLES];
        long[] stoneMaskMs = new long[SAMPLES];
        long[] climateMs   = new long[SAMPLES];
        long sliceFillNanosTotal = 0;
        long skippedCellsTotal = 0;

        for (int i = 0; i < SAMPLES; i++) {
            NoiseRouter router = NoiseRouter.build(data, settings, 0L);

            long t0 = System.nanoTime();
            NoiseChunk nc = new NoiseChunk(i * 3, i * 3, router, settings);
            long t1 = System.nanoTime();
            nc.stoneMask();
            long t2 = System.nanoTime();
            sampler.sampleChunk(i * 3, i * 3, nc);
            long t3 = System.nanoTime();

            constructMs[i] = (t1 - t0) / 1_000_000;
            stoneMaskMs[i] = (t2 - t1) / 1_000_000;
            climateMs[i]   = (t3 - t2) / 1_000_000;
            sliceFillNanosTotal += nc.sliceFillNanos;
            skippedCellsTotal += nc.skippedCells;
        }

        long sumC = 0, sumS = 0, sumB = 0;
        for (int i = 0; i < SAMPLES; i++) {
            sumC += constructMs[i];
            sumS += stoneMaskMs[i];
            sumB += climateMs[i];
        }
        System.out.printf("=== ChunkGen per-chunk averages (n=%d, post-warmup) ===%n", SAMPLES);
        System.out.printf("  NoiseChunk construct (mapAll+FlatCache fill+climateCache): %4d ms%n", sumC / SAMPLES);
        System.out.printf("  stoneMask  (terrain density interpolation):               %4d ms%n", sumS / SAMPLES);
        System.out.printf("    └ sliceFill (corner DF evals):                          %4d ms%n", sliceFillNanosTotal / SAMPLES / 1_000_000);
        System.out.printf("    └ block loop (lerp + tree walk):                        %4d ms%n", (sumS - sliceFillNanosTotal / 1_000_000) / SAMPLES);
        System.out.printf("    └ skipped cells: %d / 768 per chunk%n", skippedCellsTotal / SAMPLES);
        System.out.printf("  sampleChunk(biome search):                                %4d ms%n", sumB / SAMPLES);
        System.out.printf("  TOTAL per chunk:                                          %4d ms%n", (sumC + sumS + sumB) / SAMPLES);
        System.out.printf("  64-radius estimate (~16641 chunks, single-threaded):      %4d s%n",
            (sumC + sumS + sumB) / SAMPLES * 16641 / 1000);
    }

    @Test
    void benchmarkActualWorldSourceGenerate() throws Exception {
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(0L, blocks, registries);

        for (int i = 0; i < 4; i++) source.generate(i, -i);

        int samples = 24;
        long noCarver = 0;
        long withCarver = 0;
        for (int i = 0; i < samples; i++) {
            int cx = i * 3 - 20;
            int cz = 17 - i * 2;

            long t0 = System.nanoTime();
            source.generate(cx, cz);
            long t1 = System.nanoTime();
            source.generateWithCarvers(cx, cz);
            long t2 = System.nanoTime();

            noCarver += t1 - t0;
            withCarver += t2 - t1;
        }

        long noCarverMs = noCarver / samples / 1_000_000;
        long withCarverMs = withCarver / samples / 1_000_000;
        System.out.printf("=== NoiseWorldSource actual generate averages (n=%d) ===%n", samples);
        System.out.printf("  generate() server path, no carvers:                    %4d ms/chunk%n", noCarverMs);
        System.out.printf("  generateWithCarvers() parity/test path:                %4d ms/chunk%n", withCarverMs);
        System.out.printf("  initial view-distance 5 (121 chunks), no carvers:      %4d ms single-thread%n",
            noCarverMs * 121);
        System.out.printf("  initial view-distance 10 (441 chunks), no carvers:     %4d ms single-thread%n",
            noCarverMs * 441);
    }

    @Test
    void benchmarkActualGeneratePhases() throws Exception {
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        NoiseSettings settings = NoiseSettings.fromJson(data.noiseSettings("minecraft:overworld"));
        NoiseRouter router = NoiseRouter.build(data, settings, 0L);
        BiomeSampler biomeSampler = BiomeSampler.load(data, registries);
        String[] registryBiomeNames = registries.entries("minecraft:worldgen/biome").toArray(new String[0]);
        SurfaceSystem surfaceSystem = SurfaceSystem.create(data, settings, blocks, 0L,
            registryBiomeNames, biomeSampler, router);
        int stoneId = blocks.defaultStateId(settings.defaultBlock());
        int waterId = blocks.defaultStateId(settings.defaultFluid());
        int lavaId = blocks.defaultStateId("minecraft:lava");

        for (int i = 0; i < 4; i++) {
            NoiseChunk nc = new NoiseChunk(i, i, router, settings);
            Aquifer aquifer = new NoiseBasedAquifer(nc, i, i, router, settings,
                router.aquiferRandom(), stoneId, waterId, lavaId);
            nc.generateBlockIds(stoneId, aquifer);
        }

        int samples = 12;
        long construct = 0, blocksTime = 0, biome = 0, column = 0, surface = 0;
        for (int i = 0; i < samples; i++) {
            int chunkX = i * 3 - 12;
            int chunkZ = 9 - i * 2;

            long t0 = System.nanoTime();
            NoiseChunk nc = new NoiseChunk(chunkX, chunkZ, router, settings);
            long t1 = System.nanoTime();
            Aquifer aquifer = new NoiseBasedAquifer(nc, chunkX, chunkZ, router, settings,
                router.aquiferRandom(), stoneId, waterId, lavaId);
            int[] blockIds = nc.generateBlockIds(stoneId, aquifer);
            long t2 = System.nanoTime();
            int[] biomeIds = biomeSampler.sampleChunk(chunkX, chunkZ, nc);
            long t3 = System.nanoTime();

            ChunkColumn col = new ChunkColumn(0);
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
                    for (int y = settings.minY() + settings.height() - 1; y >= settings.minY(); y--) {
                        int idx = (y - settings.minY()) * 256 + lz * 16 + lx;
                        int state = blockIds[idx];
                        if (state != ChunkSection.AIR && surfaceHeight == 0) {
                            surfaceHeight = (y - settings.minY()) + 1;
                        }
                        col.setBlock(lx, y, lz, state);
                    }
                    col.setSurface(lx, lz, surfaceHeight);
                }
            }
            long t4 = System.nanoTime();
            surfaceSystem.buildSurface(col, biomeIds, nc, chunkX, chunkZ);
            long t5 = System.nanoTime();

            construct += t1 - t0;
            blocksTime += t2 - t1;
            biome += t3 - t2;
            column += t4 - t3;
            surface += t5 - t4;
        }

        System.out.printf("=== NoiseWorldSource generate phase averages (n=%d) ===%n", samples);
        System.out.printf("  NoiseChunk construct:                                  %4d ms%n", construct / samples / 1_000_000);
        System.out.printf("  generateBlockIds(final density + aquifer):             %4d ms%n", blocksTime / samples / 1_000_000);
        System.out.printf("  sampleChunk biome:                                     %4d ms%n", biome / samples / 1_000_000);
        System.out.printf("  ChunkColumn fill:                                      %4d ms%n", column / samples / 1_000_000);
        System.out.printf("  SurfaceSystem.buildSurface:                           %4d ms%n", surface / samples / 1_000_000);
        System.out.printf("  total mirrored generate:                               %4d ms%n",
            (construct + blocksTime + biome + column + surface) / samples / 1_000_000);
    }

    /**
     * 실서버 청크 서빙 경로(generateWithFeatures → ChunkPipeline.generateFull) 실측.
     * 인접 청크를 순차 요청하는 실제 사용 패턴에서 이웃 재생성 비용이 얼마인지 본다.
     */
    @Test
    void benchmarkPipelineNeighborRedundancy() throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(4242L, reg, registries);

        source.generateWithFeatures(500, 500); // warmup

        int n = 9;
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            source.generateWithFeatures(1000 + i, 1000);
        }
        long elapsed = System.nanoTime() - start;
        long perChunkMs = elapsed / n / 1_000_000;

        System.out.println("=== ChunkPipeline.generateFull (실서버 서빙 경로) ===");
        System.out.printf("  인접 %d청크 순차 요청: %d ms/chunk (wall, 병렬 포함)%n", n, perChunkMs);
        System.out.printf("  총 소요: %d ms%n", elapsed / 1_000_000);
        System.out.printf("  단일 generate 52ms 대비 배수: %.1fx%n", perChunkMs / 52.0);
        System.out.printf("  view-distance 10(441청크) 추정: %d s%n", perChunkMs * 441 / 1000);
    }

    /**
     * generateFull의 비용 분해 — 같은 청크를 두 번 요청한다.
     * 1회차: grid 25청크 cold(생성) + decoration 9 origin
     * 2회차: grid 전부 캐시 히트(복사만) + decoration 9 origin
     * 차이 = grid 생성 비용, 2회차 = decoration + 복사 비용.
     */
    @Test
    void benchmarkPipelinePhaseSplit() throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(4242L, reg, registries);
        source.generateWithFeatures(500, 500); // JIT warmup

        int cx = 2000, cz = 2000;
        long t0 = System.nanoTime();
        source.generateWithFeatures(cx, cz);
        long cold = (System.nanoTime() - t0) / 1_000_000;

        long t1 = System.nanoTime();
        source.generateWithFeatures(cx, cz);
        long warm = (System.nanoTime() - t1) / 1_000_000;

        System.out.println("=== generateFull 비용 분해 ===");
        System.out.printf("  1회차(grid cold + decoration): %4d ms%n", cold);
        System.out.printf("  2회차(grid 캐시 + decoration): %4d ms%n", warm);
        System.out.printf("  → grid 생성 비용:              %4d ms%n", cold - warm);
        System.out.printf("  → decoration + 복사 비용:      %4d ms  (%d%%)%n", warm, warm * 100 / Math.max(1, cold));
    }
}
