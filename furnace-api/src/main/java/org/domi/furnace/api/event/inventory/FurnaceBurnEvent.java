// 화로(화로·용광로·훈연기)가 연료 하나를 태우기 시작하기 직전 — 화로의 region 스레드에서 불린다.
// 취소하면 연료를 쓰지 않고 불도 켜지지 않는다. burnTime 을 바꿀 수 있고, setBurning(false) 면 불은 켜지되 연료는 줄지 않는다.
package org.domi.furnace.api.event.inventory;

import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.block.BlockEvent;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.world.BlockPos;

public final class FurnaceBurnEvent extends BlockEvent implements Cancellable {

    private final String blockType;
    private final ItemStack fuel;
    private int burnTime;
    private boolean burning = true;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public FurnaceBurnEvent(String world, BlockPos pos, String blockType, ItemStack fuel, int burnTime) {
        super(world, pos);
        this.blockType = blockType;
        this.fuel = fuel;
        this.burnTime = burnTime;
    }

    /** minecraft:furnace, minecraft:blast_furnace, minecraft:smoker. */
    public String blockType() { return blockType; }

    public ItemStack fuel() { return fuel; }

    /** 탈 시간(틱). */
    public int burnTime() { return burnTime; }

    /** 0 이하면 불이 켜지지 않는다(연료도 줄지 않는다). */
    public void setBurnTime(int burnTime) { this.burnTime = burnTime; }

    /** 연료를 하나 쓸지. */
    public boolean isBurning() { return burning; }

    public void setBurning(boolean burning) { this.burning = burning; }
}
