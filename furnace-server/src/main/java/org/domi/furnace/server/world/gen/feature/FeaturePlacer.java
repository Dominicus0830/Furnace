package org.domi.furnace.server.world.gen.feature;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.RandomTrace;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.carver.XoroshiroWorldgenRandom;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;
import org.domi.furnace.server.world.gen.structure.BoundingBox;
import org.domi.furnace.server.world.gen.structure.StructurePiece;
import org.domi.furnace.server.world.gen.structure.StructureStart;

public final class FeaturePlacer {

    /** vanilla GenerationStep.Decoration.SURFACE_STRUCTURES.ordinal() — 구조물 postProcess가 이 step 앞에서 실행된다(F1). */
    private static final int STRUCTURE_STEP = 4;

    /**
     * desert_pyramid의 구조물 시드 인덱스 i1 (F1). vanilla applyBiomeDecoration은 전체 Structure registry를
     * step ordinal로 groupingBy(registry stream 순서)한 뒤 그룹 내 순번을 setFeatureSeed(l, i1, step)에 넣는다 —
     * start 유무와 무관하게 그룹의 모든 구조물이 i1을 소비. "측정값 22"는 oracle의
     * VanillaRegistries.createLookup().listElements() 순서(datagen lookup, identity-hash 기반이라 JVM 실행마다 요동)에서
     * 뽑은 것으로, 실서버 registry 순서(RegistryDataLoader/MappedRegistry.stream(), 등록 순서 고정)와 같다는 보장이 없다.
     * 22는 oracle 픽스처와 Furnace가 공유하는 핀 고정 컨벤션일 뿐 — 진짜 실서버 순서 검증은 .mca 자가검증 게이트(스펙 §3) 몫.
     * 다른 구조물 지원 시(S4+) registry 그룹핑으로 일반화할 것.
     */
    private static final int DESERT_PYRAMID_STRUCTURE_INDEX = 22;

    private final long seed;
    private final BiomeFeatureData biomeData;
    private final FeatureSorter sorter;
    private final BlockRegistry blocks;
    /** 바닐라 RandomState.getOrCreateRandomFactory("minecraft:worldgen_region_random") 미러 — WorldGenLevel.getRandom()의 원천. */
    private final XoroshiroRandomSource.XoroshiroPositionalRandomFactory regionRandomFactory;

    public FeaturePlacer(long seed, BiomeFeatureData biomeData, FeatureSorter sorter, BlockRegistry blocks) {
        this.seed = seed;
        this.biomeData = biomeData;
        this.sorter = sorter;
        this.blocks = blocks;
        this.regionRandomFactory = new XoroshiroRandomSource(seed).forkPositional()
            .fromHashOf("minecraft:worldgen_region_random").forkPositional();
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
        XoroshiroRandomSource regionRandom = regionRandomFactory.at(cx * 16, 0, cz * 16);
        BoundingBox writableBox = new BoundingBox(
            cx * 16, Integer.MIN_VALUE / 2, cz * 16, cx * 16 + 15, Integer.MAX_VALUE / 2, cz * 16 + 15);

        if (DEBUG) {
            System.out.printf("FURNACE decorate cx=%d cz=%d decorationSeed=%d biomes=%s%n",
                cx, cz, decorationSeed, new java.util.TreeSet<>(biomeSet));
        }

        for (int step = 0; step < BiomeFeatureData.STEP_COUNT; step++) {
            if (step == STRUCTURE_STEP && !starts.isEmpty()) {
                // vanilla: step 그룹의 구조물마다 setFeatureSeed(l, i1, step) — desert_pyramid의 i1은 registry 그룹 순번 22 (상수 주석 참조).
                featureRandom.setFeatureSeed(decorationSeed, DESERT_PYRAMID_STRUCTURE_INDEX, step);
                for (StructureStart start : starts) {
                    for (StructurePiece piece : start.pieces()) {
                        if (piece.boundingBox().intersects(writableBox)) {
                            piece.postProcess(view, blocks, featureRandom, regionRandom, writableBox);
                        }
                    }
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
                int[] before = BLOCK_DEBUG ? snapshotCenter(view) : null;
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
                    int[] after = snapshotCenter(view);
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

    private static int[] snapshotCenter(FeatureWorldView view) {
        int[] snapshot = new int[384 * 16 * 16];
        int index = 0;
        int minX = view.cx() * 16;
        int minZ = view.cz() * 16;
        for (int y = -64; y < 320; y++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
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
            int plane = 16 * 16;
            int y = -64 + i / plane;
            int rem = i % plane;
            int lz = rem / 16;
            int lx = rem % 16;
            return String.format("lx=%d y=%d lz=%d %d->%d", lx, y, lz, before[i], after[i]);
        }
        return "-";
    }

    private static void logChangedBlocks(int[] before, int[] after, int cx, int cz, int step, int idx, String id) {
        for (int i = 0; i < before.length; i++) {
            if (before[i] == after[i]) continue;
            int plane = 16 * 16;
            int y = -64 + i / plane;
            int rem = i % plane;
            int lz = rem / 16;
            int lx = rem % 16;
            int wx = cx * 16 + lx;
            int wz = cz * 16 + lz;
            System.out.printf(
                "BLOCK_DELTA source=FURNACE step=%d idx=%d id=%s wx=%d y=%d wz=%d old=%d new=%d%n",
                step, idx, id, wx, y, wz, before[i], after[i]);
        }
    }

    public BiomeFeatureData biomeData() {
        return biomeData;
    }

    public FeatureSorter sorter() {
        return sorter;
    }
}
