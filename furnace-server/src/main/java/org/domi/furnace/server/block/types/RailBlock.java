// 바닐라 RailBlock: 일반 레일(꺾일 수 있다). 신호원이 바뀌고 이을 수 있는 레일이 셋이면 모양을 다시 정한다(갈림길 전환).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public class RailBlock extends BaseRailBlock {

    public RailBlock(int block) { super(block, false); }

    @Override
    protected void updateState(int state, BlockLevel level, Pos pos, int sourceBlock) {
        int d = B.defaultState(sourceBlock);
        if (B.behavior(d).isSignalSource(d) && new RailState(level, pos, state).countPotentialConnections() == 3) updateDir(level, pos, state, false);
    }
}
