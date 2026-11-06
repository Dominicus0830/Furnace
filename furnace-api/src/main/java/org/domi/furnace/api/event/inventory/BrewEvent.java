// 양조대가 재료 하나로 병을 바꾸기 직전(양조 시간이 끝났을 때) — 양조대의 region 스레드에서 불린다. 취소하면 병도 재료도 그대로이고 양조는 멈춘다(연료는 이미 썼다).
// contents() 는 양조대 다섯 칸 스냅샷(0~2 병, 3 재료, 4 연료).
package org.domi.furnace.api.event.inventory;

import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.block.BlockEvent;
import org.domi.furnace.api.inventory.InventorySnapshot;
import org.domi.furnace.api.world.BlockPos;

public final class BrewEvent extends BlockEvent implements Cancellable {

    private final InventorySnapshot contents;
    private final int fuelLevel;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public BrewEvent(String world, BlockPos pos, InventorySnapshot contents, int fuelLevel) {
        super(world, pos);
        this.contents = contents;
        this.fuelLevel = fuelLevel;
    }

    public InventorySnapshot contents() { return contents; }

    /** 남은 연료 횟수(이번 양조에 쓴 것을 뺀 값). */
    public int fuelLevel() { return fuelLevel; }
}
