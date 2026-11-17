package org.bukkit.event.inventory;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.inventory.ItemStack;

/** 양조대가 연료 칸의 블레이즈 가루를 연료로 쓰기 직전. */
public class BrewingStandFuelEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final ItemStack fuel;
    private int fuelPower;
    private boolean cancelled;
    private boolean consuming = true;

    public BrewingStandFuelEvent(Block brewingStand, ItemStack fuel, int fuelPower) {
        super(brewingStand);
        this.fuel = fuel;
        this.fuelPower = fuelPower;
    }

    public ItemStack getFuel() { return fuel; }

    /** 채울 연료 횟수(기본 20). */
    public int getFuelPower() { return fuelPower; }

    public void setFuelPower(int fuelPower) { this.fuelPower = fuelPower; }

    public boolean isConsuming() { return consuming; }

    public void setConsuming(boolean consuming) { this.consuming = consuming; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
