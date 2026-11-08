package org.bukkit.event.inventory;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/** 커서 아이템을 눌러 끌어 여러 칸에 나눠 놓기(놓기 직전, 둘 이상의 칸일 때). */
public class InventoryDragEvent extends InventoryInteractEvent {

    private static final HandlerList handlers = new HandlerList();

    private final DragType type;
    private final Map<Integer, ItemStack> addedItems;
    private final Set<Integer> containerSlots;
    private final ItemStack oldCursor;
    private ItemStack newCursor;

    public InventoryDragEvent(InventoryView what, ItemStack newCursor, ItemStack oldCursor, boolean right, Map<Integer, ItemStack> slots) {
        super(what);
        if (oldCursor == null) throw new IllegalArgumentException("oldCursor");
        Objects.requireNonNull(slots, "slots");
        this.type = right ? DragType.SINGLE : DragType.EVEN;
        this.newCursor = newCursor;
        this.oldCursor = oldCursor;
        this.addedItems = slots;
        Set<Integer> b = new LinkedHashSet<>();
        for (Integer slot : slots.keySet()) b.add(what.convertSlot(slot));
        this.containerSlots = Collections.unmodifiableSet(b);
    }

    /** 창 칸 번호 → 놓인 뒤 그 칸의 아이템. */
    public Map<Integer, ItemStack> getNewItems() { return Collections.unmodifiableMap(addedItems); }

    public Set<Integer> getRawSlots() { return addedItems.keySet(); }

    /** 각 인벤토리 안의 칸 번호. */
    public Set<Integer> getInventorySlots() { return containerSlots; }

    /** 놓은 뒤 커서에 남을 아이템(없으면 null). */
    public ItemStack getCursor() { return newCursor; }

    /** 남을 커서 바꾸기 — Furnace 는 아직 그 값을 서버에 되돌려 쓰지 못한다(UnsupportedFeature). */
    public void setCursor(ItemStack newCursor) {
        throw new org.domi.furnace.compat.paper.UnsupportedFeature("InventoryDragEvent#setCursor", "끌어 나누기 결과의 커서 아이템을 바꾸는 Furnace API 가 없다 — 이벤트를 취소하고 다시 놓게 한다");
    }

    public ItemStack getOldCursor() { return oldCursor.clone(); }

    public DragType getType() { return type; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
