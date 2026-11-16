package org.bukkit.event.inventory;

import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;

/**
 * 양조대가 양조를 끝내 병을 바꾸기 직전. getResults() 를 고치면 그 아이템이 병 칸 0~2 에 들어간다. 취소하면 병도 재료도 그대로다.
 * Furnace: getContents() 는 이벤트 순간의 읽기 전용 사본이다.
 */
public class BrewEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final BrewerInventory contents;
    private final List<ItemStack> results;
    private final int fuelLevel;
    private boolean cancelled;

    public BrewEvent(Block brewer, BrewerInventory contents, List<ItemStack> results, int fuelLevel) {
        super(brewer);
        this.contents = contents;
        this.results = results;
        this.fuelLevel = fuelLevel;
    }

    public BrewerInventory getContents() { return contents; }

    /** 병 칸 0~2 에 들어갈 결과 — 고칠 수 있는 목록. */
    public List<ItemStack> getResults() { return results; }

    public int getFuelLevel() { return fuelLevel; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
