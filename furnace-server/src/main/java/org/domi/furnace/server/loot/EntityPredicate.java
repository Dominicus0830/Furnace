// 바닐라 EntityPredicate 중 지금 엔티티 모델로 판단할 수 있는 부분. 판단할 수 없는 필드는 읽을 때 경고하고 거짓으로 본다.
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record EntityPredicate(HolderSet type, Map<String, Boolean> flags, Map<String, ItemPredicate> equipment,
                              JsonObject typeSpecific, LocationPredicate location, Bounds[] distance,
                              Map<String, EffectPredicate> effects, org.domi.furnace.server.net.io.nbt.NbtCompound nbt, Bounds[] movement,
                              LocationPredicate steppingOn, LocationPredicate movementAffectedBy, Integer periodicTick,
                              EntityPredicate vehicle, EntityPredicate passenger, EntityPredicate targetedEntity, String team,
                              Map<String, ItemPredicate> slots, EntityPredicate entityStruck,
                              List<StatCheck> stats, EntityPredicate lookingAt) {

    /** 바닐라 PlayerPredicate.StatMatcher: 통계(종류·값)의 현재 값이 범위 안. key 는 LootEntity.typeSpecific 로 묻는 이름. */
    public record StatCheck(String type, String stat, Bounds value) {
        String key() { return "stat:" + type + "|" + stat; }
    }

    /** 바닐라 EntityEffectPredicate.MobEffectInstancePredicate. */
    public record EffectPredicate(Bounds amplifier, Bounds duration, Boolean ambient, Boolean visible) {}

    /** nbt 조건이 하나라도 읽혔나(떠 두는 쪽이 NBT 를 미리 복사할지 정하는 용도). */
    public static volatile boolean nbtUsed;

    /**
     * player looking_at 조건이 스냅샷 경로(처치·피해·execute 문맥)에서 쓰일 수 있게 읽혔나(시선 검사는 비싸서 그때만 떠 둔다).
     * 발전 과제 player 조건은 소유 스레드의 살아 있는 보기로 평가하므로 {@link #liveViewOnly} 로 읽을 때는 켜지 않는다
     * (바닐라 망원경 발전 과제 3종 때문에 늘 켜져 있는 것을 막는다).
     */
    public static volatile boolean lookingAtUsed;

    /** 이 스레드가 읽는 조건은 살아 있는 보기로만 평가된다(발전 과제 player 조건). */
    private static final ThreadLocal<Boolean> LIVE_VIEW_ONLY = ThreadLocal.withInitial(() -> false);

    /** 발전 과제 player 조건 읽기를 감싼다: 그 안의 looking_at 은 lookingAtUsed 를 켜지 않는다. */
    public static <T> T liveViewOnly(java.util.function.Supplier<T> parse) {
        boolean before = LIVE_VIEW_ONLY.get();
        LIVE_VIEW_ONLY.set(true);
        try {
            return parse.get();
        } finally {
            LIVE_VIEW_ONLY.set(before);
        }
    }

    /** 바닐라 EntitySubPredicates 19종(type_specific.type 은 이 레지스트리 키여야 한다). */
    private static final Set<String> SUB_PREDICATES = Set.of("minecraft:lightning", "minecraft:fishing_hook", "minecraft:player", "minecraft:slime",
            "minecraft:raider", "minecraft:axolotl", "minecraft:boat", "minecraft:fox", "minecraft:mooshroom", "minecraft:rabbit", "minecraft:horse",
            "minecraft:llama", "minecraft:villager", "minecraft:parrot", "minecraft:tropical_fish", "minecraft:painting", "minecraft:cat",
            "minecraft:frog", "minecraft:wolf");

    /** type_specific 변종 조건이 하나라도 읽혔나(변종은 저장 NBT 를 거치므로 쓰일 때만 미리 읽는다). */
    public static volatile boolean variantUsed;

    private static final Set<String> SLOTS = Set.of("head", "chest", "legs", "feet", "body", "mainhand", "offhand");

    /** 바닐라 EntityFlagsPredicate 가 읽는 플래그(모르는 이름은 코덱이 무시한다). */
    private static final Set<String> FLAGS = Set.of("is_on_ground", "is_on_fire", "is_sneaking", "is_sprinting", "is_swimming", "is_flying", "is_baby");

    /** 바닐라 stat_type 레지스트리 9종(종류 이름 → 값을 찾는 레지스트리는 PlayerStats 쪽). */
    private static final Set<String> STAT_TYPES = Set.of("minecraft:mined", "minecraft:crafted", "minecraft:used", "minecraft:broken",
            "minecraft:picked_up", "minecraft:dropped", "minecraft:killed", "minecraft:killed_by", "minecraft:custom");

    public static EntityPredicate parse(JsonElement e, LootRegistries regs) {
        JsonObject o = e.getAsJsonObject();
        HolderSet type = null;
        Map<String, Boolean> flags = new LinkedHashMap<>();
        Map<String, ItemPredicate> equipment = new LinkedHashMap<>();
        JsonObject typeSpecific = null;
        LocationPredicate location = null;
        Bounds[] distance = null;
        Map<String, EffectPredicate> effects = null;
        org.domi.furnace.server.net.io.nbt.NbtCompound nbt = null;
        Bounds[] movement = null;
        LocationPredicate steppingOn = null, affectedBy = null;
        Integer periodic = null;
        EntityPredicate vehicle = null, passenger = null, targeted = null;
        String team = null;
        Map<String, ItemPredicate> slots = null;
        EntityPredicate struck = null;
        List<StatCheck> stats = null;
        EntityPredicate lookingAt = null;
        for (var f : o.entrySet()) {
            switch (f.getKey()) {
                case "type" -> type = HolderSet.parse("minecraft:entity_type", f.getValue(), regs);
                case "flags" -> {
                    for (var x : f.getValue().getAsJsonObject().entrySet()) {
                        if (FLAGS.contains(x.getKey())) flags.put(x.getKey(), x.getValue().getAsBoolean());   // 모르는 플래그는 값도 안 본다(바닐라 코덱이 무시)
                    }
                }
                case "equipment" -> {
                    for (var x : f.getValue().getAsJsonObject().entrySet()) {
                        if (!SLOTS.contains(x.getKey())) throw new LootParseException("모르는 장비 칸: " + x.getKey());
                        equipment.put(x.getKey(), ItemPredicate.parse(x.getValue(), regs));
                    }
                }
                case "type_specific" -> {
                    typeSpecific = f.getValue().getAsJsonObject();
                    String subType = LootJson.id(typeSpecific, "type");
                    if (!SUB_PREDICATES.contains(subType)) throw new LootParseException("모르는 type_specific 종류: " + subType);   // dispatch 코덱 오류
                    if (typeSpecific.has("variant")) variantUsed = true;
                    if (typeSpecific.has("entity_struck")) struck = parse(typeSpecific.get("entity_struck"), regs);
                    if (typeSpecific.has("stats")) stats = parseStats(typeSpecific.get("stats"));
                    if (typeSpecific.has("looking_at")) {
                        lookingAt = parse(typeSpecific.get("looking_at"), regs);
                        if (!LIVE_VIEW_ONLY.get()) lookingAtUsed = true;
                    }
                }
                case "location" -> location = LocationPredicate.parse(f.getValue(), regs);
                case "distance" -> distance = parseDistance(f.getValue());
                case "effects" -> {
                    effects = new LinkedHashMap<>();
                    for (var x : f.getValue().getAsJsonObject().entrySet()) {
                        JsonObject v = x.getValue().getAsJsonObject();
                        effects.put(LootJson.id(x.getKey()), new EffectPredicate(Bounds.parse(v.get("amplifier")), Bounds.parse(v.get("duration")),
                                v.has("ambient") ? v.get("ambient").getAsBoolean() : null, v.has("visible") ? v.get("visible").getAsBoolean() : null));
                    }
                }
                case "nbt" -> {
                    try {
                        nbt = org.domi.furnace.server.command.arguments.TagParser.parseTag(f.getValue().getAsString());
                    } catch (org.domi.furnace.server.command.brigadier.CommandSyntaxException ex) {
                        throw new LootParseException("엔티티 조건 nbt 를 읽을 수 없다: " + ex.getMessage());
                    }
                    nbtUsed = true;
                }
                case "movement" -> {   // 바닐라 MovementPredicate: x, y, z, speed, horizontal_speed, vertical_speed, fall_distance
                    JsonObject d = f.getValue().getAsJsonObject();
                    movement = new Bounds[]{Bounds.parse(d.get("x")), Bounds.parse(d.get("y")), Bounds.parse(d.get("z")), Bounds.parse(d.get("speed")),
                            Bounds.parse(d.get("horizontal_speed")), Bounds.parse(d.get("vertical_speed")), Bounds.parse(d.get("fall_distance"))};
                }
                case "stepping_on" -> steppingOn = LocationPredicate.parse(f.getValue(), regs);
                case "movement_affected_by" -> affectedBy = LocationPredicate.parse(f.getValue(), regs);
                case "periodic_tick" -> periodic = f.getValue().getAsInt();
                case "vehicle" -> vehicle = parse(f.getValue(), regs);
                case "passenger" -> passenger = parse(f.getValue(), regs);
                case "targeted_entity" -> targeted = parse(f.getValue(), regs);
                case "team" -> team = f.getValue().getAsString();
                case "slots" -> {
                    slots = new LinkedHashMap<>();
                    for (var x : f.getValue().getAsJsonObject().entrySet()) {
                        if (org.domi.furnace.server.command.arguments.SlotRanges.byName(x.getKey()) == null) throw new LootParseException("모르는 슬롯 범위: " + x.getKey());
                        slots.put(x.getKey(), ItemPredicate.parse(x.getValue(), regs));
                    }
                }
                // 바닐라 코덱(RecordCodecBuilder)은 모르는 키를 오류로 보지 않고 무시한다 — 데이터 팩 오타는 조용히 빠진다
                default -> org.slf4j.LoggerFactory.getLogger(EntityPredicate.class).warn("모르는 엔티티 조건 필드(바닐라처럼 무시한다): {}", f.getKey());
            }
        }
        return new EntityPredicate(type, flags, equipment, typeSpecific, location, distance, effects, nbt, movement,
                steppingOn, affectedBy, periodic, vehicle, passenger, targeted, team, slots, struck, stats, lookingAt);
    }

    /**
     * 바닐라 PlayerPredicate.stats: [{type, stat, value?}] — type 은 stat_type 레지스트리, stat 은 그 종류가 가리키는 레지스트리의 값,
     * value 는 정수 범위(없으면 전부). 모르는 종류·값은 코덱 오류라 그 파일을 거부한다.
     */
    private static List<StatCheck> parseStats(JsonElement e) {
        List<StatCheck> out = new ArrayList<>();
        for (JsonElement x : e.getAsJsonArray()) {
            JsonObject o = x.getAsJsonObject();
            String type = LootJson.id(o, "type");
            if (!STAT_TYPES.contains(type)) throw new LootParseException("모르는 통계 종류: " + type);
            String stat = LootJson.id(o, "stat");
            if (org.domi.furnace.server.item.ItemContext.installed() && !org.domi.furnace.server.stats.PlayerStats.isValid(new org.domi.furnace.server.stats.PlayerStats.Stat(type, stat))) {
                throw new LootParseException("모르는 통계: " + type + " " + stat);
            }
            out.add(new StatCheck(type, stat, Bounds.parse(o.get("value"))));
        }
        return out;
    }

    public boolean matches(LootContext ctx, double[] origin, LootEntity entity) {
        if (entity == null) return false;
        LootRegistries regs = ctx.registries();
        if (type != null && !type.contains(entity.type(), regs)) return false;
        if (distance != null) {
            double[] pos = entity.position();
            if (origin == null || pos == null || !matchesDistance(distance, origin, pos)) return false;
        }
        for (var f : flags.entrySet()) {
            boolean actual = switch (f.getKey()) {
                case "is_on_fire" -> entity.isOnFire();
                case "is_sneaking" -> entity.isCrouching();
                case "is_sprinting" -> entity.isSprinting();
                case "is_swimming" -> entity.isSwimming();
                case "is_on_ground" -> entity.onGround();
                case "is_flying" -> entity.isFlying();
                case "is_baby" -> entity.isBaby();
                default -> f.getValue();   // parse 가 모르는 이름은 걸러 둔다
            };
            if (actual != f.getValue()) return false;
        }
        for (var q : equipment.entrySet()) if (!q.getValue().test(entity.equipment(q.getKey()), regs)) return false;
        if (location != null && !location.matches(ctx, entity.position())) return false;
        if (typeSpecific != null && !typeSpecific(ctx, origin, entity)) return false;
        return matchesExtra(ctx, origin, entity);
    }

    /** 바닐라 EntityPredicate.matches 의 나머지 필드(이동·효과·위치 래퍼·탈것·승객·표적·팀·슬롯·주기·NBT). */
    private boolean matchesExtra(LootContext ctx, double[] origin, LootEntity entity) {
        LootRegistries regs = ctx.registries();
        if (movement != null) {
            double[] v = entity.velocity();
            if (v == null) return false;
            double x = v[0] * 20.0, y = v[1] * 20.0, z = v[2] * 20.0;   // getKnownMovement().scale(20)
            if (!movement[0].matches(x) || !movement[1].matches(y) || !movement[2].matches(z)) return false;
            if (!matchesSqr(movement[3], x * x + y * y + z * z) || !matchesSqr(movement[4], x * x + z * z)) return false;
            if (!movement[5].matches(Math.abs(y)) || !movement[6].matches(entity.fallDistance())) return false;
        }
        if (steppingOn != null && !steppingOn.matches(ctx, entity.steppingOnPos())) return false;
        if (movementAffectedBy != null && !movementAffectedBy.matches(ctx, entity.movementAffectedByPos())) return false;
        if (effects != null) {
            var actual = entity.effects();
            for (var e : effects.entrySet()) {
                LootEntity.Effect a = actual.get(e.getKey());
                EffectPredicate p = e.getValue();
                if (a == null || !p.amplifier().matches(a.amplifier()) || !p.duration().matches(a.duration())) return false;
                if (p.ambient() != null && p.ambient() != a.ambient()) return false;
                if (p.visible() != null && p.visible() != a.visible()) return false;
            }
        }
        if (vehicle != null && !vehicle.matches(ctx, origin, entity.vehicle())) return false;
        if (targetedEntity != null && !targetedEntity.matches(ctx, origin, entity.targetedEntity())) return false;
        if (team != null && !team.equals(entity.team())) return false;
        if (slots != null) {
            for (var q : slots.entrySet()) {
                boolean any = false;
                for (LootStack s : entity.slotItems(q.getKey())) if (q.getValue().test(s, regs)) { any = true; break; }
                if (!any) return false;
            }
        }
        if (passenger != null) {
            boolean any = false;
            for (LootEntity p : entity.passengers()) if (passenger.matches(ctx, origin, p)) { any = true; break; }
            if (!any) return false;
        }
        if (periodicTick != null && periodicTick != 0 && entity.tickCount() % periodicTick != 0) return false;
        if (nbt != null) {
            var tag = entity.nbt();
            if (tag == null || !org.domi.furnace.server.net.io.nbt.NbtTags.compareNbt(nbt, tag, true)) return false;
        }
        return true;
    }

    /** 바닐라 DistancePredicate: x, y, z, horizontal, absolute. */
    public static Bounds[] parseDistance(JsonElement e) {
        JsonObject d = e.getAsJsonObject();
        return new Bounds[]{Bounds.parse(d.get("x")), Bounds.parse(d.get("y")), Bounds.parse(d.get("z")),
                Bounds.parse(d.get("horizontal")), Bounds.parse(d.get("absolute"))};
    }

    /** DistancePredicate.matches(a, b): 축은 절댓값, horizontal·absolute 는 제곱 비교, 차이는 바닐라처럼 float. */
    public static boolean matchesDistance(Bounds[] d, double[] a, double[] b) {
        float dx = (float) (a[0] - b[0]), dy = (float) (a[1] - b[1]), dz = (float) (a[2] - b[2]);
        if (!d[0].matches(Math.abs(dx)) || !d[1].matches(Math.abs(dy)) || !d[2].matches(Math.abs(dz))) return false;
        return matchesSqr(d[3], dx * dx + dz * dz) && matchesSqr(d[4], dx * dx + dy * dy + dz * dz);
    }

    /** MinMaxBounds.Doubles.matchesSqr: 경계를 제곱해 비교. */
    private static boolean matchesSqr(Bounds b, double sq) {
        return (b.min() == null || b.min() * b.min() <= sq) && (b.max() == null || sq <= b.max() * b.max());
    }

    /** 변종을 열거형 이름으로 비교하는 종류(바닐라 EntitySubPredicates 의 enum 변종). */
    private static final Set<String> ENUM_VARIANT = Set.of("minecraft:axolotl", "minecraft:boat", "minecraft:fox", "minecraft:horse",
            "minecraft:llama", "minecraft:mooshroom", "minecraft:parrot", "minecraft:rabbit", "minecraft:tropical_fish");

    private static final Map<String, String> VARIANT_REGISTRY = Map.of("minecraft:cat", "minecraft:cat_variant",
            "minecraft:painting", "minecraft:painting_variant", "minecraft:frog", "minecraft:frog_variant",
            "minecraft:wolf", "minecraft:wolf_variant", "minecraft:villager", "minecraft:villager_type");

    private boolean typeSpecific(LootContext ctx, double[] outerOrigin, LootEntity e) {
        String t = LootJson.id(typeSpecific, "type");
        return switch (t) {
            case "minecraft:slime" -> e.typeSpecific("size") instanceof Integer size && Bounds.parse(typeSpecific.get("size")).matches(size);
            // 바닐라 RaiderPredicate: has_raid·is_captain 기본값 false, 둘 다 같아야 한다
            case "minecraft:raider" -> e.typeSpecific("is_captain") instanceof Boolean captain
                    && e.typeSpecific("has_raid") instanceof Boolean raid
                    && captain == (typeSpecific.has("is_captain") && typeSpecific.get("is_captain").getAsBoolean())
                    && raid == (typeSpecific.has("has_raid") && typeSpecific.get("has_raid").getAsBoolean());
            case "minecraft:fishing_hook" -> !typeSpecific.has("in_open_water")
                    || Boolean.valueOf(typeSpecific.get("in_open_water").getAsBoolean()).equals(e.typeSpecific("in_open_water"));
            case "minecraft:lightning" -> {
                if (!(e.typeSpecific("blocks_set_on_fire") instanceof Integer fires)) yield false;
                if (!Bounds.parse(typeSpecific.get("blocks_set_on_fire")).matches(fires)) yield false;
                if (entityStruck == null) yield true;
                if (!(e.typeSpecific("entity_struck") instanceof java.util.List<?> hit)) yield false;
                // 바닐라는 맞은 엔티티를 바깥 조건의 원점 그대로 검사한다
                for (Object h : hit) if (h instanceof LootEntity he && entityStruck.matches(ctx, outerOrigin, he)) yield true;
                yield false;
            }
            case "minecraft:player" -> playerSpecific(ctx, e);
            default -> {
                if (!ENUM_VARIANT.contains(t) && !VARIANT_REGISTRY.containsKey(t)) yield false;
                Object v = e.typeSpecific("variant");
                JsonElement want = typeSpecific.get("variant");
                if (v == null || want == null) yield false;
                boolean enumVariant = ENUM_VARIANT.contains(t);
                yield variantMatches(want, v.toString(), enumVariant, VARIANT_REGISTRY.get(t), ctx.registries());
            }
        };
    }

    /** 바닐라 변종 조건: 문자열 하나, 목록, "#태그"(태그를 아는 레지스트리만). */
    private static boolean variantMatches(JsonElement want, String actual, boolean enumVariant, String registry, LootRegistries regs) {
        if (want.isJsonArray()) {
            for (JsonElement x : want.getAsJsonArray()) if (variantMatches(x, actual, enumVariant, registry, regs)) return true;
            return false;
        }
        String s = want.getAsString();
        if (enumVariant) return s.equals(actual);
        if (s.startsWith("#")) return regs.tag(registry, s.substring(1)).contains(actual);
        return LootJson.id(s).equals(actual);
    }

    /** 바닐라 PlayerPredicate: level, gamemode, stats, recipes, advancements, looking_at. */
    private boolean playerSpecific(LootContext ctx, LootEntity e) {
        if (!(e.typeSpecific("level") instanceof Integer level) || !Bounds.parse(typeSpecific.get("level")).matches(level)) return false;
        if (typeSpecific.has("gamemode")) {
            JsonElement gm = typeSpecific.get("gamemode");
            Object actual = e.typeSpecific("gamemode");
            boolean ok = false;
            if (gm.isJsonArray()) {
                for (JsonElement x : gm.getAsJsonArray()) if (x.getAsString().equals(actual)) ok = true;
            } else {
                ok = gm.getAsString().equals(actual);
            }
            if (!ok) return false;
        }
        if (stats != null) {
            for (StatCheck c : stats) if (!(e.typeSpecific(c.key()) instanceof Integer n) || !c.value().matches(n)) return false;
        }
        if (typeSpecific.has("recipes")) {
            for (var r : typeSpecific.getAsJsonObject("recipes").entrySet()) {
                if (!Boolean.valueOf(r.getValue().getAsBoolean()).equals(e.typeSpecific("recipe:" + LootJson.id(r.getKey())))) return false;
            }
        }
        if (typeSpecific.has("advancements")) {
            for (var a : typeSpecific.getAsJsonObject("advancements").entrySet()) {
                String id = LootJson.id(a.getKey());
                if (a.getValue().isJsonObject()) {
                    for (var c : a.getValue().getAsJsonObject().entrySet()) {
                        if (!Boolean.valueOf(c.getValue().getAsBoolean()).equals(e.typeSpecific("advancement:" + id + "|" + c.getKey()))) return false;
                    }
                } else if (!Boolean.valueOf(a.getValue().getAsBoolean()).equals(e.typeSpecific("advancement:" + id))) {
                    return false;
                }
            }
        }
        if (lookingAt != null) {
            // 시선 레이캐스트와 hasLineOfSight 는 LootEntity 쪽이 하고(벽 뒤면 null), 하위 조건은 플레이어 위치를 원점으로 본다(바닐라 matches(player, target))
            if (!(e.typeSpecific("looking_at") instanceof LootEntity target) || !lookingAt.matches(ctx, e.position(), target)) return false;
        }
        return true;
    }
}
