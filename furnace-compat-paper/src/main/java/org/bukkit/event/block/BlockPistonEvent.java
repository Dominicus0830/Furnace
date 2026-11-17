package org.bukkit.event.block;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Cancellable;

/** 피스톤 이벤트의 공통 부모. 처리기 목록은 확장·수축이 각자 가진다(이 클래스에는 getHandlerList 가 없다 — Bukkit 도 같다). */
public abstract class BlockPistonEvent extends BlockEvent implements Cancellable {

    private boolean cancelled;
    private final BlockFace direction;

    protected BlockPistonEvent(Block block, BlockFace direction) {
        super(block);
        this.direction = direction;
    }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    /** 끈끈이 피스톤인가(Bukkit 은 블록 종류로 가른다 — 움직이는 피스톤도 끈끈이로 친다). */
    public boolean isSticky() { return block.getType() == Material.STICKY_PISTON || block.getType() == Material.MOVING_PISTON; }

    /** 피스톤이 일하는 방향. 확장은 피스톤이 바라보는 쪽, 블록을 끄는 수축은 그 반대(CraftBukkit/Paper 는 미는 방향을 준다). */
    public BlockFace getDirection() { return direction; }
}
