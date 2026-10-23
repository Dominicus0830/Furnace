// 바닐라 Criterion — 트리거 id + 조건. 조건 중 "player" 는 모든 트리거 공통(전리품 조건 목록 또는 엔티티 조건).
// 아직 서버에 그 사건이 없는 트리거(몹 처치·차원 이동 등)도 읽기는 하되 일치시키지 않는다.
package org.domi.furnace.server.advancement;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.loot.Bounds;
import org.domi.furnace.server.loot.EntityPredicate;
import org.domi.furnace.server.loot.ItemPredicate;
import org.domi.furnace.server.loot.LootBlockState;
import org.domi.furnace.server.loot.LootCondition;
import org.domi.furnace.server.loot.LootContext;
import org.domi.furnace.server.loot.LootParams;
import org.domi.furnace.server.loot.LootRegistries;
import org.domi.furnace.server.loot.LootStack;
import org.domi.furnace.server.loot.StateProperties;

public record Criterion(String trigger, Matcher matcher, List<LootCondition> player) {

    /** 트리거별 사건과 조건 비교(바닐라 SimpleInstance 의 matches). */
    public interface Matcher { boolean test(Object event, LootRegistries regs); }

    /** 바닐라 CriteriaTriggers 등록 순서(레지스트리 id 순). */
    public static final List<String> TRIGGERS = List.of("impossible", "player_killed_entity", "entity_killed_player", "enter_block",
            "inventory_changed", "recipe_unlocked", "player_hurt_entity", "entity_hurt_player", "enchanted_item", "filled_bucket",
            "brewed_potion", "construct_beacon", "used_ender_eye", "summoned_entity", "bred_animals", "location", "slept_in_bed",
            "cured_zombie_villager", "villager_trade", "item_durability_changed", "levitation", "changed_dimension", "tick", "tame_animal",
            "placed_block", "consume_item", "effects_changed", "used_totem", "nether_travel", "fishing_rod_hooked", "channeled_lightning",
            "shot_crossbow", "killed_by_crossbow", "hero_of_the_village", "voluntary_exile", "slide_down_block", "bee_nest_destroyed",
            "target_hit", "item_used_on_block", "default_block_use", "any_block_use", "player_generates_container_loot",
            "thrown_item_picked_up_by_entity", "thrown_item_picked_up_by_player", "player_interacted_with_entity", "started_riding",
            "lightning_strike", "using_item", "fall_from_height", "ride_entity_in_lava", "kill_mob_near_sculk_catalyst",
            "allay_drop_item_on_block", "avoid_vibration", "recipe_crafted", "crafter_recipe_crafted", "fall_after_explosion");

    private static final Set<String> PLAYER_TRIGGERS = Set.of("minecraft:location", "minecraft:slept_in_bed", "minecraft:tick",
            "minecraft:hero_of_the_village", "minecraft:voluntary_exile", "minecraft:avoid_vibration", "minecraft:started_riding");

    /** inventory_changed 사건: 플레이어 인벤토리 41칸과 막 바뀐 스택. */
    public record InventoryEvent(LootStack[] inventory, LootStack changed) {}

    /** recipe_crafted 사건: 쓴 레시피 id 와 제작 격자에 있던 스택들. */
    public record RecipeCraftedEvent(ResourceId recipe, List<LootStack> ingredients) {}

    /** placed_block·item_used_on_block 사건: ADVANCEMENT_LOCATION 문맥(위치·블록 상태·도구·플레이어). */
    public record LocationEvent(LootContext context) {}

    static Criterion parse(JsonElement e, LootRegistries regs) {
        JsonObject o = e.getAsJsonObject();
        if (!o.has("trigger")) throw new IllegalArgumentException("Missing \"trigger\" in: MapLike[" + o + "]");
        String trigger = ResourceId.parse(o.get("trigger").getAsString()).toString();
        if (!trigger.startsWith("minecraft:") || !TRIGGERS.contains(trigger.substring(10))) {
            throw new IllegalArgumentException("Unknown registry key in ResourceKey[minecraft:root / minecraft:trigger_type]: " + trigger);
        }
        JsonObject c = o.has("conditions") && o.get("conditions").isJsonObject() ? o.getAsJsonObject("conditions") : new JsonObject();
        List<LootCondition> player = c.has("player") ? playerPredicate(c.get("player"), regs) : List.of();
        return new Criterion(trigger, matcher(trigger, c, regs), player);
    }

    /** 바닐라 EntityPredicate.ADVANCEMENT_CODEC — 조건 목록이거나, 엔티티 조건 하나(this 대상 entity_properties). */
    static List<LootCondition> playerPredicate(JsonElement e, LootRegistries regs) {
        if (e.isJsonArray()) {
            List<LootCondition> l = new ArrayList<>();
            for (JsonElement x : e.getAsJsonArray()) l.add(LootCondition.parse(x, regs));
            return List.copyOf(l);
        }
        EntityPredicate p = EntityPredicate.parse(e, regs);
        return List.of(ctx -> p.matches(ctx, ctx.param(LootParams.ORIGIN), ctx.param(LootParams.THIS_ENTITY)));
    }

