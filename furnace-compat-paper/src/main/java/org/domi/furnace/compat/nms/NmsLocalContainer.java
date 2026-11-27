package org.domi.furnace.compat.nms;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** 서버와 이어지지 않은 제 칸의 컨테이너(바닐라 생성자로 만든 인벤토리 창의 제작 칸). 스택 객체를 그대로 든다. */
public final class NmsLocalContainer implements Container {

    private final ItemStack[] slots;

    private final org.bukkit.inventory.InventoryHolder owner;
    private final boolean hasOwner;

    public NmsLocalContainer(int size) { this(size, null, false); }

    /** 호환 모듈 전용: Bukkit 인벤토리의 복사본으로 쓰는 컨테이너(getOwner 는 그 인벤토리의 주인, 없으면 null). */
    public NmsLocalContainer(int size, org.bukkit.inventory.InventoryHolder owner) { this(size, owner, true); }

    private NmsLocalContainer(int size, org.bukkit.inventory.InventoryHolder owner, boolean hasOwner) {
        slots = new ItemStack[size];
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
        this.owner = owner;
        this.hasOwner = hasOwner;
    }

    @Override
    public int getContainerSize() { return slots.length; }

    @Override
    public synchronized boolean isEmpty() {
        for (ItemStack s : slots) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public synchronized ItemStack getItem(int slot) { return slot >= 0 && slot < slots.length ? slots[slot] : ItemStack.EMPTY; }

    @Override
    public synchronized ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= slots.length || amount <= 0 || slots[slot].isEmpty()) return ItemStack.EMPTY;
        return slots[slot].split(amount);
    }

    @Override
    public synchronized ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= slots.length) return ItemStack.EMPTY;
        ItemStack s = slots[slot];
        slots[slot] = ItemStack.EMPTY;
        return s;
    }

    @Override
    public synchronized void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < slots.length) slots[slot] = java.util.Objects.requireNonNull(stack, "stack");
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public synchronized void clearContent() { java.util.Arrays.fill(slots, ItemStack.EMPTY); }

    @Override
    public java.util.List<ItemStack> getContents() {
        java.util.List<ItemStack> out = new java.util.ArrayList<>(getContainerSize());
        for (int k = 0; k < getContainerSize(); k++) out.add(getItem(k));
        return out;
    }

    @Override
    public int getMaxStackSize() { return 99; }

    @Override
    public void setMaxStackSize(int size) { throw bukkitSide("setMaxStackSize"); }

    @Override
    public org.bukkit.Location getLocation() { throw bukkitSide("getLocation"); }

    @Override
    public org.bukkit.inventory.InventoryHolder getOwner() {
        if (!hasOwner) throw bukkitSide("getOwner");
        return owner;
    }

    @Override
    public java.util.List<org.bukkit.entity.HumanEntity> getViewers() { throw bukkitSide("getViewers"); }

    @Override
    public void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity who) { throw bukkitSide("onOpen"); }

    @Override
    public void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity who) { throw bukkitSide("onClose"); }

    private static org.domi.furnace.compat.nms.UnsupportedNmsCall bukkitSide(String what) {
        return new org.domi.furnace.compat.nms.UnsupportedNmsCall("Container#" + what, "the Bukkit-side view of this container is not part of the shim (use the Bukkit Inventory)");
    }
}
