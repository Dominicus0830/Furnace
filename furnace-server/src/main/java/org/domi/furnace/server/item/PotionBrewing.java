// 바닐라 PotionBrewing(양조 표): 병 종류 바꾸기(화약·용의 숨결)와 물약 바꾸기. 디컴파일 원본의 등록 순서를 그대로 옮겼다.
package org.domi.furnace.server.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;

public final class PotionBrewing {
    private PotionBrewing() { }

    private record ItemMix(String from, String ingredient, String to) { }
    private record PotionMix(String from, String ingredient, String to) { }

    private static final List<String> CONTAINERS = List.of("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion");
    private static final List<ItemMix> ITEM_MIXES = List.of(
            new ItemMix("minecraft:potion", "minecraft:gunpowder", "minecraft:splash_potion"),
            new ItemMix("minecraft:splash_potion", "minecraft:dragon_breath", "minecraft:lingering_potion"));
    private static final List<PotionMix> POTION_MIXES = new ArrayList<>();

    private static void mix(String from, String ingredient, String to) { POTION_MIXES.add(new PotionMix("minecraft:" + from, "minecraft:" + ingredient, "minecraft:" + to)); }

    /** 재료 → 물약: 물 + 재료 = 어색한/평범한 물약, 어색한 물약 + 재료 = 그 물약 (바닐라 addMix(ingredient, potion)). */
    private static void mix(String ingredient, String potion) {
        mix("water", ingredient, "mundane");
        mix("awkward", ingredient, potion);
    }

    static {
        mix("water", "glowstone_dust", "thick");
        mix("water", "redstone", "mundane");
        mix("water", "nether_wart", "awkward");
        mix("breeze_rod", "wind_charged");
        mix("slime_block", "oozing");
        mix("stone", "infested");
        mix("cobweb", "weaving");
        mix("awkward", "golden_carrot", "night_vision");
        mix("night_vision", "redstone", "long_night_vision");
        mix("night_vision", "fermented_spider_eye", "invisibility");
        mix("long_night_vision", "fermented_spider_eye", "long_invisibility");
        mix("invisibility", "redstone", "long_invisibility");
        mix("magma_cream", "fire_resistance");
        mix("fire_resistance", "redstone", "long_fire_resistance");
        mix("rabbit_foot", "leaping");
        mix("leaping", "redstone", "long_leaping");
        mix("leaping", "glowstone_dust", "strong_leaping");
        mix("leaping", "fermented_spider_eye", "slowness");
        mix("long_leaping", "fermented_spider_eye", "long_slowness");
        mix("slowness", "redstone", "long_slowness");
        mix("slowness", "glowstone_dust", "strong_slowness");
        mix("awkward", "turtle_helmet", "turtle_master");
        mix("turtle_master", "redstone", "long_turtle_master");
        mix("turtle_master", "glowstone_dust", "strong_turtle_master");
        mix("swiftness", "fermented_spider_eye", "slowness");
        mix("long_swiftness", "fermented_spider_eye", "long_slowness");
        mix("sugar", "swiftness");
        mix("swiftness", "redstone", "long_swiftness");
        mix("swiftness", "glowstone_dust", "strong_swiftness");
        mix("awkward", "pufferfish", "water_breathing");
        mix("water_breathing", "redstone", "long_water_breathing");
        mix("glistering_melon_slice", "healing");
        mix("healing", "glowstone_dust", "strong_healing");
        mix("healing", "fermented_spider_eye", "harming");
        mix("strong_healing", "fermented_spider_eye", "strong_harming");
        mix("harming", "glowstone_dust", "strong_harming");
        mix("poison", "fermented_spider_eye", "harming");
        mix("long_poison", "fermented_spider_eye", "harming");
        mix("strong_poison", "fermented_spider_eye", "strong_harming");
        mix("spider_eye", "poison");
        mix("poison", "redstone", "long_poison");
        mix("poison", "glowstone_dust", "strong_poison");
        mix("ghast_tear", "regeneration");
        mix("regeneration", "redstone", "long_regeneration");
        mix("regeneration", "glowstone_dust", "strong_regeneration");
        mix("blaze_powder", "strength");
        mix("strength", "redstone", "long_strength");
        mix("strength", "glowstone_dust", "strong_strength");
        mix("water", "fermented_spider_eye", "weakness");
        mix("weakness", "redstone", "long_weakness");
        mix("awkward", "phantom_membrane", "slow_falling");
        mix("slow_falling", "redstone", "long_slow_falling");
    }

    private static String name(ItemStack s) { return ItemTools.itemName(s); }

    /** 물약 아이템의 potion 종류(potion_contents.potion), 없으면 null. */
    public static String potionOf(ItemStack s) {
        return ItemTools.component(s, "minecraft:potion_contents") instanceof Map<?, ?> m && m.get("potion") instanceof String p ? p : null;
    }

    /** PotionContents.createItemStack(item, potion). */
    public static ItemStack createItemStack(String item, String potion) {
        Integer id = ItemContext.id("minecraft:item", item);
        if (id == null) return ItemStack.EMPTY;
        Map<String, Object> contents = new LinkedHashMap<>();
        contents.put("potion", potion);
        return ItemTools.with(new ItemStack(id, 1), "minecraft:potion_contents", contents);
    }

    /** 양조대 병 칸에 놓을 수 있는 아이템(물약 세 종과 유리병). */
    public static boolean isBottleSlotItem(ItemStack s) {
        String n = name(s);
        return CONTAINERS.contains(n) || n.equals("minecraft:glass_bottle");
    }

    private static boolean isContainer(ItemStack s) { return CONTAINERS.contains(name(s)); }

    /** isIngredient: 병 종류를 바꾸는 재료이거나 물약을 바꾸는 재료인가. */
    public static boolean isIngredient(ItemStack s) {
        String n = name(s);
        for (ItemMix m : ITEM_MIXES) if (m.ingredient().equals(n)) return true;
        for (PotionMix m : POTION_MIXES) if (m.ingredient().equals(n)) return true;
        return false;
    }

    /** hasMix(bottle, ingredient): 이 병에 이 재료를 넣으면 바뀌는가. */
    public static boolean hasMix(ItemStack bottle, ItemStack ingredient) {
        if (!isContainer(bottle)) return false;
        String bn = name(bottle), in = name(ingredient), potion = potionOf(bottle);
        for (ItemMix m : ITEM_MIXES) if (bn.equals(m.from()) && in.equals(m.ingredient())) return true;
        if (potion != null) for (PotionMix m : POTION_MIXES) if (potion.equals(m.from()) && in.equals(m.ingredient())) return true;
        return false;
    }

    /** mix(ingredient, bottle): 바뀐 병(안 바뀌면 그대로). */
    public static ItemStack mix(ItemStack ingredient, ItemStack bottle) {
        if (bottle.isEmpty()) return bottle;
        String potion = potionOf(bottle);
        if (potion == null) return bottle;
        String bn = name(bottle), in = name(ingredient);
        for (ItemMix m : ITEM_MIXES) if (bn.equals(m.from()) && in.equals(m.ingredient())) return createItemStack(m.to(), potion);
        for (PotionMix m : POTION_MIXES) if (potion.equals(m.from()) && in.equals(m.ingredient())) return createItemStack(bn, m.to());
        return bottle;
    }
}
