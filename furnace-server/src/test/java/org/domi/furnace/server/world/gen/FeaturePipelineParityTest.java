// feature pipeline parity 테스트 — Furnace generateWithFeatures vs oracle featurefull fixture 비트일치
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.carver.XoroshiroWorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BiomeFeatureData;
import org.domi.furnace.server.world.gen.feature.FeaturePlacer;
import org.domi.furnace.server.world.gen.feature.FeatureSorter;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.PlacedFeature;
import org.junit.jupiter.api.Test;

class FeaturePipelineParityTest {

    private static final int MIN_Y             = -64;
    private static final int HEIGHT            = 384;
    private static final int FEATUREFULL_MAGIC = 0x46464C42;

    private static Map<Integer, String> loadIdToName(String resourcePath) throws Exception {
        Map<Integer, String> map = new HashMap<>();
        try (InputStream in = CarverParityTest.class.getResourceAsStream(resourcePath);
             BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] parts = line.split(" ");
                if (parts.length == 2) map.put(Integer.parseInt(parts[1]), parts[0]);
            }
        }
        return map;
    }

    private void assertFeatureParity(long seed, int cx, int cz) throws Exception {
        BlockRegistry reg11 = BlockRegistry.load("blocks/1.21.1.txt");
        Map<Integer, String> oracleIdToName = loadIdToName("/blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");

        NoiseWorldSource source = NoiseWorldSource.overworld(seed, reg11, registries);
        ChunkColumn col = source.generateWithFeatures(cx, cz);

        String fixtureName = "/worldgen/fixtures/featurefull_" + seed + "_" + cx + "_" + cz + ".bin";
        byte[] fx = loadFixture(fixtureName, seed, cx, cz);

        int mismatch = countMismatches(col, fx, oracleIdToName, reg11, true, seed, cx, cz);
        if (mismatch != 0) {
            diagnoseFeatureDivergence(seed, cx, cz, reg11, oracleIdToName, registries, fx);
        }

        System.out.printf("featurefull seed=%d cx=%d cz=%d: %d mismatches%n", seed, cx, cz, mismatch);
        assertEquals(0, mismatch, "seed=" + seed + " cx=" + cx + " cz=" + cz
            + " mismatches=" + mismatch);
    }

    private static byte[] loadFixture(String fixtureName, long seed, int cx, int cz) throws Exception {
        byte[] fx;
        try (InputStream in = FeaturePipelineParityTest.class.getResourceAsStream(fixtureName)) {
            assertNotNull(in, "fixture not found: " + fixtureName);
            fx = in.readAllBytes();
        }

        ByteBuffer header = ByteBuffer.wrap(fx, 0, 20).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(FEATUREFULL_MAGIC, header.getInt(), "magic mismatch");
        assertEquals(seed, header.getLong(), "seed mismatch");
        assertEquals(cx, header.getInt(), "cx mismatch");
        assertEquals(cz, header.getInt(), "cz mismatch");
        return fx;
    }

    private static int countMismatches(
        ChunkColumn col,
        byte[] fx,
        Map<Integer, String> oracleIdToName,
        BlockRegistry reg,
        boolean logFirstMismatches,
        long seed,
        int cx,
        int cz
    ) {
        int mismatch = 0;
        int pos = 20;
        for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int oracleId = ByteBuffer.wrap(fx, pos, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                    pos += 2;
                    int got = col.getBlock(lx, y, lz);

                    String name = oracleIdToName.get(oracleId);
                    int expectedId = (name == null) ? oracleId : tryId(reg, name, oracleId);
                    if (expectedId != got) {
                        mismatch++;
                        if (logFirstMismatches && mismatch <= 20) {
                            System.out.printf(
                                "MM seed=%d cx=%d cz=%d lx=%d y=%d lz=%d oracle=%d(%s) exp=%d got=%d%n",
                                seed, cx, cz, lx, y, lz, oracleId, name, expectedId, got);
                        }
                    }
                }
            }
        }
        return mismatch;
    }

    private static void diagnoseFeatureDivergence(
        long seed,
        int cx,
        int cz,
        BlockRegistry reg,
        Map<Integer, String> oracleIdToName,
        RegistrySet registries,
        byte[] fx
    ) throws Exception {
        NoiseWorldSource source = NoiseWorldSource.overworld(seed, reg, registries);
        ChunkColumn[] grid = new ChunkColumn[9];
        int[][] biomeIdsGrid = new int[9][];
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int gx = cx + dx;
                int gz = cz + dz;
                int gridIndex = (dz + 1) * 3 + (dx + 1);
                ChunkColumn column = source.generate(gx, gz);
                source.applyCarvers(column, gx, gz);
                source.applyStructureStarts(gx, gz);
                grid[gridIndex] = column;
                biomeIdsGrid[gridIndex] = source.sampleBiomes(gx, gz);
            }
        }

        FeatureWorldView view = new FeatureWorldView(grid, biomeIdsGrid, cx, cz, false, seed);
        Set<String> neighborBiomes = source.collectNeighborBiomes(cx, cz);
        FeaturePlacer placer = reflect(source, "featurePlacer", FeaturePlacer.class);
        if (placer == null) {
            System.out.println("Feature diagnostics skipped: featurePlacer unavailable");
            return;
        }

        BiomeFeatureData biomeData = reflect(placer, "biomeData", BiomeFeatureData.class);
        FeatureSorter sorter = reflect(placer, "sorter", FeatureSorter.class);
        long worldSeed = reflectLong(source, "seed");

        WorldgenRandom random = new XoroshiroWorldgenRandom();
        long decorationSeed = random.setDecorationSeed(worldSeed, cx * 16, cz * 16);
        WorldgenRandom featureRandom = new XoroshiroWorldgenRandom();

        int mismatchBefore = countMismatches(grid[4], fx, oracleIdToName, reg, false, seed, cx, cz);
        System.out.printf("FURNACE diagnose cx=%d cz=%d decorationSeed=%d biomes=%s mismatchStart=%d%n",
            cx, cz, decorationSeed, new TreeSet<>(neighborBiomes), mismatchBefore);

        for (int step = 0; step < BiomeFeatureData.STEP_COUNT; step++) {
            FeatureSorter.StepFeatureData stepData = sorter.featuresPerStep().get(step);
            TreeSet<Integer> indices = new TreeSet<>();
            for (String biome : neighborBiomes) {
                for (PlacedFeature feature : biomeData.featuresForStep(biome, step)) {
                    int idx = stepData.indexOf(feature);
                    if (idx < 0) {
                        throw new IllegalStateException(
                            "PlacedFeature not interned for biome " + biome + " step " + step);
                    }
                    indices.add(idx);
                }
            }
            if (indices.isEmpty()) continue;

            System.out.printf("FURNACE step=%d indices=%s%n", step, indices);
            for (int idx : indices) {
                PlacedFeature pf = stepData.features().get(idx);
                featureRandom.setFeatureSeed(decorationSeed, idx, step);

                int[] before = snapshot(grid[4]);
                String featureId = biomeData.idOf(pf);
                long featureSeed = decorationSeed + idx + 10000L * step;
                boolean placed = pf.placeWithBiomeCheck(view, featureRandom, cx * 16, cz * 16,
                    biomeData.validBiomeIds(pf));
                int[] after = snapshot(grid[4]);
                int mismatchAfter = countMismatches(grid[4], fx, oracleIdToName, reg, false, seed, cx, cz);
                int changed = countChangedBlocks(before, after);
                int debugIdx = Integer.getInteger("furnace.featureBlockDebugIdx", -1);

                if (placed || changed > 0 || mismatchAfter != mismatchBefore) {
                    System.out.printf(
                        "  step=%d idx=%d id=%s featureSeed=%d placed=%s changed=%d mismatch %d->%d firstChange=%s%n",
                        step, idx, featureId, featureSeed, placed, changed, mismatchBefore, mismatchAfter,
                        firstChange(before, after, reg));
                }
                if (mismatchAfter > mismatchBefore) {
                    System.out.printf(
                        "  DIVERGE at step=%d idx=%d id=%s: mismatch %d->%d%n",
                        step, idx, featureId, mismatchBefore, mismatchAfter);
                }
                if (idx == debugIdx) {
                    logChangedBlocks(before, after, reg, step, idx, featureId, cx, cz);
                }
                mismatchBefore = mismatchAfter;
            }
        }
    }

    private static int[] snapshot(ChunkColumn col) {
        int[] snapshot = new int[16 * 16 * HEIGHT];
        int index = 0;
        for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    snapshot[index++] = col.getBlock(lx, y, lz);
                }
            }
        }
        return snapshot;
    }

    private static int countChangedBlocks(int[] before, int[] after) {
        int changed = 0;
        for (int i = 0; i < before.length; i++) {
            if (before[i] != after[i]) changed++;
        }
        return changed;
    }

    private static String firstChange(int[] before, int[] after, BlockRegistry reg) {
        for (int i = 0; i < before.length; i++) {
            if (before[i] == after[i]) continue;
            int yIndex = i / 256;
            int yz = i % 256;
            int lz = yz / 16;
            int lx = yz % 16;
            int y = MIN_Y + yIndex;
            return String.format("lx=%d y=%d lz=%d %s(%d)->%s(%d)",
                lx, y, lz, reg.nameOf(before[i]), before[i], reg.nameOf(after[i]), after[i]);
        }
        return "-";
    }

    private static void logChangedBlocks(int[] before, int[] after, BlockRegistry reg, int step, int idx, String featureId, int cx, int cz) {
        for (int i = 0; i < before.length; i++) {
            if (before[i] == after[i]) continue;
            int yIndex = i / 256;
            int yz = i % 256;
            int lz = yz / 16;
            int lx = yz % 16;
            int y = MIN_Y + yIndex;
            System.out.printf(
                "BLOCK_DELTA source=FURNACE step=%d idx=%d id=%s wx=%d y=%d wz=%d old=%s(%d) new=%s(%d)%n",
                step, idx, featureId, cx * 16 + lx, y, cz * 16 + lz,
                reg.nameOf(before[i]), before[i], reg.nameOf(after[i]), after[i]);
        }
    }

    private static long reflectLong(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getLong(target);
    }

    private static <T> T reflect(Object target, String fieldName, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static int tryId(BlockRegistry reg, String name, int fallback) {
        try { return reg.defaultStateId(name); }
        catch (Exception e) { return fallback; }
    }

    @Test void feature_seed1234_coord1() throws Exception { assertFeatureParity(1234L, 0, 0); }
    @Test void feature_seed1234_coord2() throws Exception { assertFeatureParity(1234L, -5, 3); }
    @Test void feature_seed1234_coord3() throws Exception { assertFeatureParity(1234L, 7, -2); }

    /** Furnace step 6 feature 순서가 oracle feature_order.txt와 일치하는지 검증. */
    @Test void verifyStep6FeatureOrder() throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(1234L, reg, registries);
        var fp = reflect(source, "featurePlacer", org.domi.furnace.server.world.gen.feature.FeaturePlacer.class);
        var step6 = fp.sorter().featuresPerStep().get(6);

        // oracle feature_order.txt에서 STEP 6 목록 로드
        java.util.List<String> oracleStep6 = new java.util.ArrayList<>();
        try (InputStream in = FeaturePipelineParityTest.class.getResourceAsStream("/worldgen/fixtures/feature_order.txt");
             java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(in))) {
            boolean inStep6 = false;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("STEP 6")) { inStep6 = true; continue; }
                if (inStep6 && line.startsWith("STEP ")) break;
                if (inStep6) oracleStep6.add(line.trim());
            }
        }

        // Furnace step 6 목록
        java.util.List<String> furnaceStep6 = new java.util.ArrayList<>();
        for (int i = 0; i < step6.features().size(); i++) {
            furnaceStep6.add(fp.biomeData().idOf(step6.features().get(i)));
        }

        System.out.println("Oracle step 6 (" + oracleStep6.size() + "): " + oracleStep6);
        System.out.println("Furnace step 6 (" + furnaceStep6.size() + "): " + furnaceStep6);

        // ore_coal_lower idx 출력
        int coalIdx = furnaceStep6.indexOf("minecraft:ore_coal_lower");
        System.out.println("ore_coal_lower idx in Furnace step6: " + coalIdx);

        assertEquals(oracleStep6, furnaceStep6, "Step 6 feature order mismatch");
    }

    @Test void dumpStep9Features() throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(1234L, reg, registries);
        var fp = reflect(source, "featurePlacer", org.domi.furnace.server.world.gen.feature.FeaturePlacer.class);
        var step9 = fp.sorter().featuresPerStep().get(9);
        System.out.println("Step 9 total features: " + step9.features().size());
        for (int i = 0; i < step9.features().size(); i++) {
            System.out.printf("idx=%d id=%s%n", i, fp.biomeData().idOf(step9.features().get(i)));
        }
    }

    /** seagrass_deep PRNG scatter trace (CORRECTED) — featureSeed=91321 for seed=1234 cx=0 cz=0.
     *  실제 실행: 48×(in_square(nextInt(16)*2) + scatter(nextInt(8)*4) + isTall(nextDouble)) */
    @Test void seagrassDeepScatterTrace() {
        long featureSeed = 91321L; // decorationSeed(1234) + idx(87) + 10000*step(9)
        XoroshiroWorldgenRandom rng = new XoroshiroWorldgenRandom();
        rng.setSeed(featureSeed);

        // in_square는 count의 각 48 iter에서 한 번씩 호출됨 (not once before all iters)
        System.out.println("iter | insqX insqZ | dx1 dx2 dz1 dz2 | placeX placeZ | isTall | inCenter");
        int inCenterCount = 0;
        for (int i = 0; i < 48; i++) {
            int insqX = rng.nextInt(16); // in_square x-offset within chunk
            int insqZ = rng.nextInt(16); // in_square z-offset within chunk
            int originX = 0 + insqX;    // blockX=cx*16=0 for cx=0
            int originZ = 0 + insqZ;    // blockZ=cz*16=0 for cz=0
            int dx1 = rng.nextInt(8), dx2 = rng.nextInt(8);
            int dz1 = rng.nextInt(8), dz2 = rng.nextInt(8);
            int placeX = originX + dx1 - dx2;
            int placeZ = originZ + dz1 - dz2;
            double d = rng.nextDouble(); // consumed even if not water in oracle model
            boolean isTall = d < 0.8;
            boolean inCenter = placeX >= 0 && placeX < 16 && placeZ >= 0 && placeZ < 16;
            if (inCenter) inCenterCount++;
            System.out.printf("%3d  | %2d %2d | %d %d %d %d | %3d %3d | %s | %s%n",
                i, insqX, insqZ, dx1, dx2, dz1, dz2, placeX, placeZ, isTall ? "tall" : "short", inCenter ? "IN" : "-");
        }
        System.out.printf("Total in-center (water assumed): %d%n", inCenterCount);
    }

    /** ore_coal_lower 첫 blob RNG trace — featureSeed 및 RNG 소비 순서 확인. */
    @Test void traceCoalOreRng() throws Exception {
        // decorationSeed for cx=7,cz=-2 seed=1234 — computed by correct Xoroshiro RNG
        long decorationSeed = 1031541745140485346L;
        int idx = 10, step = 6;
        long featureSeed = decorationSeed + idx + 10000L * step;
        System.out.printf("featureSeed=%d%n", featureSeed);

        XoroshiroWorldgenRandom rng = new XoroshiroWorldgenRandom();
        rng.setSeed(featureSeed);

        // Build actual world state (cx=7, cz=-2, seed=1234) after steps 0..9 idx<10 to get accurate guard.
        // For now, just print blob y values (guard-check must be done with real world).
        // count(20) → no random consumed.
        // Each blob: in_square(2 nextInt(16)) + trapezoid(2 nextInt(97)) + OreFeature random.
        // OreFeature.place(): nextFloat(angle) + nextInt(3)(startY) + nextInt(3)(endY) always consumed.
        // doPlace: 17*nextDouble IF guard passes. tryPlace nextFloat per pos (unknown without world state).
        // For guard approximation: ocean floor for cx=7,cz=-2 is ~63, so minY = y-4.
        //   if y-4 <= 63 (y<=67): guard likely passes. else: likely fails (no doPlace).
        // NOTE: This is APPROXIMATE — actual wgFloor varies per (bx,bz).
        int cx = 7, cz = -2;
        int blockX = cx * 16, blockZ = cz * 16;
        System.out.println("Furnace RNG blob trace (approximate guard based on y<=67):");
        for (int blob = 0; blob < 20; blob++) {
            int sqX = rng.nextInt(16);
            int sqZ = rng.nextInt(16);
            int bx = blockX + sqX;
            int bz = blockZ + sqZ;
            int t1 = rng.nextInt(97);
            int t2 = rng.nextInt(97);
            int y = t1 + t2; // trapezoid(0,192,0)
            int minY = y - 4; // size=17: padding=2, minY = y - 2 - padding = y - 4
            boolean guardLikelyPasses = minY <= 63;
            System.out.printf("  blob=%2d sqX=%2d sqZ=%2d t1=%2d t2=%2d y=%3d minY=%3d %s (bx=%d bz=%d)%n",
                blob, sqX, sqZ, t1, t2, y, minY, guardLikelyPasses ? "PLACE" : "SKIP", bx, bz);
            // Always consume: nextFloat(angle) + nextInt(3)(startY) + nextInt(3)(endY)
            rng.nextFloat();
            rng.nextInt(3);
            rng.nextInt(3);
            // If guard passes: also 17*nextDouble consumed by doPlace
            if (guardLikelyPasses) {
                for (int s = 0; s < 17; s++) rng.nextDouble();
                // tryPlace random (nextFloat per pos): unknown without world state — skip
            }
        }

        // Oracle fixture: scan for ALL coal positions in cx=7,cz=-2
        String fixtureName = "/worldgen/fixtures/featurefull_1234_7_-2.bin";
        byte[] fx;
        try (InputStream in = FeaturePipelineParityTest.class.getResourceAsStream(fixtureName)) {
            assertNotNull(in, "fixture not found: " + fixtureName);
            fx = in.readAllBytes();
        }
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        int coalId = reg.defaultStateId("minecraft:coal_ore");
        int deepslateCoalId = reg.defaultStateId("minecraft:deepslate_coal_ore");
        System.out.printf("coal_ore=%d deepslate_coal_ore=%d%n", coalId, deepslateCoalId);
        System.out.println("Oracle coal positions (all y):");
        int pos = 20;
        int count = 0;
        for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int stateId = ByteBuffer.wrap(fx, pos, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                    pos += 2;
                    if (stateId == coalId || stateId == deepslateCoalId) {
                        System.out.printf("  lx=%d y=%d lz=%d stateId=%d%n", lx, y, lz, stateId);
                        count++;
                    }
                }
            }
        }
        System.out.printf("Total oracle coal: %d%n", count);
    }

    /** oracle binary에서 seagrass 위치 스캔 (seed=1234, cx=0, cz=0). */
    @Test void oracleSeagrassPositions() throws Exception {
        String fixtureName = "/worldgen/fixtures/featurefull_1234_0_0.bin";
        byte[] fx;
        try (InputStream in = FeaturePipelineParityTest.class.getResourceAsStream(fixtureName)) {
            assertNotNull(in, "fixture not found: " + fixtureName);
            fx = in.readAllBytes();
        }
        System.out.println("Oracle seagrass positions (seed=1234 cx=0 cz=0):");
        int pos = 20;
        for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int stateId = ByteBuffer.wrap(fx, pos, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                    pos += 2;
                    // seagrass=2008, tall_seagrass=2009,2010,2011,2012,2013
                    if (stateId >= 2008 && stateId <= 2013) {
                        System.out.printf("  lx=%d y=%d lz=%d stateId=%d%n", lx, y, lz, stateId);
                    }
                }
            }
        }
    }
}
