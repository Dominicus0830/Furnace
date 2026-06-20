package org.domi.furnace.server.world.gen.feature;

import java.util.List;
import java.util.stream.Stream;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public record PlacedFeature(ConfiguredFeature feature, List<PlacementModifier> placement) {

    public boolean place(FeatureWorldView view, WorldgenRandom random, int blockX, int blockZ) {
        Stream<BlockPos> positions = Stream.of(new BlockPos(blockX, 0, blockZ));
        for (PlacementModifier modifier : placement) {
            positions = positions.flatMap(pos -> modifier.filter(view, random, pos));
        }

        boolean[] placedAny = {false};
        positions.forEach(pos -> placedAny[0] |= feature.place(view, random, pos.x(), pos.y(), pos.z()));
        return placedAny[0];
    }
}
