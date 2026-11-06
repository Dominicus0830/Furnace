// 이웃 블록이 바뀌어 이 블록이 갱신(neighborChanged·모양 갱신)을 받기 직전 — 그 블록의 region 스레드에서 불린다. 아주 자주 불린다.
// 취소하면 이 블록은 이번 갱신을 무시한다(붙어 있을 수 없는 블록도 떨어지지 않고, 레드스톤 부품도 신호를 다시 보지 않는다).
package org.domi.furnace.api.event.block;

import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;

public final class BlockPhysicsEvent extends BlockEvent implements Cancellable {

    private final BlockData block;
    private final BlockPos sourcePos;
    private final String changedType;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public BlockPhysicsEvent(String world, BlockPos pos, BlockData block, BlockPos sourcePos, String changedType) {
        super(world, pos);
        this.block = block;
        this.sourcePos = sourcePos;
        this.changedType = changedType;
    }

    /** 갱신을 받는 블록. */
    public BlockData block() { return block; }

    /** 갱신을 일으킨 칸. */
    public BlockPos sourcePos() { return sourcePos; }

    /** 갱신을 일으킨 블록 종류(minecraft:redstone_wire 등). */
    public String changedType() { return changedType; }
}
