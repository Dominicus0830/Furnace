// 바닐라 CopperBulbBlock: 신호가 새로 들어올 때마다 켜짐(lit)을 뒤집는다. 비교기는 켜져 있으면 15.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class CopperBulbBlock extends BlockBehavior {

    private static final Prop POWERED = B.prop("powered"), LIT = B.prop("lit");

    public CopperBulbBlock(int block) { super(block); }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (B.block(oldState) != B.block(state)) checkAndFlip(state, level, pos);
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        checkAndFlip(state, level, pos);
    }

    public void checkAndFlip(int state, BlockLevel level, Pos pos) {
        boolean signal = level.hasNeighborSignal(pos);
        if (signal != POWERED.is(state)) {
            int s = state;
            if (!POWERED.is(state)) {
                s = LIT.cycle(s);
                level.playSound(null, pos, LIT.is(s) ? "minecraft:block.copper_bulb.turn_on" : "minecraft:block.copper_bulb.turn_off", "blocks", 1.0F, 1.0F);
            }
            level.setBlock(pos, POWERED.with(s, signal), 3);
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return LIT.is(level.getBlockState(pos)) ? 15 : 0; }
}
