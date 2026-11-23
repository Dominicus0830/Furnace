package net.minecraft.world.entity.player;

import java.util.function.Predicate;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;

/**
 * 플레이어 인벤토리(Mojang 이름 shim). 칸 번호는 바닐라와 같다: 0..8 핫바, 9..35 주, 36..39 발·다리·가슴·머리, 40 왼손(41칸).
 *
 * <p>두 가지 인벤토리가 있다. <b>접속한 플레이어의 것</b>({@code player.getInventory()})은 서버의 인벤토리를 그대로 비춘다 — 칸을 읽으면 그 칸의 살아 있는 스택(고치면 그
 * 칸에 되쓴다, 손 칸과 같은 규칙)이고 쓰기는 아무 스레드에서나 된다(서버 인벤토리의 잠금 안에서 일어난다). 그 칸에 놓인 스택 <i>객체</i>가 같은지(참조 비교)는 바닐라와 달리
 * 보장하지 않는다(읽을 때마다 새 보기). <b>바닐라 생성자로 만든 것</b>({@code new Inventory(player)})은 어느 서버 인벤토리와도 이어지지 않은 제 칸을 들고, 스택 객체를
 * 그대로 든다(월드에 없는 플레이어의 인벤토리도 이쪽이다).
 *
 * <p>{@code selected} 는 고른 핫바 칸을 비춘 공개 필드다. 이 인벤토리의 메서드가 불릴 때마다 서버 값으로 맞추고, 플레이어 코드가 필드에 직접 쓴 값은 다음 메서드 호출 때
 * 서버에 알린다(바닐라는 필드를 바로 읽는다 — 서버에서 바뀐 값은 다음 호출·{@code getInventory()} 때 보인다).
 */
public class Inventory implements Container, Nameable {

    public static final int POP_TIME_DURATION = 5;
    public static final int INVENTORY_SIZE = 36;
    public static final int SLOT_OFFHAND = 40;
    public static final int NOT_FOUND_INDEX = -1;
    public static final int[] ALL_ARMOR_SLOTS = {0, 1, 2, 3};
    public static final int[] HELMET_SLOT_ONLY = {3};

    private static final int SIZE = 41;

    /** 칸을 어디에 두는가: 서버 인벤토리(접속한 플레이어) 또는 제 칸. */
    private interface Store {
        ItemStack get(int index);

        void set(int index, ItemStack stack);

        /** 그 칸에서 최대 amount 개를 떼어 돌려준다(칸에는 나머지). 그새 다른 쓰기가 끼면 다시 읽는다. */
        ItemStack take(int index, int amount);

        /** 바닐라 {@code add(ItemStack)}: 합치고 빈 칸에 넣고, 못 넣은 나머지를 돌려준다. */
        ItemStack add(ItemStack stack, int selected);

        /** 고른 핫바 칸을 서버에서 읽는다(제 칸이면 null — 필드가 그대로 값이다). */
        Integer heldSlot();

        /** 서버에 고른 칸을 바꾸라고 하고, 그 일이 끝나면 완료되는 것을 돌려준다(주인 스레드가 실행한다 — 기다리지 않는다). */
        java.util.concurrent.CompletableFuture<Void> setHeldSlot(int slot);
    }

    private final Store store;
    public final NonNullList<ItemStack> items;
    public final NonNullList<ItemStack> armor;
    public final NonNullList<ItemStack> offhand;
    public int selected;
    public final Player player;
    /** 마지막으로 서버와 맞춘 selected(필드를 직접 쓴 것과 서버가 바꾼 것을 가른다). */
    private int publishedSelected;
    private volatile java.util.concurrent.CompletableFuture<Void> pendingHeld;

    /** 바닐라 생성자: 어느 서버 인벤토리와도 이어지지 않은 제 칸의 인벤토리. */
    public Inventory(Player player) {
        this(player, new LocalStore());
    }

    private Inventory(Player player, Store store) {
        this.player = java.util.Objects.requireNonNull(player, "player");
        this.store = store;
        this.items = new View(0, INVENTORY_SIZE);
        this.armor = new View(INVENTORY_SIZE, 4);
        this.offhand = new View(INVENTORY_SIZE + 4, 1);
        Integer held = store.heldSlot();
        this.selected = this.publishedSelected = held == null ? 0 : held;
    }

    /** 호환 모듈 전용 — 접속한 플레이어의 서버 인벤토리를 비추는 인벤토리. */
    public static Inventory furnaceLive(Player player, org.domi.furnace.api.entity.Player handle) {
        return new Inventory(player, new LiveStore(handle));
    }

    /** items·armor·offhand: 칸을 이 인벤토리의 번호로 읽고 쓰는 고정 크기 목록(바닐라 NonNullList 필드). */
    private final class View extends NonNullList<ItemStack> {
        private final int base;
        private final int size;

