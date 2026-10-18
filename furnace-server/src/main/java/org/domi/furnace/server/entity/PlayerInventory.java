// 플레이어 인벤토리(창 칸 0..45 + 커서 + 든 칸) — 플레이어가 가진다. region 스레드(아이템 줍기·쓰기)와 연결 스레드(클릭·저장)가
// 함께 쓰므로 메서드마다 잠근다. 클라이언트가 아는 상태(remote)를 따로 들고 바닐라 AbstractContainerMenu.broadcastChanges 처럼
// 달라진 칸만 container_set_slot 으로 보낸다. 창 칸: 0 제작 결과, 1..4 제작 칸, 5..8 머리·가슴·다리·발, 9..35 주, 36..44 핫바, 45 왼손.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.ContainerSetContent;
import org.domi.furnace.server.net.protocol.play.ContainerSetSlot;

public final class PlayerInventory {

    public static final int SIZE = 46;

    private final ItemStack[] slots = new ItemStack[SIZE];
    private final ItemStack[] remote = new ItemStack[SIZE];
    private ItemStack carried = ItemStack.EMPTY, remoteCarried = ItemStack.EMPTY;
    private int selected;
    private int stateId;

    public PlayerInventory() {
        Arrays.fill(slots, ItemStack.EMPTY);
        Arrays.fill(remote, ItemStack.EMPTY);
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

    public synchronized ItemStack carried() { return carried; }

    public synchronized void setCarried(ItemStack stack) { carried = stack == null ? ItemStack.EMPTY : stack; }

    /** 든 칸(창 칸 36 + 선택). */
    public synchronized int heldWindowSlot() { return 36 + selected; }

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

    private int slotWithRemainingSpace(ItemStack stack, int maxStack) {
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

    // ------------------------------------------------------------------ 클라이언트 동기(AbstractContainerMenu)

    public synchronized int incrementStateId() {
        stateId = stateId + 1 & 32767;
        return stateId;
    }

    /** setRemoteSlot — 클라이언트가 이미 아는 값(클릭·크리에이티브 칸)으로 맞춘다. */
    public synchronized void setRemote(int windowSlot, ItemStack stack) { remote[windowSlot] = stack == null ? ItemStack.EMPTY : stack; }

    public synchronized void setRemoteCarried(ItemStack stack) { remoteCarried = stack == null ? ItemStack.EMPTY : stack; }

    /** sendAllDataToRemote: 전체 내용 패킷(새 상태 번호)과 함께 remote 를 지금 상태로. */
    public synchronized ContainerSetContent fullState() {
        System.arraycopy(slots, 0, remote, 0, SIZE);
        remoteCarried = carried;
        return new ContainerSetContent(0, incrementStateId(), slots.clone(), carried);
    }

    /** broadcastChanges: remote 와 다른 칸마다 container_set_slot(상태 번호 하나씩), 커서가 다르면 -1 칸. 바뀐 창 칸 목록도 돌려준다. */
    public synchronized List<Packet> broadcastChanges(List<Integer> changedOut) {
        List<Packet> out = null;
        for (int i = 0; i < SIZE; i++) {
            if (!slots[i].equals(remote[i])) {
                remote[i] = slots[i];
                if (out == null) out = new ArrayList<>();
                out.add(new ContainerSetSlot(0, incrementStateId(), i, slots[i]));
                if (changedOut != null) changedOut.add(i);
            }
        }
        if (!carried.equals(remoteCarried)) {
            remoteCarried = carried;
            if (out == null) out = new ArrayList<>();
            out.add(new ContainerSetSlot(-1, stateId, -1, carried));
        }
        return out == null ? List.of() : out;
    }
}
