// 바닐라 IronBarsBlock: 철창·유리판. 단단한 면(연결 예외 제외)·다른 철창/유리판·담장에 붙는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.Shape;

import org.domi.furnace.server.block.BlockActor;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;

public class IronBarsBlock extends CrossCollisionBlock {

    public IronBarsBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        BlockLevel level = ctx.level();
        Pos pos = ctx.clickedPos();
        Pos n = pos.north(), s = pos.south(), w = pos.west(), e = pos.east();
        int ns = level.getBlockState(n), ss = level.getBlockState(s), ws = level.getBlockState(w), es = level.getBlockState(e);
        int st = defaultState();
        st = NORTH.with(st, attachsTo(ns, B.isFaceSturdy(ns, level, n, Dir.SOUTH)));
        st = SOUTH.with(st, attachsTo(ss, B.isFaceSturdy(ss, level, s, Dir.NORTH)));
        st = WEST.with(st, attachsTo(ws, B.isFaceSturdy(ws, level, w, Dir.EAST)));
        st = EAST.with(st, attachsTo(es, B.isFaceSturdy(es, level, e, Dir.WEST)));
        return WATERLOGGED.with(st, B.waterAt(ctx));
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        if (Dir.horizontal(dir)) return BY_DIR[dir].with(state, attachsTo(neighborState, B.isFaceSturdy(neighborState, level, neighborPos, Dir.opposite(dir))));
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    /** attachsTo(state, faceSturdy). */
    public static boolean attachsTo(int state, boolean sturdy) {
        return !B.isExceptionForConnection(state) && sturdy || B.behavior(state) instanceof IronBarsBlock || B.isTag(state, "minecraft:walls");
    }

    /** getVisualShape: 빈 모양. */
    @Override
    public Shape getVisualShape(int state, BlockLevel level, Pos pos, BlockActor entity) { return Shape.EMPTY; }
}