        View(int base, int size) {
            super(java.util.Collections.emptyList(), ItemStack.EMPTY);
            this.base = base;
            this.size = size;
        }

        @Override
        public ItemStack get(int index) {
            java.util.Objects.checkIndex(index, size);
            return store.get(base + index);
        }

        @Override
        public ItemStack set(int index, ItemStack value) {
            java.util.Objects.checkIndex(index, size);
            java.util.Objects.requireNonNull(value, "value");
            ItemStack old = store.get(base + index);
            store.set(base + index, value);
            return old;
        }

        @Override
        public int size() { return size; }
    }

    /** 호환 모듈 전용 — {@code selected} 를 서버와 맞춘다({@link Player#getInventory} 가 부른다). */
    public void furnaceSync() { sync(); }

    /** selected 를 서버와 맞춘다: 필드를 직접 쓴 값이 있으면 서버에 알리고, 아니면 서버 값을 읽는다. */
    private void sync() {
        Integer held = store.heldSlot();
        if (held == null) return;
        var pending = pendingHeld;
        if (pending != null) {
            if (!pending.isDone()) return;   // 서버가 아직 바꾸는 중 — 플레이어 코드가 쓴 값을 그대로 둔다
            pendingHeld = null;
            if (pending.isCompletedExceptionally()) {
                selected = held;   // 서버가 받지 않았다 — 서버 값으로 돌아간다
                publishedSelected = selected;
                return;
            }
        }
        if (selected != publishedSelected) {
            if (isHotbarSlot(selected)) {
                pendingHeld = store.setHeldSlot(selected);
            } else {
                selected = held;   // 바닐라는 범위 밖 값을 그대로 들지만 서버는 0..8 만 안다
            }
        } else {
            selected = held;
        }
        publishedSelected = selected;
    }

    public static boolean isHotbarSlot(int slot) { return slot >= 0 && slot < 9; }

    public static int getSelectionSize() { return 9; }

    @Override
    public int getContainerSize() { return SIZE; }

