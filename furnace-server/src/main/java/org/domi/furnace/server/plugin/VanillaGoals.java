// 플러그인 API VanillaGoal(이름표 + 값) → 이 몹에 걸 실제 바닐라 목표. 몹 종류가 맞지 않거나 값이 틀리면 IllegalArgumentException.
// 엔티티·아이템 종류는 키로 받아 바닐라 목표의 종류 조건(클래스)을 키 비교로 바꿔 건다 — 찾는 대상·순서는 바닐라 그 목표와 같다.
package org.domi.furnace.server.plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.domi.furnace.api.entity.ai.VanillaGoal;
import org.domi.furnace.server.entity.Animal;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.goal.AvoidEntityGoal;
import org.domi.furnace.server.entity.ai.goal.BreathAirGoal;
import org.domi.furnace.server.entity.ai.goal.BreedGoal;
import org.domi.furnace.server.entity.ai.goal.FleeSunGoal;
import org.domi.furnace.server.entity.ai.goal.FloatGoal;
import org.domi.furnace.server.entity.ai.goal.FollowParentGoal;
import org.domi.furnace.server.entity.ai.goal.HurtByTargetGoal;
import org.domi.furnace.server.entity.ai.goal.LeapAtTargetGoal;
import org.domi.furnace.server.entity.ai.goal.LookAtPlayerGoal;
import org.domi.furnace.server.entity.ai.goal.MeleeAttackGoal;
import org.domi.furnace.server.entity.ai.goal.MoveTowardsRestrictionGoal;
import org.domi.furnace.server.entity.ai.goal.MoveTowardsTargetGoal;
import org.domi.furnace.server.entity.ai.goal.NearestAttackableTargetGoal;
import org.domi.furnace.server.entity.ai.goal.OpenDoorGoal;
import org.domi.furnace.server.entity.ai.goal.PanicGoal;
import org.domi.furnace.server.entity.ai.goal.RandomLookAroundGoal;
import org.domi.furnace.server.entity.ai.goal.RandomStrollGoal;
import org.domi.furnace.server.entity.ai.goal.RandomSwimmingGoal;
import org.domi.furnace.server.entity.ai.goal.RestrictSunGoal;
import org.domi.furnace.server.entity.ai.goal.TemptGoal;
import org.domi.furnace.server.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import org.domi.furnace.server.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import org.domi.furnace.server.entity.ai.util.GoalUtils;
import org.domi.furnace.server.item.ItemContext;

final class VanillaGoals {

    private VanillaGoals() {}

    static Goal create(Mob mob, VanillaGoal spec) {
        if (!spec.key().namespace().equals("minecraft")) throw new IllegalArgumentException("바닐라 목표가 아니다: " + spec.key());
        Map<String, Object> p = spec.params();
        String name = spec.key().name();
        return switch (name) {
            case "float" -> new FloatGoal(mob);
            case "melee_attack" -> new MeleeAttackGoal(pathfinder(mob, name), d(p, "speed"), b(p, "follow_even_if_not_seen"));
            case "random_stroll" -> new RandomStrollGoal(pathfinder(mob, name), d(p, "speed"), positive(i(p, "interval"), "interval"));
            case "water_avoiding_random_stroll" -> new WaterAvoidingRandomStrollGoal(pathfinder(mob, name), d(p, "speed"));
            case "random_swimming" -> new RandomSwimmingGoal(pathfinder(mob, name), d(p, "speed"), positive(i(p, "interval"), "interval"));
            case "water_avoiding_random_flying" -> new WaterAvoidingRandomFlyingGoal(pathfinder(mob, name), d(p, "speed"));
            case "look_at_player" -> new LookAtPlayerGoal(mob, f(p, "distance"), f(p, "probability"));
            case "random_look_around" -> new RandomLookAroundGoal(mob);
            case "panic" -> new PanicGoal(pathfinder(mob, name), d(p, "speed"));
            case "tempt" -> new TemptGoal(pathfinder(mob, name), d(p, "speed"), itemSet(p), b(p, "can_scare"));
            case "avoid_entity" -> avoid(pathfinder(mob, name), s(p, "entity_type"), f(p, "max_distance"), d(p, "walk_speed"), d(p, "sprint_speed"));
            case "leap_at_target" -> new LeapAtTargetGoal(mob, f(p, "yd"));
            case "move_towards_target" -> new MoveTowardsTargetGoal(pathfinder(mob, name), d(p, "speed"), f(p, "within"));
            case "move_towards_restriction" -> new MoveTowardsRestrictionGoal(pathfinder(mob, name), d(p, "speed"));
            case "open_door" -> {
                if (!GoalUtils.hasGroundPathNavigation(mob)) throw unsupported(mob, name);
                yield new OpenDoorGoal(mob, b(p, "close_door"));
            }
            case "restrict_sun" -> new RestrictSunGoal(pathfinder(mob, name));
            case "flee_sun" -> new FleeSunGoal(pathfinder(mob, name), d(p, "speed"));
            case "breath_air" -> new BreathAirGoal(pathfinder(mob, name));
            case "breed" -> new BreedGoal(animal(mob, name), d(p, "speed"));
            case "follow_parent" -> new FollowParentGoal(animal(mob, name), d(p, "speed"));
            case "nearest_attackable_target" -> nearestTarget(mob, s(p, "entity_type"), b(p, "must_see"));
            case "hurt_by_target" -> new HurtByTargetGoal(pathfinder(mob, name));
            default -> throw new IllegalArgumentException("모르는 바닐라 목표: " + spec.key());
        };
    }

