// 바닐라 LavaCauldronBlock: 용암 가마솥. 늘 가득 차 있고 비교기 신호 3.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public class LavaCauldronBlock extends AbstractCauldronBlock {

    public LavaCauldronBlock(int block) { super(block, CauldronInteraction.LAVA); }

    @Override
    protected double getContentHeight(int state) { return 0.9375; }

    @Override
    public boolean isFull(int state) { return true; }

    // TODO(엔티티): entityInside — 안에 든 엔티티를 용암에 닿게(lavaHurt).

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return 3; }
}
