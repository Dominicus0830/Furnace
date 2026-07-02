package org.domi.furnace.server.world.gen.feature;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.domi.furnace.server.world.gen.carver.RandomTrace;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public record PlacedFeature(ConfiguredFeature feature, List<PlacementModifier> placement) {

    public boolean place(FeatureWorldView view, WorldgenRandom random, int blockX, int blockZ) {
        return placeAt(view, random, new BlockPos(blockX, 0, blockZ), null);
    }

    /** vanilla biome placement filter 포함 배치. validBiomeIds = null이면 biome check 없음. */
    public boolean placeWithBiomeCheck(FeatureWorldView view, WorldgenRandom random,
                                       int blockX, int blockZ, Set<Integer> validBiomeIds) {
        return placeAt(view, random, new BlockPos(blockX, 0, blockZ), validBiomeIds);
    }

    /** 주어진 origin에서 placement modifier 적용 후 feature 배치. 내포 placed feature(random_patch inner 등)용. */
    public boolean placeAt(FeatureWorldView view, WorldgenRandom random, BlockPos origin) {
        return placeAt(view, random, origin, null);
    }

    private static final boolean SEA_STREAM_DEBUG = Boolean.getBoolean("furnace.seaStreamDebug");

    private boolean placeAt(FeatureWorldView view, WorldgenRandom random, BlockPos origin, Set<Integer> validBiomeIds) {
        Stream<BlockPos> positions = Stream.of(origin);
        for (PlacementModifier modifier : placement) {
            positions = positions.flatMap(pos -> modifier.filter(view, random, pos));
        }

        if (SEA_STREAM_DEBUG && feature instanceof SeagrassFeature) {
            java.util.List<BlockPos> posList = positions.collect(java.util.stream.Collectors.toList());
            System.out.printf("SEA_STREAM: %d positions collected%n", posList.size());
            for (int i = 0; i < Math.min(5, posList.size()); i++) {
                System.out.printf("  pos[%d]=(%d,%d,%d)%n", i, posList.get(i).x(), posList.get(i).y(), posList.get(i).z());
            }
            positions = posList.stream();
        }

        boolean[] placedAny = {false};
        positions.forEach(pos -> {
            RandomTrace.log("placed_feature_origin x=" + pos.x() + " y=" + pos.y() + " z=" + pos.z()
                + " feature=" + feature.getClass().getSimpleName());
            if (validBiomeIds != null && !validBiomeIds.isEmpty()) {
                int biomeId = view.getBiomeId(pos.x(), pos.y(), pos.z());
                if (!validBiomeIds.contains(biomeId)) {
                    RandomTrace.log("placed_feature_skip_biome biomeId=" + biomeId);
                    return;
                }
            }
            boolean treeTrace = feature instanceof TreeFeature && RandomTrace.isActive();
            if (treeTrace) RandomTrace.beginTreePlacement(pos.x(), pos.y(), pos.z());
            try {
                boolean placed = feature.place(view, random, pos.x(), pos.y(), pos.z());
                RandomTrace.log("placed_feature_result placed=" + placed + " x=" + pos.x() + " y=" + pos.y() + " z=" + pos.z());
                placedAny[0] |= placed;
            } finally {
                if (treeTrace) RandomTrace.endTreePlacement();
            }
        });
        return placedAny[0];
    }
}
