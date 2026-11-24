// 호환 계층의 바닐라 목표 생성자(몹 목표 설계 M2b, 보충 설계 furnace/designs/mob-goals-m2b-addendum-2026-11-24.md) → 이 몹의 실제 서버 목표.
// 매개변수는 몹 다음의 것(수·참거짓·대상 클래스)이고 바닐라 생성자와 같은 순서·형식이다. 맞는 생성자가 없으면 null.
package org.domi.furnace.server.plugin;

import java.util.Set;
import java.util.function.Predicate;
import org.domi.furnace.server.entity.Animal;
import org.domi.furnace.server.entity.Entity;
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
import org.domi.furnace.server.entity.ai.goal.LookAtMobGoal;
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
import org.domi.furnace.server.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import org.domi.furnace.server.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import org.domi.furnace.server.entity.ai.util.GoalUtils;

final class CompatVanillaGoals {

    private CompatVanillaGoals() {}

    /** 바닐라 엔티티 클래스 매개변수: 플레이어만이거나, 종류 이름이 그 집합 안인 엔티티(플레이어 없음). */
    record Targets(boolean playersOnly, Set<String> types) {
        boolean has(Object e) { return e instanceof Entity x && types.contains(x.type().name()); }
    }

    static Goal create(Mob mob, String name, Object[] a) {
        String sig = signature(a);
        return switch (name + sig) {
            case "FloatGoal" -> new FloatGoal(mob);
            case "MeleeAttackGoal(DZ)" -> new MeleeAttackGoal(pathfinder(mob), d(a, 0), z(a, 1));
            case "RandomStrollGoal(D)" -> new RandomStrollGoal(pathfinder(mob), d(a, 0));
            case "RandomStrollGoal(DI)" -> new RandomStrollGoal(pathfinder(mob), d(a, 0), i(a, 1));
            case "RandomStrollGoal(DIZ)" -> new RandomStrollGoal(pathfinder(mob), d(a, 0), i(a, 1), z(a, 2));
            case "WaterAvoidingRandomStrollGoal(D)" -> new WaterAvoidingRandomStrollGoal(pathfinder(mob), d(a, 0));
            case "WaterAvoidingRandomStrollGoal(DF)" -> new WaterAvoidingRandomStrollGoal(pathfinder(mob), d(a, 0), f(a, 1));
            case "RandomSwimmingGoal(DI)" -> new RandomSwimmingGoal(pathfinder(mob), d(a, 0), i(a, 1));
            case "WaterAvoidingRandomFlyingGoal(D)" -> new WaterAvoidingRandomFlyingGoal(pathfinder(mob), d(a, 0));
            case "LookAtPlayerGoal(TF)" -> lookAt(mob, t(a, 0), f(a, 1), 0.02F, false);
            case "LookAtPlayerGoal(TFF)" -> lookAt(mob, t(a, 0), f(a, 1), f(a, 2), false);
            case "LookAtPlayerGoal(TFFZ)" -> lookAt(mob, t(a, 0), f(a, 1), f(a, 2), z(a, 3));
            case "RandomLookAroundGoal" -> new RandomLookAroundGoal(mob);
            case "PanicGoal(D)" -> new PanicGoal(pathfinder(mob), d(a, 0));
            case "AvoidEntityGoal(TFDD)" -> avoid(pathfinder(mob), t(a, 0), f(a, 1), d(a, 2), d(a, 3));
            case "LeapAtTargetGoal(F)" -> new LeapAtTargetGoal(mob, f(a, 0));
            case "MoveTowardsTargetGoal(DF)" -> new MoveTowardsTargetGoal(pathfinder(mob), d(a, 0), f(a, 1));
            case "MoveTowardsRestrictionGoal(D)" -> new MoveTowardsRestrictionGoal(pathfinder(mob), d(a, 0));
            case "OpenDoorGoal(Z)" -> {
                // 바닐라 DoorInteractGoal 생성자의 검사(같은 문구)
                if (!GoalUtils.hasGroundPathNavigation(mob)) throw new IllegalArgumentException("Unsupported mob type for DoorInteractGoal");
                yield new OpenDoorGoal(mob, z(a, 0));
            }
            case "RestrictSunGoal" -> new RestrictSunGoal(pathfinder(mob));
            case "FleeSunGoal(D)" -> new FleeSunGoal(pathfinder(mob), d(a, 0));
            case "BreathAirGoal" -> new BreathAirGoal(pathfinder(mob));
            case "BreedGoal(D)" -> new BreedGoal(animal(mob), d(a, 0));
            case "FollowParentGoal(D)" -> new FollowParentGoal(animal(mob), d(a, 0));
            case "NearestAttackableTargetGoal(TZ)" -> nearest(mob, t(a, 0), z(a, 1), false);
            case "NearestAttackableTargetGoal(TZZ)" -> nearest(mob, t(a, 0), z(a, 1), z(a, 2));
            case "HurtByTargetGoal" -> new HurtByTargetGoal(pathfinder(mob));
            default -> null;
        };
    }

