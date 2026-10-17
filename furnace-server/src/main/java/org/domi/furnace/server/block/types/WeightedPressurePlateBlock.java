// 바닐라 WeightedPressurePlateBlock: 무게 압력판. 엔티티 수/최대 무게 비율로 0~15. 눌림 유지 10틱.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class WeightedPressurePlateBlock extends BasePressurePlateBlock {

    private static final Prop POWER = B.prop("power");

    public WeightedPressurePlateBlock(int block) { super(block); }

    @Override
    protected int signalForState(int state) { return POWER.intValue(state); }

    @Override
    protected int setSignalForState(int state, int signal) { return POWER.withInt(state, signal); }

    @Override
    protected int signalStrength(BlockLevel level, Pos pos) { return 0; }   // TODO(엔티티): ceil(min(엔티티 수, max_weight)/max_weight*15)

    @Override
    protected int pressedTime() { return 10; }
}
