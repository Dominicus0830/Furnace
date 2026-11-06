// 바닐라 WallBlock: 벽. 옆면은 none/low/tall(위 칸 충돌 모양 아랫면이 덮으면 tall), 기둥(up)은 모양·위 칸에 따라 세운다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.Shape;

public class WallBlock extends BlockBehavior {

    static final Prop UP = B.prop("up"), NORTH = B.prop("north"), EAST = B.prop("east"), SOUTH = B.prop("south"), WEST = B.prop("west");
    private static final String NONE = "none", LOW = "low", TALL = "tall";

    public WallBlock(int block) { super(block); }

    /** connectsTo(state, faceSturdy, dir): 벽·(연결 예외 아닌) 단단한 면·철창·옆으로 선 울타리 문. */
    private static boolean connectsTo(int state, boolean sturdy, int dir) {
        BlockBehavior b = B.behavior(state);
        boolean gate = b instanceof FenceGateBlock && FenceGateBlock.connectsToDirection(state, dir);
        return B.isTag(state, "minecraft:walls") || !B.isExceptionForConnection(state) && sturdy || b instanceof IronBarsBlock || gate;
    }

    private static boolean connects(BlockLevel level, int state, Pos pos, int face) {
        return connectsTo(state, B.isFaceSturdy(state, level, pos, face), face);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        BlockLevel level = ctx.level();
        Pos pos = ctx.clickedPos();
        Pos n = pos.north(), e = pos.east(), s = pos.south(), w = pos.west(), above = pos.above();
        int ns = level.getBlockState(n), es = level.getBlockState(e), ss = level.getBlockState(s), ws = level.getBlockState(w);
        int as = level.getBlockState(above);
        boolean cn = connects(level, ns, n, Dir.SOUTH), ce = connects(level, es, e, Dir.WEST),
                cs = connects(level, ss, s, Dir.NORTH), cw = connects(level, ws, w, Dir.EAST);
        int st = WATERLOGGED.with(defaultState(), B.waterAt(ctx));
        return updateShape(level, st, above, as, cn, ce, cs, cw);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        if (dir == Dir.DOWN) return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
        if (dir == Dir.UP) {
            return updateShape(level, state, neighborPos, neighborState, connected(state, NORTH), connected(state, EAST), connected(state, SOUTH), connected(state, WEST));
        }
        int o = Dir.opposite(dir);
        boolean side = connects(level, neighborState, neighborPos, o);
        boolean cn = dir == Dir.NORTH ? side : connected(state, NORTH);
        boolean ce = dir == Dir.EAST ? side : connected(state, EAST);
        boolean cs = dir == Dir.SOUTH ? side : connected(state, SOUTH);
        boolean cw = dir == Dir.WEST ? side : connected(state, WEST);
        Pos above = pos.above();
        return updateShape(level, state, above, level.getBlockState(above), cn, ce, cs, cw);
    }

    private static boolean connected(int state, Prop p) { return !NONE.equals(p.valueName(state)); }

    private int updateShape(BlockLevel level, int state, Pos abovePos, int aboveState, boolean n, boolean e, boolean s, boolean w) {
        Shape above = B.behavior(aboveState).getCollisionShape(aboveState, level, abovePos, null);
        int st = NORTH.with(state, side(n, above, 7, 0, 9, 9));
        st = EAST.with(st, side(e, above, 7, 7, 16, 9));
        st = SOUTH.with(st, side(s, above, 7, 7, 9, 16));
        st = WEST.with(st, side(w, above, 0, 7, 9, 9));
        return UP.with(st, shouldRaisePost(st, aboveState, above));
    }

    /** makeWallState: 이어지면 위 칸 아랫면이 그 옆 시험 모양을 덮을 때 tall, 아니면 low. */
    private static String side(boolean connected, Shape above, double x0, double z0, double x1, double z1) {
        if (!connected) return NONE;
        return covers(above, x0, z0, x1, z1) ? TALL : LOW;
    }

    /** isCovered(위 칸 아랫면, 시험 기둥) — 좌표는 1/16 단위. */
    private static boolean covers(Shape above, double x0, double z0, double x1, double z1) {
        return above.faceCovers(Dir.DOWN, x0 / 16, z0 / 16, x1 / 16, z1 / 16);
    }

    private static boolean shouldRaisePost(int state, int aboveState, Shape above) {
        if (B.behavior(aboveState) instanceof WallBlock && UP.is(aboveState)) return true;
        String n = NORTH.valueName(state), s = SOUTH.valueName(state), e = EAST.valueName(state), w = WEST.valueName(state);
        boolean sNone = NONE.equals(s), wNone = NONE.equals(w), eNone = NONE.equals(e), nNone = NONE.equals(n);
        if (nNone && sNone && wNone && eNone || nNone != sNone || wNone != eNone) return true;
        boolean straightTall = TALL.equals(n) && TALL.equals(s) || TALL.equals(e) && TALL.equals(w);
        return !straightTall && (B.isTag(aboveState, "minecraft:wall_post_override") || covers(above, 7, 7, 9, 9));
    }
}
