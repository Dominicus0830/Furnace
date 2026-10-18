// 바닐라 WeightedPressurePlateBlock: 무게 압력판. 엔티티 수/최대 무게 비율로 0~15. 눌림 유지 10틱.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class WeightedPressurePlateBlock extends BasePressurePlateBlock {

    private static final Prop POWER = B.prop("power");

    private final int maxWeight;

    public WeightedPressurePlateBlock(int block) {
        super(block);
        this.maxWeight = org.domi.furnace.server.block.Blocks.get().params(block).get("max_weight").getAsInt();
    }

    @Override
    protected int signalForState(int state) { return POWER.intValue(state); }

    @Override
    protected int setSignalForState(int state, int signal) { return POWER.withInt(state, signal); }

    @Override
    protected int signalStrength(BlockLevel level, Pos pos) {
        int n = Math.min(countOnPlate(level, pos, false), maxWeight);
        if (n <= 0) return 0;
        float f = (float) Math.min(maxWeight, n) / (float) maxWeight;
        return (int) Math.ceil(f * 15.0F);
    }

    @Override
    protected int pressedTime() { return 10; }
}
