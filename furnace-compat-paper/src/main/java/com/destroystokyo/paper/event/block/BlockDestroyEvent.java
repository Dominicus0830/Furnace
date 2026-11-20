package com.destroystokyo.paper.event.block;

import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockExpEvent;

/**
 * 서버가 블록을 부수기 직전(Paper — NMS Level#destroyBlock: 받침을 잃은 선인장, 물에 쓸리는 블록, 엔티티·명령·플러그인이 부수는 블록 등).
 * 플레이어가 손으로 부수는 것은 BlockBreakEvent. 취소·효과 블록·효과 끄기·떨굼·경험치를 바꿀 수 있다. Furnace 서버 BlockDestroyEvent 에서 온다(T-224).
 */
public class BlockDestroyEvent extends BlockExpEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final BlockData newState;
    private boolean willDrop;
    private boolean playEffect = true;
    private BlockData effectBlock;
    private boolean cancelled;

    public BlockDestroyEvent(Block block, BlockData newState, BlockData effectBlock, int xp, boolean willDrop) {
        super(block, xp);
        this.newState = newState;
        this.effectBlock = effectBlock;
        this.willDrop = willDrop;
    }

    /** 부서지는 효과(입자·소리)에 쓸 블록. */
    public BlockData getEffectBlock() { return effectBlock; }

    public void setEffectBlock(BlockData effectBlock) { this.effectBlock = effectBlock; }

    /** 부서진 뒤 그 칸(복사본). */
    public BlockData getNewState() { return newState.clone(); }

    public boolean willDrop() { return willDrop; }

    public void setWillDrop(boolean willDrop) { this.willDrop = willDrop; }

    public boolean playEffect() { return playEffect; }

    public void setPlayEffect(boolean playEffect) { this.playEffect = playEffect; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return HANDLER_LIST; }

    public static HandlerList getHandlerList() { return HANDLER_LIST; }
}