    private static Matcher matcher(String trigger, JsonObject c, LootRegistries regs) {
        if (PLAYER_TRIGGERS.contains(trigger)) return (ev, r) -> true;
        return switch (trigger) {
            case "minecraft:impossible" -> (ev, r) -> false;
            case "minecraft:recipe_crafted" -> {
                // 바닐라 RecipeCraftedTrigger.TriggerInstance.matches: 레시피 id 가 같고, 재료 조건마다 아직 안 쓴 스택 하나씩이 맞아야 한다
                if (!c.has("recipe_id")) throw new IllegalArgumentException("No key recipe_id in MapLike[" + c + "]");
                ResourceId recipe = ResourceId.parse(c.get("recipe_id").getAsString());
                List<ItemPredicate> ingredients = new ArrayList<>();
                if (c.has("ingredients")) for (JsonElement x : c.getAsJsonArray("ingredients")) ingredients.add(ItemPredicate.parse(x, regs));
                yield (ev, r) -> {
                    RecipeCraftedEvent e2 = (RecipeCraftedEvent) ev;
                    if (!recipe.equals(e2.recipe())) return false;
                    List<LootStack> remaining = new ArrayList<>(e2.ingredients());
                    for (ItemPredicate p : ingredients) {
                        boolean found = false;
                        var it = remaining.iterator();
                        while (it.hasNext()) {
                            if (p.test(it.next(), r)) { it.remove(); found = true; break; }
                        }
                        if (!found) return false;
                    }
                    return true;
                };
            }
            case "minecraft:recipe_unlocked" -> {
                if (!c.has("recipe")) throw new IllegalArgumentException("No key recipe in MapLike[" + c + "]");
                ResourceId recipe = ResourceId.parse(c.get("recipe").getAsString());
                yield (ev, r) -> recipe.equals(ev);
            }
            case "minecraft:inventory_changed" -> {
                JsonObject slots = c.has("slots") ? c.getAsJsonObject("slots") : new JsonObject();
                Bounds occupied = Bounds.parse(slots.get("occupied")), full = Bounds.parse(slots.get("full")), empty = Bounds.parse(slots.get("empty"));
                List<ItemPredicate> items = new ArrayList<>();
                if (c.has("items")) for (JsonElement x : c.getAsJsonArray("items")) items.add(ItemPredicate.parse(x, regs));
                yield (ev, r) -> {
                    InventoryEvent inv = (InventoryEvent) ev;
                    int f = 0, em = 0, oc = 0;
                    for (LootStack s : inv.inventory()) {
                        if (s == null || s.isEmpty()) em++;
                        else { oc++; if (s.count() >= s.maxStackSize()) f++; }
                    }
                    if (!full.matches(f) || !empty.matches(em) || !occupied.matches(oc)) return false;
                    if (items.isEmpty()) return true;
                    if (items.size() == 1) return inv.changed() != null && !inv.changed().isEmpty() && items.get(0).test(inv.changed(), r);
                    List<ItemPredicate> remaining = new ArrayList<>(items);
                    for (LootStack s : inv.inventory()) {
                        if (remaining.isEmpty()) return true;
                        if (s != null && !s.isEmpty()) remaining.removeIf(p -> p.test(s, r));
                    }
                    return remaining.isEmpty();
                };
            }
            case "minecraft:placed_block", "minecraft:item_used_on_block", "minecraft:allay_drop_item_on_block",
                 "minecraft:default_block_use", "minecraft:any_block_use" -> {
                List<LootCondition> location = c.has("location") ? playerPredicateList(c.get("location"), regs) : List.of();
                yield (ev, r) -> LootCondition.allOf(location, ((LocationEvent) ev).context());
            }
            case "minecraft:consume_item", "minecraft:using_item", "minecraft:filled_bucket", "minecraft:used_totem", "minecraft:shot_crossbow" -> {
                ItemPredicate item = c.has("item") ? ItemPredicate.parse(c.get("item"), regs) : null;
                yield (ev, r) -> item == null || item.test((LootStack) ev, r);
            }
            case "minecraft:enter_block" -> {
                String block = c.has("block") ? ResourceId.parse(c.get("block").getAsString()).toString() : null;
                java.util.Map<String, JsonElement> state = new java.util.LinkedHashMap<>();
                if (c.has("state")) for (var s : c.getAsJsonObject("state").entrySet()) state.put(s.getKey(), s.getValue());
                yield (ev, r) -> {
                    LootBlockState s = (LootBlockState) ev;
                    return (block == null || block.equals(s.block())) && (state.isEmpty() || StateProperties.matches(state, s));
                };
            }
            case "minecraft:changed_dimension" -> {
                String from = c.has("from") ? ResourceId.parse(c.get("from").getAsString()).toString() : null;
                String to = c.has("to") ? ResourceId.parse(c.get("to").getAsString()).toString() : null;
                yield (ev, r) -> {
                    String[] d = (String[]) ev;
                    return (from == null || from.equals(d[0])) && (to == null || to.equals(d[1]));
                };
            }
            default -> (ev, r) -> false;   // 사건이 아직 없는 트리거
        };
    }

    private static List<LootCondition> playerPredicateList(JsonElement e, LootRegistries regs) {
        List<LootCondition> l = new ArrayList<>();
        for (JsonElement x : e.getAsJsonArray()) l.add(LootCondition.parse(x, regs));
        return List.copyOf(l);
    }
}
