// Bukkit ItemMeta — 아이템의 컴포넌트를 사본으로 들고 있다가 ItemStack#setItemMeta 때 돌려놓는다(Bukkit 과 같은 "사본" 규칙).
// 종류별 하위 형(가죽 방어구·머리·현수막 …, MetaKind)은 이 클래스를 이어받아 같은 컴포넌트 사본 위에서 자기 값을 읽고 쓴다.
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.craftbukkit.inventory.SerializableMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.persistence.PersistentDataContainer;
import org.domi.furnace.api.item.AttributeModifiers;
import org.domi.furnace.api.item.DyedColor;
import org.domi.furnace.api.item.ItemComponents;
import org.domi.furnace.api.item.ItemEnchantments;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.item.Unbreakable;
import org.domi.furnace.api.text.Text;

// Paper 의 CraftMetaItem 처럼 {@code ==: ItemMeta} 로 저장되고 SerializableMeta 가 되읽는다(ItemMetaSerialization)
@DelegateDeserialization(SerializableMeta.class)
public class CompatItemMeta implements ItemMeta, Damageable, Repairable {

    ItemStack work;
    final EnumSet<ItemFlag> flags;
    final ItemPdc pdc;

    CompatItemMeta(ItemStack work, EnumSet<ItemFlag> flags, ItemPdc pdc) {
        this.work = work;
        this.flags = flags;
        this.pdc = pdc;
    }

    /** 이 메타의 종류(하위 형이 덮어쓴다). */
    MetaKind kind() { return MetaKind.UNSPECIFIC; }

    /** 스택 종류에 맞는 하위 형으로, 스택의 지금 컴포넌트에서 사본을 만든다. 공기는 null(Paper 와 같다). */
    public static CompatItemMeta of(ItemStack stack) {
        if (stack.type().equals("minecraft:air")) return null;
        return of(MetaKind.of(stack.type()), stack);
    }

