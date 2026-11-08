// 플러그인 메뉴가 아닌 창(상자·화로·제작대·플레이어 2×2 제작 칸)의 위쪽 칸 — 이벤트가 가진 그 순간의 내용을 읽기만 한다.
// Furnace 이벤트는 그 창이 어느 블록의 것인지 알리지 않아 쓰기는 UnsupportedFeature, getHolder() 는 null 이다. 이벤트 밖(getOpenInventory)에서는
// 내용도 모르므로 읽기도 UnsupportedFeature 다.
package org.domi.furnace.compat.paper;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.event.inventory.InventoryType;

final class SnapshotInventory extends CompatInventory {

    SnapshotInventory(InventoryType type, List<org.domi.furnace.api.item.ItemStack> contents, int size, String key) {
        super(new Slots(key, contents, size), type, () -> null);
    }

    private record Slots(String type, List<org.domi.furnace.api.item.ItemStack> items, int size) implements org.domi.furnace.api.inventory.Inventory {
        private UnsupportedFeature unknown() {
            return new UnsupportedFeature("Inventory 읽기(" + type + ")", "이벤트 밖에서는 플러그인 메뉴가 아닌 창의 내용을 알 수 없다 — 인벤토리 이벤트 안에서 읽는다");
        }

        @Override
        public CompletableFuture<org.domi.furnace.api.item.ItemStack> get(int slot) {
            if (items == null) return CompletableFuture.failedFuture(unknown());
            if (slot < 0 || slot >= size) return CompletableFuture.failedFuture(new IndexOutOfBoundsException("Index " + slot + " out of bounds for length " + size));
            return CompletableFuture.completedFuture(slot < items.size() ? items.get(slot) : org.domi.furnace.api.item.ItemStack.EMPTY);
        }

        @Override
        public CompletableFuture<Void> set(int slot, org.domi.furnace.api.item.ItemStack item) {
            return CompletableFuture.failedFuture(new UnsupportedFeature("Inventory#setItem(" + type + ")",
                    "Furnace 인벤토리 이벤트는 그 창의 블록·엔티티를 알리지 않아 위쪽 칸에 쓸 수 없다 — 클릭을 취소하거나 Furnace 블록 API 로 쓴다"));
        }

        @Override
        public CompletableFuture<List<org.domi.furnace.api.item.ItemStack>> contents() {
            if (items == null) return CompletableFuture.failedFuture(unknown());
            List<org.domi.furnace.api.item.ItemStack> out = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++) out.add(i < items.size() ? items.get(i) : org.domi.furnace.api.item.ItemStack.EMPTY);
            return CompletableFuture.completedFuture(out);
        }
    }
}
