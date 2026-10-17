// 바닐라 SlabBlock: 반 블록(위·아래·겹침). 같은 반 블록을 맞는 쪽에 대면 겹친다. 겹친 반 블록은 물에 잠기지 않는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class SlabBlock extends BlockBehavior {

    static final Prop TYPE = B.prop("type");

    public SlabBlock(int block) { super(block); }

    public static boolean isDouble(int state) { return "double".equals(TYPE.valueName(state)); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        Pos pos = ctx.clickedPos();
        int existing = ctx.level().getBlockState(pos);
        if (B.is(existing, block)) return WATERLOGGED.with(TYPE.with(existing, "double"), false);
        int s = WATERLOGGED.with(TYPE.with(defaultState(), "bottom"), B.waterAt(ctx));
        int face = ctx.clickedFace();
        return face != Dir.DOWN && (face == Dir.UP || !(ctx.clickY() - pos.y() > 0.5)) ? s : TYPE.with(s, "top");
    }

    @Override
    public boolean canBeReplaced(int state, PlaceContext ctx) {
        String type = TYPE.valueName(state);
        if ("double".equals(type) || !ctx.holding(block)) return false;
        if (ctx.replacingClickedOnBlock()) {
            boolean upper = ctx.clickY() - ctx.clickedPos().y() > 0.5;
            int face = ctx.clickedFace();
            return "bottom".equals(type) ? face == Dir.UP || upper && Dir.horizontal(face) : face == Dir.DOWN || !upper && Dir.horizontal(face);
        }
        return true;
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
