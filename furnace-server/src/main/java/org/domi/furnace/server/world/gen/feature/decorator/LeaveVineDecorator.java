package org.domi.furnace.server.world.gen.feature.decorator;

import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.TreeFeature;

public record LeaveVineDecorator(float probability) implements TreeDecorator {
    @Override
    public void place(Context context) {
        for (BlockPos pos : context.leaves()) {
            for (TreeFeature.Direction dir : TreeFeature.HORIZONTAL_DIRECTIONS) {
                if (context.random().nextFloat() >= probability) continue;
                int wx = pos.x() + dir.dx();
                int wz = pos.z() + dir.dz();
                if (context.view().getBlock(wx, pos.y(), wz) == 0) {
                    addHangingVine(context, new BlockPos(wx, pos.y(), wz), dir.opposite());
                }
            }
        }
    }

    private void addHangingVine(Context context, BlockPos start, TreeFeature.Direction facing) {
        BlockPos current = start;
        for (int i = 0; i < 4 && context.view().getBlock(current.x(), current.y(), current.z()) == 0; i++) {
            context.view().setBlock(current.x(), current.y(), current.z(), context.config().vineStateId(facing));
            context.decorations().add(current);
            current = new BlockPos(current.x(), current.y() - 1, current.z());
        }
    }
}
