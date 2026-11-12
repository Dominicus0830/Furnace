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

    /** player_killed_entity·entity_killed_player 사건: 상대 문맥, 플레이어 문맥·위치, 죽인 피해. */
    public record KillEvent(LootContext entity, LootContext player, double[] playerPos, org.domi.furnace.server.loot.LootDamageSource source) {}

    /** bred_animals 사건: 부모·짝·새끼 문맥(새끼가 없으면 null). */
    public record BreedEvent(LootContext parent, LootContext partner, LootContext child) {}

    /** villager_trade 사건: 상인 문맥과 거래 결과 스택. */
    public record TradeEvent(LootContext villager, LootStack item) {}

    /** enchanted_item(levels)·item_durability_changed(새 피해 값): 아이템과 정수 하나. */
    public record ItemIntEvent(LootStack item, int value) {}

    /** levitation·nether_travel·fall_from_height·ride_entity_in_lava·fall_after_explosion: 시작 위치, 지금 위치, 플레이어 문맥, 지속 틱, 원인 문맥(없으면 null). */
    public record MovedEvent(double[] start, double[] now, LootContext ctx, int duration, LootContext cause) {}

    /** player_hurt_entity·entity_hurt_player: 피해 입은 쪽 문맥(entity_hurt_player 는 null), 플레이어 문맥·위치, 피해 원인, 원래·실제 피해량, 막았나. */
    public record HurtEvent(LootContext entity, LootContext player, double[] playerPos,
                            org.domi.furnace.server.loot.LootDamageSource source, float dealt, float taken, boolean blocked) {}

    /** fishing_rod_hooked: 낚싯대, 걸린 엔티티 문맥, 걸린 것이 아이템 엔티티면 그 스택(아니면 null), 건진 전리품들. */
    public record FishingEvent(LootStack rod, LootContext hooked, LootStack hookedItem, List<LootStack> loot) {}

    /** channeled_lightning·killed_by_crossbow: 피해자 문맥들(killed_by_crossbow 는 서로 다른 종류 수도). */
    public record VictimsEvent(List<LootContext> victims, int uniqueTypes) {}

    /** target_hit: 투사체 문맥과 신호 세기. */
    public record TargetHitEvent(LootContext projectile, int signal) {}

    /** thrown_item_picked_up_*·player_interacted_with_entity: 아이템과 엔티티 문맥(null 일 수 있다). */
    public record ItemEntityEvent(LootStack item, LootContext entity) {}

    /** lightning_strike: 번개 문맥과 근처 구경꾼 문맥들. */
    public record LightningEvent(LootContext lightning, List<LootContext> bystanders) {}

    /** bee_nest_destroyed: 부순 블록 상태, 쓴 도구, 안에 있던 벌 수. */
    public record BeeEvent(LootBlockState block, LootStack tool, int bees) {}

    /** effects_changed: 플레이어 문맥(효과를 본다)과 효과를 준 쪽 문맥(없으면 null). */
    public record EffectsEvent(LootContext player, LootContext source) {}

    /** cured_zombie_villager: 좀비 문맥과 주민 문맥. */
    public record CuredEvent(LootContext zombie, LootContext villager) {}

    static Criterion parse(JsonElement e, LootRegistries regs) {
        JsonObject o = e.getAsJsonObject();
        if (!o.has("trigger")) throw new IllegalArgumentException("Missing \"trigger\" in: MapLike[" + o + "]");
        String trigger = ResourceId.parse(o.get("trigger").getAsString()).toString();
        if (!trigger.startsWith("minecraft:") || !TRIGGERS.contains(trigger.substring(10))) {
            throw new IllegalArgumentException("Unknown registry key in ResourceKey[minecraft:root / minecraft:trigger_type]: " + trigger);
        }
        JsonObject c = o.has("conditions") && o.get("conditions").isJsonObject() ? o.getAsJsonObject("conditions") : new JsonObject();
        // player 조건은 소유 스레드의 살아 있는 보기로 평가한다 — looking_at 을 스냅샷에서 미리 뜨게 만들지 않는다
        List<LootCondition> player = c.has("player") ? EntityPredicate.liveViewOnly(() -> playerPredicate(c.get("player"), regs)) : List.of();
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
            case "minecraft:recipe_crafted", "minecraft:crafter_recipe_crafted" -> {
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
            case "minecraft:player_killed_entity", "minecraft:entity_killed_player", "minecraft:kill_mob_near_sculk_catalyst" -> {
                // 바닐라 KilledTrigger.TriggerInstance.matches: killing_blow 먼저, 그다음 entity
                List<LootCondition> entity = c.has("entity") ? playerPredicate(c.get("entity"), regs) : List.of();
                org.domi.furnace.server.loot.DamageSourcePredicate blow =
                        c.has("killing_blow") ? org.domi.furnace.server.loot.DamageSourcePredicate.parse(c.get("killing_blow"), regs) : null;
                yield (ev, r) -> {
                    KillEvent k = (KillEvent) ev;
                    if (blow != null && !blow.matches(k.player(), k.playerPos(), k.source())) return false;
                    return entity.isEmpty() || LootCondition.allOf(entity, k.entity());
                };
            }
            case "minecraft:used_ender_eye" -> {
                // 바닐라 UsedEnderEyeTrigger.TriggerInstance.matches: distance.matchesSqr(수평 거리 제곱)
                Bounds distance = c.has("distance") ? Bounds.parse(c.get("distance")) : Bounds.ANY;
                yield (ev, r) -> {
                    double sq = (Double) ev;
                    return (distance.min() == null || distance.min() * distance.min() <= sq) && (distance.max() == null || sq <= distance.max() * distance.max());
                };
            }
            case "minecraft:tame_animal" -> {
                List<LootCondition> entity = c.has("entity") ? playerPredicate(c.get("entity"), regs) : List.of();
                yield (ev, r) -> entity.isEmpty() || LootCondition.allOf(entity, (LootContext) ev);
            }
            case "minecraft:bred_animals" -> {
                // 바닐라 BredAnimalsTrigger.TriggerInstance.matches: child 먼저, 그다음 (parent, partner) 또는 뒤바꾼 짝
                List<LootCondition> parent = c.has("parent") ? playerPredicate(c.get("parent"), regs) : null;
                List<LootCondition> partner = c.has("partner") ? playerPredicate(c.get("partner"), regs) : null;
                List<LootCondition> child = c.has("child") ? playerPredicate(c.get("child"), regs) : null;
                yield (ev, r) -> {
                    BreedEvent b = (BreedEvent) ev;
                    if (child != null && (b.child() == null || !LootCondition.allOf(child, b.child()))) return false;
                    return opt(parent, b.parent()) && opt(partner, b.partner()) || opt(parent, b.partner()) && opt(partner, b.parent());
                };
            }
            case "minecraft:villager_trade" -> {
                List<LootCondition> villager = c.has("villager") ? playerPredicate(c.get("villager"), regs) : null;
                ItemPredicate item = c.has("item") ? ItemPredicate.parse(c.get("item"), regs) : null;
                yield (ev, r) -> {
                    TradeEvent t = (TradeEvent) ev;
                    if (villager != null && !LootCondition.allOf(villager, t.villager())) return false;
                    return item == null || item.test(t.item(), r);
                };
            }
            case "minecraft:enter_block", "minecraft:slide_down_block" -> {
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
            default -> more(trigger, c, regs);
        };
    }

    private static boolean opt(List<LootCondition> p, LootContext ctx) { return p == null || LootCondition.allOf(p, ctx); }

    private static List<LootCondition> playerPredicateList(JsonElement e, LootRegistries regs) {
        List<LootCondition> l = new ArrayList<>();
        for (JsonElement x : e.getAsJsonArray()) l.add(LootCondition.parse(x, regs));
        return List.copyOf(l);
    }

    private static List<LootCondition> cond(JsonObject c, String key, LootRegistries regs) {
        return c.has(key) ? playerPredicate(c.get(key), regs) : null;
    }

    private static ItemPredicate item(JsonObject c, String key, LootRegistries regs) {
        return c.has(key) ? ItemPredicate.parse(c.get(key), regs) : null;
    }

    private static boolean itemOk(ItemPredicate p, LootStack s, LootRegistries r) { return p == null || p.test(s, r); }

    /** 이동 계열 공통: start_position·distance(·cause). */
    private static Matcher moved(JsonObject c, LootRegistries regs, boolean cause) {
        org.domi.furnace.server.loot.LocationPredicate start = c.has("start_position")
                ? org.domi.furnace.server.loot.LocationPredicate.parse(c.get("start_position"), regs) : null;
        Bounds[] distance = c.has("distance") ? EntityPredicate.parseDistance(c.get("distance")) : null;
        List<LootCondition> causeCond = cause ? cond(c, "cause", regs) : null;
        return (ev, r) -> {
            MovedEvent m = (MovedEvent) ev;
            if (start != null && !start.matches(m.ctx(), m.start())) return false;
            if (distance != null && !EntityPredicate.matchesDistance(distance, m.start(), m.now())) return false;
            return causeCond == null || m.cause() != null && LootCondition.allOf(causeCond, m.cause());
        };
    }

    /** 바닐라 DamagePredicate (dealt·taken·source_entity·blocked·type). */
    private static java.util.function.Predicate<HurtEvent> damage(JsonElement e, LootRegistries regs) {
        JsonObject o = e.getAsJsonObject();
        Bounds dealt = Bounds.parse(o.get("dealt")), taken = Bounds.parse(o.get("taken"));
        EntityPredicate src = o.has("source_entity") ? EntityPredicate.parse(o.get("source_entity"), regs) : null;
        Boolean blocked = o.has("blocked") ? o.get("blocked").getAsBoolean() : null;
        org.domi.furnace.server.loot.DamageSourcePredicate type =
                o.has("type") ? org.domi.furnace.server.loot.DamageSourcePredicate.parse(o.get("type"), regs) : null;
        return h -> {
            if (!dealt.matches(h.dealt()) || !taken.matches(h.taken())) return false;
            if (src != null && !src.matches(h.player(), h.playerPos(), h.source().entity())) return false;
            if (blocked != null && blocked != h.blocked()) return false;
            return type == null || type.matches(h.player(), h.playerPos(), h.source());
        };
    }

    /** 사건 클래스가 따로 필요한 나머지 트리거들. 모르는 트리거는 늘 거짓. */
    private static Matcher more(String trigger, JsonObject c, LootRegistries regs) {
        return switch (trigger) {
            case "minecraft:construct_beacon" -> {
                Bounds level = Bounds.parse(c.get("level"));
                yield (ev, r) -> level.matches((Integer) ev);
            }
            case "minecraft:enchanted_item" -> {
                ItemPredicate item = item(c, "item", regs);
                Bounds levels = Bounds.parse(c.get("levels"));
                yield (ev, r) -> itemOk(item, ((ItemIntEvent) ev).item(), r) && levels.matches(((ItemIntEvent) ev).value());
            }
            case "minecraft:item_durability_changed" -> {
                ItemPredicate item = item(c, "item", regs);
                Bounds durability = Bounds.parse(c.get("durability")), delta = Bounds.parse(c.get("delta"));
                yield (ev, r) -> {
                    ItemIntEvent i = (ItemIntEvent) ev;
                    if (!itemOk(item, i.item(), r)) return false;
                    // 바닐라: durability = 최대 - 새 피해, delta = 지금 피해 - 새 피해(깎이면 음수)
                    return durability.matches(i.item().maxDamage() - i.value()) && delta.matches(i.item().damage() - i.value());
                };
            }
            case "minecraft:brewed_potion" -> {
                String potion = c.has("potion") ? ResourceId.parse(c.get("potion").getAsString()).toString() : null;
                yield (ev, r) -> potion == null || potion.equals(ev);
            }
            case "minecraft:summoned_entity" -> {
                List<LootCondition> entity = cond(c, "entity", regs);
                yield (ev, r) -> entity == null || LootCondition.allOf(entity, (LootContext) ev);
            }
            case "minecraft:cured_zombie_villager" -> {
                List<LootCondition> zombie = cond(c, "zombie", regs), villager = cond(c, "villager", regs);
                yield (ev, r) -> {
                    CuredEvent e = (CuredEvent) ev;
                    return opt(zombie, e.zombie()) && opt(villager, e.villager());
                };
            }
            case "minecraft:levitation" -> {
                Bounds[] distance = c.has("distance") ? EntityPredicate.parseDistance(c.get("distance")) : null;
                Bounds duration = Bounds.parse(c.get("duration"));
                yield (ev, r) -> {
                    MovedEvent m = (MovedEvent) ev;
                    return (distance == null || EntityPredicate.matchesDistance(distance, m.start(), m.now())) && duration.matches(m.duration());
                };
            }
            case "minecraft:nether_travel", "minecraft:fall_from_height", "minecraft:ride_entity_in_lava" -> moved(c, regs, false);
            case "minecraft:fall_after_explosion" -> moved(c, regs, true);
            case "minecraft:effects_changed" -> {
                EntityPredicate effects = c.has("effects") ? effectsOnly(c.get("effects"), regs) : null;
                List<LootCondition> source = cond(c, "source", regs);
                yield (ev, r) -> {
                    EffectsEvent e = (EffectsEvent) ev;
                    if (effects != null) {
                        var pc = e.player();
                        if (!effects.matches(pc, pc.param(LootParams.ORIGIN), pc.param(LootParams.THIS_ENTITY))) return false;
                    }
                    return source == null || e.source() != null && LootCondition.allOf(source, e.source());
                };
            }
            case "minecraft:fishing_rod_hooked" -> {
                ItemPredicate rod = item(c, "rod", regs), item = item(c, "item", regs);
                List<LootCondition> entity = cond(c, "entity", regs);
                yield (ev, r) -> {
                    FishingEvent f = (FishingEvent) ev;
                    if (!itemOk(rod, f.rod(), r)) return false;
                    if (entity != null && !LootCondition.allOf(entity, f.hooked())) return false;
                    if (item == null) return true;
                    if (f.hookedItem() != null && item.test(f.hookedItem(), r)) return true;
                    for (LootStack s : f.loot()) if (item.test(s, r)) return true;
                    return false;
                };
            }
            case "minecraft:channeled_lightning" -> {
                List<List<LootCondition>> victims = condList(c, "victims", regs);
                yield (ev, r) -> {
                    VictimsEvent v = (VictimsEvent) ev;
                    for (var want : victims) {
                        boolean any = false;
                        for (LootContext x : v.victims()) if (LootCondition.allOf(want, x)) { any = true; break; }
                        if (!any) return false;
                    }
                    return true;
                };
            }
            case "minecraft:killed_by_crossbow" -> {
                List<List<LootCondition>> victims = condList(c, "victims", regs);
                Bounds unique = Bounds.parse(c.get("unique_entity_types"));
                yield (ev, r) -> {
                    VictimsEvent v = (VictimsEvent) ev;
                    List<LootContext> left = new ArrayList<>(v.victims());
                    for (var want : victims) {   // 조건마다 아직 안 쓴 피해자 하나씩
                        boolean found = false;
                        var it = left.iterator();
                        while (it.hasNext()) {
                            if (LootCondition.allOf(want, it.next())) { it.remove(); found = true; break; }
                        }
                        if (!found) return false;
                    }
                    return unique.matches(v.uniqueTypes());
                };
            }
            case "minecraft:bee_nest_destroyed" -> {
                String block = c.has("block") ? ResourceId.parse(c.get("block").getAsString()).toString() : null;
                ItemPredicate item = item(c, "item", regs);
                Bounds bees = Bounds.parse(c.get("num_bees_inside"));
                yield (ev, r) -> {
                    BeeEvent b = (BeeEvent) ev;
                    if (block != null && !block.equals(b.block().block())) return false;
                    return itemOk(item, b.tool(), r) && bees.matches(b.bees());
                };
            }
            case "minecraft:target_hit" -> {
                Bounds signal = Bounds.parse(c.get("signal_strength"));
                List<LootCondition> projectile = cond(c, "projectile", regs);
                yield (ev, r) -> {
                    TargetHitEvent t = (TargetHitEvent) ev;
                    return signal.matches(t.signal()) && (projectile == null || LootCondition.allOf(projectile, t.projectile()));
                };
            }
            case "minecraft:player_generates_container_loot" -> {
                if (!c.has("loot_table")) throw new IllegalArgumentException("No key loot_table in MapLike[" + c + "]");
                String table = ResourceId.parse(c.get("loot_table").getAsString()).toString();
                yield (ev, r) -> table.equals(ev);
            }
            case "minecraft:thrown_item_picked_up_by_entity", "minecraft:thrown_item_picked_up_by_player",
                 "minecraft:player_interacted_with_entity" -> {
                ItemPredicate item = item(c, "item", regs);
                List<LootCondition> entity = cond(c, "entity", regs);
                yield (ev, r) -> {
                    ItemEntityEvent i = (ItemEntityEvent) ev;
                    return itemOk(item, i.item(), r) && (entity == null || i.entity() != null && LootCondition.allOf(entity, i.entity()));
                };
            }
            case "minecraft:lightning_strike" -> {
                List<LootCondition> lightning = cond(c, "lightning", regs), bystander = cond(c, "bystander", regs);
                yield (ev, r) -> {
                    LightningEvent l = (LightningEvent) ev;
                    if (lightning != null && !LootCondition.allOf(lightning, l.lightning())) return false;
                    if (bystander == null) return true;
                    for (LootContext b : l.bystanders()) if (LootCondition.allOf(bystander, b)) return true;
                    return false;
                };
            }
            case "minecraft:player_hurt_entity" -> {
                var damage = c.has("damage") ? damage(c.get("damage"), regs) : null;
                List<LootCondition> entity = cond(c, "entity", regs);
                yield (ev, r) -> {
                    HurtEvent h = (HurtEvent) ev;
                    return (damage == null || damage.test(h)) && (entity == null || LootCondition.allOf(entity, h.entity()));
                };
            }
            case "minecraft:entity_hurt_player" -> {
                var damage = c.has("damage") ? damage(c.get("damage"), regs) : null;
                yield (ev, r) -> damage == null || damage.test((HurtEvent) ev);
            }
            default -> (ev, r) -> false;
        };
    }

    private static EntityPredicate effectsOnly(JsonElement effects, LootRegistries regs) {
        JsonObject o = new JsonObject();
        o.add("effects", effects);
        return EntityPredicate.parse(o, regs);
    }

    /** 엔티티 조건 목록 필드(victims 등). */
    private static List<List<LootCondition>> condList(JsonObject c, String key, LootRegistries regs) {
        List<List<LootCondition>> l = new ArrayList<>();
        if (c.has(key)) for (JsonElement x : c.getAsJsonArray(key)) l.add(playerPredicate(x, regs));
        return l;
    }
}
