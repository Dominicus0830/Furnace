package org.domi.furnace.server.world.gen;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
import org.domi.furnace.server.world.gen.feature.TreeFeature;
import org.junit.jupiter.api.Test;

class TreeFeatureParityDiagnosticTest {

    private static final int FEATUREFULL_MAGIC = 0x46464C42;
    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;
    private static final int CANOPY_MIN_X = 0;
    private static final int CANOPY_MAX_X = 4;
    private static final int CANOPY_MIN_Z = 0;
    private static final int CANOPY_MAX_Z = 3;
    private static final int CANOPY_MIN_Y = 82;
    private static final int CANOPY_MAX_Y = 83;

    @Test
    void diagnoseSeed1234SavannaTreeChunk() throws Exception {
        diagnoseChunk(1234L, 9, -64, "/worldgen/fixtures/featurefull_1234_9_-64.bin");
    }

    @Test
    void diagnoseSeed1234PureForestChunk() throws Exception {
        diagnoseChunk(1234L, -49, -30, "/worldgen/fixtures/featurefull_1234_-49_-30.bin");
    }

    @Test
    void diagnoseSeed1234BirchTallChunk() throws Exception {
        diagnoseChunk(1234L, -53, -26, "/worldgen/fixtures/featurefull_1234_-53_-26.bin");
    }

