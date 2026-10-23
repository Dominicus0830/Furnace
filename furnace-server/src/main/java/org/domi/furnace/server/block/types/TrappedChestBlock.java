// 바닐라 TrappedChestBlock: 덫 상자. 상자와 같고, 여는 사람 수(최대 15)만큼 신호를 낸다(아래로는 직접 신호).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;

public class TrappedChestBlock extends ChestBlock {

    public TrappedChestBlock(int block) { super(block); }

    @Override
    protected String openChestStat() { return "minecraft:trigger_trapped_chest"; }

    @Override
    public boolean isSignalSource(int state) { return true; }

    /** ChestBlockEntity.getOpenCount: 그 칸 상자를 열어 둔 사람 수. */
    private static int openCount(BlockLevel level, Pos pos) {
        return level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.ContainerBlockEntity c ? c.openCount : 0;
    }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) { return Math.clamp(openCount(level, pos), 0, 15); }

    @Override
    public int getDirectSignal(int state, BlockLevel level, Pos pos, int dir) { return dir == Dir.UP ? getSignal(state, level, pos, dir) : 0; }
}
