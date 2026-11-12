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

public class CompatInventoryView implements InventoryView {

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

    /** Paper: 이 창의 제목(setTitle 로 바꿨으면 그 값). 같은 인벤토리를 보는 다른 사람의 setTitle 은 보이지 않는다. */
    @Override
    public String getTitle() {
        if (!(top instanceof CompatMenuInventory m)) return title;
        org.domi.furnace.api.text.Text shown = player.furnaceHandle().menuTitle(m.menu());
        if (shown == null || shown == m.menu().title()) return m.title();
        String raw = m.retitledRaw(player.getUniqueId(), shown);
        return raw != null ? raw : Compat.legacy(shown);
    }

    @Override
    public String getOriginalTitle() { return top instanceof CompatMenuInventory m ? m.title() : title; }

    /**
     * Paper CraftInventoryView#setTitle: 이 플레이어 창에만 open_screen 을 다시 보낸다 — 인벤토리 제목·다른 보는 사람·다음에 여는 창은 그대로.
     * null 은 IllegalArgumentException("Title cannot be null").
     */
    @Override
    public void setTitle(String title) {
        if (title == null) throw new IllegalArgumentException("Title cannot be null");
        if (top instanceof CompatMenuInventory m) {
            org.domi.furnace.api.text.Text t = Compat.text(title);
            m.retitled(player.getUniqueId(), t, title);   // getTitle 이 서식 문자열을 그대로 돌려주게(Text 를 거치면 §r 이 붙는 등 달라진다)
            player.furnaceHandle().retitleMenu(t);
            return;
        }
        throw new UnsupportedFeature("InventoryView#setTitle", "플러그인이 만든 인벤토리(createInventory)의 창만 제목을 바꿀 수 있다");
    }

    // ------------------------------------------------------------------ 창 종류별 뷰(Paper 1.21.1 org.bukkit.inventory.view)

    /** 모루 창. 비용·이름은 PrepareAnvilEvent 안에서만 안다(state). */
    static final class Anvil extends CompatInventoryView implements org.bukkit.inventory.view.AnvilView {
        private final EventInventories.Anvil top;

        Anvil(CraftPlayer player, EventInventories.Anvil top, String title, org.domi.furnace.api.item.ItemStack cursor) {
            super(player, top, InventoryType.ANVIL, title, cursor);
            this.top = top;
        }

        @Override
        public org.bukkit.inventory.AnvilInventory getTopInventory() { return top; }

        private EventInventories.AnvilState state(String what) {
            var st = top.state();
            if (st == null) throw EventInventories.anvilUnknown(what);
            return st;
        }

        @Override
        public String getRenameText() { return state("AnvilView#getRenameText").renameText; }

        @Override
        public int getRepairItemCountCost() { throw EventInventories.anvilUnknown("AnvilView#getRepairItemCountCost"); }

        @Override
        public int getRepairCost() { return state("AnvilView#getRepairCost").repairCost; }

        @Override
        public int getMaximumRepairCost() { return EventInventories.AnvilState.MAX_REPAIR_COST; }

        @Override
        public void setRepairItemCountCost(int amount) { throw EventInventories.anvilFixed("AnvilView#setRepairItemCountCost"); }

        @Override
        public void setRepairCost(int cost) { state("AnvilView#setRepairCost").repairCost = cost; }

        @Override
        public void setMaximumRepairCost(int levels) { top.setMaximumRepairCost(levels); }

        @Override
        public boolean bypassesEnchantmentLevelRestriction() { return false; }

        @Override
        public void bypassEnchantmentLevelRestriction(boolean bypass) {
            if (bypass) throw EventInventories.anvilFixed("AnvilView#bypassEnchantmentLevelRestriction");
        }
    }

    /** 마법 부여대 창. 제안은 PrepareItemEnchantEvent 안에서만 안다(offers). */
    static final class Enchantment extends CompatInventoryView implements org.bukkit.inventory.view.EnchantmentView {
        private final EventInventories.Enchanting top;
        private final org.bukkit.enchantments.EnchantmentOffer[] offers;

        Enchantment(CraftPlayer player, EventInventories.Enchanting top, String title, org.domi.furnace.api.item.ItemStack cursor,
                    org.bukkit.enchantments.EnchantmentOffer[] offers) {
            super(player, top, InventoryType.ENCHANTING, title, cursor);
            this.top = top;
            this.offers = offers;
        }

        @Override
        public org.bukkit.inventory.EnchantingInventory getTopInventory() { return top; }

        @Override
        public int getEnchantmentSeed() {
            throw new UnsupportedFeature("EnchantmentView#getEnchantmentSeed", "Furnace API 가 플레이어의 마법 부여 씨앗을 알리지 않는다");
        }

        @Override
        public void setEnchantmentSeed(int seed) {
            throw new UnsupportedFeature("EnchantmentView#setEnchantmentSeed", "Furnace API 가 플레이어의 마법 부여 씨앗을 바꾸지 못한다");
        }

        private org.bukkit.enchantments.EnchantmentOffer[] offers(String what) {
            if (offers == null) throw new UnsupportedFeature(what, "제안은 Furnace 가 제안을 정할 때(PrepareItemEnchantEvent)만 알린다");
            return offers;
        }

        /** Paper 처럼 이벤트의 배열 그대로(고치면 이벤트 결과가 바뀐다). */
        @Override
        public org.bukkit.enchantments.EnchantmentOffer[] getOffers() { return offers("EnchantmentView#getOffers"); }

        @Override
        public void setOffers(org.bukkit.enchantments.EnchantmentOffer[] offers) {
            var mine = offers("EnchantmentView#setOffers");
            if (offers.length != 3) throw new IllegalArgumentException("There must be 3 offers given");
            System.arraycopy(offers, 0, mine, 0, 3);
        }
    }
}
