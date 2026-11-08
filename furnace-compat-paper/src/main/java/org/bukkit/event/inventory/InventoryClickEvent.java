package org.bukkit.event.inventory;

import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * 창 안 클릭(서버가 클릭을 적용하기 전). 끌어 나누기는 이 이벤트가 아니라 {@link InventoryDragEvent} 로 온다(Paper 와 같다).
 * Furnace: 클릭은 그 플레이어의 연결 스레드에서 처리된다(Bukkit 은 주 스레드).
 */
public class InventoryClickEvent extends InventoryInteractEvent {

    private static final HandlerList handlers = new HandlerList();

    private final ClickType click;
    private final InventoryAction action;
    private final SlotType slotType;
    private final int whichSlot;
    private final int rawSlot;
    private ItemStack current;
    private int hotbarKey = -1;

    public InventoryClickEvent(InventoryView view, SlotType type, int slot, ClickType click, InventoryAction action) {
        super(view);
        this.slotType = type;
        this.rawSlot = slot;
        this.whichSlot = view.convertSlot(slot);
        this.click = click;
        this.action = action;
    }

    public InventoryClickEvent(InventoryView view, SlotType type, int slot, ClickType click, InventoryAction action, int key) {
        this(view, type, slot, click, action);
        this.hotbarKey = key;
    }

    public SlotType getSlotType() { return slotType; }

    /** 커서 아이템(클릭 전). */
    public ItemStack getCursor() { return getView().getCursor(); }

    /** 클릭한 칸의 아이템(창 밖이면 setCurrentItem 으로 정한 값). 빈 칸은 null. */
    public ItemStack getCurrentItem() {
        if (slotType == SlotType.OUTSIDE) return current;
        return getView().getItem(rawSlot);
    }

    public boolean isRightClick() { return click.isRightClick(); }

    public boolean isLeftClick() { return click.isLeftClick(); }

    public boolean isShiftClick() { return click.isShiftClick(); }

    /** 커서 바꾸기 — Furnace 는 아직 못 한다(UnsupportedFeature). */
    @Deprecated(since = "1.5.2")
    public void setCursor(ItemStack stack) { getView().setCursor(stack); }

    public void setCurrentItem(ItemStack stack) {
        if (slotType == SlotType.OUTSIDE) current = stack;
        else getView().setItem(rawSlot, stack);
    }

    /** 클릭한 칸이 든 인벤토리(창 밖이면 null). */
    public Inventory getClickedInventory() { return getView().getInventory(rawSlot); }

    /** 그 인벤토리 안의 칸 번호. */
    public int getSlot() { return whichSlot; }

    /** 창 전체의 칸 번호. */
    public int getRawSlot() { return rawSlot; }

    /** NUMBER_KEY 면 누른 핫바 칸(0..8), SWAP_OFFHAND 면 40, 아니면 -1. */
    public int getHotbarButton() { return hotbarKey; }

    public InventoryAction getAction() { return action; }

    public ClickType getClick() { return click; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
