package org.domi.furnace.server.world.gen.feature;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.domi.furnace.server.world.gen.carver.RandomTrace;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

@org.domi.furnace.server.VanillaPort("net.minecraft.world.level.levelgen.placement.PlacedFeature")
public record PlacedFeature(ConfiguredFeature feature, List<PlacementModifier> placement) {

    public boolean place(FeatureWorldView view, WorldgenRandom random, int blockX, int blockZ) {
        return placeAt(view, random, new BlockPos(blockX, 0, blockZ), null, false);
    }

    /** vanilla biome placement filter 포함 배치. validBiomeIds = null이면 biome check 없음. */
    public boolean placeWithBiomeCheck(FeatureWorldView view, WorldgenRandom random,
                                       int blockX, int blockZ, Set<Integer> validBiomeIds) {
        // 바닐라 applyBiomeDecoration: origin = SectionPos.of(chunk, minSection).origin() → y = 최소 건축 높이 (void_start_platform 이 origin.y 를 쓴다)
        return placeAt(view, random, new BlockPos(blockX, view.minBuildHeight(), blockZ), validBiomeIds, false);
    }

    /** 주어진 origin에서 placement modifier 적용 후 feature 배치. 내포 placed feature(random_patch inner 등)용. */
    public boolean placeAt(FeatureWorldView view, WorldgenRandom random, BlockPos origin) {
        return placeAt(view, random, origin, null, false);
    }

    /**
     * vanilla PlacedFeature#place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos) — top feature 가 없는 배치(플러그인이 직접 부른 경우).
     * biome 필터에 닿는 위치마다 바닐라 BiomeFilter 처럼 IllegalStateException 을 던진다(그 전 modifier 의 난수 소비는 그대로).
     */
    public boolean placeWithoutTopFeature(FeatureWorldView view, WorldgenRandom random, BlockPos origin) {
        return placeAt(view, random, origin, null, true);
    }


    private static final boolean LOG_ALL = System.getProperty("furnace.logall") != null;

    private boolean placeAt(FeatureWorldView view, WorldgenRandom random, BlockPos origin, Set<Integer> validBiomeIds, boolean noTopFeature) {
        Stream<BlockPos> positions = Stream.of(origin);
        for (PlacementModifier modifier : placement) {
            if (noTopFeature && modifier instanceof PlacementModifier.BiomeFilter) {
                positions = positions.flatMap(pos -> {
                    throw new IllegalStateException("Tried to biome check an unregistered feature, or a feature that should not restrict the biome");
                });
                continue;
            }
            if (RandomTrace.isActive()) {
                positions = positions.flatMap(pos -> {
                    java.util.List<BlockPos> out = modifier.filter(view, random, pos).collect(java.util.stream.Collectors.toList());
                    RandomTrace.log("modifier " + modifier.getClass().getSimpleName() + " in=(" + pos.x() + "," + pos.y() + "," + pos.z() + ") out=" + out.size()
                        + (out.size() == 1 ? " (" + out.get(0).x() + "," + out.get(0).y() + "," + out.get(0).z() + ")" : ""));
                    return out.stream();
                });
                continue;
            }
            positions = positions.flatMap(pos -> modifier.filter(view, random, pos));
        }

        boolean[] placedAny = {false};
        positions.forEach(pos -> {
            if (RandomTrace.isActive()) RandomTrace.log("placed_feature_origin x=" + pos.x() + " y=" + pos.y() + " z=" + pos.z()
                + " feature=" + feature.getClass().getSimpleName());
            if (validBiomeIds != null && !validBiomeIds.isEmpty()) {
                int biomeId = view.getBlendedBiomeId(pos.x(), pos.y(), pos.z());
                if (RandomTrace.isActive()) RandomTrace.log("vbio " + pos.x() + " " + pos.y() + " " + pos.z() + " id=" + biomeId + " -> " + validBiomeIds.contains(biomeId));
                if (!validBiomeIds.contains(biomeId)) {
                    if (RandomTrace.isActive()) RandomTrace.log("placed_feature_skip_biome biomeId=" + biomeId);
                    return;
                }
            }
            if (LOG_ALL) System.out.println("FURNACE_ALL view=" + view.debugCenter() + " " + feature.getClass().getSimpleName() + " " + pos.x() + "," + pos.y() + "," + pos.z());
            if (FeatureWorldView.WATCHING && !(feature instanceof SimpleBlockFeature)) FeatureWorldView.debugOrigin = pos.x() + "," + pos.y() + "," + pos.z() + " " + feature.getClass().getSimpleName();
            boolean treeTrace = feature instanceof TreeFeature && RandomTrace.isActive();
            if (treeTrace) RandomTrace.beginTreePlacement(pos.x(), pos.y(), pos.z());
            try {
                boolean placed = feature.place(view, random, pos.x(), pos.y(), pos.z());
                if (RandomTrace.isActive()) RandomTrace.log("placed_feature_result placed=" + placed + " x=" + pos.x() + " y=" + pos.y() + " z=" + pos.z()
                    + " feature=" + feature.getClass().getSimpleName() + " seed=" + random.debugState());
                placedAny[0] |= placed;
            } finally {
                if (treeTrace) RandomTrace.endTreePlacement();
            }
        });
        return placedAny[0];
    }
}
