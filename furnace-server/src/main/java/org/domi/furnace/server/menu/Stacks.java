// 바닐라 ItemStack 의 메뉴가 쓰는 판정들(불변 ItemStack 위의 보조 함수).
package org.domi.furnace.server.menu;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemStackCodecs;

public final class Stacks {

    private Stacks() { }

    public static int maxStack(ItemStack s) { return ItemStackCodecs.maxStackSize(s); }

    /** ItemStack.isSameItem — 아이템 종류만 같으면 된다(컴포넌트·개수 무시). */
    public static boolean isSameItem(ItemStack a, ItemStack b) { return a.itemId() == b.itemId(); }

    /** ItemStack.isSameItemSameComponents. */
    public static boolean isSameItemSameComponents(ItemStack a, ItemStack b) {
        if (a.isEmpty()) return b.isEmpty();
        return !b.isEmpty() && a.sameItemSameComponents(b);
    }

    /** ItemStack.matches — 개수·컴포넌트까지 같다(빈 스택끼리도 같다). */
    public static boolean matches(ItemStack a, ItemStack b) {
        if (a == b) return true;
        if (a.isEmpty() && b.isEmpty()) return true;
        return a.count() == b.count() && isSameItemSameComponents(a, b);
    }

    /** isStackable: 최대 개수 > 1 이고 닳은 도구가 아니다. */
    public static boolean isStackable(ItemStack s) {
        if (s.isEmpty() || maxStack(s) <= 1) return false;
        return !(s.patch().entries().get("minecraft:damage") instanceof Number n && n.intValue() > 0);
    }

    /** copyWithCount. */
    public static ItemStack withCount(ItemStack s, int n) { return s.withCount(n); }
}
