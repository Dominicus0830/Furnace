// 바닐라 BaseEntityBlock: 블록 엔티티를 가진 블록의 바탕. 블록 이벤트를 블록 엔티티에도 넘긴다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public abstract class BaseEntityBlock extends BlockBehavior {

    protected BaseEntityBlock(int block) { super(block); }

    /** triggerEvent: super 를 부른 뒤 그 칸 블록 엔티티의 triggerEvent 결과. */
    @Override
    public boolean triggerEvent(int state, BlockLevel level, Pos pos, int type, int data) {
        super.triggerEvent(state, level, pos, type, data);
        return blockEntityTriggerEvent(level, pos, type, data);
    }

    /** 그 칸 블록 엔티티의 triggerEvent(없으면 false). */
    protected boolean blockEntityTriggerEvent(BlockLevel level, Pos pos, int type, int data) {
        return level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.BlockEntity be && be.triggerEvent(type, data);
    }
}
