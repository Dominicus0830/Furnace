package org.domi.furnace.server.world.gen.feature.decorator;

import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.TreeFeature;

public final class TrunkVineDecorator implements TreeDecorator {
    @Override
    public void place(Context context) {
        for (BlockPos pos : context.logs()) {
            for (TreeFeature.Direction dir : TreeFeature.HORIZONTAL_DIRECTIONS) {
                if (context.random().nextInt(3) > 0) continue;
                int wx = pos.x() + dir.dx();
                int wz = pos.z() + dir.dz();
                if (context.view().getBlock(wx, pos.y(), wz) == 0) {
                    context.view().setBlock(wx, pos.y(), wz, context.config().vineStateId(dir.opposite()));
                    context.decorations().add(new BlockPos(wx, pos.y(), wz));
                }
            }
        }
    }
}
