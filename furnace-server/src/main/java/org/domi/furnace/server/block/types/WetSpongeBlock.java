// 바닐라 WetSpongeBlock: 젖은 스펀지. 매우 뜨거운 차원(네더)에 놓이면 바로 마른 스펀지가 된다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public class WetSpongeBlock extends BlockBehavior {

    private static final int SPONGE = B.id("sponge");

    public WetSpongeBlock(int block) { super(block); }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (level.ultraWarm()) {
            level.setBlock(pos, B.defaultState(SPONGE), 3);
            level.levelEvent(null, 2009, pos, 0);
            level.playSound(null, pos, "minecraft:block.wet_sponge.dries", "blocks", 1.0F, (1.0F + level.random().nextFloat() * 0.2F) * 0.7F);
        }
    }
}