    private void diagnoseChunk(long seed, int cx, int cz, String fixturePath) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(seed, reg, registries);
        ChunkColumn col = source.generateWithFeatures(cx, cz);
        byte[] fx = loadFixture(fixturePath, seed, cx, cz);
        Map<Integer, String> oracleIdToName = loadIdToName("/blocks/1.21.1.txt");
        BitSet treeMask = collectFurnaceTreeMask(source, seed, cx, cz);
        int mismatch = countMismatches(col, fx, oracleIdToName, reg, seed, cx, cz);
        int treeMismatch = countTreeMismatches(col, fx, oracleIdToName, reg, treeMask, seed, cx, cz);
        dumpBirchTallCanopy(col, fx, oracleIdToName, reg, seed, cx, cz);
        System.out.printf("TREE_PARITY seed=%d cx=%d cz=%d mismatches=%d%n", seed, cx, cz, mismatch);
        System.out.printf("TREE_ONLY_PARITY seed=%d cx=%d cz=%d mismatches=%d%n", seed, cx, cz, treeMismatch);
    }

    private static byte[] loadFixture(String fixturePath, long seed, int cx, int cz) throws Exception {
        byte[] fx;
        try (InputStream in = TreeFeatureParityDiagnosticTest.class.getResourceAsStream(fixturePath)) {
            fx = in.readAllBytes();
        }
        ByteBuffer header = ByteBuffer.wrap(fx, 0, 20).order(ByteOrder.LITTLE_ENDIAN);
        if (header.getInt() != FEATUREFULL_MAGIC) throw new IllegalStateException("magic mismatch: " + fixturePath);
        if (header.getLong() != seed) throw new IllegalStateException("seed mismatch: " + fixturePath);
        if (header.getInt() != cx) throw new IllegalStateException("cx mismatch: " + fixturePath);
        if (header.getInt() != cz) throw new IllegalStateException("cz mismatch: " + fixturePath);
        return fx;
    }

    private static Map<Integer, String> loadIdToName(String resourcePath) throws Exception {
        Map<Integer, String> map = new HashMap<>();
        try (InputStream in = TreeFeatureParityDiagnosticTest.class.getResourceAsStream(resourcePath);
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

    private static int countMismatches(
        ChunkColumn col,
        byte[] fx,
        Map<Integer, String> oracleIdToName,
        BlockRegistry reg,
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
                        if (mismatch <= 20) {
                            System.out.printf(
                                "TREE_MM seed=%d cx=%d cz=%d lx=%d y=%d lz=%d oracle=%d(%s) exp=%d got=%d%n",
                                seed, cx, cz, lx, y, lz, oracleId, name, expectedId, got);
                        }
                    }
                }
            }
        }
        return mismatch;
    }

    private static int countTreeMismatches(
        ChunkColumn col,
        byte[] fx,
        Map<Integer, String> oracleIdToName,
        BlockRegistry reg,
        BitSet treeMask,
        long seed,
        int cx,
        int cz
    ) {
        int mismatch = 0;
        int pos = 20;
        int index = 0;
        for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int oracleId = ByteBuffer.wrap(fx, pos, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                    pos += 2;
                    int got = col.getBlock(lx, y, lz);
                    String name = oracleIdToName.get(oracleId);
                    int expectedId = (name == null) ? oracleId : tryId(reg, name, oracleId);
                    String gotName = reg.nameOf(got);
                    if (expectedId == got) {
                        index++;
                        continue;
                    }
                    if (!isTreeRelevant(treeMask.get(index), name, gotName, oracleId, got)) {
                        index++;
                        continue;
                    }
                    mismatch++;
                    if (mismatch <= 20) {
                        System.out.printf(
                            "TREE_ONLY_MM seed=%d cx=%d cz=%d lx=%d y=%d lz=%d oracle=%d(%s) exp=%d got=%d%n",
                            seed, cx, cz, lx, y, lz, oracleId, name, expectedId, got);
                    }
                    index++;
                }
            }
        }
        return mismatch;
    }

    private static boolean isTreeRelevant(boolean furnaceTreeChanged, String oracleName, String gotName, int oracleId, int got) {
        if (furnaceTreeChanged) return true;
        if (TreeFeature.isLeaves(oracleId) || TreeFeature.isLeaves(got)) return true;
        return isExplicitTreeName(oracleName) || isExplicitTreeName(gotName);
    }

    private static boolean isExplicitTreeName(String name) {
        if (name == null) return false;
        return name.endsWith("_leaves")
            || name.endsWith("_log")
            || name.endsWith("_wood")
            || name.endsWith("_sapling")
            || name.equals("minecraft:vine")
            || name.equals("minecraft:cocoa")
            || name.equals("minecraft:bee_nest");
    }

    private static BitSet collectFurnaceTreeMask(
        NoiseWorldSource source,
        long seed,
        int cx,
        int cz
    ) throws Exception {
        ChunkColumn[] grid = new ChunkColumn[9];
        int[][] biomeIdsGrid = new int[9][];
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int gx = cx + dx;
                int gz = cz + dz;
                int gridIndex = (dz + 1) * 3 + (dx + 1);
                ChunkColumn column = source.generate(gx, gz);
                source.applyCarvers(column, gx, gz);
                source.applyStructureStarts(column, gx, gz);
                grid[gridIndex] = column;
                biomeIdsGrid[gridIndex] = source.sampleBiomes(gx, gz);
            }
        }

        FeatureWorldView view = new FeatureWorldView(grid, biomeIdsGrid, cx, cz, true, seed);
        FeaturePlacer placer = source.featurePlacer();
        if (placer == null) {
            System.out.printf(
                "TREE_FEATURE_COUNTS seed=%d cx=%d cz=%d featurePlacer=null writes=0 changed=0%n",
                seed, cx, cz);
            return new BitSet(HEIGHT * 16 * 16);
        }
        BiomeFeatureData biomeData = placer.biomeData();
        FeatureSorter sorter = placer.sorter();
        Set<String> neighborBiomes = source.collectNeighborBiomes(cx, cz);
        long worldSeed = source.seed();

        WorldgenRandom random = new XoroshiroWorldgenRandom();
        long decorationSeed = random.setDecorationSeed(worldSeed, cx * 16, cz * 16);
        WorldgenRandom featureRandom = new XoroshiroWorldgenRandom();
        BitSet treeMask = new BitSet(HEIGHT * 16 * 16);

        for (int step = 0; step < BiomeFeatureData.STEP_COUNT; step++) {
            FeatureSorter.StepFeatureData stepData = sorter.featuresPerStep().get(step);
            Set<Integer> indices = new HashSet<>();
            for (String biome : neighborBiomes) {
                for (PlacedFeature feature : biomeData.featuresForStep(biome, step)) {
                    indices.add(stepData.indexOf(feature));
                }
            }
            for (int idx : indices) {
                if (idx < 0) continue;
                PlacedFeature pf = stepData.features().get(idx);
                if (!(pf.feature() instanceof TreeFeature)) continue;
                featureRandom.setFeatureSeed(decorationSeed, idx, step);
                int[] before = snapshot(grid[4]);
                view.resetDebugSetBlockCalls();
                boolean placed = pf.placeWithBiomeCheck(view, featureRandom, cx * 16, cz * 16, biomeData.validBiomeIds(pf));
                int[] after = snapshot(grid[4]);
                int changed = markChangedBlocks(before, after, treeMask);
                String featureId = biomeData.idOf(pf);
                System.out.printf(
                    "TREE_FEATURE_COUNTS seed=%d cx=%d cz=%d step=%d idx=%d id=%s placed=%s writes=%d changed=%d%n",
                    seed, cx, cz, step, idx, featureId, placed, view.debugSetBlockCalls(), changed);
            }
        }
        return treeMask;
    }

    private static int[] snapshot(ChunkColumn col) {
        int[] snapshot = new int[HEIGHT * 16 * 16];
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

    private static int markChangedBlocks(int[] before, int[] after, BitSet mask) {
        int changed = 0;
        for (int i = 0; i < before.length; i++) {
            if (before[i] == after[i]) continue;
            changed++;
            mask.set(i);
        }
        return changed;
    }

    private static int tryId(BlockRegistry reg, String name, int fallback) {
        try { return reg.defaultStateId(name); }
        catch (Exception e) { return fallback; }
    }

    private static void dumpBirchTallCanopy(
        ChunkColumn col,
        byte[] fx,
        Map<Integer, String> oracleIdToName,
        BlockRegistry reg,
        long seed,
        int cx,
        int cz
    ) {
        if (seed != 1234L || cx != -53 || cz != -26) return;
        System.out.printf("TREE_CANOPY_WINDOW seed=%d cx=%d cz=%d x=%d..%d z=%d..%d y=%d..%d%n",
            seed, cx, cz, CANOPY_MIN_X, CANOPY_MAX_X, CANOPY_MIN_Z, CANOPY_MAX_Z, CANOPY_MIN_Y, CANOPY_MAX_Y);

        for (int y = CANOPY_MIN_Y; y <= CANOPY_MAX_Y; y++) {
            for (int lz = CANOPY_MIN_Z; lz <= CANOPY_MAX_Z; lz++) {
                for (int lx = CANOPY_MIN_X; lx <= CANOPY_MAX_X; lx++) {
                    int oracleId = oracleStateAt(fx, lx, y, lz);
                    String oracleName = oracleIdToName.get(oracleId);
                    int expectedId = (oracleName == null) ? oracleId : tryId(reg, oracleName, oracleId);
                    int got = col.getBlock(lx, y, lz);
                    if (!TreeFeature.isLeaves(expectedId) && !TreeFeature.isLeaves(got)) continue;

                    int oracleNearest = nearestLogDistanceOracle(fx, oracleIdToName, reg, lx, y, lz);
                    int furnaceNearest = nearestLogDistanceFurnace(col, reg, lx, y, lz);
                    System.out.printf(
                        "TREE_CANOPY_CELL y=%d lz=%d lx=%d oracle=%d got=%d oracleName=%s gotName=%s oracleLogManhattan=%d furnaceLogManhattan=%d mismatch=%s%n",
                        y,
                        lz,
                        lx,
                        expectedId,
                        got,
                        oracleName,
                        reg.nameOf(got),
                        oracleNearest,
                        furnaceNearest,
                        expectedId == got ? "no" : "yes"
                    );
                }
            }
        }
    }

    private static int oracleStateAt(byte[] fx, int lx, int y, int lz) {
        int index = ((y - MIN_Y) * 16 + lz) * 16 + lx;
        int pos = 20 + index * 2;
        return ByteBuffer.wrap(fx, pos, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
    }

    private static int nearestLogDistanceOracle(byte[] fx, Map<Integer, String> oracleIdToName, BlockRegistry reg, int lx, int y, int lz) {
        int best = Integer.MAX_VALUE;
        for (int scanY = MIN_Y; scanY < MIN_Y + HEIGHT; scanY++) {
            for (int scanZ = 0; scanZ < 16; scanZ++) {
                for (int scanX = 0; scanX < 16; scanX++) {
                    int oracleId = oracleStateAt(fx, scanX, scanY, scanZ);
                    String oracleName = oracleIdToName.get(oracleId);
                    int expectedId = (oracleName == null) ? oracleId : tryId(reg, oracleName, oracleId);
                    if (!isLogId(expectedId, oracleName)) continue;
                    int distance = Math.abs(scanX - lx) + Math.abs(scanY - y) + Math.abs(scanZ - lz);
                    best = Math.min(best, distance);
                }
            }
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    private static int nearestLogDistanceFurnace(ChunkColumn col, BlockRegistry reg, int lx, int y, int lz) {
        int best = Integer.MAX_VALUE;
        for (int scanY = MIN_Y; scanY < MIN_Y + HEIGHT; scanY++) {
            for (int scanZ = 0; scanZ < 16; scanZ++) {
                for (int scanX = 0; scanX < 16; scanX++) {
                    int stateId = col.getBlock(scanX, scanY, scanZ);
                    if (!isLogId(stateId, reg.nameOf(stateId))) continue;
                    int distance = Math.abs(scanX - lx) + Math.abs(scanY - y) + Math.abs(scanZ - lz);
                    best = Math.min(best, distance);
                }
            }
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    private static boolean isLogId(int stateId, String name) {
        if (name != null && (name.endsWith("_log") || name.endsWith("_wood"))) return true;
        return stateId >= 130 && stateId <= 150 && stateId != 145 && stateId != 146 && stateId != 147;
    }

}