    /** 바닐라 LookAtPlayerGoal: 플레이어 대상이면 플레이어 검색(탄 몹 빼기), 아니면 그 클래스 엔티티 검색. */
    private static Goal lookAt(Mob mob, Targets t, float distance, float probability, boolean onlyHorizontal) {
        if (t.playersOnly()) return new LookAtPlayerGoal(mob, distance, probability, onlyHorizontal);
        return new LookAtMobGoal(mob, LivingEntity.class, t::has, distance, probability, onlyHorizontal);
    }

    /** 바닐라 5인자 AvoidEntityGoal: 피할 조건은 늘 참, 길찾기 조건은 창작·관전 빼기. 종류 조건은 피할 조건 자리(후보 걸러내기와 같다). */
    private static Goal avoid(PathfinderMob mob, Targets t, float maxDist, double walk, double sprint) {
        if (t.playersOnly()) return new AvoidEntityGoal(mob, Player.class, maxDist, walk, sprint);
        Predicate<Targetable> has = t::has;
        return new AvoidEntityGoal(mob, LivingEntity.class, has, maxDist, walk, sprint, AvoidEntityGoal.NO_CREATIVE_OR_SPECTATOR);
    }

    private static Goal nearest(Mob mob, Targets t, boolean mustSee, boolean mustReach) {
        if (t.playersOnly()) return new NearestAttackableTargetGoal(mob, Player.class, mustSee, mustReach);
        return new NearestAttackableTargetGoal(mob, LivingEntity.class, 10, mustSee, mustReach, t::has);
    }

    /** 매개변수 형식 줄: 수는 D·F·I, 참거짓 Z, 대상 클래스 T. 없으면 빈 문자열. */
    private static String signature(Object[] a) {
        if (a.length == 0) return "";
        StringBuilder b = new StringBuilder("(");
        for (Object o : a) {
            b.append(switch (o) {
                case Double x -> 'D';
                case Float x -> 'F';
                case Integer x -> 'I';
                case Boolean x -> 'Z';
                case Targets x -> 'T';
                default -> '?';
            });
        }
        return b.append(')').toString();
    }

    private static PathfinderMob pathfinder(Mob mob) {
        if (mob instanceof PathfinderMob pm) return pm;
        throw new IllegalArgumentException(mob.type().name() + " is not a pathfinder mob");
    }

    private static Animal animal(Mob mob) {
        if (mob instanceof Animal an) return an;
        throw new IllegalArgumentException(mob.type().name() + " is not an animal");
    }

    private static double d(Object[] a, int i) { return (Double) a[i]; }

    private static float f(Object[] a, int i) { return (Float) a[i]; }

    private static int i(Object[] a, int i) { return (Integer) a[i]; }

    private static boolean z(Object[] a, int i) { return (Boolean) a[i]; }

    private static Targets t(Object[] a, int i) { return (Targets) a[i]; }
}