    /** 종류를 정해서 만든다(되읽기는 아이템 종류를 몰라 임시 종류 위에서 만든다). */
    public static CompatItemMeta of(MetaKind kind, ItemStack stack) {
        ItemStack w = new ItemStack(stack.type(), 1, stack.components(), stack.removedComponents());
        EnumSet<ItemFlag> flags = EnumSet.noneOf(ItemFlag.class);
        ItemEnchantments ench = w.get(ItemComponents.ENCHANTMENTS);
        if (ench != null && !ench.showInTooltip()) flags.add(ItemFlag.HIDE_ENCHANTS);
        AttributeModifiers attrs = w.get(ItemComponents.ATTRIBUTE_MODIFIERS);
        if (attrs != null && !attrs.showInTooltip()) flags.add(ItemFlag.HIDE_ATTRIBUTES);
        Unbreakable unb = w.get(ItemComponents.UNBREAKABLE);
        if (unb != null && !unb.showInTooltip()) flags.add(ItemFlag.HIDE_UNBREAKABLE);
        if (w.has(ItemComponents.HIDE_ADDITIONAL_TOOLTIP)) flags.add(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        DyedColor dye = w.get(ItemComponents.DYED_COLOR);
        if (dye != null && !dye.showInTooltip()) flags.add(ItemFlag.HIDE_DYE);
        return kind.create(w, flags, new ItemPdc(stack.persistentData()));
    }

    /** 이 메타를 stack 에 적용한 새 스택(종류·개수는 stack 그대로). */
    public ItemStack applyTo(ItemStack stack) {
        ItemStack r = new ItemStack(stack.type(), stack.amount(), work.components(), work.removedComponents());

        ItemEnchantments ench = r.get(ItemComponents.ENCHANTMENTS);
        if (flags.contains(ItemFlag.HIDE_ENCHANTS)) {
            r = r.with(ItemComponents.ENCHANTMENTS, (ench == null ? ItemEnchantments.EMPTY : ench).withShowInTooltip(false));
        } else if (ench != null && !ench.showInTooltip()) {
            r = ench.isEmpty() ? r.without(ItemComponents.ENCHANTMENTS) : r.with(ItemComponents.ENCHANTMENTS, ench.withShowInTooltip(true));
        }
        AttributeModifiers attrs = r.get(ItemComponents.ATTRIBUTE_MODIFIERS);
        if (flags.contains(ItemFlag.HIDE_ATTRIBUTES)) {
            r = r.with(ItemComponents.ATTRIBUTE_MODIFIERS, new AttributeModifiers((attrs == null ? AttributeModifiers.EMPTY : attrs).modifiers(), false));
        } else if (attrs != null && !attrs.showInTooltip()) {
            r = r.with(ItemComponents.ATTRIBUTE_MODIFIERS, new AttributeModifiers(attrs.modifiers(), true));
        }
        if (r.get(ItemComponents.UNBREAKABLE) != null) r = r.with(ItemComponents.UNBREAKABLE, new Unbreakable(!flags.contains(ItemFlag.HIDE_UNBREAKABLE)));
        DyedColor dye = r.get(ItemComponents.DYED_COLOR);
        if (dye != null) r = r.with(ItemComponents.DYED_COLOR, new DyedColor(dye.rgb(), !flags.contains(ItemFlag.HIDE_DYE)));
        boolean hideExtra = flags.contains(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (hideExtra && !r.has(ItemComponents.HIDE_ADDITIONAL_TOOLTIP)) r = r.with(ItemComponents.HIDE_ADDITIONAL_TOOLTIP);
        if (!hideExtra && r.has(ItemComponents.HIDE_ADDITIONAL_TOOLTIP)) r = r.without(ItemComponents.HIDE_ADDITIONAL_TOOLTIP);

        r = finishApply(r);
        if (pdc.dirty()) r = r.withPersistentData(pdc::applyTo);
        return r;
    }

    /** 종류별로 표시 항목을 컴포넌트에 반영한다(applyTo 끝, 영속 데이터를 쓰기 전). */
    ItemStack finishApply(ItemStack r) { return r; }

    // ------------------------------------------------------------------ 이름·설명

    @Override
    public boolean hasDisplayName() { return work.get(ItemComponents.CUSTOM_NAME) != null; }

    @Override
    public String getDisplayName() { return Compat.legacy(work.get(ItemComponents.CUSTOM_NAME)); }

    @Override
    public void setDisplayName(String name) {
        work = name == null ? work.without(ItemComponents.CUSTOM_NAME) : work.with(ItemComponents.CUSTOM_NAME, Compat.text(name));
    }

    @Override
    public boolean hasItemName() { return work.components().containsKey("minecraft:item_name"); }

    @Override
    public String getItemName() { return hasItemName() ? Compat.legacy(work.get(ItemComponents.ITEM_NAME)) : null; }

    @Override
    public void setItemName(String name) {
        work = name == null ? work.without(ItemComponents.ITEM_NAME) : work.with(ItemComponents.ITEM_NAME, Compat.text(name));
    }

    @Override
    public boolean hasLore() {
        List<Text> l = work.get(ItemComponents.LORE);
        return l != null && !l.isEmpty();
    }

    @Override
    public List<String> getLore() {
        List<Text> l = work.get(ItemComponents.LORE);
        if (l == null || l.isEmpty()) return null;
        List<String> out = new ArrayList<>(l.size());
        for (Text t : l) out.add(Compat.legacy(t));
        return out;
    }

    @Override
    public void setLore(List<String> lore) {
        if (lore == null || lore.isEmpty()) {
            work = work.without(ItemComponents.LORE);
            return;
        }
        List<Text> l = new ArrayList<>(lore.size());
        for (String s : lore) l.add(Compat.text(s == null ? "" : s));
        work = work.with(ItemComponents.LORE, l);
    }

    @Override
    public boolean hasCustomModelData() { return work.get(ItemComponents.CUSTOM_MODEL_DATA) != null; }

    @Override
    public int getCustomModelData() {
        Integer v = work.get(ItemComponents.CUSTOM_MODEL_DATA);
        if (v == null) throw new IllegalStateException("We don't have CustomModelData! Check hasCustomModelData first!");
        return v;
    }

    @Override
    public void setCustomModelData(Integer data) {
        work = data == null ? work.without(ItemComponents.CUSTOM_MODEL_DATA) : work.with(ItemComponents.CUSTOM_MODEL_DATA, data);
    }

    // ------------------------------------------------------------------ 마법 부여

    private ItemEnchantments enchants() {
        ItemEnchantments e = work.get(ItemComponents.ENCHANTMENTS);
        return e == null ? ItemEnchantments.EMPTY : e;
    }

    @Override
    public boolean hasEnchants() { return !enchants().isEmpty(); }

    @Override
    public boolean hasEnchant(Enchantment ench) { return enchants().level(ench.getKey().toString()) > 0; }

    @Override
    public int getEnchantLevel(Enchantment ench) { return enchants().level(ench.getKey().toString()); }

    @Override
    public Map<Enchantment, Integer> getEnchants() {
        Map<Enchantment, Integer> out = new LinkedHashMap<>();
        for (var e : enchants().levels().entrySet()) {
            Enchantment k = Enchantment.fromKey(e.getKey());
            if (k != null) out.put(k, e.getValue());
        }
        return out;
    }

    @Override
    public boolean addEnchant(Enchantment ench, int level, boolean ignoreLevelRestriction) {
        Objects.requireNonNull(ench, "Enchantment cannot be null");
        if (level < 1 || level > 255) return false;
        ItemEnchantments cur = enchants();
        String key = ench.getKey().toString();
        if (cur.level(key) == level) return false;
        work = work.with(ItemComponents.ENCHANTMENTS, cur.with(key, level));
        return true;
    }

    @Override
    public boolean removeEnchant(Enchantment ench) {
        ItemEnchantments cur = enchants();
        String key = ench.getKey().toString();
        if (cur.level(key) == 0) return false;
        ItemEnchantments next = cur.without(key);
        work = next.isEmpty() && next.showInTooltip() ? work.without(ItemComponents.ENCHANTMENTS) : work.with(ItemComponents.ENCHANTMENTS, next);
        return true;
    }

    // ------------------------------------------------------------------ 표시 항목

    /** 호환 계층이 숨길 수 없는 표시 항목(그 컴포넌트가 아이템에 있을 때만 문제다). */
    private String unsupportedComponent(ItemFlag f) {
        if (kind().handledComponents().contains("minecraft:trim") && f == ItemFlag.HIDE_ARMOR_TRIM) return null;   // 장식 메타가 직접 반영한다
        return switch (f) {
            case HIDE_DESTROYS -> "minecraft:can_break";
            case HIDE_PLACED_ON -> "minecraft:can_place_on";
            case HIDE_ARMOR_TRIM -> "minecraft:trim";
            default -> null;
        };
    }

    @Override
    public void addItemFlags(ItemFlag... itemFlags) {
        for (ItemFlag f : itemFlags) {
            String c = unsupportedComponent(f);
            if (c != null && work.components().containsKey(c)) {
                throw new UnsupportedFeature("ItemMeta#addItemFlags(" + f + ")", "the API cannot change the visibility of the " + c + " component yet");
            }
            flags.add(f);
        }
    }

    @Override
    public void removeItemFlags(ItemFlag... itemFlags) {
        for (ItemFlag f : itemFlags) {
            String c = unsupportedComponent(f);
            if (c != null && work.components().containsKey(c) && flags.contains(f)) {
                throw new UnsupportedFeature("ItemMeta#removeItemFlags(" + f + ")", "the API cannot change the visibility of the " + c + " component yet");
            }
            flags.remove(f);
        }
    }

    @Override
    public Set<ItemFlag> getItemFlags() { return java.util.Collections.unmodifiableSet(EnumSet.copyOf(flags)); }

    @Override
    public boolean hasItemFlag(ItemFlag flag) { return flags.contains(flag); }

    @Override
    public boolean isUnbreakable() { return work.get(ItemComponents.UNBREAKABLE) != null; }

    @Override
    public void setUnbreakable(boolean unbreakable) {
        if (!unbreakable) work = work.without(ItemComponents.UNBREAKABLE);
        else if (!isUnbreakable()) work = work.with(ItemComponents.UNBREAKABLE, new Unbreakable(true));
    }

    @Override
    public boolean isFireResistant() { return work.has(ItemComponents.FIRE_RESISTANT); }

    @Override
    public void setFireResistant(boolean fireResistant) {
        work = fireResistant ? work.with(ItemComponents.FIRE_RESISTANT) : work.without(ItemComponents.FIRE_RESISTANT);
    }

    @Override
    public boolean isHideTooltip() { return work.has(ItemComponents.HIDE_TOOLTIP); }

    @Override
    public void setHideTooltip(boolean hideTooltip) {
        work = hideTooltip ? work.with(ItemComponents.HIDE_TOOLTIP) : work.without(ItemComponents.HIDE_TOOLTIP);
    }

    @Override
    public boolean hasEnchantmentGlintOverride() { return work.get(ItemComponents.ENCHANTMENT_GLINT_OVERRIDE) != null; }

    @Override
    public Boolean getEnchantmentGlintOverride() {
        Boolean v = work.get(ItemComponents.ENCHANTMENT_GLINT_OVERRIDE);
        if (v == null) throw new IllegalStateException("We don't have an enchantment_glint_override! Check hasEnchantmentGlintOverride first!");
        return v;
    }

    @Override
    public void setEnchantmentGlintOverride(Boolean override) {
        work = override == null ? work.without(ItemComponents.ENCHANTMENT_GLINT_OVERRIDE) : work.with(ItemComponents.ENCHANTMENT_GLINT_OVERRIDE, override);
    }

    // ------------------------------------------------------------------ Damageable·Repairable

    @Override
    public boolean hasDamage() { return work.damage() > 0; }

    @Override
    public int getDamage() { return work.damage(); }

    @Override
    public void setDamage(int damage) { work = work.with(ItemComponents.DAMAGE, damage); }

    @Override
    public boolean hasRepairCost() { return getRepairCost() > 0; }

    @Override
    public int getRepairCost() {
        Integer v = work.get(ItemComponents.REPAIR_COST);
        return v == null ? 0 : v;
    }

    @Override
    public void setRepairCost(int cost) { work = work.with(ItemComponents.REPAIR_COST, cost); }

    // ------------------------------------------------------------------ 직렬화

    @Override
    public Map<String, Object> serialize() { return ItemMetaSerialization.serialize(this); }

    /** 직렬화 전용: 지금 컴포넌트(영속 데이터의 바뀐 것은 아직 안 들어 있다). */
    ItemStack work() { return work; }

    /** 직렬화 전용: 살아 있는 표시 항목 집합(읽을 때 채운다). */
    EnumSet<ItemFlag> flagSet() { return flags; }

    /** 직렬화 전용: 영속 데이터의 바뀐 것까지 반영한 컴포넌트. */
    ItemStack effective() { return pdc.dirty() ? work.withPersistentData(pdc::applyTo) : work; }

    // ------------------------------------------------------------------ 영속 데이터·같음·사본

    @Override
    public PersistentDataContainer getPersistentDataContainer() { return pdc; }

    @Override
    public CompatItemMeta clone() { return kind().create(work, EnumSet.copyOf(flags), pdc.copy()); }

    /**
     * 다른 종류의 메타로 옮긴다(Paper CraftItemFactory#asMetaFor). 같은 종류면 자기 자신. 일반 메타로 옮길 때는 지금 종류가 맡던 값을 버리고,
     * 일반 메타에서 옮길 때는 컴포넌트가 모두 따라간다(Paper 의 unhandled 가 그 컴포넌트를 들고 다닌다).
     */
    public CompatItemMeta asKind(MetaKind target) {
        if (target == kind() && target != MetaKind.UNSPECIFIC) return this;
        ItemStack w = work;
        if (kind() != MetaKind.UNSPECIFIC && kind() != target) {
            // 목적지 종류도 맡는 컴포넌트(가죽 말 갑옷 ↔ 가죽 방어구의 염색 색)는 남긴다 — Paper 는 이 옮김에서 색을 잃지만 같은 컴포넌트라 잃을 이유가 없다
            Map<String, String> comps = new java.util.TreeMap<>(w.components());
            comps.keySet().removeAll(kind().handledComponents());
            for (String id : target.handledComponents()) if (work.components().containsKey(id)) comps.put(id, work.components().get(id));
            w = new ItemStack(w.type(), w.amount(), comps, w.removedComponents());
        }
        return target.create(w, EnumSet.copyOf(flags), pdc.copy());
    }

    /** 아무것도 정하지 않은 메타인가(Paper CraftMetaItem#isEmpty). 비어 있으면 setItemMeta 가 아이템의 컴포넌트를 모두 지운다. */
    public boolean isEmpty() { return work.components().isEmpty() && work.removedComponents().isEmpty() && flags.isEmpty() && pdc.isEmpty(); }

    /** 이 메타를 붙일 수 있는 아이템 종류인가(Paper CraftMetaItem#applicableTo). 공기와 아이템이 아닌 것은 안 된다. */
    public boolean applicableTo(String itemType) {
        if (itemType.equals("minecraft:air")) return false;
        org.bukkit.Material m = org.bukkit.Material.matchMaterial(itemType);
        if (m != null && !m.isItem()) return false;
        return kind() == MetaKind.UNSPECIFIC || kind().accepts(MetaKind.of(itemType));
    }

    /** 컴포넌트를 정해진 값(SNBT)으로 둔다. */
    final void setComponent(String id, String snbt) { work = work.withComponent(id, snbt); }

    /**
     * 컴포넌트를 "정하지 않은" 상태로 되돌린다 — 값도 지움 표시도 없앤다(Paper 는 적용할 때 정한 것만 쓰므로 아이템 종류의 기본값이 남는다).
     * 기본값이 있는 컴포넌트(현수막의 빈 무늬 목록)에 지움 표시가 남으면 같은 현수막끼리 쌓이지 않는다.
     */
    final void clearComponent(String id) {
        if (!work.components().containsKey(id) && !work.removedComponents().contains(id)) return;
        Map<String, String> comps = new java.util.TreeMap<>(work.components());
        comps.remove(id);
        Set<String> removed = new java.util.TreeSet<>(work.removedComponents());
        removed.remove(id);
        work = new ItemStack(work.type(), work.amount(), comps, removed);
    }

    /** 직렬화 전용: 종류별 키를 맵에 적는다. */
    void serializeKind(Map<String, Object> out) {}

    /** 직렬화 전용: 맵의 종류별 키를 읽어 들인다. */
    void deserializeKind(Map<String, Object> map) {}

    @Override
    public boolean equals(Object o) {
        // 종류는 보지 않는다(Paper 도 한쪽의 종류별 값이 비어 있으면 종류가 달라도 같다): 컴포넌트가 같으면 아이템에 적용한 결과가 같다.
        // 설정·스트림에서 되읽은 메타는 아직 어느 아이템 종류에도 붙지 않았다
        return o instanceof CompatItemMeta m && m.work.components().equals(work.components()) && m.work.removedComponents().equals(work.removedComponents())
                && m.flags.equals(flags) && m.pdc.equals(pdc);
    }

    @Override
    public int hashCode() { return Objects.hash(work.components(), work.removedComponents(), flags, pdc); }

    @Override
    public String toString() { return "ItemMeta" + work.components() + flags; }
}
