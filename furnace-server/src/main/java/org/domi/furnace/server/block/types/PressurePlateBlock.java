// 바닐라 PressurePlateBlock: 나무·돌 압력판(켜짐/꺼짐). 나무는 모든 엔티티, 돌은 생물만 감지.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class PressurePlateBlock extends BasePressurePlateBlock {

    private static final Prop POWERED = B.prop("powered");

    public PressurePlateBlock(int block) { super(block); }

    @Override
    protected int signalForState(int state) { return POWERED.is(state) ? 15 : 0; }

    @Override
    protected int setSignalForState(int state, int signal) { return POWERED.with(state, signal > 0); }

    @Override
    protected int signalStrength(BlockLevel level, Pos pos) {
        return countOnPlate(level, pos, !type.plateSensitivityEverything()) > 0 ? 15 : 0;   // EVERYTHING(나무) 은 모든 엔티티, MOBS(돌) 는 생물
    }
}
