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
import org.domi.furnace.server.entity.ai.goal.TemptGoal;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import org.domi.furnace.server.entity.ai.util.GoalUtils;

final class CompatVanillaGoals {

    private CompatVanillaGoals() {}

    /** 바닐라 엔티티 클래스 매개변수: 플레이어만이거나, 종류 이름이 그 집합 안인 엔티티(플레이어 없음). */
    record Targets(boolean playersOnly, Set<String> types) {
        boolean has(Object e) { return e instanceof Entity x && types.contains(x.type().name()); }

        /** 클래스 목록 매개변수에서: 플레이어(그 표시가 있으면) 또는 종류 집합. */
        boolean matches(Object e) { return playersOnly && e instanceof Player || has(e); }
    }

    /** 아이템 판정 매개변수(API GoalItems 와 같은 뜻). */
    record Items(Set<String> items, Predicate<org.domi.furnace.api.item.ItemStack> test, String owner, java.util.function.BooleanSupplier alive) {}

    /** 엔티티 판정 매개변수(API GoalEntities — {@code test} 는 ChunkInternalsImpl 이 서버 엔티티를 API 핸들로 바꿔 부르는 것). */
    record Entities(Predicate<Targetable> test, Object source, String owner, java.util.function.BooleanSupplier alive) {}

    /** 피해 종류 태그 매개변수(API GoalDamageTag). */
    record DamageTag(String tag, java.util.function.Supplier<String> dynamic, String owner, java.util.function.BooleanSupplier alive) {}

    /** 클래스 목록 매개변수(API GoalClasses) — 하나라도 맞으면. */
    record Classes(java.util.List<Targets> any) {
        boolean matches(Object e) {
            for (Targets t : any) if (t.matches(e)) return true;
            return false;
        }
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
            case "PanicGoal(DG)" -> {
                Goal[] self = {null};
                yield self[0] = new PanicGoal(pathfinder(mob), d(a, 0), damageTag(mob, self, g(a, 1)));
            }
            case "AvoidEntityGoal(TFDD)" -> avoid(pathfinder(mob), t(a, 0), f(a, 1), d(a, 2), d(a, 3));
            case "AvoidEntityGoal(TFDDE)" -> {
                // 바닐라 6인자: 피할 조건 늘 참, 마지막 판정이 길찾기 조건 자리
                Goal[] self = {null};
                yield self[0] = avoid(pathfinder(mob), t(a, 0), x -> true, f(a, 1), d(a, 2), d(a, 3), entities(mob, self, e(a, 4)));
            }
            case "AvoidEntityGoal(TEFDDE)" -> {
                Goal[] self = {null};
                yield self[0] = avoid(pathfinder(mob), t(a, 0), entities(mob, self, e(a, 1)), f(a, 2), d(a, 3), d(a, 4), entities(mob, self, e(a, 5)));
            }
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
            case "BreedGoal(DT)" -> {
                // 바닐라: 짝 클래스로 후보만 넓힌다(짝짓기는 canMate 가 같은 종류만)
                Targets t = t(a, 1);
                yield new BreedGoal(animal(mob), d(a, 0), Animal.class, t::has);
            }
            case "FollowParentGoal(D)" -> new FollowParentGoal(animal(mob), d(a, 0));
            case "NearestAttackableTargetGoal(TZ)" -> nearest(mob, t(a, 0), z(a, 1), false);
            case "NearestAttackableTargetGoal(TZZ)" -> nearest(mob, t(a, 0), z(a, 1), z(a, 2));
            case "NearestAttackableTargetGoal(TZE)" -> {
                Goal[] self = {null};
                yield self[0] = nearest(mob, t(a, 0), 10, z(a, 1), false, entities(mob, self, e(a, 2)));
            }
            case "NearestAttackableTargetGoal(TIZZE)" -> {
                Goal[] self = {null};
                yield self[0] = nearest(mob, t(a, 0), i(a, 1), z(a, 2), z(a, 3), entities(mob, self, e(a, 4)));
            }
            case "HurtByTargetGoal" -> new HurtByTargetGoal(pathfinder(mob));
            case "HurtByTargetGoal(C)" -> {
                Classes c = c(a, 0);
                yield new HurtByTargetGoal(pathfinder(mob), c::matches);
            }
            case "TemptGoal(DPZ)" -> {
                Goal[] self = {null};
                yield self[0] = new TemptGoal(pathfinder(mob), d(a, 0), items(mob, self, p(a, 1)), z(a, 2));
            }
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

    /**
     * 바닐라 7인자 AvoidEntityGoal(판정 함수): 선택 조건 = 길찾기 조건 그리고 피할 조건. 종류 집합이면 종류 거르기를 맨 앞에 — 바닐라는 클래스로 먼저 모아
     * 플러그인 판정이 다른 종류를 보지 않는다.
     */
    private static Goal avoid(PathfinderMob mob, Targets t, Predicate<Targetable> avoid, float maxDist, double walk, double sprint, Predicate<Targetable> path) {
        if (t.playersOnly()) return new AvoidEntityGoal(mob, Player.class, avoid, maxDist, walk, sprint, path);
        return new AvoidEntityGoal(mob, LivingEntity.class, avoid, maxDist, walk, sprint, keep(path, x -> t.has(x) && path.test(x)));
    }

