// 바닐라 AbstractContainerMenu 이식 — 서버 권위 컨테이너 클릭(doClick 7종)과 클라이언트 동기(remoteSlots·stateId·broadcastChanges).
// 바닐라 필드 대응: slots / lastSlots(리스너용) / remoteSlots(클라이언트가 아는 값) / carried / remoteCarried / quickcraft*.
// ItemStack 이 불변이라 바닐라가 스택을 그 자리에서 바꾸는 곳은 새 스택으로 다시 set 한다. 가방(bundle) 클릭 재정의는 tryItemClickBehaviourOverride.
package org.domi.furnace.server.menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.entity.ItemStack;

public abstract class AbstractContainerMenu {

    public static final int SLOT_CLICKED_OUTSIDE = -999;
    public static final int QUICKCRAFT_HEADER_START = 0;
    public static final int QUICKCRAFT_HEADER_CONTINUE = 1;
    public static final int QUICKCRAFT_HEADER_END = 2;
    public static final int QUICKCRAFT_TYPE_CHARITABLE = 0;
    public static final int QUICKCRAFT_TYPE_GREEDY = 1;
    public static final int QUICKCRAFT_TYPE_CLONE = 2;

    public final int containerId;
    private final String menuType;   // minecraft:menu 레지스트리 이름(인벤토리 메뉴는 null)

    public final List<Slot> slots = new ArrayList<>();
    private final List<ItemStack> lastSlots = new ArrayList<>();
    private final List<ItemStack> remoteSlots = new ArrayList<>();
    private final List<DataSlot> dataSlots = new ArrayList<>();
    private int[] remoteDataSlots = new int[0];
    private ItemStack carried = ItemStack.EMPTY;
    private ItemStack remoteCarried = ItemStack.EMPTY;
    private int stateId;
    private int quickcraftType = -1;
    private int quickcraftStatus;
    private final Set<Slot> quickcraftSlots = new LinkedHashSet<>();
    private final List<MenuListener> containerListeners = new ArrayList<>();
    private MenuSynchronizer synchronizer;
    private boolean suppressRemoteUpdates;

    private volatile Object pluginOwner;

    /** 플러그인 Menu 가 연 창이면 그 세션(plugin.MenuSession), 아니면 null. */
    public Object pluginOwner() { return pluginOwner; }

    public void pluginOwner(Object owner) { this.pluginOwner = owner; }

    protected AbstractContainerMenu(String menuType, int containerId) {
        this.menuType = menuType;
        this.containerId = containerId;
    }

    public String menuType() {
        if (menuType == null) throw new UnsupportedOperationException("Unable to construct this menu by type");
        return menuType;
    }

    public static void checkContainerSize(Container c, int expected) {
        if (c.size() < expected) throw new IllegalArgumentException("Container size " + c.size() + " is smaller than expected " + expected);
    }

    /** 바닐라 isValidSlotIndex: -1(커서)·-999(창 밖)·칸 번호. */
    public boolean isValidSlotIndex(int slot) { return slot == -1 || slot == SLOT_CLICKED_OUTSIDE || slot < slots.size(); }

    protected Slot addSlot(Slot slot) {
        slot.index = slots.size();
        slots.add(slot);
        lastSlots.add(ItemStack.EMPTY);
        remoteSlots.add(ItemStack.EMPTY);
        return slot;
    }

    protected DataSlot addDataSlot(DataSlot data) {
        dataSlots.add(data);
        remoteDataSlots = Arrays.copyOf(remoteDataSlots, dataSlots.size());   // 바닐라 remoteDataSlots.add(0)
        return data;
    }

    /** 이 메뉴가 블록에 매인 컨테이너면 그 블록 좌표 {x,y,z}, 아니면 null — 클릭을 그 블록의 region 에서 처리하고 블록이 사라지면 닫는다. */
    public int[] containerBlockPos() { return null; }

