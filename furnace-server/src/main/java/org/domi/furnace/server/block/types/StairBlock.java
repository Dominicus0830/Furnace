// 바닐라 StairBlock: 바라보는 방향·위아래 반쪽·이웃 계단에 따른 모양(안쪽·바깥쪽 꺾임). 물에 잠길 수 있다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class StairBlock extends BlockBehavior {

    protected static final Prop FACING = B.prop("facing");
    protected static final Prop HALF = B.prop("half");
    protected static final Prop SHAPE = B.prop("shape");
    private static final String TOP = "top", BOTTOM = "bottom";

    public StairBlock(int block) { super(block); }

    /** getStateForPlacement: 아랫면을 클릭했거나 옆면 위쪽 반을 클릭하면 위 반쪽. */
    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int face = ctx.clickedFace();
        Pos pos = ctx.clickedPos();
        boolean bottom = face != Dir.DOWN && (face == Dir.UP || !(ctx.clickY() - pos.y() > 0.5));
        int s = FACING.with(defaultState(), Dir.NAME[ctx.horizontalDirection()]);
        s = HALF.with(s, bottom ? BOTTOM : TOP);
        s = WATERLOGGED.with(s, B.waterAt(ctx));
        return SHAPE.with(s, getStairsShape(s, ctx.level(), pos));
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return Dir.horizontal(dir) ? SHAPE.with(state, getStairsShape(state, level, pos))
                : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    /** getStairsShape: 앞 칸 계단이 옆을 보면 바깥 꺾임, 뒤 칸 계단이 옆을 보면 안쪽 꺾임. */
    private static String getStairsShape(int state, BlockLevel level, Pos pos) {
        int facing = facing(state);
        int front = level.getBlockState(pos.relative(facing));
        if (isStairs(front) && HALF.get(state) == HALF.get(front)) {
            int f = facing(front);
            if (Dir.axis(f) != Dir.axis(facing) && canTakeShape(state, level, pos, Dir.opposite(f))) {
                return f == Dir.counterClockWise(facing) ? "outer_left" : "outer_right";
            }
        }
        int back = level.getBlockState(pos.relative(Dir.opposite(facing)));
        if (isStairs(back) && HALF.get(state) == HALF.get(back)) {
            int f = facing(back);
            if (Dir.axis(f) != Dir.axis(facing) && canTakeShape(state, level, pos, f)) {
                return f == Dir.counterClockWise(facing) ? "inner_left" : "inner_right";
            }
        }
        return "straight";
    }

    private static boolean canTakeShape(int state, BlockLevel level, Pos pos, int dir) {
        int s = level.getBlockState(pos.relative(dir));
        return !isStairs(s) || FACING.get(s) != FACING.get(state) || HALF.get(s) != HALF.get(state);
    }

    private static int facing(int state) { return Dir.byName(FACING.valueName(state)); }

    /** isStairs: StairBlock 계열 블록인가. */
    public static boolean isStairs(int state) { return B.behavior(state) instanceof StairBlock; }
}
