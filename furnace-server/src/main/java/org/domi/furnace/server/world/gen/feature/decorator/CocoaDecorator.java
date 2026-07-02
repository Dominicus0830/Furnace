package org.domi.furnace.server.world.gen.feature.decorator;

import java.util.List;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.TreeFeature;

public record CocoaDecorator(float probability) implements TreeDecorator {
    @Override
    public void place(Context context) {
        if (context.logs().isEmpty() || context.random().nextFloat() >= probability) return;
        int baseY = context.logs().get(0).y();
        for (BlockPos pos : context.logs()) {
            if (pos.y() - baseY > 2) continue;
            for (TreeFeature.Direction dir : TreeFeature.HORIZONTAL_DIRECTIONS) {
                if (context.random().nextFloat() <= 0.25f) {
                    int wx = pos.x() + dir.dx();
                    int wz = pos.z() + dir.dz();
                    if (context.view().getBlock(wx, pos.y(), wz) == 0) {
                        int age = context.random().nextInt(3);
                        context.view().setBlock(wx, pos.y(), wz, context.config().cocoaStateId(dir.opposite(), age));
                        context.decorations().add(new BlockPos(wx, pos.y(), wz));
                    }
                }
            }
        }
    }
}
