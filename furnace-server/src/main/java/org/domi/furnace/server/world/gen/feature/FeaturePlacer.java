package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.domi.furnace.server.world.gen.noise.PositionalRandom;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.RandomTrace;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.carver.XoroshiroWorldgenRandom;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;
import org.domi.furnace.server.world.gen.structure.BoundingBox;
import org.domi.furnace.server.world.gen.structure.StructureRegistry;
import org.domi.furnace.server.world.gen.structure.StructureStart;

public final class FeaturePlacer {



    private final long seed;
    private final BiomeFeatureData biomeData;
    private final FeatureSorter sorter;
    private final BlockRegistry blocks;
    /** 바닐라 RandomState.getOrCreateRandomFactory("minecraft:worldgen_region_random") 미러 — WorldGenLevel.getRandom()의 원천. */
    private final PositionalRandom regionRandomFactory;

    private volatile org.domi.furnace.server.world.gen.structure.StructureEngine structureEngine;

    public void setStructureEngine(org.domi.furnace.server.world.gen.structure.StructureEngine e) { this.structureEngine = e; }

    public FeaturePlacer(long seed, BiomeFeatureData biomeData, FeatureSorter sorter, BlockRegistry blocks) {
        this(seed, biomeData, sorter, blocks, new XoroshiroRandomSource(seed).forkPositional());
    }

    /** @param randomState RandomState.random — WorldGenRegion.getRandom 은 그 위의 "worldgen_region_random" 팩토리(legacy 차원은 LCG). */
    public FeaturePlacer(long seed, BiomeFeatureData biomeData, FeatureSorter sorter, BlockRegistry blocks, PositionalRandom randomState) {
        this.seed = seed;
        this.biomeData = biomeData;
        this.sorter = sorter;
        this.blocks = blocks;
        this.regionRandomFactory = randomState.fromHashOf("minecraft:worldgen_region_random").forkPositional();
    }

    // 진단용 플래그 — 디버깅 완료 후 제거
    private static final boolean DEBUG = Boolean.getBoolean("furnace.featureDebug");
    private static final boolean BLOCK_DEBUG = Boolean.getBoolean("furnace.featureBlockDebug");

    public void decorate(FeatureWorldView view, Set<String> biomeSet, int cx, int cz) {
        decorate(view, biomeSet, cx, cz, List.of());
    }

    /**
     * starts: 이 청크(cx,cz)가 자신의 ProtoColumn에서 직접 생성한 StructureStart 목록(현재 desert_pyramid만).
     * vanilla ChunkGenerator.applyBiomeDecoration — 구조물 postProcess는 해당 step(F1: SURFACE_STRUCTURES)의
     * feature들보다 먼저, 같은 featureRandom 시퀀스(F3)로 실행된다. writableArea는 이 청크(F2, 16x16)만.
     */
    public void decorate(FeatureWorldView view, Set<String> biomeSet, int cx, int cz, List<StructureStart> starts) {
        WorldgenRandom random = new XoroshiroWorldgenRandom();
        long decorationSeed = random.setDecorationSeed(seed, cx * 16, cz * 16);
        WorldgenRandom featureRandom = new XoroshiroWorldgenRandom();
        // 바닐라 WorldGenRegion.getRandom() — 이 청크(cx,cz) 원점에 fork, 구조물+feature 전체가 순차 공유 소비.
        NoiseRandom regionRandom = regionRandomFactory.at(cx * 16, 0, cz * 16);
        BoundingBox writableBox = new BoundingBox(
            cx * 16, Integer.MIN_VALUE / 2, cz * 16, cx * 16 + 15, Integer.MAX_VALUE / 2, cz * 16 + 15);

        if (DEBUG) {
            System.out.printf("FURNACE decorate cx=%d cz=%d decorationSeed=%d biomes=%s%n",
                cx, cz, decorationSeed, new java.util.TreeSet<>(biomeSet));
        }

        for (int step = 0; step < BiomeFeatureData.STEP_COUNT; step++) {
            if (!starts.isEmpty() && structureEngine != null) {
                // vanilla: step 그룹의 구조물마다 setFeatureSeed(decorationSeed, m, step), m = 그 step 안 레지스트리 순번(시작점이 없어도 증가).
                int m = 0;
                for (StructureRegistry.Def def : structureEngine.registry().structuresOfStep(step)) {
                    boolean seeded = false;
                    java.util.List<StructureStart> ofDef = new java.util.ArrayList<>();
                    for (StructureStart start : starts) if (start.structureId().equals(def.id())) ofDef.add(start);
                    // 바닐라: 청크 참조 집합(LongOpenHashSet) 순회 순서대로 — 난수를 이어 쓰므로 순서가 결과를 바꾼다.
                    for (StructureStart start : org.domi.furnace.server.world.gen.structure.ReferenceOrder.order(ofDef)) {
                        if (!seeded) {
                            featureRandom.setFeatureSeed(decorationSeed, m, step);
                            seeded = true;
                        }
                        synchronized (start) {
                            start.placeInChunk(structureEngine, view, featureRandom, regionRandom, writableBox, cx, cz);
                        }
                    }
                    m++;
                }
            }
            FeatureSorter.StepFeatureData stepData = sorter.featuresPerStep().get(step);
            TreeSet<Integer> indices = new TreeSet<>();
            for (String biome : biomeSet) {
                for (PlacedFeature feature : biomeData.featuresForStep(biome, step)) {
                    int idx = stepData.indexOf(feature);
                    if (idx < 0) {
                        throw new IllegalStateException("PlacedFeature not interned for biome " + biome + " step " + step);
                    }
                    indices.add(idx);
                }
            }
            if (DEBUG && !indices.isEmpty()) {
                System.out.printf("FURNACE step=%d indices=%s%n", step, indices);
            }
            for (int idx : indices) {
                featureRandom.setFeatureSeed(decorationSeed, idx, step);
                PlacedFeature pf = stepData.features().get(idx);
                String featureId = biomeData.idOf(pf);
                long featureSeed = decorationSeed + idx + 10000L * step;
                int[] before = BLOCK_DEBUG ? snapshotGrid(view) : null;
                if (DEBUG) {
                    System.out.printf("FURNACE step=%d idx=%d featureSeed=%d id=%s%n",
                        step, idx, featureSeed, featureId);
                }
                boolean trace = RandomTrace.shouldTrace(cx, cz, step, idx, featureId);
                if (trace) RandomTrace.begin(seed, cx, cz, step, idx, featureId, featureSeed);
                boolean placed;
                try {
                    placed = pf.placeWithBiomeCheck(view, featureRandom, cx * 16, cz * 16,
                            biomeData.validBiomeIds(pf));
                } finally {
                    if (trace) RandomTrace.end();
                }
                if (BLOCK_DEBUG) {
                    int[] after = snapshotGrid(view);
                    int changed = countChanged(before, after);
                    if (placed || changed > 0) {
                        System.out.printf(
                            "  FURNACE step=%d idx=%d id=%s placed=%s changed=%d firstChange=%s%n",
                            step, idx, featureId, placed, changed, firstChange(before, after, cx, cz));
                        logChangedBlocks(before, after, cx, cz, step, idx, featureId);
                    }
                }
            }
        }
    }

