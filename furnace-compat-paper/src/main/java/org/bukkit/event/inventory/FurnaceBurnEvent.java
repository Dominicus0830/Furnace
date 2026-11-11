package org.bukkit.event.inventory;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.inventory.ItemStack;

/** 화로가 연료 하나를 태우기 시작한다. 태우는 시간·불붙음을 바꿀 수 있다. Furnace 서버의 연료 이벤트와 이어진다. */
public class FurnaceBurnEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    private final ItemStack fuel;
    private int burnTime;
    private boolean burning;

    public FurnaceBurnEvent(Block furnace, ItemStack fuel, int burnTime) {
        super(furnace);
        this.fuel = fuel;
        this.burnTime = burnTime;
        this.burning = true;
    }

    public ItemStack getFuel() { return fuel; }

    public int getBurnTime() { return burnTime; }

    public void setBurnTime(int burnTime) { this.burnTime = Math.max(0, burnTime); }

    public boolean isBurning() { return burning; }

    public void setBurning(boolean burning) { this.burning = burning; }
}
