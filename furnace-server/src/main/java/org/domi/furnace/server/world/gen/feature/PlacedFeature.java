package org.domi.furnace.server.world.gen.feature;

import java.util.List;
import java.util.stream.Stream;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public record PlacedFeature(ConfiguredFeature feature, List<PlacementModifier> placement) {

    public boolean place(FeatureWorldView view, WorldgenRandom random, int blockX, int blockZ) {
        return placeAt(view, random, new BlockPos(blockX, 0, blockZ));
    }

    /** 주어진 origin에서 placement modifier 적용 후 feature 배치. 내포 placed feature(random_patch inner 등)용. */
    public boolean placeAt(FeatureWorldView view, WorldgenRandom random, BlockPos origin) {
        Stream<BlockPos> positions = Stream.of(origin);
        for (PlacementModifier modifier : placement) {
            positions = positions.flatMap(pos -> modifier.filter(view, random, pos));
        }

        boolean[] placedAny = {false};
        positions.forEach(pos -> placedAny[0] |= feature.place(view, random, pos.x(), pos.y(), pos.z()));
        return placedAny[0];
    }
}
