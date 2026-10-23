// 바닐라 Enchantment 정의 중 전리품이 쓰는 부분(데이터 팩 enchantment/*.json).
package org.domi.furnace.server.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

public record Enchantment(String id, int weight, int maxLevel, int minBase, int minPer, int maxBase, int maxPer,
                          HolderSet supportedItems, HolderSet primaryItems, HolderSet exclusiveSet, List<String> slots) {

    static Enchantment parse(String id, JsonObject o, LootRegistries regs) {
        JsonObject min = LootJson.req(o, "min_cost").getAsJsonObject(), max = LootJson.req(o, "max_cost").getAsJsonObject();
        List<String> slots = new ArrayList<>();
        for (JsonElement s : (JsonArray) LootJson.req(o, "slots")) slots.add(s.getAsString());
        return new Enchantment(id, LootJson.req(o, "weight").getAsInt(), LootJson.req(o, "max_level").getAsInt(),
                min.get("base").getAsInt(), min.get("per_level_above_first").getAsInt(),
                max.get("base").getAsInt(), max.get("per_level_above_first").getAsInt(),
                HolderSet.parse("minecraft:item", LootJson.req(o, "supported_items"), regs),
                o.has("primary_items") ? HolderSet.parse("minecraft:item", o.get("primary_items"), regs) : null,
                o.has("exclusive_set") ? HolderSet.parse("minecraft:enchantment", o.get("exclusive_set"), regs) : null,
                List.copyOf(slots));
    }

    public int minCost(int level) { return minBase + minPer * (level - 1); }

    public int maxCost(int level) { return maxBase + maxPer * (level - 1); }

    public boolean isSupportedItem(LootStack s, LootRegistries regs) { return supportedItems.contains(s.item(), regs); }

    public boolean isPrimaryItem(LootStack s, LootRegistries regs) {
        return isSupportedItem(s, regs) && (primaryItems == null || primaryItems.contains(s.item(), regs));
    }

    /** 바닐라 areCompatible. */
    public static boolean areCompatible(Enchantment a, Enchantment b, LootRegistries regs) {
        return !a.id.equals(b.id) && (a.exclusiveSet == null || !a.exclusiveSet.contains(b.id, regs))
                && (b.exclusiveSet == null || !b.exclusiveSet.contains(a.id, regs));
    }

    /** 바닐라 EquipmentSlotGroup.test. */
    public boolean matchingSlot(String slot) {
        for (String g : slots) {
            boolean m = switch (g) {
                case "any" -> true;
                case "hand" -> slot.equals("mainhand") || slot.equals("offhand");
                case "armor" -> slot.equals("feet") || slot.equals("legs") || slot.equals("chest") || slot.equals("head");
                default -> g.equals(slot);
            };
            if (m) return true;
        }
        return false;
    }
}
