// 양조대가 연료 칸의 블레이즈 가루를 연료로 쓰기 직전(연료가 0 이고 연료 칸에 블레이즈 가루가 있을 때) — 양조대의 region 스레드에서 불린다.
// 취소하면 연료로 쓰지 않는다. fuelPower 는 채울 연료 횟수(기본 20), consuming 이 false 면 블레이즈 가루를 줄이지 않는다.
package org.domi.furnace.api.event.inventory;

import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.block.BlockEvent;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.world.BlockPos;

public final class BrewingStandFuelEvent extends BlockEvent implements Cancellable {

    private final ItemStack fuel;
    private int fuelPower;
    private boolean consuming = true;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public BrewingStandFuelEvent(String world, BlockPos pos, ItemStack fuel, int fuelPower) {
        super(world, pos);
        this.fuel = fuel;
        this.fuelPower = fuelPower;
    }

    /** 연료 칸의 아이템. */
    public ItemStack fuel() { return fuel; }

    public int fuelPower() { return fuelPower; }

    public void setFuelPower(int fuelPower) { this.fuelPower = fuelPower; }

    public boolean isConsuming() { return consuming; }

    public void setConsuming(boolean consuming) { this.consuming = consuming; }
}
