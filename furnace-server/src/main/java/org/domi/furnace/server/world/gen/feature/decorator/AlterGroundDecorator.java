// vanilla AlterGroundDecorator 포팅 — mega_spruce 계열의 podzol 바닥 치환
package org.domi.furnace.server.world.gen.feature.decorator;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.TreeFeature;

public record AlterGroundDecorator(int groundStateId) implements TreeDecorator {

    @Override
    public void place(Context context) {
        List<BlockPos> list = new ArrayList<>();
        List<BlockPos> roots = context.roots();
        List<BlockPos> logs = context.logs();
        if (roots.isEmpty()) {
            list.addAll(logs);
        } else if (!logs.isEmpty() && roots.get(0).y() == logs.get(0).y()) {
            list.addAll(logs);
            list.addAll(roots);
        } else {
            list.addAll(roots);
        }
        if (list.isEmpty()) return;

        int y = list.get(0).y();
        for (BlockPos pos : list) {
            if (pos.y() != y) continue;
            placeCircle(context, pos.x() - 1, y, pos.z() - 1);
            placeCircle(context, pos.x() + 2, y, pos.z() - 1);
            placeCircle(context, pos.x() - 1, y, pos.z() + 2);
            placeCircle(context, pos.x() + 2, y, pos.z() + 2);
            for (int i = 0; i < 5; i++) {
                int r = context.random().nextInt(64);
                int rx = r % 8;
                int rz = r / 8;
                if (rx == 0 || rx == 7 || rz == 0 || rz == 7) {
                    placeCircle(context, pos.x() - 3 + rx, y, pos.z() - 3 + rz);
                }
            }
        }
    }

    private void placeCircle(Context context, int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                placeBlockAt(context, x + dx, y, z + dz);
            }
        }
    }

    // vanilla placeBlockAt — 위(+2)부터 아래(-3)로 훑으며 첫 dirt-태그(#minecraft:dirt, grass/mycelium
    // 포함 = config.dirtStates()) 블록을 치환. 공기가 아닌 다른 블록을 y<0에서 만나면 중단.
    private void placeBlockAt(Context context, int x, int y, int z) {
        for (int i = 2; i >= -3; i--) {
            int by = y + i;
            int state = context.view().getBlock(x, by, z);
            if (context.config().dirtStates().contains(state)) {
                context.view().setBlock(x, by, z, groundStateId);
                context.decorations().add(new BlockPos(x, by, z));
                break;
            }
            if (!TreeFeature.isAir(state) && i < 0) {
                break;
            }
        }
    }
}
