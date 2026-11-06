// 플레이어 인벤토리(창 칸 0..45 + 든 칸) — 플레이어가 가진다. region 스레드(아이템 줍기·쓰기)와 연결 스레드(클릭·저장)가
// 함께 쓰므로 메서드마다 잠근다. 클라이언트 동기(remote·stateId·커서)는 InventoryMenu(AbstractContainerMenu)가 맡는다.
// 창 칸: 0 제작 결과, 1..4 제작 칸, 5..8 머리·가슴·다리·발, 9..35 주, 36..44 핫바, 45 왼손.
package org.domi.furnace.server.entity;

import java.util.Arrays;

public final class PlayerInventory {

    public static final int SIZE = 46;

    private final ItemStack[] slots = new ItemStack[SIZE];
    private int selected;

    public PlayerInventory() {
        Arrays.fill(slots, ItemStack.EMPTY);
    }

    /** Inventory 번호(0..8 핫바, 9..35 주, 36..39 발·다리·가슴·머리, 40 왼손) → 창 칸. */
    public static int windowSlot(int inventoryIndex) {
        if (inventoryIndex < 9) return 36 + inventoryIndex;
        if (inventoryIndex < 36) return inventoryIndex;
        if (inventoryIndex < 40) return 8 - (inventoryIndex - 36);
        return 45;
    }

    public synchronized ItemStack get(int windowSlot) {
        ItemStack s = windowSlot >= 0 && windowSlot < SIZE ? slots[windowSlot] : null;
        return s == null ? ItemStack.EMPTY : s;
    }

    public synchronized void set(int windowSlot, ItemStack stack) { slots[windowSlot] = stack == null ? ItemStack.EMPTY : stack; }

    /** 그 칸이 아직 expect 이면 now 로 바꾼다. */
    public synchronized boolean replace(int windowSlot, ItemStack expect, ItemStack now) {
        if (get(windowSlot) != expect) return false;
        slots[windowSlot] = now == null ? ItemStack.EMPTY : now;
        return true;
    }

    public synchronized ItemStack[] snapshot() { return slots.clone(); }

    public synchronized void load(ItemStack[] saved) {
        for (int i = 0; i < SIZE; i++) slots[i] = i < saved.length && saved[i] != null ? saved[i] : ItemStack.EMPTY;
    }

    public synchronized void clear() { Arrays.fill(slots, ItemStack.EMPTY); }

    public synchronized int selected() { return selected; }

    public synchronized void setSelected(int hotbar) { selected = hotbar; }

    /** 든 칸(창 칸 36 + 선택). */
    public synchronized int heldWindowSlot() { return 36 + selected; }

    /** Inventory.getSuitableHotbarSlot: 지금 칸부터 돌며 빈 칸, 없으면 마법 부여 안 된 칸, 그것도 없으면 지금 칸. */
    public synchronized int suitableHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            int s = (selected + i) % 9;
            if (slots[36 + s].isEmpty()) return s;
        }
        for (int i = 0; i < 9; i++) {
            int s = (selected + i) % 9;
            if (org.domi.furnace.server.item.EnchantTools.levels(slots[36 + s], org.domi.furnace.server.item.EnchantTools.ENCHANTMENTS).isEmpty()) return s;
        }
        return selected;
    }

    /** Inventory.pickSlot(inventoryIndex): 알맞은 핫바 칸을 고르고 그 칸과 inventoryIndex(0..35) 칸을 맞바꾼다. */
    public synchronized void pickSlot(int inventoryIndex) {
        selected = suitableHotbarSlot();
        int a = 36 + selected, b = windowSlot(inventoryIndex);
        ItemStack t = slots[a];
        slots[a] = slots[b];
        slots[b] = t;
    }

    /**
     * Inventory.add(stack): 손상된 아이템은 빈 칸에만, 아니면 같은 아이템이 덜 찬 칸(든 칸 → 왼손 → 0..35) 다음 빈 칸(0..35)에 나눠 넣는다.
     * 무한 재료(크리에이티브)면 못 넣은 것도 다 넣은 셈. 남은 스택(다 넣었으면 EMPTY)을 돌려준다.
     */
    public synchronized ItemStack add(ItemStack stack, boolean infinite, int maxStack, boolean damaged) {
        if (stack.isEmpty()) return stack;
        if (damaged) {
            int free = freeSlot();
            if (free >= 0) {
                slots[windowSlot(free)] = stack;
                return ItemStack.EMPTY;
            }
            return infinite ? ItemStack.EMPTY : stack;
        }
        int start = stack.count();
        int left = start;
        int prev;
        do {
            prev = left;
            int slot = slotWithRemainingSpace(stack, maxStack);
            if (slot == -1) slot = freeSlot();
            if (slot == -1) break;
            int ws = windowSlot(slot);
            ItemStack cur = slots[ws];
            int have = cur.isEmpty() ? 0 : cur.count();
            int room = Math.min(maxStack, 99) - have;
            int put = Math.min(left, room);
            if (put <= 0) break;
            slots[ws] = new ItemStack(stack.itemId(), have + put, stack.patch());
            left -= put;
        } while (left > 0 && left < prev);
        if (left == start && infinite) return ItemStack.EMPTY;
        return left == 0 ? ItemStack.EMPTY : stack.withCount(left);
    }

    /** Inventory.getSlotWithRemainingSpace: 든 칸 → 왼손(40) → 0..35 의 첫 자리 있는 칸, 없으면 -1. */
    public synchronized int slotWithRemainingSpace(ItemStack stack, int maxStack) {
        if (hasRemainingSpace(slots[36 + selected], stack, maxStack)) return selected;
        if (hasRemainingSpace(slots[45], stack, maxStack)) return 40;
        for (int i = 0; i < 36; i++) if (hasRemainingSpace(slots[windowSlot(i)], stack, maxStack)) return i;
        return -1;
    }

    private static boolean hasRemainingSpace(ItemStack dest, ItemStack stack, int maxStack) {
        return !dest.isEmpty() && dest.itemId() == stack.itemId() && dest.patch().equals(stack.patch()) && maxStack > 1
                && dest.count() < Math.min(maxStack, 99);
    }

    private int freeSlot() {
        for (int i = 0; i < 36; i++) if (slots[windowSlot(i)].isEmpty()) return i;
        return -1;
    }

    /** Inventory.contains(stack): 같은 아이템·컴포넌트가 어느 칸에든 있는가. */
    public synchronized boolean contains(ItemStack stack) {
        for (int ws = 5; ws < SIZE; ws++) {
            ItemStack cur = slots[ws];
            if (!cur.isEmpty() && cur.itemId() == stack.itemId() && cur.patch().equals(stack.patch())) return true;
        }
        return false;
    }
}
