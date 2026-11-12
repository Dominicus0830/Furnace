// 양조대가 재료 하나로 병을 바꾸기 직전(양조 시간이 끝났을 때) — 양조대의 region 스레드에서 불린다. 취소하면 병도 재료도 그대로이고 양조는 멈춘다(연료는 이미 썼다).
// contents() 는 양조대 다섯 칸 스냅샷(0~2 병, 3 재료, 4 연료). results() 는 양조 뒤 병 세 칸에 들어갈 아이템(바꿀 수 있다 — 그대로 칸에 들어간다,
// 세 개보다 짧으면 남는 칸은 비고 EMPTY 는 빈 칸).
package org.domi.furnace.api.event.inventory;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.block.BlockEvent;
import org.domi.furnace.api.inventory.InventorySnapshot;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.world.BlockPos;

public final class BrewEvent extends BlockEvent implements Cancellable {

    private final InventorySnapshot contents;
    private final List<ItemStack> results;
    private final int fuelLevel;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    /** results 를 모를 때: 병 칸 그대로(바꾸지 않는 양조로 본다). */
    public BrewEvent(String world, BlockPos pos, InventorySnapshot contents, int fuelLevel) {
        this(world, pos, contents, contents.contents().subList(0, Math.min(3, contents.size())), fuelLevel);
    }

    public BrewEvent(String world, BlockPos pos, InventorySnapshot contents, List<ItemStack> results, int fuelLevel) {
        super(world, pos);
        this.contents = contents;
        this.results = new ArrayList<>(results);
        this.fuelLevel = fuelLevel;
    }

    /** 양조 뒤 병 칸 0~2 에 들어갈 아이템 — 고칠 수 있는 목록. */
    public List<ItemStack> results() { return results; }

    public InventorySnapshot contents() { return contents; }

    /** 남은 연료 횟수(이번 양조에 쓴 것을 뺀 값). */
    public int fuelLevel() { return fuelLevel; }
}
