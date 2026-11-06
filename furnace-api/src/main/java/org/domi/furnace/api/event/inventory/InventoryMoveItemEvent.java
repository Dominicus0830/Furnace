// 깔때기(블록·광산 수레)가 용기에서 용기로 아이템을 옮기기 직전 — 깔때기의 region 스레드에서 불린다.
// 취소하면 옮기지 않고, 블록 깔때기는 8 틱 쉰다. setItem 으로 옮길 아이템을 바꿀 수 있다(꺼낸 원래 아이템은 다른 걸로 바뀌어도 빠진다 — Bukkit 과 같다).
// source·destination 은 이벤트 시점 스냅샷(type 은 블록 엔티티·엔티티 종류, 꺼낸 한 개는 source 에서 이미 빠져 있다).
package org.domi.furnace.api.event.inventory;

import java.util.Objects;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.inventory.InventorySnapshot;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.world.Location;

public final class InventoryMoveItemEvent extends Event implements Cancellable {

    private final InventorySnapshot source;
    private final Location sourceLocation;
    private final InventorySnapshot destination;
    private final Location destinationLocation;
    private final boolean initiatorIsSource;
    private ItemStack item;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public InventoryMoveItemEvent(InventorySnapshot source, Location sourceLocation, InventorySnapshot destination, Location destinationLocation,
                                  ItemStack item, boolean initiatorIsSource) {
        this.source = source;
        this.sourceLocation = sourceLocation;
        this.destination = destination;
        this.destinationLocation = destinationLocation;
        this.item = item;
        this.initiatorIsSource = initiatorIsSource;
    }

    public InventorySnapshot source() { return source; }

    /** 꺼내는 용기를 찾은 자리(블록이면 칸 가운데). */
    public Location sourceLocation() { return sourceLocation; }

    public InventorySnapshot destination() { return destination; }

    public Location destinationLocation() { return destinationLocation; }

    /** 옮길 아이템. */
    public ItemStack item() { return item; }

    public void setItem(ItemStack item) { this.item = Objects.requireNonNull(item, "item"); }

    /** true = 꺼내는 쪽 깔때기가 밀어 넣음, false = 받는 쪽 깔때기가 빨아들임. */
    public boolean initiatorIsSource() { return initiatorIsSource; }

    /** 일을 벌인 깔때기 쪽 용기. */
    public InventorySnapshot initiator() { return initiatorIsSource ? source : destination; }
}
