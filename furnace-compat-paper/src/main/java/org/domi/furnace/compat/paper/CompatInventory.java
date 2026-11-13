// Furnace Inventory 위의 Bukkit Inventory. 칸 읽기·쓰기는 Furnace 의 Future 가 곧바로 끝나는 경우(주인 스레드, 플레이어 읽기)를 동기로 올린다.
// 소유 스레드가 아닌 곳(접속·퇴장 처리기, 전역 스레드의 명령)의 쓰기는 소유 스레드로 예약되는데, Bukkit 코드는 쓰자마자 읽는다고 믿으므로
// 이 객체가 "아직 적용되지 않은 쓰기"를 들고 있다가 읽을 때 겹쳐 보여 준다(같은 스레드의 addItem 두 번이 서로 덮어쓰지 않는다).
package org.domi.furnace.compat.paper;

import java.util.AbstractList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class CompatInventory implements Inventory {

    final org.domi.furnace.api.inventory.Inventory handle;
    private final InventoryType type;
    private final java.util.function.Supplier<InventoryHolder> holder;

    /** 예약돼 아직 적용되지 않은 칸 쓰기: 칸 번호 → (값, 쓰기 번호). 같은 칸을 다시 쓰면 나중 것이 이긴다. */
    private record Pending(org.domi.furnace.api.item.ItemStack stack, long seq) {}

    private final java.util.Map<Integer, Pending> pending = new java.util.HashMap<>();
    private long seq;

    CompatInventory(org.domi.furnace.api.inventory.Inventory handle, InventoryType type, java.util.function.Supplier<InventoryHolder> holder) {
        this.handle = handle;
        this.type = type;
        this.holder = holder;
    }

    // ------------------------------------------------------------------ 칸 읽기·쓰기(예약된 쓰기를 겹쳐 본다)

    final org.domi.furnace.api.item.ItemStack readSlot(int index) {
        synchronized (pending) {
            Pending p = pending.get(index);
            if (p != null) return p.stack();
        }
        return Owned.read(handle.get(index), "Inventory#getItem(" + index + ")");
    }

    private List<org.domi.furnace.api.item.ItemStack> readAll() {
        List<org.domi.furnace.api.item.ItemStack> all = new java.util.ArrayList<>(Owned.read(handle.contents(), "Inventory#getContents"));
        synchronized (pending) {
            for (var e : pending.entrySet()) if (e.getKey() < all.size()) all.set(e.getKey(), e.getValue().stack());
        }
        return all;
    }

    final void writeSlot(int index, org.domi.furnace.api.item.ItemStack stack, String what) {
        org.domi.furnace.api.item.ItemStack value = stack.isEmpty() ? org.domi.furnace.api.item.ItemStack.EMPTY : stack;
        CompletableFuture<Void> f = handle.set(index, value);
        if (f.isDone()) {
            Owned.write(f, what);
            return;
        }
        long mine;
        synchronized (pending) {
            mine = ++seq;
            pending.put(index, new Pending(value, mine));
        }
        f.whenComplete((v, e) -> {
            synchronized (pending) {
                Pending cur = pending.get(index);
                if (cur != null && cur.seq() == mine) pending.remove(index);
            }
            if (e != null) System.getLogger("FurnaceCompat").log(System.Logger.Level.WARNING, what + " 예약 작업이 실패했다", e);
        });
    }

    /** 저장 칸 수(플레이어는 방어구·왼손을 뺀 36). */
    int storageSize() { return handle.size(); }

    // ------------------------------------------------------------------ 칸 거울

    /**
     * 칸 하나를 비추는 ItemSlot — 읽을 때마다 칸을 다시 읽고, 쓸 때마다 칸을 덮어쓴다.
     * 거울을 거치지 않고 그 칸을 바꾸면(setItem·clear·setContents) {@link #detachMirrors} 가 거울을 그 순간의 값으로 떼어 낸다.
     * CraftBukkit 의 거울이 NMS 객체에 묶여 칸이 바뀌면 옛 값을 들고 남는 것과 같은 결과다(칸 번호가 아니라 칸 안의 아이템에 묶인다).
     */
    private final class Mirror implements ItemSlot {
        private final int index;
        private volatile ItemSlot detached;

        Mirror(int index) { this.index = index; }

        @Override
        public org.domi.furnace.api.item.ItemStack get() {
            ItemSlot d = detached;
            return d != null ? d.get() : readSlot(index);
        }

        @Override
        public void set(org.domi.furnace.api.item.ItemStack stack) {
            ItemSlot d = detached;
            if (d != null) d.set(stack);
            else writeSlot(index, stack, "ItemStack(Inventory slot " + index + ") 쓰기");
        }

        @Override
        public int amount() {
            ItemSlot d = detached;
            return d != null ? d.amount() : get().amount();
        }

        @Override
        public void setAmount(int n) {
            ItemSlot d = detached;
            if (d != null) d.setAmount(n);
            else set(get().withAmount(n));
        }
    }

    /** 칸 번호 → 살아 있는(아직 떨어져 나가지 않은) 거울들. 약한 참조라 버려진 스택은 남지 않는다. */
    private final java.util.Map<Integer, List<java.lang.ref.WeakReference<Mirror>>> liveMirrors = new HashMap<>();

    final ItemSlot mirror(int index) {
        Mirror m = new Mirror(index);
        synchronized (liveMirrors) {
            List<java.lang.ref.WeakReference<Mirror>> refs = liveMirrors.computeIfAbsent(index, k -> new java.util.ArrayList<>(2));
            refs.removeIf(r -> r.get() == null);
            refs.add(new java.lang.ref.WeakReference<>(m));
        }
        return m;
    }

    /** 거울을 거치지 않는 칸 쓰기 직전에 부른다: 그 칸의 거울을 지금 값의 평범한 사본으로 떼어 낸다. */
    private void detachMirrors(int index) {
        List<Mirror> mirrors = new java.util.ArrayList<>(2);
        synchronized (liveMirrors) {
            List<java.lang.ref.WeakReference<Mirror>> refs = liveMirrors.remove(index);
            if (refs == null) return;
            for (var r : refs) {
                Mirror m = r.get();
                if (m != null) mirrors.add(m);
            }
        }
        if (mirrors.isEmpty()) return;
        org.domi.furnace.api.item.ItemStack now;
        try {
            now = readSlot(index);
        } catch (RuntimeException e) {
            // 소유 스레드가 아니라 읽을 수 없다 — 그 거울도 읽을 때마다 같은 예외를 던지므로 떼어 낼 값이 없다
            synchronized (liveMirrors) {
                liveMirrors.computeIfAbsent(index, k -> new java.util.ArrayList<>(2)).addAll(mirrors.stream().map(java.lang.ref.WeakReference::new).toList());
            }
            return;
        }
        for (Mirror m : mirrors) m.detached = new PlainSlot(now, now.amount());
    }

    private void check(int index) {
        if (index < 0 || index >= handle.size()) throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + handle.size());
    }

    private boolean isEmptyAt(int index) { return readSlot(index).isEmpty(); }

    // ------------------------------------------------------------------ Inventory

    @Override
    public int getSize() { return handle.size(); }

    @Override
    public int getMaxStackSize() { return 64; }

    @Override
    public ItemStack getItem(int index) {
        check(index);
        return readSlot(index).isEmpty() ? null : new ItemStack(mirror(index));
    }

    @Override
    public void setItem(int index, ItemStack item) {
        check(index);
        org.domi.furnace.api.item.ItemStack value = CompatItems.toFurnace(item); // 넣을 값은 거울이 떨어져 나가기 전에 읽는다
        detachMirrors(index);
        writeSlot(index, value, "Inventory#setItem(" + index + ")");
    }

    @Override
    public ItemStack[] getContents() { return slice(0, handle.size()); }

    @Override
    public ItemStack[] getStorageContents() { return slice(0, storageSize()); }

    private ItemStack[] slice(int from, int to) {
        List<org.domi.furnace.api.item.ItemStack> all = readAll();
        ItemStack[] out = new ItemStack[to - from];
        for (int i = from; i < to; i++) out[i - from] = all.get(i).isEmpty() ? null : new ItemStack(mirror(i));
        return out;
    }

    @Override
    public void setContents(ItemStack[] items) {
        Objects.requireNonNull(items, "Cannot set contents to null");
        if (items.length > handle.size()) throw new IllegalArgumentException("Invalid inventory size (" + items.length + "); expected " + handle.size() + " or less");
        for (int i = 0; i < handle.size(); i++) setItem(i, i < items.length ? items[i] : null);
    }

    @Override
    public void setStorageContents(ItemStack[] items) {
        Objects.requireNonNull(items, "Cannot set contents to null");
        int n = storageSize();
        if (items.length > n) throw new IllegalArgumentException("Invalid inventory size (" + items.length + "); expected " + n + " or less");
        for (int i = 0; i < n; i++) setItem(i, i < items.length ? items[i] : null);
    }

    /** 저장 칸에서 합칠 수 있는 첫 칸(같은 종류이고 가득 차지 않은 칸), 없으면 -1. */
    private int firstPartial(ItemStack item) {
        ItemStack[] inv = getStorageContents();
        ItemStack filter = item;
        if (filter == null) return -1;
        for (int i = 0; i < inv.length; i++) {
            ItemStack cItem = inv[i];
            if (cItem != null && cItem.getAmount() < cItem.getMaxStackSize() && cItem.isSimilar(filter)) return i;
        }
        return -1;
    }

    @Override
    public HashMap<Integer, ItemStack> addItem(ItemStack... items) {
        Objects.requireNonNull(items, "Items cannot be null");
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            Objects.requireNonNull(item, "Item " + i + " cannot be null");
            while (true) {
                int firstPartial = firstPartial(item);
                if (firstPartial == -1) {
                    int firstFree = firstEmpty();
                    if (firstFree == -1) {
                        leftover.put(i, item);
                        break;
                    }
                    int max = item.getMaxStackSize();
                    if (item.getAmount() > max) {
                        ItemStack stack = item.clone();
                        stack.setAmount(max);
                        setItem(firstFree, stack);
                        item.setAmount(item.getAmount() - max);
                    } else {
                        setItem(firstFree, item);
                        break;
                    }
                } else {
                    ItemStack partialItem = getItem(firstPartial);
                    int amount = item.getAmount();
                    int partialAmount = partialItem.getAmount();
                    int maxAmount = partialItem.getMaxStackSize();
                    if (amount + partialAmount <= maxAmount) {
                        partialItem.setAmount(amount + partialAmount);
                        break;
                    }
                    partialItem.setAmount(maxAmount);
                    item.setAmount(amount + partialAmount - maxAmount);
                }
            }
        }
        return leftover;
    }

    @Override
    public HashMap<Integer, ItemStack> removeItem(ItemStack... items) {
        Objects.requireNonNull(items, "Items cannot be null");
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            Objects.requireNonNull(item, "Item " + i + " cannot be null");
            int toDelete = item.getAmount();
            while (true) {
                int first = firstSimilar(item);
                if (first == -1) {
                    item.setAmount(toDelete);
                    leftover.put(i, item);
                    break;
                }
                ItemStack itemStack = getItem(first);
                int amount = itemStack.getAmount();
                if (amount <= toDelete) {
                    toDelete -= amount;
                    clear(first);
                } else {
                    itemStack.setAmount(amount - toDelete);
                    toDelete = 0;
                }
                if (toDelete <= 0) break;
            }
        }
        return leftover;
    }

    /** 모든 칸에서 isSimilar 인 첫 칸(개수 무시), 없으면 -1. */
    private int firstSimilar(ItemStack item) {
        if (item == null) return -1;
        ItemStack[] inv = getContents();
        for (int i = 0; i < inv.length; i++) if (inv[i] != null && item.isSimilar(inv[i])) return i;
        return -1;
    }

    @Override
    public boolean contains(Material material) {
        Objects.requireNonNull(material, "Material cannot be null");
        for (ItemStack i : getContents()) if (i != null && i.getType() == material) return true;
        return false;
    }

    @Override
    public boolean contains(ItemStack item) {
        if (item == null) return false;
        for (ItemStack i : getContents()) if (item.equals(i)) return true;
        return false;
    }

    @Override
    public boolean contains(Material material, int amount) {
        Objects.requireNonNull(material, "Material cannot be null");
        if (amount <= 0) return true;
        for (ItemStack i : getContents()) {
            if (i != null && i.getType() == material && (amount -= i.getAmount()) <= 0) return true;
        }
        return false;
    }

    @Override
    public boolean contains(ItemStack item, int amount) {
        if (item == null) return false;
        if (amount <= 0) return true;
        for (ItemStack i : getContents()) if (item.equals(i) && --amount <= 0) return true;
        return false;
    }

    @Override
    public boolean containsAtLeast(ItemStack item, int amount) {
        if (item == null) return false;
        if (amount <= 0) return true;
        for (ItemStack i : getContents()) if (item.isSimilar(i) && (amount -= i.getAmount()) <= 0) return true;
        return false;
    }

    @Override
    public HashMap<Integer, ? extends ItemStack> all(Material material) {
        Objects.requireNonNull(material, "Material cannot be null");
        HashMap<Integer, ItemStack> slots = new HashMap<>();
        ItemStack[] inv = getContents();
        for (int i = 0; i < inv.length; i++) if (inv[i] != null && inv[i].getType() == material) slots.put(i, inv[i]);
        return slots;
    }

    @Override
    public HashMap<Integer, ? extends ItemStack> all(ItemStack item) {
        HashMap<Integer, ItemStack> slots = new HashMap<>();
        if (item != null) {
            ItemStack[] inv = getContents();
            for (int i = 0; i < inv.length; i++) if (item.equals(inv[i])) slots.put(i, inv[i]);
        }
        return slots;
    }

    @Override
    public int first(Material material) {
        Objects.requireNonNull(material, "Material cannot be null");
        ItemStack[] inv = getContents();
        for (int i = 0; i < inv.length; i++) if (inv[i] != null && inv[i].getType() == material) return i;
        return -1;
    }

    @Override
    public int first(ItemStack item) {
        if (item == null) return -1;
        ItemStack[] inv = getContents();
        for (int i = 0; i < inv.length; i++) if (inv[i] != null && item.equals(inv[i])) return i;
        return -1;
    }

    @Override
    public int firstEmpty() {
        for (int i = 0; i < storageSize(); i++) if (isEmptyAt(i)) return i;
        return -1;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < handle.size(); i++) if (!isEmptyAt(i)) return false;
        return true;
    }

    @Override
    public void remove(Material material) {
        Objects.requireNonNull(material, "Material cannot be null");
        ItemStack[] inv = getContents();
        for (int i = 0; i < inv.length; i++) if (inv[i] != null && inv[i].getType() == material) clear(i);
    }

    @Override
    public void remove(ItemStack item) {
        Objects.requireNonNull(item, "ItemStack cannot be null");
        ItemStack[] inv = getContents();
        for (int i = 0; i < inv.length; i++) if (inv[i] != null && inv[i].equals(item)) clear(i);
    }

    @Override
    public void clear(int index) { setItem(index, null); }

    @Override
    public void clear() {
        for (int i = 0; i < handle.size(); i++) clear(i);
    }

    @Override
    public InventoryType getType() { return type; }

    @Override
    public InventoryHolder getHolder() { return holder.get(); }

    /** 위치를 모르는 인벤토리(플러그인이 만든 인벤토리 등)는 null. */
    @Override
    public org.bukkit.Location getLocation() { return null; }

    /** Furnace 는 블록·엔티티 보관함을 누가 보고 있는지 알리지 않아 빈 목록이다(플러그인이 만든 인벤토리만 안다). */
    @Override
    public java.util.List<org.bukkit.entity.HumanEntity> getViewers() { return java.util.List.of(); }

    @Override
    public ListIterator<ItemStack> iterator() {
        return new AbstractList<ItemStack>() {
            @Override
            public ItemStack get(int index) { return getItem(index); }

            @Override
            public ItemStack set(int index, ItemStack element) {
                ItemStack old = getItem(index);
                setItem(index, element);
                return old;
            }

            @Override
            public int size() { return getSize(); }
        }.listIterator();
    }
}
