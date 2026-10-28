// 바닐라 EntityPredicate 중 지금 엔티티 모델로 판단할 수 있는 부분. 판단할 수 없는 필드는 읽을 때 경고하고 거짓으로 본다.
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record EntityPredicate(HolderSet type, Map<String, Boolean> flags, Map<String, ItemPredicate> equipment,
                              JsonObject typeSpecific, LocationPredicate location, Bounds[] distance, boolean unsupported) {

    private static final Set<String> SLOTS = Set.of("head", "chest", "legs", "feet", "body", "mainhand", "offhand");

    public static EntityPredicate parse(JsonElement e, LootRegistries regs) {
        JsonObject o = e.getAsJsonObject();
        boolean unsupported = false;
        HolderSet type = null;
        Map<String, Boolean> flags = new LinkedHashMap<>();
        Map<String, ItemPredicate> equipment = new LinkedHashMap<>();
        JsonObject typeSpecific = null;
        LocationPredicate location = null;
        Bounds[] distance = null;
        for (var f : o.entrySet()) {
            switch (f.getKey()) {
                case "type" -> type = HolderSet.parse("minecraft:entity_type", f.getValue(), regs);
                case "flags" -> { for (var x : f.getValue().getAsJsonObject().entrySet()) flags.put(x.getKey(), x.getValue().getAsBoolean()); }
                case "equipment" -> {
                    for (var x : f.getValue().getAsJsonObject().entrySet()) {
                        if (!SLOTS.contains(x.getKey())) throw new LootParseException("모르는 장비 칸: " + x.getKey());
                        equipment.put(x.getKey(), ItemPredicate.parse(x.getValue(), regs));
                    }
                }
                case "type_specific" -> typeSpecific = f.getValue().getAsJsonObject();
                case "location" -> location = LocationPredicate.parse(f.getValue(), regs);
                case "distance" -> {   // 바닐라 DistancePredicate: x, y, z, horizontal, absolute
                    JsonObject d = f.getValue().getAsJsonObject();
                    distance = new Bounds[]{Bounds.parse(d.get("x")), Bounds.parse(d.get("y")), Bounds.parse(d.get("z")),
                            Bounds.parse(d.get("horizontal")), Bounds.parse(d.get("absolute"))};
                }
                default -> unsupported = true;
            }
        }
        if (unsupported) org.slf4j.LoggerFactory.getLogger(EntityPredicate.class).warn("아직 지원하지 않는 엔티티 조건 필드: {}", o.keySet());
        return new EntityPredicate(type, flags, equipment, typeSpecific, location, distance, unsupported);
    }

    public boolean matches(LootContext ctx, double[] origin, LootEntity entity) {
        if (entity == null || unsupported) return false;
        LootRegistries regs = ctx.registries();
        if (type != null && !type.contains(entity.type(), regs)) return false;
        if (distance != null) {   // DistancePredicate.matches(origin, entity 위치): 축은 절댓값, horizontal·absolute 는 제곱 비교
            double[] pos = entity.position();
            if (origin == null || pos == null) return false;
            float dx = (float) (origin[0] - pos[0]), dy = (float) (origin[1] - pos[1]), dz = (float) (origin[2] - pos[2]);   // 바닐라도 float
            if (!distance[0].matches(Math.abs(dx)) || !distance[1].matches(Math.abs(dy)) || !distance[2].matches(Math.abs(dz))) return false;
            if (!matchesSqr(distance[3], dx * dx + dz * dz) || !matchesSqr(distance[4], dx * dx + dy * dy + dz * dz)) return false;
        }
        for (var f : flags.entrySet()) {
            boolean actual = switch (f.getKey()) {
                case "is_on_fire" -> entity.isOnFire();
                case "is_sneaking" -> entity.isCrouching();
                case "is_sprinting" -> entity.isSprinting();
                case "is_swimming" -> entity.isSwimming();
                case "is_on_ground" -> entity.onGround();
                case "is_baby" -> entity.isBaby();
                default -> !f.getValue();   // 모르는 플래그는 맞지 않는 것으로
            };
            if (actual != f.getValue()) return false;
        }
        for (var q : equipment.entrySet()) if (!q.getValue().test(entity.equipment(q.getKey()), regs)) return false;
        if (location != null && !location.matches(ctx, entity.position())) return false;
        if (typeSpecific != null && !typeSpecific(ctx, entity)) return false;
        return true;
    }

    /** MinMaxBounds.Doubles.matchesSqr: 경계를 제곱해 비교. */
    private static boolean matchesSqr(Bounds b, double sq) {
        return (b.min() == null || b.min() * b.min() <= sq) && (b.max() == null || sq <= b.max() * b.max());
    }

    private boolean typeSpecific(LootContext ctx, LootEntity e) {
        String t = LootJson.id(typeSpecific, "type");
        return switch (t) {
            case "minecraft:slime" -> e.typeSpecific("size") instanceof Integer size && Bounds.parse(typeSpecific.get("size")).matches(size);
            case "minecraft:raider" -> e.typeSpecific("is_captain") instanceof Boolean captain
                    && (!typeSpecific.has("is_captain") || typeSpecific.get("is_captain").getAsBoolean() == captain);
            case "minecraft:fishing_hook" -> !typeSpecific.has("in_open_water")
                    || Boolean.valueOf(typeSpecific.get("in_open_water").getAsBoolean()).equals(e.typeSpecific("in_open_water"));
            case "minecraft:frog", "minecraft:cat", "minecraft:wolf", "minecraft:painting", "minecraft:axolotl", "minecraft:fox",
                 "minecraft:horse", "minecraft:llama", "minecraft:mooshroom", "minecraft:parrot", "minecraft:rabbit",
                 "minecraft:tropical_fish", "minecraft:villager" -> {
                Object v = e.typeSpecific("variant");
                JsonElement want = typeSpecific.get("variant");
                yield v != null && want != null && (want.isJsonArray() ? want.toString().contains("\"" + v + "\"") : LootJson.id(want.getAsString()).equals(v));
            }
            default -> false;
        };
    }
}
