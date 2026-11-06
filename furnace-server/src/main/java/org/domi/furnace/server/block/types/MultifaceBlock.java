// 바닐라 MultifaceBlock: 여러 면에 붙는 블록(발광 이끼·스컬크 맥) 바탕. 면마다 불리언 속성(down/up/north/south/west/east).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public abstract class MultifaceBlock extends BlockBehavior {

    /** 방향 번호 → 면 속성. */
    static final Prop[] FACE = {B.prop("down"), B.prop("up"), B.prop("north"), B.prop("south"), B.prop("west"), B.prop("east")};
    private static final int AIR = B.defaultState("air");

    protected MultifaceBlock(int block) { super(block); }

    /** isFaceSupported: 이 종류가 그 면을 가질 수 있는가. */
    protected boolean isFaceSupported(int dir) { return true; }

    public abstract MultifaceSpreader getSpreader();

    public static boolean hasFace(int state, int dir) { return FACE[dir].has(state) && FACE[dir].is(state); }

    /** pack(방향들): 방향 번호 비트 묶음. */
    public static byte pack(java.util.Collection<Integer> dirs) {
        byte b = 0;
        for (int d : dirs) b = (byte) (b | 1 << d);
        return b;
    }

    protected static boolean hasAnyFace(int state) {
        for (int d : Dir.ALL) if (hasFace(state, d)) return true;
        return false;
    }

    private static boolean hasAnyVacantFace(int state) {
        for (int d : Dir.ALL) if (!hasFace(state, d)) return true;
        return false;
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (!hasAnyFace(state)) return AIR;
        return hasFace(state, dir) && !canAttachTo(level, dir, neighborPos, neighborState) ? removeFace(state, dir) : state;
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        boolean any = false;
        for (int d : Dir.ALL) {
            if (hasFace(state, d)) {
                Pos p = pos.relative(d);
                if (!canAttachTo(level, d, p, level.getBlockState(p))) return false;
                any = true;
            }
        }
        return any;
    }

    @Override
    public boolean canBeReplaced(int state, PlaceContext ctx) { return hasAnyVacantFace(state); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        BlockLevel level = ctx.level();
        Pos pos = ctx.clickedPos();
        int cur = level.getBlockState(pos);
        for (int d : ctx.nearestLookingDirections()) {
            int s = getStateForPlacement(cur, level, pos, d);
            if (s >= 0) return s;
        }
        return -1;
    }

    /** isValidStateForPlacement: 그 면을 아직 갖지 않았고 너머에 붙을 수 있는가. */
    public boolean isValidStateForPlacement(BlockLevel level, int state, Pos pos, int dir) {
        if (isFaceSupported(dir) && (!B.is(state, block) || !hasFace(state, dir))) {
            Pos p = pos.relative(dir);
            return canAttachTo(level, dir, p, level.getBlockState(p));
        }
        return false;
    }

    /** getStateForPlacement(현재 상태, level, pos, 면): 그 면을 더한 상태(못 붙으면 -1). */
    public int getStateForPlacement(int current, BlockLevel level, Pos pos, int dir) {
        if (!isValidStateForPlacement(level, current, pos, dir)) return -1;
        int base;
        if (B.is(current, block)) base = current;
        else if (WATERLOGGED.has(defaultState()) && Fluids.isWaterSource(B.fluid(current))) base = WATERLOGGED.with(defaultState(), true);
        else base = defaultState();
        return FACE[dir].with(base, true);
    }

    /** canAttachTo(level, dir, pos, state): 지지 모양이나 충돌 모양의 맞닿는 면이 꽉 찼는가. */
    public static boolean canAttachTo(BlockLevel level, int dir, Pos pos, int state) { return VineBlock.canAttachTo(level, dir, pos, state); }

    private static int removeFace(int state, int dir) {
        int s = FACE[dir].with(state, false);
        return hasAnyFace(s) ? s : AIR;
    }
}
