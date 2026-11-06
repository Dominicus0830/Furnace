// 바닐라 WallSkullBlock: 벽에 붙인 머리. 시선 순서대로 수평 방향 중 그 쪽 칸이 바뀔 수 없는 첫 방향을 등지고 놓인다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Prop;

public class WallSkullBlock extends AbstractSkullBlock {

    private static final Prop FACING = B.prop("facing");

    public WallSkullBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = super.getStateForPlacement(ctx);
        for (int d : ctx.nearestLookingDirections()) {
            if (Dir.horizontal(d)) {
                s = FACING.with(s, Dir.NAME[Dir.opposite(d)]);
                if (!B.canBeReplaced(ctx.level().getBlockState(ctx.clickedPos().relative(d)), ctx)) return s;
            }
        }
        return -1;
    }
}
