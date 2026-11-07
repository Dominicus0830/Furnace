// Bukkit ItemStack ↔ Furnace ItemStack 변환(호환 모듈 안에서만 쓴다).
package org.domi.furnace.compat.paper;

import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class CompatItems {

    private CompatItems() {}

    /** 평범한 ItemStack 의 새 칸(ItemStack 생성자용). */
    public static ItemSlot plainSlot(Material type, int amount) {
        if (type == null) throw new IllegalArgumentException("Material 이 null");
        return new PlainSlot(new org.domi.furnace.api.item.ItemStack(type.getKey().toString(), Math.max(0, amount), Map.of(), Set.of()), amount);
    }

    /** 복사 생성자용: 값과 개수(0·음수 포함)를 그대로 옮긴 새 칸. */
    public static ItemSlot copySlot(ItemStack other) { return new PlainSlot(other.asFurnace(), other.getAmount()); }

    /** 새 평범한 ItemStack. 종류가 아이템이 아니거나 공기면 Bukkit 처럼 그 종류 그대로 두고, 인벤토리에 넣을 때 빈 칸이 된다. */
    public static ItemStack plain(Material type, int amount) {
        return new ItemStack(plainSlot(type, amount));
    }

    /** Furnace → Bukkit 평범한 사본. 빈 스택이면 null 이 아니라 AIR 스택이다(칸에서 꺼낼 때는 {@link #mirror}). */
    public static ItemStack copyOf(org.domi.furnace.api.item.ItemStack s) { return copyOf(s, s.amount()); }

    /** 개수를 따로 정해 Furnace → Bukkit 평범한 사본(Bukkit 스택의 0·음수 개수를 복사할 때). */
    public static ItemStack copyOf(org.domi.furnace.api.item.ItemStack s, int amount) {
        return new ItemStack(new PlainSlot(s, amount));
    }

    /** 칸을 비추는 스택. 칸이 비었으면 null (Bukkit 의 getItem 처럼). */
    public static ItemStack mirror(ItemSlot slot) {
        return slot.get().isEmpty() ? null : new ItemStack(slot);
    }

    /** Bukkit → Furnace. null·공기·아이템이 아닌 종류·개수 0 이하는 EMPTY(칸을 비운다). */
    public static org.domi.furnace.api.item.ItemStack toFurnace(ItemStack s) {
        if (s == null) return org.domi.furnace.api.item.ItemStack.EMPTY;
        org.domi.furnace.api.item.ItemStack f = s.asFurnace();
        if (f.isEmpty() || s.getAmount() <= 0) return org.domi.furnace.api.item.ItemStack.EMPTY;
        Material m = Material.matchMaterial(f.type());
        if (m == null || !m.isItem()) return org.domi.furnace.api.item.ItemStack.EMPTY;
        return f;
    }

    /** Furnace 종류 키 → Material. */
    public static Material material(String itemType) {
        Material m = Material.matchMaterial(itemType);
        if (m == null) throw new UnsupportedFeature("Material " + itemType, "Bukkit Material 열거형에 없는 아이템이다(데이터 팩·플러그인 아이템)");
        return m;
    }
}
