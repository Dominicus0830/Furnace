package org.bukkit.inventory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;
import org.domi.furnace.api.item.ItemComponents;
import org.domi.furnace.api.item.ItemEnchantments;
import org.domi.furnace.compat.paper.CompatItemMeta;
import org.domi.furnace.compat.paper.CompatItems;
import org.domi.furnace.compat.paper.ItemSlot;

/**
 * 아이템 스택. 직접 만든 것은 값을 스스로 들고 있고, 인벤토리에서 꺼낸 것은 그 칸을 비추는 "거울"이라
 * 바꾸면 칸에 곧바로 반영된다(Bukkit 과 같다). 종류·개수·내구도·메타(이름·설명·마법 부여·영속 데이터)를 다룬다.
 * Material 로 나타낼 수 없는 아이템(데이터 팩·플러그인 아이템)의 getType 은 UnsupportedFeature 다.
 */
public class ItemStack implements Cloneable {

    private final ItemSlot slot;

    /** 호환 모듈 전용 — 칸에 붙은 스택. */
    public ItemStack(ItemSlot slot) { this.slot = Objects.requireNonNull(slot, "slot"); }

    public ItemStack(Material type) { this(type, 1); }

    public ItemStack(Material type, int amount) { this(CompatItems.plainSlot(type, amount)); }

    public ItemStack(ItemStack stack) {
        this(CompatItems.copySlot(Objects.requireNonNull(stack, "Cannot copy null stack")));
    }

    /** 호환 모듈 전용 — 지금 값(Furnace 쪽 불변 스택). */
    public org.domi.furnace.api.item.ItemStack asFurnace() { return slot.get(); }

    public Material getType() { return CompatItems.material(slot.get().type()); }

    public void setType(Material type) {
        Objects.requireNonNull(type, "Material cannot be null");
        var cur = slot.get();
        int amount = slot.amount();
        slot.set(new org.domi.furnace.api.item.ItemStack(type.getKey().toString(), Math.max(0, amount), cur.components(), cur.removedComponents()));
        if (amount < 0 || amount == 0) slot.setAmount(amount);
    }

    public int getAmount() { return slot.amount(); }

    public void setAmount(int amount) { slot.setAmount(amount); }

    public boolean isEmpty() { return getAmount() <= 0 || slot.get().isEmpty(); }

    /** 이 종류가 한 칸에 쌓이는 최대 개수(메타의 max_stack_size 가 있으면 그 값). */
    public int getMaxStackSize() {
        Material m = getType();
        if (!m.isItem()) return m.getMaxStackSize();
        Integer v = slot.get().get(ItemComponents.MAX_STACK_SIZE);
        return v != null ? v : m.getMaxStackSize();
    }

    public short getDurability() { return (short) slot.get().damage(); }

    public void setDurability(short durability) { slot.set(slot.get().with(ItemComponents.DAMAGE, (int) durability)); }

    // ------------------------------------------------------------------ 메타

    public boolean hasItemMeta() {
        var s = slot.get();
        return !s.components().isEmpty() || !s.removedComponents().isEmpty();
    }

    /** 메타의 사본. 바꾼 뒤 {@link #setItemMeta} 로 돌려놓아야 반영된다. */
    public ItemMeta getItemMeta() { return CompatItemMeta.of(slot.get()); }

    /** 메타를 넣는다. null 이면 메타(구성 요소)를 모두 없앤다. Furnace 메타가 아니면 IllegalArgumentException. */
    public boolean setItemMeta(ItemMeta meta) {
        var cur = slot.get();
        if (meta == null) {
            slot.set(new org.domi.furnace.api.item.ItemStack(cur.type(), cur.amount()));
            return true;
        }
        if (!(meta instanceof CompatItemMeta cm)) throw new IllegalArgumentException("Furnace 가 만든 ItemMeta 가 아니다: " + meta.getClass().getName());
        slot.set(cm.applyTo(cur));
        return true;
    }

    // ------------------------------------------------------------------ 마법 부여

    public Map<Enchantment, Integer> getEnchantments() {
        Map<Enchantment, Integer> out = new LinkedHashMap<>();
        ItemEnchantments e = slot.get().get(ItemComponents.ENCHANTMENTS);
        if (e == null) return out;
        for (var en : e.levels().entrySet()) {
            Enchantment k = Enchantment.fromKey(en.getKey());
            if (k != null) out.put(k, en.getValue());
        }
        return out;
    }

    public boolean containsEnchantment(Enchantment ench) { return getEnchantmentLevel(ench) > 0; }

    public int getEnchantmentLevel(Enchantment ench) {
        ItemEnchantments e = slot.get().get(ItemComponents.ENCHANTMENTS);
        return e == null ? 0 : e.level(ench.getKey().toString());
    }

    /** 레벨 1~255 이면 넣는다(Bukkit 이 하는 최대 레벨·적용 아이템 검사는 서버가 값을 알려 주지 않아 하지 않는다). */
    public void addEnchantment(Enchantment ench, int level) {
        Objects.requireNonNull(ench, "Enchantment cannot be null");
        if (level < 1 || level > 255) throw new IllegalArgumentException("Enchantment level is either too low or too high (given " + level + ", bounds are 1 to 255)");
        addUnsafeEnchantment(ench, level);
    }

    public void addEnchantments(Map<Enchantment, Integer> enchantments) {
        Objects.requireNonNull(enchantments, "Enchantments cannot be null");
        for (var e : enchantments.entrySet()) addEnchantment(e.getKey(), e.getValue());
    }

    public void addUnsafeEnchantment(Enchantment ench, int level) {
        Objects.requireNonNull(ench, "Enchantment cannot be null");
        ItemEnchantments cur = slot.get().get(ItemComponents.ENCHANTMENTS);
        slot.set(slot.get().with(ItemComponents.ENCHANTMENTS, (cur == null ? ItemEnchantments.EMPTY : cur).with(ench.getKey().toString(), level)));
    }

    public void addUnsafeEnchantments(Map<Enchantment, Integer> enchantments) {
        Objects.requireNonNull(enchantments, "Enchantments cannot be null");
        for (var e : enchantments.entrySet()) addUnsafeEnchantment(e.getKey(), e.getValue());
    }

    /** 지운 마법 부여의 이전 레벨(없었으면 0). */
    public int removeEnchantment(Enchantment ench) {
        ItemEnchantments cur = slot.get().get(ItemComponents.ENCHANTMENTS);
        int old = cur == null ? 0 : cur.level(ench.getKey().toString());
        if (old > 0) slot.set(slot.get().with(ItemComponents.ENCHANTMENTS, cur.without(ench.getKey().toString())));
        return old;
    }

    // ------------------------------------------------------------------ 같음

    /** 종류와 메타가 같은가(개수는 보지 않는다). */
    public boolean isSimilar(ItemStack stack) {
        if (stack == null) return false;
        if (stack == this) return true;
        return slot.get().isSimilar(stack.slot.get());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ItemStack stack)) return false;
        return getAmount() == stack.getAmount() && isSimilar(stack);
    }

    @Override
    public int hashCode() {
        var s = slot.get();
        return Objects.hash(s.type(), getAmount(), s.components(), s.removedComponents());
    }

    @Override
    public ItemStack clone() { return new ItemStack(this); }

    @Override
    public String toString() {
        var s = slot.get();
        return "ItemStack{" + s.type().substring(s.type().indexOf(':') + 1).toUpperCase(java.util.Locale.ROOT) + " x " + getAmount()
                + (hasItemMeta() ? ", " + s.components() : "") + "}";
    }
}
