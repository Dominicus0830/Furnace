package net.minecraft.world.item.enchantment;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * 아이템에 붙은 마법 부여와 레벨(Mojang 이름 shim, 불변, NMS shim 5번 묶음). {@code ItemStack#getEnchantments()} 가 그 순간의 사본을 준다 — 고쳐도 아이템은
 * 안 바뀐다(바닐라도 불변이다). 읽기만 있다: Mutable·tooltip·코덱 멤버는 없다(호출 위치에서 실패). 순서는 이름순(Furnace 와 같다 — 바닐라는 넣은 순서).
 */
public class ItemEnchantments {

    public static final ItemEnchantments EMPTY = new ItemEnchantments(Map.of());

    /** 마법 부여 이름 → 레벨. */
    private final Map<ResourceKey<Object>, Integer> levels;

    private ItemEnchantments(Map<ResourceKey<Object>, Integer> levels) { this.levels = levels; }

    /** 호환 모듈 전용 — Furnace 값에서. */
    public static ItemEnchantments fromFurnace(org.domi.furnace.api.item.ItemEnchantments e) {
        if (e == null || e.isEmpty()) return EMPTY;
        Map<ResourceKey<Object>, Integer> m = new LinkedHashMap<>();
        for (var en : e.levels().entrySet()) m.put(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(en.getKey())), en.getValue());
        return new ItemEnchantments(m);
    }

    private static Holder<Enchantment> holder(ResourceKey<Object> key) {
        return new Holder.Reference<>(castKey(key), new Enchantment(key.location()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResourceKey<Enchantment> castKey(ResourceKey<Object> key) { return (ResourceKey) key; }

    public int getLevel(Holder<Enchantment> enchantment) {
        for (var e : levels.entrySet()) if (enchantment.is(castKey(e.getKey()))) return e.getValue();
        return 0;
    }

    public boolean isEmpty() { return levels.isEmpty(); }

    public int size() { return levels.size(); }

    public Set<Holder<Enchantment>> keySet() {
        Set<Holder<Enchantment>> out = new LinkedHashSet<>();
        for (var k : levels.keySet()) out.add(holder(k));
        return java.util.Collections.unmodifiableSet(out);
    }

    /** 바닐라 서명은 fastutil 의 {@code Set<Object2IntMap.Entry<Holder<Enchantment>>>}. fastutil 은 이 메서드를 부르는 플러그인만 닿는다. */
    public Set<it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Holder<Enchantment>>> entrySet() {
        Set<it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Holder<Enchantment>>> out = new LinkedHashSet<>();
        for (var e : levels.entrySet()) out.add(new it.unimi.dsi.fastutil.objects.AbstractObject2IntMap.BasicEntry<>(holder(e.getKey()), e.getValue()));
        return java.util.Collections.unmodifiableSet(out);
    }

    @Override
    public boolean equals(Object o) { return this == o || o instanceof ItemEnchantments i && levels.equals(i.levels); }

    @Override
    public int hashCode() { return levels.hashCode(); }

    @Override
    public String toString() { return "ItemEnchantments{enchantments=" + levels + "}"; }
}
