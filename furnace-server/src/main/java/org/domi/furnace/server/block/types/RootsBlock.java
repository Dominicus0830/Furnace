// 바닐라 RootsBlock: 진홍·뒤틀린 뿌리. 흙류·경작지에 더해 #nylium·영혼 흙 위에서도 산다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public class RootsBlock extends BushBlock {

    private static final int SOUL_SOIL = B.id("soul_soil");

    public RootsBlock(int block) { super(block); }

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) {
        return B.isTag(state, "minecraft:nylium") || B.is(state, SOUL_SOIL) || super.mayPlaceOn(state, level, pos);
    }
}