    /** 바닐라 판정 함수 NearestAttackableTargetGoal — 종류 집합이면 종류 거르기를 판정 앞에. */
    private static Goal nearest(Mob mob, Targets t, int interval, boolean mustSee, boolean mustReach, Predicate<Targetable> filter) {
        if (t.playersOnly()) return new NearestAttackableTargetGoal(mob, Player.class, interval, mustSee, mustReach, filter);
        return new NearestAttackableTargetGoal(mob, LivingEntity.class, interval, mustSee, mustReach, keep(filter, x -> t.has(x) && filter.test(x)));
    }

    /** 합친 판정에 원래 판정의 플러그인 코드 표시를 잇는다(X1 끄기 — TargetingConditions). */
    private static Predicate<Targetable> keep(Predicate<Targetable> from, Predicate<Targetable> joined) {
        return TargetingConditions.isPlugin(from) ? TargetingConditions.pluginCode(joined) : joined;
    }

    /**
     * 만든 뒤 설정(API MobGoals#configureGoal). 지금은 HurtByTargetGoal#setAlertOthers(정확히 같은 클래스 — 종류 집합으로 받는다)뿐.
     */
    static void configure(Goal goal, String call, Object[] a) {
        String sig = signature(a);
        switch (call + sig) {
            case "HurtByTargetGoal#setAlertOthers(C)" -> {
                if (!(goal instanceof HurtByTargetGoal h)) throw new UnsupportedOperationException(call + " on " + goal.getClass().getSimpleName());
                Classes c = c(a, 0);
                h.setAlertOthers(c::matches);
            }
            default -> throw new UnsupportedOperationException("no vanilla goal method " + call + sig);
        }
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
                case Items x -> 'P';
                case Entities x -> 'E';
                case DamageTag x -> 'G';
                case Classes x -> 'C';
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

    private static Items p(Object[] a, int i) { return (Items) a[i]; }

    private static Entities e(Object[] a, int i) { return (Entities) a[i]; }

    private static DamageTag g(Object[] a, int i) { return (DamageTag) a[i]; }

    private static Classes c(Object[] a, int i) { return (Classes) a[i]; }

    /**
     * 아이템 판정: 종류 집합이면 서버에서 바로, 플러그인 판정이면 API 스택으로(예외면 로그 한 번 뒤 늘 거짓 — 그 목표만 멈춘다). 플러그인이 꺼졌으면 판정을 부르지 않고
     * 거짓이며, 그 목표({@code self})를 몹의 두 선택기에서 뺀다(M2a 어댑터와 같다 — 닫힌 클래스 로더의 코드를 부르지 않는다).
     */
    static Predicate<ItemStack> items(Mob mob, Goal[] self, Items g) {
        if (g.test() == null) {
            Set<String> ids = Set.copyOf(g.items());
            if (ids.isEmpty()) return ItemStack::isEmpty;
            return s -> !s.isEmpty() && ids.contains(org.domi.furnace.server.item.ItemTools.itemName(s));
        }
        var test = g.test();
        var f = guard(mob, self, g.alive(), test.getClass().getName(), g.owner(), "goal.item-test-threw", false,
                (ItemStack s) -> test.test(ChunkInternalsImpl.apiItem(s)));
        return TargetingConditions.pluginCode(f::apply);
    }

    /** 엔티티 판정 — 아이템 판정과 같은 보호. */
    static Predicate<Targetable> entities(Mob mob, Goal[] self, Entities g) {
        var test = g.test();
        var f = guard(mob, self, g.alive(), g.source().getClass().getName(), g.owner(), "goal.entity-test-threw", false, test::test);
        return TargetingConditions.pluginCode(f::apply);
    }

    /** 피해 종류 태그 — 고정이면 그 이름, 함수면 같은 보호(멈추면 null — 어느 피해도 맞지 않음). */
    static java.util.function.Supplier<String> damageTag(Mob mob, Goal[] self, DamageTag g) {
        if (g.dynamic() == null) {
            String tag = g.tag();
            return () -> tag;
        }
        var dyn = g.dynamic();
        var f = guard(mob, self, g.alive(), dyn.getClass().getName(), g.owner(), "goal.tag-test-threw", (String) null, (Object unused) -> dyn.get());
        return () -> f.apply(null);
    }

    /**
     * 플러그인 코드 보호: 예외(RuntimeException·LinkageError)면 로그 한 번 뒤 늘 {@code dead}. 플러그인이 꺼졌으면 부르지 않고 {@code dead}이며
     * 그 목표({@code self})를 몹의 두 선택기에서 뺀다(M2a 어댑터와 같다 — 닫힌 클래스 로더의 코드를 부르지 않는다).
     */
    private static <T, R> java.util.function.Function<T, R> guard(Mob mob, Goal[] self, java.util.function.BooleanSupplier alive, String what, String owner,
            String key, R dead, java.util.function.Function<T, R> call) {
        boolean[] broken = {false};
        return s -> {
            if (broken[0]) return dead;
            if (alive != null && !alive.getAsBoolean()) {
                broken[0] = true;
                Goal goal = self[0];
                if (goal != null) {
                    mob.goalSelector().removeGoalsMatching(x -> x == goal);
                    mob.targetSelector().removeGoalsMatching(x -> x == goal);
                }
                return dead;
            }
            try {
                return call.apply(s);
            } catch (org.domi.furnace.api.concurrent.ThreadOwnershipException elsewhere) {
                // 다른 region 이 쥔 것을 읽었다 — 그 후보만 거짓(바닐라에 없는 경계라 목표를 멈추지 않는다, M2b-2 검토 2)
                return dead;
            } catch (RuntimeException | LinkageError ex) {
                broken[0] = true;
                LOG.warn(org.domi.furnace.server.i18n.Lang.t(key, owner, what), ex);
                return dead;
            }
        };
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Furnace");
}
