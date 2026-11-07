// Furnace 플레이어 인벤토리 위의 Bukkit PlayerInventory: 0~8 단축 칸, 9~35 가방, 36 신발·37 바지·38 갑옷·39 투구, 40 왼손.
package org.domi.furnace.compat.paper;

import java.util.Objects;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

final class CompatPlayerInventory extends CompatInventory implements PlayerInventory {

    private static final int BOOTS = 36, LEGGINGS = 37, CHESTPLATE = 38, HELMET = 39, OFF_HAND = 40, STORAGE = 36;

    private final CompatPlayer player;

    CompatPlayerInventory(CompatPlayer player) {
        super(player.handle().inventory(), InventoryType.PLAYER, () -> player);
        this.player = player;
    }

    @Override
    int storageSize() { return STORAGE; }

    /** 예약돼 아직 적용되지 않은 단축 칸 선택(없으면 -1). */
    private volatile int pendingHeld = -1;

    private int held() {
        int p = pendingHeld;
        return p >= 0 ? p : player.handle().heldSlot();
    }

    @Override
    public ItemStack[] getArmorContents() {
        ItemStack[] all = getContents();
        return new ItemStack[] {all[BOOTS], all[LEGGINGS], all[CHESTPLATE], all[HELMET]};
    }

    @Override
    public void setArmorContents(ItemStack[] items) {
        Objects.requireNonNull(items, "items");
        if (items.length > 4) throw new IllegalArgumentException("Invalid inventory size (" + items.length + "); expected 4 or less");
        for (int i = 0; i < 4; i++) setItem(BOOTS + i, i < items.length ? items[i] : null);
    }

    @Override
    public ItemStack[] getExtraContents() { return new ItemStack[] {getItem(OFF_HAND)}; }

    @Override
    public void setExtraContents(ItemStack[] items) {
        Objects.requireNonNull(items, "items");
        if (items.length > 1) throw new IllegalArgumentException("Invalid inventory size (" + items.length + "); expected 1 or less");
        setItem(OFF_HAND, items.length == 0 ? null : items[0]);
    }

    @Override
    public ItemStack getHelmet() { return getItem(HELMET); }

    @Override
    public void setHelmet(ItemStack helmet) { setItem(HELMET, helmet); }

    @Override
    public ItemStack getChestplate() { return getItem(CHESTPLATE); }

    @Override
    public void setChestplate(ItemStack chestplate) { setItem(CHESTPLATE, chestplate); }

    @Override
    public ItemStack getLeggings() { return getItem(LEGGINGS); }

    @Override
    public void setLeggings(ItemStack leggings) { setItem(LEGGINGS, leggings); }

    @Override
    public ItemStack getBoots() { return getItem(BOOTS); }

    @Override
    public void setBoots(ItemStack boots) { setItem(BOOTS, boots); }

    /** 손 아이템은 비어 있어도 null 이 아니라 공기 스택이다(Bukkit 과 같다). */
    @Override
    public ItemStack getItemInMainHand() { return new ItemStack(mirror(held())); }

    @Override
    public void setItemInMainHand(ItemStack item) { setItem(held(), item); }

    @Override
    public ItemStack getItemInOffHand() { return new ItemStack(mirror(OFF_HAND)); }

    @Override
    public void setItemInOffHand(ItemStack item) { setItem(OFF_HAND, item); }

    @Override
    public ItemStack getItemInHand() { return getItemInMainHand(); }

    @Override
    public void setItemInHand(ItemStack stack) { setItemInMainHand(stack); }

    private int slotOf(EquipmentSlot slot) {
        return switch (Objects.requireNonNull(slot, "slot")) {
            case HAND -> held();
            case OFF_HAND -> OFF_HAND;
            case FEET -> BOOTS;
            case LEGS -> LEGGINGS;
            case CHEST -> CHESTPLATE;
            case HEAD -> HELMET;
        };
    }

    @Override
    public ItemStack getItem(EquipmentSlot slot) {
        int i = slotOf(slot);
        return slot == EquipmentSlot.HAND || slot == EquipmentSlot.OFF_HAND ? new ItemStack(mirror(i)) : getItem(i);
    }

    @Override
    public void setItem(EquipmentSlot slot, ItemStack item) { setItem(slotOf(slot), item); }

    @Override
    public int getHeldItemSlot() { return held(); }

    @Override
    public void setHeldItemSlot(int slot) {
        if (slot < 0 || slot > 8) throw new IllegalArgumentException("Slot is not between 0 and 8 inclusive");
        var f = player.handle().setHeldSlot(slot);
        if (!f.isDone()) {
            pendingHeld = slot;
            f.whenComplete((v, e) -> {
                if (pendingHeld == slot) pendingHeld = -1;
            });
        }
        Owned.write(f, "PlayerInventory#setHeldItemSlot");
    }

    @Override
    public HumanEntity getHolder() { return player; }
}
