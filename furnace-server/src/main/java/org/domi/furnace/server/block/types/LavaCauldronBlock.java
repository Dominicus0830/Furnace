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

    /** entityInside: 내용물에 든 엔티티는 용암에 닿는다(lavaHurt). */
    @Override
    public void entityInside(int state, BlockLevel level, Pos pos, org.domi.furnace.server.entity.Entity entity) {
        if (isEntityInsideContent(state, pos, entity)) entity.lavaHurt();
    }

    /** entityInside 의 플레이어 쪽: 내용물에 든 플레이어는 용암에 닿는다. */
    public void entityInsidePlayer(int state, BlockLevel level, Pos pos, org.domi.furnace.server.entity.Player p) {
        if (isInsideContent(state, pos, p.y(), p.y() + p.bbHeight())) org.domi.furnace.server.entity.PlayerFire.lavaHurt(p, level instanceof org.domi.furnace.server.block.ChunkLevel cl ? cl : null);
    }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return 3; }
}
