package org.domi.furnace.compat.paper;

import org.bukkit.craftbukkit.inventory.CraftInventory;

/** T-264: Paper 의 Craft 인벤토리 클래스에는 없는 Furnace 쪽 도움 형식(craftbukkit 꾸러미에 Paper 에 없는 클래스를 두지 않으려고 여기 모은다). */
public final class InventoryParts {
    private InventoryParts() {}

    /** 아직 반영 안 된 쓰기(값·순번). */
    public record Pending(org.domi.furnace.api.item.ItemStack stack, long seq) {}

    /** 메뉴 칸 제목을 바꾼 기록(Text·원 문자열). */
    public record RetitledAs(org.domi.furnace.api.text.Text text, String raw) {}

    public static final class Mirror implements ItemSlot {
        private final CraftInventory inv;
        private final int index;
        private final Epoch epoch;

        public Mirror(CraftInventory inv, int index, Epoch epoch) {
            this.inv = inv;
            this.index = index;
            this.epoch = epoch;
        }

        @Override
        public org.domi.furnace.api.item.ItemStack get() {
            PlainSlot d = epoch.get();
            return d != null ? d.get() : inv.readSlot(index);
        }

        @Override
        public void set(org.domi.furnace.api.item.ItemStack stack) {
            PlainSlot d = epoch.get();
            if (d != null) d.set(stack);
            else inv.writeSlot(index, stack, "writing ItemStack(Inventory slot " + index + ")");
        }

        @Override
        public int amount() {
            PlainSlot d = epoch.get();
            return d != null ? d.amount() : get().amount();
        }

        @Override
        public void setAmount(int n) {
            PlainSlot d = epoch.get();
            if (d != null) d.setAmount(n);
            else set(get().withAmount(n));
        }
    }

    public static final class Epoch extends java.util.concurrent.atomic.AtomicReference<PlainSlot> {}

    /** Bukkit createInventory 메뉴의 칸을 Furnace 인벤토리로 본다. */
    public record MenuSlots(org.domi.furnace.api.menu.Menu menu) implements org.domi.furnace.api.inventory.Inventory {
        @Override
        public String type() { return menu.type().key(); }

        @Override
        public int size() { return menu.size(); }

        @Override
        public java.util.concurrent.CompletableFuture<org.domi.furnace.api.item.ItemStack> get(int slot) {
            try {
                return java.util.concurrent.CompletableFuture.completedFuture(menu.item(slot));
            } catch (IndexOutOfBoundsException e) {
                return java.util.concurrent.CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> set(int slot, org.domi.furnace.api.item.ItemStack item) {
            try {
                menu.set(slot, item);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            } catch (RuntimeException e) {
                return java.util.concurrent.CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public java.util.concurrent.CompletableFuture<Boolean> update(int slot, java.util.function.UnaryOperator<org.domi.furnace.api.item.ItemStack> fn) {
            try {
                return java.util.concurrent.CompletableFuture.completedFuture(menu.update(slot, fn));
            } catch (RuntimeException e) {
                return java.util.concurrent.CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public java.util.concurrent.CompletableFuture<java.util.List<org.domi.furnace.api.item.ItemStack>> contents() { return java.util.concurrent.CompletableFuture.completedFuture(menu.items()); }
    }

    /** 큰 상자의 두 칸 묶음. */
    public record Joined(org.domi.furnace.api.inventory.Inventory left, org.domi.furnace.api.inventory.Inventory right)
            implements org.domi.furnace.api.inventory.Inventory {

        @Override
        public String type() { return left.type(); }

        @Override
        public int size() { return left.size() + right.size(); }

        @Override
        public java.util.concurrent.CompletableFuture<org.domi.furnace.api.item.ItemStack> get(int slot) {
            if (slot < 0 || slot >= size()) return java.util.concurrent.CompletableFuture.failedFuture(new IndexOutOfBoundsException("Index " + slot + " out of bounds for length " + size()));
            return slot < left.size() ? left.get(slot) : right.get(slot - left.size());
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> set(int slot, org.domi.furnace.api.item.ItemStack item) {
            if (slot < 0 || slot >= size()) return java.util.concurrent.CompletableFuture.failedFuture(new IndexOutOfBoundsException("Index " + slot + " out of bounds for length " + size()));
            return slot < left.size() ? left.set(slot, item) : right.set(slot - left.size(), item);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Boolean> update(int slot, java.util.function.UnaryOperator<org.domi.furnace.api.item.ItemStack> fn) {
            if (slot < 0 || slot >= size()) return java.util.concurrent.CompletableFuture.failedFuture(new IndexOutOfBoundsException("Index " + slot + " out of bounds for length " + size()));
            return slot < left.size() ? left.update(slot, fn) : right.update(slot - left.size(), fn);
        }

        @Override
        public java.util.concurrent.CompletableFuture<java.util.List<org.domi.furnace.api.item.ItemStack>> contents() {
            return left.contents().thenCombine(right.contents(), (a, b) -> {
                java.util.List<org.domi.furnace.api.item.ItemStack> out = new java.util.ArrayList<>(a.size() + b.size());
                out.addAll(a);
                out.addAll(b);
                return out;
            });
        }
    }
}
