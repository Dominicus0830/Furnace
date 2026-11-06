// 엔티티가 아닌 블록이 일으킨 폭발(다른 차원의 침대, 오버월드 리스폰 정박기 등)이 블록을 부수기 직전 — 폭발 region 스레드에서 불린다.
// pos 는 폭발 중심 칸, blockType 은 터지기 전 블록. 나머지 규칙은 EntityExplodeEvent 와 같다.
package org.domi.furnace.api.event.block;

import java.util.List;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.BlockPos;

public final class BlockExplodeEvent extends BlockEvent implements Cancellable {

    private final String blockType;
    private final List<BlockPos> blocks;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public BlockExplodeEvent(String world, BlockPos pos, String blockType, List<BlockPos> blocks) {
        super(world, pos);
        this.blockType = blockType;
        this.blocks = blocks;
    }

    /** 터진 블록 종류(minecraft:red_bed 등, 이미 사라졌으면 minecraft:air). */
    public String blockType() { return blockType; }

    public List<BlockPos> blocks() { return blocks; }
}
