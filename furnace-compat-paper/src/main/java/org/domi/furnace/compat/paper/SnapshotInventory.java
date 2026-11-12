// 플러그인 메뉴가 아닌 창(상자·화로·제작대·플레이어 2×2 제작 칸)의 위쪽 칸, 깔때기가 옮기는 용기, 양조대 — 이벤트가 가진 그 순간의 내용을 읽는다.
// Furnace 이벤트는 그 창이 어느 블록의 것인지 알리지 않아 쓰기는 UnsupportedFeature, getHolder() 는 null 이다. 이벤트 밖(getOpenInventory)에서는
// 내용도 모르므로 읽기도 UnsupportedFeature 다. 이벤트가 결과를 되돌려 받는 칸(제작 준비 이벤트의 결과 칸)만 쓸 수 있다 — 쓴 값은 이 사본에 남고
// 다리가 이벤트 뒤에 읽어 Furnace 이벤트로 옮긴다.
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntPredicate;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

class SnapshotInventory extends CompatInventory {

    private static final IntPredicate READ_ONLY = i -> false;

    private final org.bukkit.Location location;

    SnapshotInventory(InventoryType type, List<org.domi.furnace.api.item.ItemStack> contents, int size, String key) {
        this(type, contents, size, key, READ_ONLY, null);
    }

    /** writable: 쓸 수 있는 칸, location: getLocation()(모르면 null). */
    SnapshotInventory(InventoryType type, List<org.domi.furnace.api.item.ItemStack> contents, int size, String key, IntPredicate writable,
                      org.bukkit.Location location) {
        super(new Slots(key, contents == null ? null : new ArrayList<>(contents), size, writable), type, () -> null);
        this.location = location;
    }

    @Override
    public org.bukkit.Location getLocation() { return location == null ? null : location.clone(); }

    /** 이 사본의 칸(쓴 값 포함) — 다리가 되돌려 쓸 때. */
    final org.domi.furnace.api.item.ItemStack furnaceAt(int slot) { return readSlot(slot); }

    /** 빈 칸은 null 인 Bukkit 사본. */
    final ItemStack bukkitAt(int slot) {
        org.domi.furnace.api.item.ItemStack s = readSlot(slot);
        return s.isEmpty() ? null : CompatItems.copyOf(s);
    }

    private static final class Slots implements org.domi.furnace.api.inventory.Inventory {
        private final String type;
        private final List<org.domi.furnace.api.item.ItemStack> items;
        private final int size;
        private final IntPredicate writable;

        Slots(String type, List<org.domi.furnace.api.item.ItemStack> items, int size, IntPredicate writable) {
            this.type = type;
            this.items = items;
            this.size = size;
            this.writable = writable;
        }

        private UnsupportedFeature unknown() {
            return new UnsupportedFeature("Inventory 읽기(" + type + ")", "이벤트 밖에서는 플러그인 메뉴가 아닌 창의 내용을 알 수 없다 — 인벤토리 이벤트 안에서 읽는다");
        }

        @Override
        public String type() { return type; }

        @Override
        public int size() { return size; }

        @Override
        public CompletableFuture<org.domi.furnace.api.item.ItemStack> get(int slot) {
            if (items == null) return CompletableFuture.failedFuture(unknown());
            if (slot < 0 || slot >= size) return CompletableFuture.failedFuture(new IndexOutOfBoundsException("Index " + slot + " out of bounds for length " + size));
            synchronized (items) {
                return CompletableFuture.completedFuture(slot < items.size() ? items.get(slot) : org.domi.furnace.api.item.ItemStack.EMPTY);
            }
        }

        @Override
        public CompletableFuture<Void> set(int slot, org.domi.furnace.api.item.ItemStack item) {
            if (items != null && slot >= 0 && slot < size && writable.test(slot)) {
                synchronized (items) {
                    while (items.size() <= slot) items.add(org.domi.furnace.api.item.ItemStack.EMPTY);
                    items.set(slot, item);
                }
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.failedFuture(new UnsupportedFeature("Inventory#setItem(" + type + ")",
                    "Furnace 인벤토리 이벤트는 그 창의 블록·엔티티를 알리지 않아 이 칸에 쓸 수 없다 — 이벤트를 취소하거나 Furnace 블록 API 로 쓴다"));
        }

        @Override
        public CompletableFuture<List<org.domi.furnace.api.item.ItemStack>> contents() {
            if (items == null) return CompletableFuture.failedFuture(unknown());
            List<org.domi.furnace.api.item.ItemStack> out = new ArrayList<>(size);
            synchronized (items) {
                for (int i = 0; i < size; i++) out.add(i < items.size() ? items.get(i) : org.domi.furnace.api.item.ItemStack.EMPTY);
            }
            return CompletableFuture.completedFuture(out);
        }
    }
}
