// 바닐라 Slot — 메뉴의 칸 하나(컨테이너의 한 인덱스). ItemStack 이 불변이라 바닐라의 "스택을 그 자리에서 줄이는" 메서드는
// 새 스택을 돌려주는 형태로 바꿨다(safeInsert 가 남은 스택을 돌려준다).
package org.domi.furnace.server.menu;

import java.util.Optional;
import org.domi.furnace.server.entity.ItemStack;

public class Slot {

    public final Container container;
    private final int slot;
    /** 메뉴 안의 번호(AbstractContainerMenu.addSlot 이 붙인다). */
    public int index;

    public Slot(Container container, int slot) {
        this.container = container;
        this.slot = slot;
    }

    /** 컨테이너 안의 번호. */
    public int containerIndex() { return slot; }

    /** onQuickCraft(picked, original): 꺼낸 만큼을 알린다. */
    public void onQuickCraft(ItemStack picked, ItemStack original) {
        int n = original.count() - picked.count();
        if (n > 0) onQuickCraft(picked, n);
    }

    protected void onQuickCraft(ItemStack stack, int count) { }

    protected void onSwapCraft(int count) { }

    protected void checkTakeAchievements(ItemStack stack) { }

    /** onTake(player, stack): 플레이어가 꺼냈다. */
    public void onTake(MenuPlayer player, ItemStack stack) { setChanged(); }

    public boolean mayPlace(ItemStack stack) { return true; }

    public ItemStack getItem() { return container.get(slot); }

    public boolean hasItem() { return !getItem().isEmpty(); }

    /** setByPlayer(stack): 플레이어가 놓았다. */
    public void setByPlayer(ItemStack stack) { setByPlayer(stack, getItem()); }

    public void setByPlayer(ItemStack newStack, ItemStack oldStack) { set(newStack); }

    /** 칸 내용을 바꾼다(컨테이너 setItem + setChanged). */
    public void set(ItemStack stack) {
        container.set(slot, stack);
        setChanged();
    }

    /** 칸의 스택 개수만 바꾼다 — 바닐라의 in-place count 변경(컨테이너 setItem 부작용 없음). */
    public void setCountInPlace(ItemStack stack) {
        container.replaceInPlace(slot, stack);
        setChanged();
    }

    public void setChanged() { container.setChanged(); }

    public int getMaxStackSize() { return container.maxStackSize(); }

    public int getMaxStackSize(ItemStack stack) { return Math.min(getMaxStackSize(), Stacks.maxStack(stack)); }

    public ItemStack remove(int amount) { return container.remove(slot, amount); }

    public boolean mayPickup(MenuPlayer player) { return true; }

    public boolean isActive() { return true; }

    public boolean allowModification(MenuPlayer player) { return mayPickup(player) && mayPlace(getItem()); }

    /** tryRemove(amount, max, player). */
    public Optional<ItemStack> tryRemove(int amount, int max, MenuPlayer player) {
        if (!mayPickup(player)) return Optional.empty();
        if (!allowModification(player) && max < getItem().count()) return Optional.empty();
        amount = Math.min(amount, max);
        ItemStack removed = remove(amount);
        if (removed.isEmpty()) return Optional.empty();
        if (getItem().isEmpty()) setByPlayer(ItemStack.EMPTY, removed);
        return Optional.of(removed);
    }

    /** safeTake: tryRemove 하고 onTake 까지. 못 꺼냈으면 EMPTY. */
    public ItemStack safeTake(int amount, int max, MenuPlayer player) {
        Optional<ItemStack> r = tryRemove(amount, max, player);
        r.ifPresent(s -> onTake(player, s));
        return r.orElse(ItemStack.EMPTY);
    }

    /** safeInsert(stack): 가능한 만큼 넣고 남은 스택을 돌려준다. */
    public ItemStack safeInsert(ItemStack stack) { return safeInsert(stack, stack.count()); }

    public ItemStack safeInsert(ItemStack stack, int amount) {
        if (stack.isEmpty() || !mayPlace(stack)) return stack;
        ItemStack cur = getItem();
        int n = Math.min(Math.min(amount, stack.count()), getMaxStackSize(stack) - cur.count());
        if (cur.isEmpty()) {
            setByPlayer(stack.withCount(n));
            return stack.withCount(stack.count() - n);
        } else if (Stacks.isSameItemSameComponents(cur, stack)) {
            setByPlayer(cur.withCount(cur.count() + n));
            return stack.withCount(stack.count() - n);
        }
        return stack;
    }
}
