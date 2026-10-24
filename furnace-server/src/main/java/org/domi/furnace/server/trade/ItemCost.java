// 바닐라 ItemCost: 거래 값 — 아이템 하나 + 개수 + (컴포넌트 조건). 지금은 컴포넌트 조건이 없는 값만 네트워크로 보낸다.
package org.domi.furnace.server.trade;

import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;

public record ItemCost(String item, int count, Map<String, Object> components) {

    public ItemCost(String item, int count) { this(item, count, Map.of()); }

    /** 아이템이 같고 조건 컴포넌트가 모두 같은 값이면 맞다(DataComponentPredicate). */
    public boolean test(ItemStack s) {
        if (s == null || s.isEmpty() || !item.equals(ItemTools.itemName(s))) return false;
        for (var e : components.entrySet()) if (!e.getValue().equals(ItemTools.component(s, e.getKey()))) return false;
        return true;
    }

    /** 이 값만큼의 스택(조건 컴포넌트를 채운 것). */
    public ItemStack stack() {
        Integer id = ItemContext.id("minecraft:item", item);
        if (id == null) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(id, count);
        for (var e : components.entrySet()) s = ItemTools.with(s, e.getKey(), e.getValue());
        return s;
    }

    public int maxStack() { return org.domi.furnace.server.item.ItemStackCodecs.maxStackSize(stack()); }
}
