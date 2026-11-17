package org.bukkit.event.block;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** 블록이 이웃 갱신을 받는다. Furnace 서버의 이웃 갱신 이벤트와 이어진다. */
public class BlockPhysicsEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    private final org.domi.furnace.api.event.LazyValue<BlockData> changed;
    private final Block sourceBlock;

    public BlockPhysicsEvent(Block block, BlockData changed) { this(block, changed, block); }

    public BlockPhysicsEvent(Block block, BlockData changed, Block sourceBlock) {
        this(block, org.domi.furnace.api.event.LazyValue.ready(changed), sourceBlock);
    }

    /**
     * 호환 모듈 전용(T-076, Paper 에 없는 생성자) — 갱신받는 칸의 상태를 처리기가 처음 읽을 때 만든다. 결과는 위 생성자에 미리 만든 값을 준 것과 같다.
     * 주의: 플러그인이 소스에서 둘째 인자로 {@code null} 을 직접 적으면 이 생성자와 모호해진다(이미 컴파일된 jar 는 영향 없음).
     */
    public BlockPhysicsEvent(Block block, org.domi.furnace.api.event.LazyValue<BlockData> changed, Block sourceBlock) {
        super(block);
        this.changed = java.util.Objects.requireNonNull(changed, "changed");
        this.sourceBlock = sourceBlock;
    }

    /** 갱신을 일으킨 칸. */
    public Block getSourceBlock() { return sourceBlock; }

    /** 갱신을 받는 블록의 종류(CraftBukkit 은 갱신받는 칸의 상태를 넘긴다). */
    public Material getChangedType() { return changed.get().getMaterial(); }

    public BlockData getChangedBlockData() { return changed.get().clone(); }
}
