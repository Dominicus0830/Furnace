// 플레이어가 보는 창 — 위쪽 인벤토리(플러그인 메뉴면 CompatMenuInventory, 아니면 SnapshotInventory)와 아래쪽 플레이어 인벤토리.
// 칸 번호 변환·칸 종류는 CraftBukkit 의 창 규칙과 같다(위쪽 칸 다음에 플레이어 주 인벤토리 27칸, 그다음 핫바 9칸. 플레이어 인벤토리 창은
// 0 결과·1..4 제작·5..8 방어구·9..35 주·36..44 핫바·45 왼손).
package org.domi.furnace.compat.paper;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public final class CompatInventoryView implements InventoryView {

    private final CraftPlayer player;
    private final Inventory top;
    private final InventoryType type;
    private final String title;
    /** 이벤트 시점의 커서(빈 것은 EMPTY). 이벤트 밖에서 만든 창은 null — 커서를 모른다. */
    private final org.domi.furnace.api.item.ItemStack cursor;

    CompatInventoryView(CraftPlayer player, Inventory top, InventoryType type, String title, org.domi.furnace.api.item.ItemStack cursor) {
        this.player = player;
        this.top = top;
        this.type = type;
        this.title = title;
        this.cursor = cursor;
    }

    @Override
    public Inventory getTopInventory() { return top; }

    @Override
    public Inventory getBottomInventory() { return player.getInventory(); }

    @Override
    public HumanEntity getPlayer() { return player; }

    @Override
    public InventoryType getType() { return type; }

    @Override
    public void setItem(int slot, ItemStack item) {
        Inventory inv = getInventory(slot);
        if (inv != null) inv.setItem(convertSlot(slot), item);
        else if (item != null) throw new UnsupportedFeature("InventoryView#setItem(OUTSIDE)", "창 밖 칸에 두면 Bukkit 은 아이템을 떨어뜨린다 — Furnace 는 아직 그 자리 떨어뜨리기 API 가 없다");
    }

    @Override
    public ItemStack getItem(int slot) {
        Inventory inv = getInventory(slot);
        return inv == null ? null : inv.getItem(convertSlot(slot));
    }

    @Override
    public void setCursor(ItemStack item) {
        throw new UnsupportedFeature("InventoryView#setCursor", "커서 아이템을 바꾸는 Furnace API 가 없다");
    }

    /** 커서 아이템(비었으면 AIR). 이벤트 밖에서 얻은 창은 UnsupportedFeature. */
    @Override
    public ItemStack getCursor() {
        if (cursor == null) throw new UnsupportedFeature("InventoryView#getCursor", "커서 아이템은 인벤토리 이벤트 안에서만 알 수 있다");
        return CompatItems.copyOf(cursor);
    }

    @Override
    public Inventory getInventory(int rawSlot) {
        if (rawSlot == OUTSIDE || rawSlot == -1) return null;
        if (rawSlot < 0) throw new IllegalArgumentException("Negative, non outside slot " + rawSlot);
        if (rawSlot >= countSlots()) throw new IllegalArgumentException("Slot " + rawSlot + " greater than inventory slot count");
        return rawSlot < top.getSize() ? top : getBottomInventory();
    }

    @Override
    public int convertSlot(int rawSlot) {
        int numInTop = top.getSize();
        if (rawSlot < numInTop) return rawSlot;
        int slot = rawSlot - numInTop;
        if (type == InventoryType.CRAFTING || type == InventoryType.CREATIVE) {
            if (slot < 4) return 39 - slot;   // 방어구(머리부터)
            if (slot > 39) return slot;       // 왼손(40)
            slot -= 4;
        }
        return slot >= 27 ? slot - 27 : slot + 9;   // 핫바는 창 끝에 있다
    }

    @Override
    public SlotType getSlotType(int slot) {
        if (slot < 0) return SlotType.OUTSIDE;
        if (slot < top.getSize()) {
            return switch (type) {
                case BLAST_FURNACE, FURNACE, SMOKER -> slot == 2 ? SlotType.RESULT : slot == 1 ? SlotType.FUEL : SlotType.CRAFTING;
                case BREWING -> slot == 3 ? SlotType.FUEL : SlotType.CRAFTING;
                case ENCHANTING, BEACON -> SlotType.CRAFTING;
                case WORKBENCH, CRAFTING -> slot == 0 ? SlotType.RESULT : SlotType.CRAFTING;
                case ANVIL, CARTOGRAPHY, GRINDSTONE, MERCHANT -> slot == 2 ? SlotType.RESULT : SlotType.CRAFTING;
                case STONECUTTER -> slot == 1 ? SlotType.RESULT : SlotType.CRAFTING;
                case LOOM, SMITHING, SMITHING_NEW -> slot == 3 ? SlotType.RESULT : SlotType.CRAFTING;
                default -> SlotType.CONTAINER;
            };
        }
        if (type == InventoryType.CRAFTING) {
            if (slot < 9) return SlotType.ARMOR;
            if (slot > 35) return SlotType.QUICKBAR;
            return SlotType.CONTAINER;
        }
        return slot >= countSlots() - (9 + 4 + 1) ? SlotType.QUICKBAR : SlotType.CONTAINER;
    }

    @Override
    public void open() {
        player.openInventory(top);
    }

    @Override
    public void close() {
        player.closeInventory();
    }

    @Override
    public int countSlots() { return top.getSize() + getBottomInventory().getSize(); }

    @Override
    public String getTitle() {
        return top instanceof CompatMenuInventory m ? Compat.legacy(m.menu().title()) : title;
    }

    @Override
    public String getOriginalTitle() { return top instanceof CompatMenuInventory m ? m.title() : title; }

    @Override
    public void setTitle(String title) {
        if (top instanceof CompatMenuInventory m) {
            m.menu().setTitle(Compat.text(title == null ? "" : title));
            return;
        }
        throw new UnsupportedFeature("InventoryView#setTitle", "플러그인이 만든 인벤토리(createInventory)의 창만 제목을 바꿀 수 있다");
    }
}