    private static Goal avoid(PathfinderMob mob, String type, float maxDist, double walk, double sprint) {
        if (type.equals("minecraft:player")) return new AvoidEntityGoal(mob, Player.class, maxDist, walk, sprint);
        return new AvoidEntityGoal(mob, LivingEntity.class, ofType(type), maxDist, walk, sprint, t -> true);
    }

    private static Goal nearestTarget(Mob mob, String type, boolean mustSee) {
        if (type.equals("minecraft:player")) return new NearestAttackableTargetGoal(mob, Player.class, mustSee);
        return new NearestAttackableTargetGoal(mob, LivingEntity.class, mustSee, ofType(type));
    }

    /** 그 종류 엔티티만(플레이어는 Player 클래스로 따로 받으므로 여기서는 빠진다). */
    private static Predicate<Targetable> ofType(String type) {
        if (EntityType.byName(type) == null) throw new IllegalArgumentException("모르는 엔티티 종류: " + type);
        return t -> t instanceof Entity e && e.type().name().equals(type);
    }

    private static Predicate<org.domi.furnace.server.entity.ItemStack> itemSet(Map<String, Object> p) {
        Set<Integer> ids = new HashSet<>();
        for (Object o : (List<?>) p.get("items")) {
            String key = o.toString();
            Integer id = ItemContext.id("minecraft:item", key.indexOf(':') < 0 ? "minecraft:" + key : key);
            if (id == null || id <= 0) throw new IllegalArgumentException("모르는 아이템: " + key);
            ids.add(id);
        }
        return s -> ids.contains(s.itemId());
    }

    private static PathfinderMob pathfinder(Mob mob, String goal) {
        if (mob instanceof PathfinderMob pm) return pm;
        throw unsupported(mob, goal);
    }

    private static Animal animal(Mob mob, String goal) {
        if (mob instanceof Animal a) return a;
        throw unsupported(mob, goal);
    }

    private static IllegalArgumentException unsupported(Mob mob, String goal) {
        return new IllegalArgumentException(mob.type().name() + " 는 minecraft:" + goal + " 목표를 쓸 수 없다");
    }

    private static double d(Map<String, Object> p, String k) { return ((Number) p.get(k)).doubleValue(); }

    private static float f(Map<String, Object> p, String k) { return ((Number) p.get(k)).floatValue(); }

    private static int i(Map<String, Object> p, String k) { return ((Number) p.get(k)).intValue(); }

    private static boolean b(Map<String, Object> p, String k) { return (Boolean) p.get(k); }

    private static String s(Map<String, Object> p, String k) { return (String) p.get(k); }

    private static int positive(int v, String k) {
        if (v <= 0) throw new IllegalArgumentException(k + " 는 양수여야 한다: " + v);
        return v;
    }
}
