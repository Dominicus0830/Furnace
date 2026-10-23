// 바닐라 NetherSproutsBlock: 네더 새싹. 나일리움·영혼 흙·흙류 위.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public class NetherSproutsBlock extends BushBlock {

    private static final int SOUL_SOIL = B.id("soul_soil");

    public NetherSproutsBlock(int block) { super(block); }

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) {
        return B.isTag(state, "minecraft:nylium") || B.is(state, SOUL_SOIL) || super.mayPlaceOn(state, level, pos);
    }
}
