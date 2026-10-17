// 바닐라 DeadBushBlock: 마른 덤불. #dead_bush_may_place_on 위에서 산다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public class DeadBushBlock extends BushBlock {

    public DeadBushBlock(int block) { super(block); }

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) { return B.isTag(state, "minecraft:dead_bush_may_place_on"); }
}
