// 불이 옆 블록을 태워 없애기(또는 불로 바꾸기) 직전 — 불의 region 스레드에서 불린다. 취소하면 그 블록은 타지 않는다(TNT 도 점화되지 않는다).
package org.domi.furnace.api.event.block;

import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.BlockPos;

public final class BlockBurnEvent extends BlockEvent implements Cancellable {

    private final String blockType;
    private final BlockPos ignitingPos;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public BlockBurnEvent(String world, BlockPos pos, String blockType, BlockPos ignitingPos) {
        super(world, pos);
        this.blockType = blockType;
        this.ignitingPos = ignitingPos;
    }

    /** 탈 블록. */
    public String blockType() { return blockType; }

    /** 태우는 불 칸. */
    public BlockPos ignitingPos() { return ignitingPos; }
}