    @Override
    public ItemStack getItem(int slot) {
        sync();
        return slot >= 0 && slot < SIZE ? store.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        java.util.Objects.requireNonNull(stack, "stack");
        sync();
        if (slot >= 0 && slot < SIZE) store.set(slot, stack);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        sync();
        return slot >= 0 && slot < SIZE && amount > 0 ? store.take(slot, amount) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        sync();
        return slot >= 0 && slot < SIZE ? store.take(slot, Integer.MAX_VALUE) : ItemStack.EMPTY;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < SIZE; i++) if (!store.get(i).isEmpty()) return false;
        return true;
    }

    public ItemStack getSelected() {
        sync();
        return isHotbarSlot(selected) ? store.get(selected) : ItemStack.EMPTY;
    }

    public ItemStack getArmor(int index) { return armor.get(index); }

    /** 바닐라 add(ItemStack): 합치고 빈 칸에 넣는다. 다 못 넣으면 스택에 나머지 개수만 남고 거짓(하나라도 들어갔으면 참 — 크리에이티브면 아무것도 못 넣어도 참). */
    public boolean add(ItemStack stack) {
        sync();
        if (stack.isEmpty()) return false;
        int before = stack.getCount();
        ItemStack left = store.add(stack, selected);
        stack.setCount(left.isEmpty() ? 0 : left.getCount());
        return stack.getCount() < before;
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player other) {
        if (player.isRemoved()) return false;
        double dx = player.getX() - other.getX(), dy = player.getY() - other.getY(), dz = player.getZ() - other.getZ();
        return dx * dx + dy * dy + dz * dz <= 64.0;
    }

    /** 바닐라: 같은 종류·컴포넌트의 스택이 어느 칸에든 있는가. */
    public boolean contains(ItemStack stack) {
        sync();
        for (int i = 0; i < SIZE; i++) {
            ItemStack s = store.get(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack)) return true;
        }
        return false;
    }

    public boolean contains(Predicate<ItemStack> test) {
        sync();
        for (int i = 0; i < SIZE; i++) if (test.test(store.get(i))) return true;
        return false;
    }

    public boolean contains(net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
        return contains(s -> !s.isEmpty() && s.is(tag));
    }

    /** 바닐라: 같은 종류·컴포넌트 스택이 있는 첫 칸(없으면 -1). */
    public int findSlotMatchingItem(ItemStack stack) {
        sync();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack s = store.get(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(stack, s)) return i;
        }
        return -1;
    }

    /** 바닐라: 첫 빈 칸(0..35, 없으면 -1). */
    public int getFreeSlot() {
        sync();
        for (int i = 0; i < INVENTORY_SIZE; i++) if (store.get(i).isEmpty()) return i;
        return -1;
    }

    @Override
    public void clearContent() {
        sync();
        for (int i = 0; i < SIZE; i++) store.set(i, ItemStack.EMPTY);
    }

    public Component getName() { return Component.translatable("container.inventory"); }

    // ------------------------------------------------------------------ 칸 저장소

    /** 인벤토리 번호 → 플레이어 창 칸(0..8 → 36..44, 9..35 → 9..35, 36..39 → 8..5, 40 → 45). */
    private static int windowSlot(int index) {
        if (index < 9) return 36 + index;
        if (index < 36) return index;
        if (index < 40) return 8 - (index - 36);
        return 45;
    }

    private static final class LiveStore implements Store {
        private final org.domi.furnace.api.entity.Player handle;

        LiveStore(org.domi.furnace.api.entity.Player handle) { this.handle = handle; }

        private static org.domi.furnace.api.internal.ChunkInternals bridge() { return org.domi.furnace.compat.nms.NmsChunks.bridge(); }

        @Override
        public ItemStack get(int index) { return ItemStack.liveSlot(handle, windowSlot(index)); }

        @Override
        public void set(int index, ItemStack stack) { bridge().setWindowSlotNow(handle, windowSlot(index), stack.furnaceValue()); }

        @Override
        public ItemStack take(int index, int amount) {
            int ws = windowSlot(index);
            while (true) {
                var cur = bridge().windowSlotNow(handle, ws);
                if (cur.isEmpty()) return ItemStack.EMPTY;
                int n = Math.min(amount, cur.amount());
                if (bridge().replaceSlotNow(handle, ws, cur, cur.withAmount(cur.amount() - n))) return ItemStack.fromFurnace(cur, n);
            }
        }

        @Override
        public ItemStack add(ItemStack stack, int selected) {
            var left = bridge().addToInventoryNow(handle, stack.furnaceValue());
            return left.isEmpty() ? ItemStack.EMPTY : ItemStack.fromFurnace(left, left.amount());
        }

        @Override
        public Integer heldSlot() { return handle.heldSlot(); }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setHeldSlot(int slot) { return handle.setHeldSlot(slot); }
    }

    private static final class LocalStore implements Store {
        private final ItemStack[] slots = new ItemStack[SIZE];

        LocalStore() { java.util.Arrays.fill(slots, ItemStack.EMPTY); }

        @Override
        public synchronized ItemStack get(int index) { return slots[index]; }

        @Override
        public synchronized void set(int index, ItemStack stack) { slots[index] = stack; }

        @Override
        public synchronized ItemStack take(int index, int amount) {
            ItemStack cur = slots[index];
            if (cur.isEmpty()) return ItemStack.EMPTY;
            if (amount >= cur.getCount()) {
                slots[index] = ItemStack.EMPTY;
                return cur;
            }
            return cur.split(amount);
        }

        @Override
        public synchronized ItemStack add(ItemStack stack, int selected) {
            // 바닐라 순서: 같은 스택 칸(든 칸 → 왼손 → 0..35)에 합치고, 남은 것은 첫 빈 칸(0..35)에
            ItemStack rest = stack.copy();
            int max = Math.min(rest.getMaxStackSize(), 99);
            int[] order = new int[2 + INVENTORY_SIZE];
            order[0] = selected;
            order[1] = SLOT_OFFHAND;
            for (int i = 0; i < INVENTORY_SIZE; i++) order[2 + i] = i;
            for (int idx : order) {
                if (rest.isEmpty()) break;
                ItemStack cur = slots[idx];
                if (cur.isEmpty() || !ItemStack.isSameItemSameComponents(cur, rest) || max <= 1 || cur.getCount() >= max) continue;
                int put = Math.min(rest.getCount(), max - cur.getCount());
                cur.grow(put);
                rest.shrink(put);
            }
            for (int i = 0; i < INVENTORY_SIZE && !rest.isEmpty(); i++) {
                if (!slots[i].isEmpty()) continue;
                int put = Math.min(rest.getCount(), Math.min(max, 99));
                slots[i] = rest.copyWithCount(put);
                rest.shrink(put);
            }
            return rest.isEmpty() ? ItemStack.EMPTY : rest;
        }

        @Override
        public Integer heldSlot() { return null; }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setHeldSlot(int slot) { return java.util.concurrent.CompletableFuture.completedFuture(null); }
    }

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
    public org.bukkit.inventory.InventoryHolder getOwner() { throw bukkitSide("getOwner"); }

    @Override
    public java.util.List<org.bukkit.entity.HumanEntity> getViewers() { throw bukkitSide("getViewers"); }

    @Override
    public void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity who) { throw bukkitSide("onOpen"); }

    @Override
    public void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity who) { throw bukkitSide("onClose"); }

    private static org.domi.furnace.compat.nms.UnsupportedNmsCall bukkitSide(String what) {
        return new org.domi.furnace.compat.nms.UnsupportedNmsCall("Container#" + what, "the Bukkit-side view of this inventory is not part of the shim (use the Bukkit Inventory)");
    }
}