    public Slot getSlot(int index) { return slots.get(index); }

    public ItemStack getCarried() { return carried; }

    public void setCarried(ItemStack stack) { carried = stack == null ? ItemStack.EMPTY : stack; }

    public int getStateId() { return stateId; }

    public int incrementStateId() {
        stateId = stateId + 1 & 32767;
        return stateId;
    }

    // ------------------------------------------------------------------ 동기(리스너·클라이언트)

    public void addSlotListener(MenuListener listener) {
        if (!containerListeners.contains(listener)) {
            containerListeners.add(listener);
            broadcastChanges();
        }
    }

    public void setSynchronizer(MenuSynchronizer sync) {
        synchronizer = sync;
        sendAllDataToRemote();
    }

    public void removeSlotListener(MenuListener listener) { containerListeners.remove(listener); }

    /** sendAllDataToRemote: remote 를 지금 상태로 맞추고 전체 내용(새 상태 번호)을 보낸다. */
    public void sendAllDataToRemote() {
        int n = slots.size();
        List<ItemStack> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ItemStack s = slots.get(i).getItem();
            items.add(s);
            remoteSlots.set(i, s);
        }
        remoteCarried = carried;
        remoteDataSlots = new int[dataSlots.size()];
        for (int i = 0; i < remoteDataSlots.length; i++) remoteDataSlots[i] = dataSlots.get(i).get();
        if (synchronizer != null) synchronizer.sendInitialData(this, items, remoteCarried, remoteDataSlots);
    }

    public List<ItemStack> getItems() {
        List<ItemStack> out = new ArrayList<>(slots.size());
        for (Slot s : slots) out.add(s.getItem());
        return out;
    }

    /** broadcastChanges: 리스너에게 알리고, 클라이언트가 아는 것과 다른 칸·커서·데이터만 보낸다. */
    public void broadcastChanges() {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack cur = slots.get(i).getItem();
            triggerSlotListeners(i, cur);
            synchronizeSlotToRemote(i, cur);
        }
        synchronizeCarriedToRemote();
        for (int i = 0; i < dataSlots.size(); i++) {
            DataSlot d = dataSlots.get(i);
            int v = d.get();
            if (d.checkAndClearUpdateFlag()) updateDataSlotListeners(i, v);
            synchronizeDataSlotToRemote(i, v);
        }
    }

    /** broadcastFullState: 리스너에게 모두 알리고 전체 내용을 다시 보낸다(상태 번호 불일치 때). */
    public void broadcastFullState() {
        for (int i = 0; i < slots.size(); i++) triggerSlotListeners(i, slots.get(i).getItem());
        for (int i = 0; i < dataSlots.size(); i++) {
            DataSlot d = dataSlots.get(i);
            int v = d.get();
            if (d.checkAndClearUpdateFlag()) updateDataSlotListeners(i, v);
        }
        sendAllDataToRemote();
    }

    private void updateDataSlotListeners(int id, int value) {
        for (MenuListener l : containerListeners) l.dataChanged(this, id, value);
    }

    private void triggerSlotListeners(int index, ItemStack cur) {
        ItemStack last = lastSlots.get(index);
        if (!Stacks.matches(last, cur)) {
            lastSlots.set(index, cur);
            for (MenuListener l : containerListeners) l.slotChanged(this, index, cur);
        }
    }

    private void synchronizeSlotToRemote(int index, ItemStack cur) {
        if (!suppressRemoteUpdates) {
            ItemStack remote = remoteSlots.get(index);
            if (!Stacks.matches(remote, cur)) {
                remoteSlots.set(index, cur);
                if (synchronizer != null) synchronizer.sendSlotChange(this, index, cur);
            }
        }
    }

    private void synchronizeDataSlotToRemote(int id, int value) {
        if (!suppressRemoteUpdates) {
            int remote = remoteDataSlots[id];
            if (remote != value) {
                remoteDataSlots[id] = value;
                if (synchronizer != null) synchronizer.sendDataChange(this, id, value);
            }
        }
    }

    private void synchronizeCarriedToRemote() {
        if (!suppressRemoteUpdates) {
            if (!Stacks.matches(carried, remoteCarried)) {
                remoteCarried = carried;
                if (synchronizer != null) synchronizer.sendCarriedChange(this, remoteCarried);
            }
        }
    }

    /** 동기기가 있으면 칸 하나를 바로 보낸다(제작 결과처럼 서버가 직접 알리는 변경 — 상태 번호는 동기기가 올린다). */
    public void sendSlotChange(int slot, ItemStack stack) {
        if (synchronizer != null) synchronizer.sendSlotChange(this, slot, stack);
    }

    /** 클라이언트가 이미 아는 값(클릭 보고·크리에이티브 칸)을 remote 로 받아들인다 — 되돌려 보내지 않는다. */
    public void setRemoteSlot(int index, ItemStack stack) { remoteSlots.set(index, stack); }

    /** setRemoteSlotNoCopy 의 범위 검사판(handleContainerClick 의 changedSlots 처리). */
    public void setRemoteSlotNoCopy(int index, ItemStack stack) {
        if (index >= 0 && index < remoteSlots.size()) remoteSlots.set(index, stack);
    }

    public void setRemoteCarried(ItemStack stack) { remoteCarried = stack; }

    public void suppressRemoteUpdates() { suppressRemoteUpdates = true; }

    public void resumeRemoteUpdates() { suppressRemoteUpdates = false; }

    public void setData(int id, int value) { dataSlots.get(id).set(value); }

    // ------------------------------------------------------------------ 클릭

    /** 서버 클릭 처리 진입점(바닐라 clicked). */
    public void clicked(int slotIndex, int button, ClickType type, MenuPlayer player) {
        doClick(slotIndex, button, type, player);
    }

    public static int getQuickcraftType(int button) { return button >> 2 & 3; }

    public static int getQuickcraftHeader(int button) { return button & 3; }

    public static int getQuickcraftMask(int header, int type) { return header & 3 | (type & 3) << 2; }

    public static boolean isValidQuickcraftType(int type, MenuPlayer player) {
        if (type == 0) return true;
        return type == 1 || type == 2 && player.isCreative();
    }

    protected void resetQuickCraft() {
        quickcraftStatus = 0;
        quickcraftSlots.clear();
    }

    public static boolean canItemQuickReplace(Slot slot, ItemStack stack, boolean ignoreSize) {
        boolean empty = slot == null || !slot.hasItem();
        if (!empty && Stacks.isSameItemSameComponents(stack, slot.getItem())) {
            return slot.getItem().count() + (ignoreSize ? 0 : stack.count()) <= Stacks.maxStack(stack);
        }
        return empty;
    }

    public static int getQuickCraftPlaceCount(Set<Slot> slots, int type, ItemStack stack) {
        return switch (type) {
            case 0 -> (int) Math.floor((float) stack.count() / slots.size());
            case 1 -> 1;
            case 2 -> Stacks.maxStack(stack);
            default -> stack.count();
        };
    }

    public boolean canDragTo(Slot slot) { return true; }

    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return true; }

    private void doClick(int slotIndex, int button, ClickType clickType, MenuPlayer player) {
        if (clickType == ClickType.QUICK_CRAFT) {
            int prevStatus = quickcraftStatus;
            quickcraftStatus = getQuickcraftHeader(button);
            if ((prevStatus != 1 || quickcraftStatus != 2) && prevStatus != quickcraftStatus) {
                resetQuickCraft();
            } else if (carried.isEmpty()) {
                resetQuickCraft();
            } else if (quickcraftStatus == 0) {
                quickcraftType = getQuickcraftType(button);
                if (isValidQuickcraftType(quickcraftType, player)) {
                    quickcraftStatus = 1;
                    quickcraftSlots.clear();
                } else {
                    resetQuickCraft();
                }
            } else if (quickcraftStatus == 1) {
                Slot slot = slots.get(slotIndex);
                ItemStack c = carried;
                if (canItemQuickReplace(slot, c, true) && slot.mayPlace(c)
                        && (quickcraftType == 2 || c.count() > quickcraftSlots.size()) && canDragTo(slot)) {
                    quickcraftSlots.add(slot);
                }
            } else if (quickcraftStatus == 2) {
                if (!quickcraftSlots.isEmpty()) {
                    if (quickcraftSlots.size() == 1) {
                        int only = quickcraftSlots.iterator().next().index;
                        resetQuickCraft();
                        doClick(only, quickcraftType, ClickType.PICKUP, player);
                        return;
                    }
                    ItemStack source = carried;
                    if (source.isEmpty()) {
                        resetQuickCraft();
                        return;
                    }
                    int remaining = carried.count();
                    for (Slot s : quickcraftSlots) {
                        ItemStack c = carried;
                        if (s != null && canItemQuickReplace(s, c, true) && s.mayPlace(c)
                                && (quickcraftType == 2 || c.count() >= quickcraftSlots.size()) && canDragTo(s)) {
                            int existing = s.hasItem() ? s.getItem().count() : 0;
                            int limit = Math.min(Stacks.maxStack(source), s.getMaxStackSize(source));
                            int place = Math.min(getQuickCraftPlaceCount(quickcraftSlots, quickcraftType, source) + existing, limit);
                            remaining -= place - existing;
                            s.setByPlayer(source.withCount(place));
                        }
                    }
                    setCarried(source.withCount(remaining));
                }
                resetQuickCraft();
            } else {
                resetQuickCraft();
            }
        } else if (quickcraftStatus != 0) {
            resetQuickCraft();
        } else if ((clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE) && (button == 0 || button == 1)) {
            boolean primary = button == 0;
            if (slotIndex == SLOT_CLICKED_OUTSIDE) {
                if (!carried.isEmpty()) {
                    if (primary) {
                        player.drop(carried, true);
                        setCarried(ItemStack.EMPTY);
                    } else {
                        player.drop(carried.withCount(1), true);
                        setCarried(carried.withCount(carried.count() - 1));
                    }
                }
            } else if (clickType == ClickType.QUICK_MOVE) {
                if (slotIndex < 0) return;
                Slot slot = slots.get(slotIndex);
                if (!slot.mayPickup(player)) return;
                ItemStack moved = quickMoveStack(player, slotIndex);
                while (!moved.isEmpty() && Stacks.isSameItem(slot.getItem(), moved)) {
                    moved = quickMoveStack(player, slotIndex);
                }
            } else {
                if (slotIndex < 0) return;
                Slot slot = slots.get(slotIndex);
                ItemStack slotItem = slot.getItem();
                ItemStack held = carried;
                if (!tryItemClickBehaviourOverride(player, primary, slot, slotItem, held)
                        && !allowsPickupAllOverride(player, primary, slot, slotItem, held)) {
                    if (slotItem.isEmpty()) {
                        if (!held.isEmpty()) {
                            int n = primary ? held.count() : 1;
                            setCarried(slot.safeInsert(held, n));
                        }
                    } else if (slot.mayPickup(player)) {
                        if (held.isEmpty()) {
                            int n = primary ? slotItem.count() : (slotItem.count() + 1) / 2;
                            Optional<ItemStack> taken = slot.tryRemove(n, Integer.MAX_VALUE, player);
                            taken.ifPresent(s -> {
                                setCarried(s);
                                slot.onTake(player, s);
                            });
                        } else if (slot.mayPlace(held)) {
                            if (Stacks.isSameItemSameComponents(slotItem, held)) {
                                int n = primary ? held.count() : 1;
                                setCarried(slot.safeInsert(held, n));
                            } else if (held.count() <= slot.getMaxStackSize(held)) {
                                setCarried(slotItem);
                                slot.setByPlayer(held);
                            }
                        } else if (Stacks.isSameItemSameComponents(slotItem, held)) {
                            Optional<ItemStack> taken = slot.tryRemove(slotItem.count(), Stacks.maxStack(held) - held.count(), player);
                            taken.ifPresent(s -> {
                                setCarried(held.withCount(held.count() + s.count()));
                                slot.onTake(player, s);
                            });
                        }
                    }
                }
                slot.setChanged();
            }
        } else if (clickType == ClickType.SWAP && (button >= 0 && button < 9 || button == 40)) {
            ItemStack invItem = player.inventoryItem(button);
            Slot slot = slots.get(slotIndex);
            ItemStack slotItem = slot.getItem();
            if (!invItem.isEmpty() || !slotItem.isEmpty()) {
                if (invItem.isEmpty()) {
                    if (slot.mayPickup(player)) {
                        player.setInventoryItem(button, slotItem);
                        slot.onSwapCraft(slotItem.count());
                        slot.setByPlayer(ItemStack.EMPTY);
                        slot.onTake(player, slotItem);
                    }
                } else if (slotItem.isEmpty()) {
                    if (slot.mayPlace(invItem)) {
                        int max = slot.getMaxStackSize(invItem);
                        if (invItem.count() > max) {
                            player.setInventoryItem(button, invItem.withCount(invItem.count() - max));
                            slot.setByPlayer(invItem.withCount(max));
                        } else {
                            player.setInventoryItem(button, ItemStack.EMPTY);
                            slot.setByPlayer(invItem);
                        }
                    }
                } else if (slot.mayPickup(player) && slot.mayPlace(invItem)) {
                    int max = slot.getMaxStackSize(invItem);
                    if (invItem.count() > max) {
                        player.setInventoryItem(button, invItem.withCount(invItem.count() - max));
                        slot.setByPlayer(invItem.withCount(max));
                        slot.onTake(player, slotItem);
                        ItemStack left = player.addToInventory(slotItem);
                        if (!left.isEmpty()) player.drop(left, true);
                    } else {
                        player.setInventoryItem(button, slotItem);
                        slot.setByPlayer(invItem);
                        slot.onTake(player, slotItem);
                    }
                }
            }
        } else if (clickType == ClickType.CLONE && player.isCreative() && carried.isEmpty() && slotIndex >= 0) {
            Slot slot = slots.get(slotIndex);
            if (slot.hasItem()) {
                ItemStack s = slot.getItem();
                setCarried(s.withCount(Stacks.maxStack(s)));
            }
        } else if (clickType == ClickType.THROW && carried.isEmpty() && slotIndex >= 0) {
            Slot slot = slots.get(slotIndex);
            int n = button == 0 ? 1 : slot.getItem().count();
            ItemStack taken = slot.safeTake(n, Integer.MAX_VALUE, player);
            player.drop(taken, true);
        } else if (clickType == ClickType.PICKUP_ALL && slotIndex >= 0) {
            Slot slot = slots.get(slotIndex);
            ItemStack held = carried;
            if (!held.isEmpty() && (!slot.hasItem() || !slot.mayPickup(player))) {
                int start = button == 0 ? 0 : slots.size() - 1;
                int step = button == 0 ? 1 : -1;
                for (int pass = 0; pass < 2; pass++) {
                    for (int i = start; i >= 0 && i < slots.size() && held.count() < Stacks.maxStack(held); i += step) {
                        Slot s = slots.get(i);
                        if (s.hasItem() && canItemQuickReplace(s, held, true) && s.mayPickup(player) && canTakeItemForPickAll(held, s)) {
                            ItemStack si = s.getItem();
                            if (pass != 0 || si.count() != Stacks.maxStack(si)) {
                                ItemStack taken = s.safeTake(si.count(), Stacks.maxStack(held) - held.count(), player);
                                held = held.withCount(held.count() + taken.count());
                                setCarried(held);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isBundle(ItemStack s) { return !s.isEmpty() && org.domi.furnace.server.item.ItemTools.itemName(s).equals("minecraft:bundle"); }

    /**
     * tryItemClickBehaviourOverride: 든 아이템이 가방이면 overrideStackedOnOther(가방을 든 채 칸을 우클릭), 아니면 칸의 아이템이 가방일 때
     * overrideOtherStackedOnMe(가방 칸을 다른 걸 든 채 우클릭). 가방은 기능 플래그 minecraft:bundle 이 켜져 있어야 한다(isItemEnabled).
     * 가방은 스택을 제자리에서 바꾸므로 여기서는 새 스택을 칸·커서에 다시 둔다.
     */
    private boolean tryItemClickBehaviourOverride(MenuPlayer player, boolean primary, Slot slot, ItemStack slotItem, ItemStack held) {
        if (primary) return false;   // ClickAction.PRIMARY 는 가방이 쓰지 않는다
        if (isBundle(held) && org.domi.furnace.server.data.FeatureFlags.isEnabled("minecraft:bundle")) {   // BundleItem.overrideStackedOnOther
            BundleContents c = BundleContents.of(held);
            if (c != null) {
                if (slotItem.isEmpty()) {
                    player.playBundleSound("minecraft:item.bundle.remove_one");
                    ItemStack one = c.removeOne();
                    if (one != null) c.tryInsert(slot.safeInsert(one));
                } else if (BundleContents.canFitInside(slotItem)) {
                    if (c.tryTransfer(slot, player) > 0) player.playBundleSound("minecraft:item.bundle.insert");
                }
                setCarried(c.applyTo(held));
                return true;
            }
        }
        if (isBundle(slotItem) && org.domi.furnace.server.data.FeatureFlags.isEnabled("minecraft:bundle") && slot.allowModification(player)) {   // overrideOtherStackedOnMe
            BundleContents c = BundleContents.of(slotItem);
            if (c != null) {
                ItemStack newCarried = held;
                if (held.isEmpty()) {
                    ItemStack one = c.removeOne();
                    if (one != null) {
                        player.playBundleSound("minecraft:item.bundle.remove_one");
                        newCarried = one;
                    }
                } else {
                    int n = c.tryInsert(held);
                    if (n > 0) {
                        player.playBundleSound("minecraft:item.bundle.insert");
                        newCarried = held.withCount(held.count() - n);
                    }
                }
                slot.set(c.applyTo(slotItem));
                setCarried(newCarried);
                return true;
            }
        }
        return false;
    }

    /** 전체 줍기(더블 클릭) 재정의 자리 — 해당 아이템이 없어 항상 거짓. */
    private boolean allowsPickupAllOverride(MenuPlayer player, boolean primary, Slot slot, ItemStack slotItem, ItemStack held) { return false; }

    // ------------------------------------------------------------------ 빠른 이동

    /** shift-클릭: 옮기고 남은(원래 칸에 있던) 스택을 돌려준다. 옮길 게 없으면 EMPTY. */
    public abstract ItemStack quickMoveStack(MenuPlayer player, int slotIndex);

    /** moveItemStackTo 의 결과: 뭔가 옮겼는가와 남은 스택. */
    public record Moved(boolean changed, ItemStack remaining) { }

    /** moveItemStackTo: stack 을 [start, end) 칸에 합치고(같은 아이템) 빈 칸에 넣는다. reverse 면 끝에서부터. */
    protected Moved moveItemStackTo(ItemStack stack, int start, int end, boolean reverse) {
        boolean changed = false;
        int i = reverse ? end - 1 : start;
        if (Stacks.isStackable(stack)) {
            while (!stack.isEmpty() && (reverse ? i >= start : i < end)) {
                Slot s = slots.get(i);
                ItemStack target = s.getItem();
                if (!target.isEmpty() && Stacks.isSameItemSameComponents(stack, target)) {
                    int sum = target.count() + stack.count();
                    int max = s.getMaxStackSize(target);
                    if (sum <= max) {
                        stack = ItemStack.EMPTY;
                        s.setCountInPlace(target.withCount(sum));
                        changed = true;
                    } else if (target.count() < max) {
                        stack = stack.withCount(stack.count() - (max - target.count()));
                        s.setCountInPlace(target.withCount(max));
                        changed = true;
                    }
                }
                i += reverse ? -1 : 1;
            }
        }
        if (!stack.isEmpty()) {
            i = reverse ? end - 1 : start;
            while (reverse ? i >= start : i < end) {
                Slot s = slots.get(i);
                ItemStack target = s.getItem();
                if (target.isEmpty() && s.mayPlace(stack)) {
                    int max = s.getMaxStackSize(stack);
                    int n = Math.min(stack.count(), max);
                    s.setByPlayer(stack.withCount(n));
                    stack = stack.withCount(stack.count() - n);
                    changed = true;
                    break;
                }
                i += reverse ? -1 : 1;
            }
        }
        return new Moved(changed, stack);
    }

    // ------------------------------------------------------------------ 열기·닫기

    /** 바닐라 clickMenuButton — container_button_click 을 처리했는가(서버가 상태를 바꿨으면 true → broadcastChanges). */
    public boolean clickMenuButton(MenuPlayer player, int id) { return false; }

    public abstract boolean stillValid(MenuPlayer player);

    /** 메뉴가 닫힐 때(removed): 커서에 든 것을 인벤토리로 돌려주거나(죽었으면 던진다) 비운다. */
    public void removed(MenuPlayer player) {
        ItemStack held = carried;
        if (!held.isEmpty()) {
            if (!player.isDeadOrDying() && !player.hasDisconnected()) player.placeItemBackInInventory(held);
            else player.drop(held, false);
            setCarried(ItemStack.EMPTY);
        }
    }

    /** clearContainer: 컨테이너 내용을 인벤토리로 돌려주거나(죽었거나 끊겼으면 던진다). */
    protected void clearContainer(MenuPlayer player, Container container) {
        boolean gone = player.isDeadOrDying() || player.hasDisconnected();
        for (int i = 0; i < container.size(); i++) {
            ItemStack s = container.removeNoUpdate(i);
            if (gone) player.drop(s, false);
            else player.placeItemBackInInventory(s);
        }
    }

    /** slotsChanged: 컨테이너가 바뀌었다(제작 격자 등이 재정의). */
    public void slotsChanged(Container container) { broadcastChanges(); }

    /** transferState: 다른 메뉴가 닫히며 같은 컨테이너·번호를 가진 칸의 동기 상태를 이어받는다. */
    public void transferState(AbstractContainerMenu other) {
        record Key(Container c, int idx) { }
        java.util.Map<Key, Integer> index = new java.util.HashMap<>();
        for (int i = 0; i < other.slots.size(); i++) {
            Slot s = other.slots.get(i);
            index.put(new Key(s.container, s.containerIndex()), i);
        }
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            Integer j = index.get(new Key(s.container, s.containerIndex()));
            if (j != null) {
                lastSlots.set(i, other.lastSlots.get(j));
                remoteSlots.set(i, other.remoteSlots.get(j));
            }
        }
    }

    /** 컨테이너 안의 번호로 이 메뉴의 칸 번호를 찾는다(없으면 -1). */
    public int findSlot(Container container, int containerIndex) {
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            if (s.container == container && s.containerIndex() == containerIndex) return i;
        }
        return -1;
    }

    @Override
    public String toString() { return getClass().getSimpleName() + "#" + containerId + Arrays.toString(new int[]{slots.size()}); }
}
