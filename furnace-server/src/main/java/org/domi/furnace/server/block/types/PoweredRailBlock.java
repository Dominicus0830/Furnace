// 바닐라 PoweredRailBlock: 전동·활성화 레일. 신호를 받거나 같은 레일 줄(최대 8칸)을 타고 신호가 이어지면 켜진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class PoweredRailBlock extends BaseRailBlock {

    private static final Prop POWERED = B.prop("powered");

    public PoweredRailBlock(int block) { super(block, true); }

    // 모양은 값 번호로 다룬다(X1: 문자열 switch·비교 제거). 값 목록 north_south|east_west|ascending_east|ascending_west|ascending_north|ascending_south.
    private static final int NORTH_SOUTH = 0, EAST_WEST = 1, ASC_EAST = 2, ASC_WEST = 3, ASC_NORTH = 4, ASC_SOUTH = 5;
    static {
        String[] vs = SHAPE.values(B.defaultState("powered_rail"));
        String[] want = {"north_south", "east_west", "ascending_east", "ascending_west", "ascending_north", "ascending_south"};
        if (!java.util.Arrays.equals(vs, want)) throw new IllegalStateException("powered_rail shape 값 순서가 다르다");
    }

    /** findPoweredRailSignal: 레일 줄을 따라 searchForward 방향으로 신호 받는 같은 레일을 찾는다. */
    protected boolean findPoweredRailSignal(BlockLevel level, Pos pos, int state, boolean forward, int depth) {
        if (depth >= 8) return false;
        int x = pos.x(), y = pos.y(), z = pos.z();
        boolean checkBelow = true;
        int shape = SHAPE.get(state);
        switch (shape) {
            case NORTH_SOUTH -> z += forward ? 1 : -1;
            case EAST_WEST -> x += forward ? -1 : 1;
            case ASC_EAST -> {
                if (forward) x--;
                else { x++; y++; checkBelow = false; }
                shape = EAST_WEST;
            }
            case ASC_WEST -> {
                if (forward) { x--; y++; checkBelow = false; }
                else x++;
                shape = EAST_WEST;
            }
            case ASC_NORTH -> {
                if (forward) z++;
                else { z--; y++; checkBelow = false; }
                shape = NORTH_SOUTH;
            }
            case ASC_SOUTH -> {
                if (forward) { z++; y++; checkBelow = false; }
                else z--;
                shape = NORTH_SOUTH;
            }
            default -> {}
        }
        return isSameRailWithPower(level, new Pos(x, y, z), forward, depth, shape)
                || checkBelow && isSameRailWithPower(level, new Pos(x, y - 1, z), forward, depth, shape);
    }

    protected boolean isSameRailWithPower(BlockLevel level, Pos pos, boolean forward, int depth, int shape) {
        int s = level.getBlockState(pos);
        if (!B.is(s, block)) return false;
        int other = SHAPE.get(s);
        if (shape == EAST_WEST && (other == NORTH_SOUTH || other == ASC_NORTH || other == ASC_SOUTH)) return false;
        if (shape == NORTH_SOUTH && (other == EAST_WEST || other == ASC_EAST || other == ASC_WEST)) return false;
        if (!POWERED.is(s)) return false;
        return level.hasNeighborSignal(pos) || findPoweredRailSignal(level, pos, s, forward, depth + 1);
    }

    private static boolean isAscending(int shape) { return shape >= ASC_EAST; }

    @Override
    protected void updateState(int state, BlockLevel level, Pos pos, int sourceBlock) {
        boolean was = POWERED.is(state);
        boolean now = level.hasNeighborSignal(pos) || findPoweredRailSignal(level, pos, state, true, 0) || findPoweredRailSignal(level, pos, state, false, 0);
        if (now != was) {
            level.setBlock(pos, POWERED.with(state, now), 3);
            level.updateNeighborsAt(pos.below(), block);
            if (isAscending(SHAPE.get(state))) level.updateNeighborsAt(pos.above(), block);
        }
    }
}
