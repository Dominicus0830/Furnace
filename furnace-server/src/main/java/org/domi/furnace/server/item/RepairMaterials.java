// 바닐라 Item.isValidRepairItem: 모루가 쓰는 "이 재료로 이 아이템을 고칠 수 있는가".
// 1.21.1 에는 repairable 컴포넌트가 없어 아이템 클래스(ArmorItem·TieredItem·Elytra·Mace·Shield)에 흩어져 있다 — 디컴파일 원본에서 옮긴 표.
package org.domi.furnace.server.item;

import java.util.List;
import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;

public final class RepairMaterials {
    private RepairMaterials() { }

    private static final List<String> TOOLS = List.of("sword", "pickaxe", "axe", "shovel", "hoe");
    private static final List<String> ARMOR = List.of("helmet", "chestplate", "leggings", "boots");

    /** 티어(TieredItem.getTier().getRepairIngredient) — 값이 '#' 로 시작하면 아이템 태그. */
    private static final Map<String, String> TIERS = Map.of("wooden", "#minecraft:planks", "stone", "#minecraft:stone_tool_materials",
            "iron", "minecraft:iron_ingot", "golden", "minecraft:gold_ingot", "diamond", "minecraft:diamond", "netherite", "minecraft:netherite_ingot");

    /** 갑옷 재질(ArmorMaterial.repairIngredient). */
    private static final Map<String, String> ARMOR_MATERIALS = Map.of("leather", "minecraft:leather", "chainmail", "minecraft:iron_ingot",
            "iron", "minecraft:iron_ingot", "golden", "minecraft:gold_ingot", "diamond", "minecraft:diamond", "turtle", "minecraft:turtle_scute",
            "netherite", "minecraft:netherite_ingot");

    public static boolean isValidRepairItem(ItemStack tool, ItemStack repair) {
        String n = ItemTools.itemName(tool);
        String ingredient = ingredientFor(n.startsWith("minecraft:") ? n.substring(10) : n);
        if (ingredient == null) return false;
        if (ingredient.startsWith("#")) {
            Integer id = ItemContext.id("minecraft:item", ItemTools.itemName(repair));
            return id != null && ItemContext.isInTag("minecraft:item", ingredient.substring(1), id);
        }
        return ingredient.equals(ItemTools.itemName(repair));
    }

    private static String ingredientFor(String path) {
        int us = path.indexOf('_');
        if (us > 0) {
            String material = path.substring(0, us), kind = path.substring(us + 1);
            if (TOOLS.contains(kind) && TIERS.containsKey(material)) return TIERS.get(material);
            if (ARMOR.contains(kind) && ARMOR_MATERIALS.containsKey(material)) return ARMOR_MATERIALS.get(material);
        }
        return switch (path) {
            case "elytra" -> "minecraft:phantom_membrane";
            case "mace" -> "minecraft:breeze_rod";
            case "shield" -> "#minecraft:planks";
            case "wolf_armor" -> "minecraft:armadillo_scute";
            default -> null;
        };
    }
}
