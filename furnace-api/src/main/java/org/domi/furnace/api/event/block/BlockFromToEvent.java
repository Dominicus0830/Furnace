// 물·용암이 옆(또는 아래) 칸으로 흘러 들어가기 직전 — 흐르는 칸의 region 스레드에서 불린다. pos 는 흐르는 칸, toPos 는 들어갈 칸.
// 취소하면 그 칸으로 흐르지 않는다(아래로 흐르기가 취소되면 그 틱에는 옆으로도 흐르지 않는다).
package org.domi.furnace.api.event.block;

import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.BlockPos;

public final class BlockFromToEvent extends BlockEvent implements Cancellable {

    private final String blockType;
    private final BlockPos toPos;
    private final String face;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public BlockFromToEvent(String world, BlockPos pos, String blockType, BlockPos toPos, String face) {
        super(world, pos);
        this.blockType = blockType;
        this.toPos = toPos;
        this.face = face;
    }

    /** 흐르는 블록(minecraft:water, minecraft:lava, 물에 잠긴 블록이면 그 블록). */
    public String blockType() { return blockType; }

    public BlockPos toPos() { return toPos; }

    /** 흐르는 쪽(down, north, south, west, east). */
    public String face() { return face; }
}