    /** 현재 origin(view.cx/cz) 뿐 아니라 3x3 grid 전체(48x48)를 스냅샷 — cross-origin spill(이웃 청크로의
     * 나무 캐노피 유출) 탐지용. 좌표는 view.cx()*16-16 ~ +31 범위(중심 청크 기준 -1..+2 offset). */
    private static int[] snapshotGrid(FeatureWorldView view) {
        int[] snapshot = new int[384 * 48 * 48];
        int index = 0;
        int minX = view.cx() * 16 - 16;
        int minZ = view.cz() * 16 - 16;
        for (int y = -64; y < 320; y++) {
            for (int lz = 0; lz < 48; lz++) {
                for (int lx = 0; lx < 48; lx++) {
                    snapshot[index++] = view.getBlock(minX + lx, y, minZ + lz);
                }
            }
        }
        return snapshot;
    }

    private static int countChanged(int[] before, int[] after) {
        int changed = 0;
        for (int i = 0; i < before.length; i++) {
            if (before[i] != after[i]) changed++;
        }
        return changed;
    }

    private static String firstChange(int[] before, int[] after, int cx, int cz) {
        for (int i = 0; i < before.length; i++) {
            if (before[i] == after[i]) continue;
            int plane = 48 * 48;
            int y = -64 + i / plane;
            int rem = i % plane;
            int lz = rem / 48 - 16;
            int lx = rem % 48 - 16;
            return String.format("originCx=%d originCz=%d gridLx=%d y=%d gridLz=%d %d->%d",
                cx, cz, lx, y, lz, before[i], after[i]);
        }
        return "-";
    }

    private static void logChangedBlocks(int[] before, int[] after, int cx, int cz, int step, int idx, String id) {
        for (int i = 0; i < before.length; i++) {
            if (before[i] == after[i]) continue;
            int plane = 48 * 48;
            int y = -64 + i / plane;
            int rem = i % plane;
            int lz = rem / 48 - 16;
            int lx = rem % 48 - 16;
            int wx = cx * 16 + lx;
            int wz = cz * 16 + lz;
            System.out.printf(
                "BLOCK_DELTA source=FURNACE originCx=%d originCz=%d step=%d idx=%d id=%s wx=%d y=%d wz=%d old=%d new=%d%n",
                cx, cz, step, idx, id, wx, y, wz, before[i], after[i]);
        }
    }

    public BiomeFeatureData biomeData() {
        return biomeData;
    }

    public FeatureSorter sorter() {
        return sorter;
    }
}
