// 청크 생성 단계별 시간 측정 — 어느 단계가 병목인지 파악
package org.domi.furnace.server.world.gen;

import org.junit.jupiter.api.Test;

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
}
