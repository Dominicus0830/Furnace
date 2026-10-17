// 바닐라 AttachedStemBlock: 열매에 붙은 줄기. 붙은 쪽 열매가 사라지면 다 자란(나이 7) 줄기로 돌아간다.
package org.domi.furnace.server.block.types;

import com.google.gson.JsonObject;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class AttachedStemBlock extends BushBlock {

    private static final Prop FACING = B.prop("facing"), AGE = B.prop("age");
    private static final int FARMLAND = B.id("farmland");
    private final int fruit, stem;

    public AttachedStemBlock(int block) {
        super(block);
        JsonObject p = Blocks.get().params(block);
        this.fruit = B.id(p.get("fruit").getAsString());
        this.stem = B.id(p.get("stem").getAsString());
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (!B.is(neighborState, fruit) && dir == Dir.byName(FACING.valueName(state))) {
            int s = B.defaultState(stem);
            return AGE.has(s) ? AGE.withInt(s, 7) : s;
        }
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) { return B.is(state, FARMLAND); }
}
