// 지옥문이 만들어지기 직전 — 불로 틀 안을 점화했을 때. 그 칸의 region 스레드에서 불린다. blocks 는 채워질 지옥문 블록 칸이다.
// 취소하면 지옥문이 생기지 않고 불만 남는다.
package org.domi.furnace.api.event.world;

import java.util.List;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.BlockPos;

public final class PortalCreateEvent extends WorldEvent implements Cancellable {

    public enum Reason { FIRE }

    private final List<BlockPos> blocks;
    private final Reason reason;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public PortalCreateEvent(String world, List<BlockPos> blocks, Reason reason) {
        super(world);
        this.blocks = List.copyOf(blocks);
        this.reason = reason;
    }

    public List<BlockPos> blocks() { return blocks; }

    public Reason reason() { return reason; }
}
