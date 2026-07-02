package org.domi.furnace.server.world.gen.feature.decorator;

import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;

public interface TreeDecorator {
    void place(Context context);

    record Context(
        FeatureWorldView view,
        WorldgenRandom random,
        List<BlockPos> roots,
        List<BlockPos> logs,
        List<BlockPos> leaves,
        List<BlockPos> decorations,
        TreeConfig config
    ) {}
}
