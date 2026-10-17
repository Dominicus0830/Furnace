// 바닐라 PoweredRailBlock: 전동·활성화 레일. 신호를 받거나 같은 레일 줄(최대 8칸)을 타고 신호가 이어지면 켜진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class PoweredRailBlock extends BaseRailBlock {

    private static final Prop POWERED = B.prop("powered");

    public PoweredRailBlock(int block) { super(block, true); }

    /** findPoweredRailSignal: 레일 줄을 따라 searchForward 방향으로 신호 받는 같은 레일을 찾는다. */
    protected boolean findPoweredRailSignal(BlockLevel level, Pos pos, int state, boolean forward, int depth) {
        if (depth >= 8) return false;
        int x = pos.x(), y = pos.y(), z = pos.z();
        boolean checkBelow = true;
        String shape = shape(state);
        switch (shape) {
            case "north_south" -> z += forward ? 1 : -1;
            case "east_west" -> x += forward ? -1 : 1;
            case "ascending_east" -> {
                if (forward) x--;
                else { x++; y++; checkBelow = false; }
                shape = "east_west";
            }
            case "ascending_west" -> {
                if (forward) { x--; y++; checkBelow = false; }
                else x++;
                shape = "east_west";
            }
            case "ascending_north" -> {
                if (forward) z++;
                else { z--; y++; checkBelow = false; }
                shape = "north_south";
            }
            case "ascending_south" -> {
                if (forward) { z++; y++; checkBelow = false; }
                else z--;
                shape = "north_south";
            }
            default -> {}
        }
        return isSameRailWithPower(level, new Pos(x, y, z), forward, depth, shape)
                || checkBelow && isSameRailWithPower(level, new Pos(x, y - 1, z), forward, depth, shape);
    }

    protected boolean isSameRailWithPower(BlockLevel level, Pos pos, boolean forward, int depth, String shape) {
        int s = level.getBlockState(pos);
        if (!B.is(s, block)) return false;
        String other = shape(s);
        if ("east_west".equals(shape) && ("north_south".equals(other) || "ascending_north".equals(other) || "ascending_south".equals(other))) return false;
        if ("north_south".equals(shape) && ("east_west".equals(other) || "ascending_east".equals(other) || "ascending_west".equals(other))) return false;
        if (!POWERED.is(s)) return false;
        return level.hasNeighborSignal(pos) || findPoweredRailSignal(level, pos, s, forward, depth + 1);
    }

    @Override
    protected void updateState(int state, BlockLevel level, Pos pos, int sourceBlock) {
        boolean was = POWERED.is(state);
        boolean now = level.hasNeighborSignal(pos) || findPoweredRailSignal(level, pos, state, true, 0) || findPoweredRailSignal(level, pos, state, false, 0);
        if (now != was) {
            level.setBlock(pos, POWERED.with(state, now), 3);
            level.updateNeighborsAt(pos.below(), block);
            if (ascending(shape(state))) level.updateNeighborsAt(pos.above(), block);
        }
    }
}
