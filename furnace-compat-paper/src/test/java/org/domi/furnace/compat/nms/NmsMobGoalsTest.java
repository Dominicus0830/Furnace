package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 몹 목표 shim(몹 목표 설계 M1): 월드 몹의 {@code goalSelector} 가 서버 선택기를 바닐라 모양으로 보이고, 바닐라처럼 {@code removeGoal} 만 멈추고 빼며,
 * 집합 보기의 빼기는 집합에서만 뺀다. 감싼 목표의 실행 여부는 지금 값, 다른 region·전역 스레드는 거부.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsMobGoalsTest {

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Goals");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()) + 3, (int) Math.floor(loc.y()), (int) Math.floor(loc.z()) + 3);
        level = ((CraftWorld) Bukkit.getWorlds().get(0)).getHandle();
    }

    @AfterAll
    void stop() { if (server != null) server.close(); }

    <T> T on(Supplier<T> task) { return server.onRegion(at, task); }

    Mob spawnChicken(double dx) {
        Mob m = (Mob) EntityType.CHICKEN.create(level);
        m.moveTo(at.x() + dx, at.y(), at.z() + 0.5, 0.0F, 0.0F);
        assertTrue(on(() -> level.addFreshEntity(m, SpawnReason.CUSTOM)));
        return m;
    }

    org.domi.furnace.server.entity.Mob serverMob(Mob m) {
        return on(() -> org.domi.furnace.api.internal.Internals.get().entityNow(((CraftEntity) m.getBukkitEntity()).furnaceHandle(),
                org.domi.furnace.server.entity.Mob.class));
    }

    void remove(Mob m) { on(() -> { m.getBukkitEntity().remove(); return null; }); }

    /** 시험용 서버 목표: 늘 쓸 수 있고, 시작·멈춤을 적는다. */
    static final class Probe extends org.domi.furnace.server.entity.ai.Goal {
        final List<String> log;
        final String name;
        Probe(List<String> log, String name, org.domi.furnace.server.entity.ai.Goal.Flag flag) {
            this.log = log;
            this.name = name;
            setFlags(EnumSet.of(flag));
        }
        @Override public boolean canUse() { return true; }
        @Override public void start() { log.add("start " + name); }
        @Override public void stop() { log.add("stop " + name); }
    }

    @Test
    void goalListMatchesTheServerWithVanillaClasses() {
        Mob m = spawnChicken(0.5);
        try {
            List<String> names = new ArrayList<>();
            List<Integer> prio = new ArrayList<>();
            for (WrappedGoal w : m.goalSelector.getAvailableGoals()) {   // 시험 스레드(틱 아님): 주인을 한 번 기다린다
                names.add(w.getGoal().getClass().getSimpleName());
                prio.add(w.getPriority());
            }
            assertEquals(List.of("FloatGoal", "PanicGoal", "BreedGoal", "TemptGoal", "FollowParentGoal", "WaterAvoidingRandomStrollGoal",
                    "LookAtPlayerGoal", "RandomLookAroundGoal"), names);
            assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), prio);
            WrappedGoal first = m.goalSelector.getAvailableGoals().iterator().next();
            assertSame(first, m.goalSelector.getAvailableGoals().iterator().next(), "같은 서버 목표는 같은 shim");
            assertTrue(m.targetSelector.getAvailableGoals().isEmpty());
            Goal tempt = m.goalSelector.getAvailableGoals().stream().map(WrappedGoal::getGoal).filter(g -> g instanceof TemptGoal).findFirst().orElseThrow();
            var flags = tempt.getFlags();
            assertTrue(flags.hasElement(Goal.Flag.MOVE) && flags.hasElement(Goal.Flag.LOOK) && flags.size() == 2, "유혹 목표의 깃발은 서버 것");

            // removeGoal(바닐라 목표 객체) — 서버에서 빠진다
            m.goalSelector.removeGoal(tempt);
            var s = serverMob(m);
            assertEquals(7, (int) on(() -> s.goalSelector().getAvailableGoals().size()));
            // 집합 보기의 removeIf
            assertTrue(m.goalSelector.getAvailableGoals().removeIf(w -> w.getGoal() instanceof PanicGoal));
            assertEquals(6, m.goalSelector.getAvailableGoals().size());
            m.goalSelector.removeAllGoals(g -> g.getClass().getSimpleName().startsWith("Random"));
            assertEquals(List.of("FloatGoal", "BreedGoal", "FollowParentGoal", "WaterAvoidingRandomStrollGoal", "LookAtPlayerGoal"),
                    m.goalSelector.getAvailableGoals().stream().map(w -> w.getGoal().getClass().getSimpleName()).toList());
            m.goalSelector.getAvailableGoals().clear();
            assertEquals(0, (int) on(() -> s.goalSelector().getAvailableGoals().size()));
        } finally {
            remove(m);
        }
    }

    /** 검토 1·3: 집합 보기로 뺀 돌던 목표는 멈추지 않고 칸을 쥔 채 남는다. 앞서 받은 감싼 목표의 isRunning 은 지금 값. */
    @Test
    void setRemovalKeepsRunningGoalsAndIsRunningIsLive() {
        Mob m = spawnChicken(1.5);
        try {
            var s = serverMob(m);
            List<String> log = new ArrayList<>();
            var held = new Probe(log, "held", org.domi.furnace.server.entity.ai.Goal.Flag.MOVE);
            on(() -> {
                s.goalSelector().removeAllGoals(g -> true);
                s.goalSelector().addGoal(0, held);
                return null;
            });
            WrappedGoal w = m.goalSelector.getAvailableGoals().iterator().next();
            assertFalse(w.isRunning());
            m.goalSelector.tick();
            assertTrue(w.isRunning(), "tick 뒤 앞서 받은 감싼 목표도 지금 값을 본다");
            assertEquals(List.of("start held"), log);

            assertTrue(m.goalSelector.getAvailableGoals().remove(w));
            assertEquals(List.of("start held"), log, "집합에서만 뺀다 — 멈추지 않는다");
            assertTrue(w.isRunning());
            var later = new Probe(log, "later", org.domi.furnace.server.entity.ai.Goal.Flag.MOVE);
            on(() -> {
                s.goalSelector().addGoal(5, later);
                return null;
            });
            m.goalSelector.tick();
            assertEquals(List.of("start held"), log, "빠진 목표가 MOVE 칸을 쥐고 있어 낮은 우선순위는 시작 못 한다(바닐라와 같음)");

            // removeGoal 은 멈추고 뺀다
            WrappedGoal lw = m.goalSelector.getAvailableGoals().iterator().next();
            m.goalSelector.removeGoal(lw.getGoal());
            assertTrue(m.goalSelector.getAvailableGoals().isEmpty());
        } finally {
            remove(m);
        }
    }

    @Test
    void controlFlagsNoAiAndThreadRules() {
        Mob m = spawnChicken(2.5);
        try {
            var s = serverMob(m);
            List<String> log = new ArrayList<>();
            var mover = new Probe(log, "mover", org.domi.furnace.server.entity.ai.Goal.Flag.MOVE);
            on(() -> {
                s.goalSelector().removeAllGoals(g -> true);
                s.goalSelector().addGoal(0, mover);
                return null;
            });
            m.goalSelector.disableControlFlag(Goal.Flag.MOVE);
            m.goalSelector.setControlFlag(Goal.Flag.UNKNOWN_BEHAVIOR, false);   // 서버 목표가 쓰지 않는 깃발 — 아무것도 안 바뀐다
            m.goalSelector.tick();
            assertEquals(List.of(), log, "막힌 칸의 목표는 시작하지 않는다");
            m.goalSelector.enableControlFlag(Goal.Flag.MOVE);
            m.goalSelector.tick();
            assertEquals(List.of("start mover"), log);
            assertTrue(m.goalSelector.hasTasks());

            assertFalse(m.isNoAi());
            m.setNoAi(true);
            assertTrue(on(s::isNoAi));
            assertTrue(m.isNoAi());
            m.setNoAi(false);

            Object r = server.onGlobal(() -> {
                try {
                    m.goalSelector.getAvailableGoals().size();
                    return null;
                } catch (RuntimeException ex) {
                    return ex;
                }
            });
            assertInstanceOf(org.domi.furnace.api.concurrent.ThreadOwnershipException.class, r);

            // 주인 스레드 안에서는 바로(기다리지 않는다)
            assertEquals(1, (int) on(() -> m.goalSelector.getAvailableGoals().size()));

            // 플러그인이 만든 감싼 목표를 집합에 바로 넣는 것은 M2b
            Goal plugin = new Goal() {
                @Override public boolean canUse() { return false; }
            };
            assertThrows(UnsupportedNmsCall.class, () -> m.goalSelector.getAvailableGoals().add(new WrappedGoal(1, plugin)));
            m.goalSelector.removeGoal(plugin);   // 서버에 없는 목표 — 바닐라처럼 아무 일 없음
        } finally {
            remove(m);
        }
    }

    /** 시험용 플러그인 목표(Goal 상속): 몹 위치를 읽고(주인 스레드에서 바로) 시작·틱·멈춤을 적는다. */
    static final class Watch extends Goal {
        final Mob mob;
        final List<String> log;
        boolean usable = true;
        Watch(Mob mob, List<String> log, EnumSet<Flag> flags) {
            this.mob = mob;
            this.log = log;
            setFlags(flags);
        }
        @Override public boolean canUse() { return usable; }
        @Override public void start() { log.add("start"); }
        @Override public void stop() { log.add("stop"); }
        @Override public void tick() { log.add("tick " + (mob.getX() == mob.getX())); }
    }

    /** M2a: 플러그인이 상속한 목표를 넣으면 서버 선택기가 그 메서드를 부르고, 집합 보기에는 그 객체가 그대로 보이며, 깃발은 Paper 처럼 살아 있다. */
    @Test
    void pluginGoalSubclassesRunOnTheServer() {
        Mob m = spawnChicken(4.5);
        try {
            var s = serverMob(m);
            List<String> log = new ArrayList<>();
            List<String> probe = new ArrayList<>();
            var mover = new Probe(probe, "mover", org.domi.furnace.server.entity.ai.Goal.Flag.MOVE);
            on(() -> {
                s.goalSelector().removeAllGoals(g -> true);
                s.goalSelector().addGoal(0, mover);
                return null;
            });
            Watch w = new Watch(m, log, EnumSet.noneOf(Goal.Flag.class));
            assertTrue(w.getFlags().hasElement(Goal.Flag.UNKNOWN_BEHAVIOR), "Paper: 깃발이 없으면 UNKNOWN_BEHAVIOR");
            m.goalSelector.addGoal(1, w);
            m.goalSelector.addGoal(3, w);   // 같은 목표 — 바닐라 집합처럼 한 번만
            assertEquals(2, m.goalSelector.getAvailableGoals().size());
            assertSame(w, m.goalSelector.getAvailableGoals().stream().map(WrappedGoal::getGoal).filter(g -> g instanceof Watch).findFirst().orElseThrow(),
                    "집합 보기에는 플러그인 목표 객체가 그대로");
            m.goalSelector.tick();
            assertEquals(List.of("start", "tick true"), log, "서버 선택기가 플러그인 목표를 돌린다(몹 읽기는 주인 스레드에서 바로)");
            assertEquals(List.of("start mover"), probe);

            // removeGoal(플러그인 목표 객체): 멈추고 뺀다
            log.clear();
            m.goalSelector.removeGoal(w);
            assertEquals(List.of("stop"), log);
            assertEquals(1, (int) on(() -> s.goalSelector().getAvailableGoals().size()));
            assertFalse(m.goalSelector.getAvailableGoals().stream().anyMatch(x -> x.getGoal() == w));

            // 넣은 뒤 받은 깃발 집합을 고쳐도(Paper 의 살아 있는 집합) 서버가 확인할 때 읽는다 — MOVE 를 쥔 앞 목표 때문에 시작하지 못한다
            List<String> log2 = new ArrayList<>();
            Watch w2 = new Watch(m, log2, EnumSet.noneOf(Goal.Flag.class));
            m.goalSelector.addGoal(1, w2);
            w2.getFlags().addUnchecked(Goal.Flag.MOVE);
            m.goalSelector.tick();
            assertEquals(List.of(), log2);
        } finally {
            remove(m);
        }
    }

    /** 바닐라 목표를 상속한 플러그인 클래스(T-237 몫 — 넣을 때 거부). */
    static final class MyFloat extends net.minecraft.world.entity.ai.goal.FloatGoal {
        MyFloat(Mob mob) { super(mob); }
        @Override public boolean canUse() { return true; }
    }

    /** M2b: 바닐라 목표 생성자는 그 자리에서 서버 목표를 만들고(즉시 부작용·검사 예외), 넣으면 그 객체가 그대로 보인다. */
    @Test
    void vanillaGoalConstructorsMakeServerGoals() {
        Mob m = spawnChicken(6.5);
        Mob other = spawnChicken(7.5);
        Mob squid = (Mob) EntityType.PARROT.create(level);
        squid.moveTo(at.x() + 8.5, at.y(), at.z() + 0.5, 0.0F, 0.0F);
        assertTrue(on(() -> level.addFreshEntity(squid, SpawnReason.CUSTOM)));
        try {
            var s = serverMob(m);
            on(() -> {
                s.goalSelector().removeAllGoals(g -> true);
                s.targetSelector().removeAllGoals(g -> true);
                s.getNavigation().setCanFloat(false);
                return null;
            });
            var floatGoal = new net.minecraft.world.entity.ai.goal.FloatGoal(m);
            assertTrue(on(() -> s.getNavigation().canFloat()), "바닐라 FloatGoal 생성자의 즉시 부작용(물에 뜨기 켜기)");
            assertTrue(floatGoal.getFlags().hasElement(Goal.Flag.JUMP));
            var melee = new net.minecraft.world.entity.ai.goal.MeleeAttackGoal((net.minecraft.world.entity.PathfinderMob) m, 1.0, false);
            assertTrue(melee.getFlags().hasElement(Goal.Flag.MOVE) && melee.getFlags().hasElement(Goal.Flag.LOOK));
            m.goalSelector.addGoal(0, floatGoal);
            m.goalSelector.addGoal(2, melee);
            var target = new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(m, net.minecraft.world.entity.player.Player.class, true);
            m.targetSelector.addGoal(1, target);
            assertEquals(List.of("FloatGoal", "MeleeAttackGoal"), on(() -> s.goalSelector().getAvailableGoals().stream()
                    .map(w -> w.getGoal().getClass().getSimpleName()).toList()), "서버에 진짜 바닐라 목표");
            assertSame(melee, m.goalSelector.getAvailableGoals().stream().map(WrappedGoal::getGoal).filter(g -> g instanceof net.minecraft.world.entity.ai.goal.MeleeAttackGoal)
                    .findFirst().orElseThrow(), "집합 보기에는 만든 그 객체");
            assertSame(target, m.targetSelector.getAvailableGoals().iterator().next().getGoal());
            m.goalSelector.removeGoal(melee);
            assertEquals(1, (int) on(() -> s.goalSelector().getAvailableGoals().size()));

            // 엔티티 클래스: 몹·동물은 플레이어가 아니라 받는다, 살아 있는 것 전부(플레이어와 섞임)는 거부
            var look = new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(m, Mob.class, 8.0F);
            assertTrue(look instanceof net.minecraft.world.entity.ai.goal.LookAtPlayerGoal);
            new net.minecraft.world.entity.ai.goal.AvoidEntityGoal<>((net.minecraft.world.entity.PathfinderMob) m, net.minecraft.world.entity.animal.Animal.class, 6.0F, 1.0, 1.2);
            var mixed = assertThrows(UnsupportedNmsCall.class,
                    () -> new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(m, net.minecraft.world.entity.LivingEntity.class, 8.0F));
            assertTrue(mixed.getMessage().contains("players"), mixed.getMessage());
            assertTrue(NmsGoals.targets("t", net.minecraft.world.entity.player.Player.class).playersOnly());
            assertTrue(NmsGoals.targets("t", Mob.class).types().contains("minecraft:zombie"));
            assertFalse(NmsGoals.targets("t", Mob.class).types().contains("minecraft:player"));

            // 바닐라 생성자 검사 예외는 원형 그대로(문 목표는 땅 길찾기만)
            var door = assertThrows(IllegalArgumentException.class, () -> new net.minecraft.world.entity.ai.goal.OpenDoorGoal(squid, false));
            assertEquals("Unsupported mob type for DoorInteractGoal", door.getMessage());

            // 바닐라 목표를 상속한 플러그인 클래스(T-237), 다른 몹의 목표는 거부
            var sub = assertThrows(UnsupportedNmsCall.class, () -> m.goalSelector.addGoal(1, new MyFloat(m)));
            assertTrue(sub.getMessage().contains("T-237"), sub.getMessage());
            var foreign = assertThrows(UnsupportedNmsCall.class, () -> other.goalSelector.addGoal(1, floatGoal));
            assertTrue(foreign.getMessage().contains("another mob"), foreign.getMessage());

            // 다른 region·전역 스레드에서 만들면 거부(그 몹의 region 에서 만들 것)
            Object r = server.onGlobal(() -> {
                try {
                    return new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(m);
                } catch (RuntimeException ex) {
                    return ex;
                }
            });
            assertInstanceOf(org.domi.furnace.api.concurrent.ThreadOwnershipException.class, r);
            assertTrue(((Exception) r).getMessage().contains("region of their mob"));
        } finally {
            remove(m);
            remove(other);
            remove(squid);
        }
    }

    /** M2b-2: TemptGoal 의 아이템 판정 — Ingredient 는 서버가 종류로, 람다는 주인 스레드에서 스택 사본으로. 판정 예외는 그 목표만 멈춘다. */
    @Test
    void temptGoalItemTests() {
        Mob m = spawnChicken(-2.5);   // 플레이어 곁(유혹 10칸 안)
        try {
            m.goalSelector.removeAllGoals(g -> true);
            var pm = (net.minecraft.world.entity.PathfinderMob) m;
            List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
            var byIngredient = new TemptGoal(pm, 1.0, net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.DIAMOND), false);
            var byLambda = new TemptGoal(pm, 1.0, st -> {
                seen.add(Thread.currentThread().getName());
                return st.is(net.minecraft.world.item.Items.EMERALD);
            }, false);
            var throwing = new TemptGoal(pm, 1.0, st -> { throw new IllegalStateException("boom"); }, false);
            assertTrue(byIngredient.getFlags().hasElement(Goal.Flag.MOVE));
            assertEquals(java.util.Set.of("minecraft:diamond"), NmsGoals.items(net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.DIAMOND)).items());
            var bukkit = org.domi.furnace.compat.paper.Compat.player(tp.player());
            on(() -> {
                bukkit.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND));
                return null;
            });
            server.tick(1);
            assertTrue(byIngredient.canUse(), "다이아몬드를 든 플레이어");
            assertFalse(byLambda.canUse());
            assertFalse(throwing.canUse(), "예외는 거짓");
            on(() -> {
                bukkit.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.EMERALD));
                return null;
            });
            server.tick(1);
            assertFalse(byIngredient.canUse());
            assertTrue(byLambda.canUse(), "람다 판정");
            assertFalse(seen.isEmpty());
            assertFalse(throwing.canUse());
        } finally {
            remove(m);
        }
    }

    /** PR 623 검토: 판정을 올린 플러그인이 꺼지면 판정을 부르지 않고 그 목표가 몹에서 빠진다(틱이 깨지지 않는다). */
    @Test
    void temptItemTestOfADisabledPluginIsDropped() {
        Mob m = spawnChicken(-1.5);
        try {
            m.goalSelector.removeAllGoals(g -> true);
            var alive = new java.util.concurrent.atomic.AtomicBoolean(true);
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            var items = new org.domi.furnace.api.internal.ChunkInternals.GoalItems(java.util.Set.of(), st -> {
                calls.incrementAndGet();
                return false;
            }, "test", alive::get);
            var s = serverMob(m);
            on(() -> {
                var g = NmsChunks.bridge().mobGoals(((CraftEntity) m.getBukkitEntity()).furnaceHandle());
                g.addGoal(false, 3, g.vanillaGoal("TemptGoal", 1.0, items, false));
                return null;
            });
            server.tick(3);
            assertTrue(calls.get() > 0, "켜져 있으면 판정을 부른다");
            alive.set(false);
            int before = calls.get();
            server.tick(5);
            assertEquals(before, calls.get(), "꺼지면 부르지 않는다");
            assertEquals(0, (int) on(() -> s.goalSelector().getAvailableGoals().size()), "그 목표는 빠진다");
        } finally {
            remove(m);
        }
    }
    // ------------------------------------------------------------------ M2b-2 나머지(판정 함수·피해 태그·클래스 목록)

    Mob spawn(EntityType<?> type, double dx, double dz) {
        Mob m = (Mob) type.create(level);
        m.moveTo(at.x() + dx, at.y(), at.z() + dz, 0.0F, 0.0F);
        assertTrue(on(() -> level.addFreshEntity(m, SpawnReason.CUSTOM)));
        return m;
    }

    /** 플러그인 엔티티 판정은 바닐라처럼 클래스에 맞는 후보에게만, 그 엔티티의 shim 객체로 불린다(종류 거르기가 판정 앞). */
    @Test
    void entityPredicatesSeeOnlyCandidatesOfTheClass() {
        Mob m = spawnChicken(-4.5);
        Mob chick = spawnChicken(-5.5);
        Mob parrot = spawn(EntityType.PARROT, -6.5, 0.5);
        try {
            m.goalSelector.removeAllGoals(g -> true);
            m.targetSelector.removeAllGoals(g -> true);
            var pm = (net.minecraft.world.entity.PathfinderMob) m;
            List<Object> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
            var nat = new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(m, net.minecraft.world.entity.animal.Chicken.class, 0, false, false,
                    e -> seen.add(e) && false);
            assertFalse(nat.canUse());
            assertTrue(seen.contains(chick), "같은 엔티티는 같은 shim 객체: " + seen);
            assertFalse(seen.contains(parrot), "다른 종류에는 판정을 부르지 않는다");
            assertFalse(seen.contains(m), "자기 자신은 조건이 먼저 뺀다");

            // 7인자 AvoidEntityGoal: 길찾기 조건 다음 피할 조건(바닐라 path.and(avoid)), 둘 다 클래스 후보에만
            List<String> order = new java.util.concurrent.CopyOnWriteArrayList<>();
            var avoid = new net.minecraft.world.entity.ai.goal.AvoidEntityGoal<>(pm, net.minecraft.world.entity.animal.Chicken.class,
                    e -> order.add("avoid") && false, 8.0F, 1.0, 1.2, e -> order.add("path"));
            assertFalse(avoid.canUse());
            assertEquals(List.of("path", "avoid"), order);
            // 6인자: 피할 조건 늘 참, 판정은 길찾기 조건
            List<Object> paths = new java.util.concurrent.CopyOnWriteArrayList<>();
            new net.minecraft.world.entity.ai.goal.AvoidEntityGoal<>(pm, net.minecraft.world.entity.animal.Parrot.class, 8.0F, 1.0, 1.2, e -> paths.add(e) && false).canUse();
            assertEquals(List.of(parrot), paths);

            // 판정 안의 다른 region 읽기(ThreadOwnershipException)는 그 후보만 거짓 — 목표는 계속 판정을 부른다
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            var crossing = new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(m, net.minecraft.world.entity.animal.Chicken.class, 0, false, false, e -> {
                calls.incrementAndGet();
                throw new org.domi.furnace.api.concurrent.ThreadOwnershipException("elsewhere");
            });
            assertFalse(crossing.canUse());
            assertFalse(crossing.canUse());
            assertEquals(2, calls.get(), "한 번의 경계 예외로 멈추지 않는다");
            // 그 밖의 예외는 그 목표를 멈춘다(로그 한 번)
            var thrown = new java.util.concurrent.atomic.AtomicInteger();
            var broken = new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(m, net.minecraft.world.entity.animal.Chicken.class, 0, false, false, e -> {
                thrown.incrementAndGet();
                throw new IllegalStateException("boom");
            });
            assertFalse(broken.canUse());
            assertFalse(broken.canUse());
            assertEquals(1, thrown.get());
        } finally {
            remove(m);
            remove(chick);
            remove(parrot);
        }
    }

    /** 검토 1: 플러그인 판정이 있으면 사거리 밖 플레이어에게도 판정을 부른다(바닐라는 거리보다 먼저 selector — X1 건너뛰기 끔). */
    @Test
    void pluginPlayerPredicateIsCalledForFarPlayersToo() {
        Mob m = spawnChicken(40.5);   // 닭의 추적 거리 16 밖
        try {
            m.targetSelector.removeAllGoals(g -> true);
            List<Object> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
            var nat = new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(m, net.minecraft.world.entity.player.Player.class, 0, false, false,
                    e -> seen.add(e));
            assertFalse(nat.canUse(), "사거리 밖이라 대상은 없다");
            assertEquals(1, seen.size(), "그래도 판정은 불렸다: " + seen);
            assertInstanceOf(net.minecraft.server.level.ServerPlayer.class, seen.get(0));
            assertSame(((CraftEntity) org.domi.furnace.compat.paper.Compat.player(tp.player())).getHandle(), seen.get(0));
        } finally {
            remove(m);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> damageTag(String path) {
        return (net.minecraft.tags.TagKey) net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace(path));
    }

    void hitByPlayer(Mob m) {
        var sm = serverMob(m);
        on(() -> {
            var sp = org.domi.furnace.api.internal.Internals.get().entityNow(tp.player(), org.domi.furnace.server.entity.Player.class);
            return sm.hurtBy(org.domi.furnace.server.entity.Entity.Damage.playerAttack(sp), 0.5F, sp);
        });
    }

    /** PanicGoal 의 태그·태그 함수: 함수는 마지막 피해가 있을 때만, 생성자에 넘긴 그 몹으로 불린다. 실패하면 태그 판정만 거짓(1.21.1 shouldPanic 은 태그뿐). */
    @Test
    void panicGoalDamageTags() {
        Mob m = spawnChicken(-3.5);
        try {
            m.goalSelector.removeAllGoals(g -> true);
            var pm = (net.minecraft.world.entity.PathfinderMob) m;
            List<Object> args = new java.util.concurrent.CopyOnWriteArrayList<>();
            var byFunction = new PanicGoal(pm, 1.0, mob -> {
                args.add(mob);
                return damageTag("panic_causes");
            });
            var fixed = new PanicGoal(pm, 1.0, damageTag("panic_causes"));
            var environmental = new PanicGoal(pm, 1.0, damageTag("panic_environmental_causes"));
            var throwing = new PanicGoal(pm, 1.0, mob -> null);
            assertFalse(byFunction.canUse());
            assertTrue(args.isEmpty(), "맞기 전에는 부르지 않는다");
            hitByPlayer(m);
            assertTrue(fixed.canUse(), "플레이어 공격은 panic_causes");
            assertFalse(environmental.canUse(), "환경 원인 태그에는 없다");
            assertTrue(byFunction.canUse());
            assertSame(pm, args.get(0), "바닐라처럼 그 몹");
            assertFalse(throwing.canUse(), "null 태그는 예외 — 거짓");
            assertFalse(throwing.canUse());
            // 다른 레지스트리의 태그는 바닐라 is 처럼 늘 거짓
            @SuppressWarnings({"unchecked", "rawtypes"})
            net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> blockTag = (net.minecraft.tags.TagKey) net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.BLOCK, net.minecraft.resources.ResourceLocation.withDefaultNamespace("panic_causes"));
            assertFalse(new PanicGoal(pm, 1.0, blockTag).canUse());
        } finally {
            remove(m);
        }
    }

    /** HurtByTargetGoal 무시 클래스(isAssignableFrom — 인터페이스·Object 도)와 setAlertOthers(정확히 같은 클래스), 번식 짝 클래스. */
    @Test
    void hurtByIgnoredClassesAlertOthersAndBreedPartner() throws Exception {
        Mob m = spawnChicken(-7.5);
        Mob zombie = spawn(EntityType.ZOMBIE, -8.5, 2.5);
        Mob piglin = spawn(EntityType.ZOMBIFIED_PIGLIN, -9.5, 2.5);
        try {
            m.targetSelector.removeAllGoals(g -> true);
            var pm = (net.minecraft.world.entity.PathfinderMob) m;
            var plain = new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(pm);
            var ignorePlayers = new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(pm, net.minecraft.world.entity.player.Player.class);
            var ignoreEverything = new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(pm, Object.class);
            var ignoreEnemies = new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(pm, net.minecraft.world.entity.monster.Enemy.class);
            var ignoreAnimals = new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(pm, net.minecraft.world.entity.animal.Animal.class);
            var sm = serverMob(m);
            var szAttacker = serverMob(zombie);
            server.tick(2);   // 맞은 시각(틱 수)이 0 이면 바닐라도 새 피해로 보지 않는다
            assertTrue(on(() -> sm.hurtBy(org.domi.furnace.server.entity.Entity.Damage.of(org.domi.furnace.server.entity.damage.DamageType.byKey("minecraft:mob_attack")), 0.5F, szAttacker)));
            assertSame(szAttacker, on(() -> sm.getLastHurtByMob()));
            assertTrue(plain.canUse(), "때린 좀비를 대상으로");
            assertTrue(ignorePlayers.canUse(), "좀비는 플레이어가 아니다");
            assertFalse(ignoreEverything.canUse(), "Object 는 모두");
            assertFalse(ignoreEnemies.canUse(), "인터페이스 Enemy — 좀비가 맞는다");
            assertTrue(ignoreAnimals.canUse());
            assertTrue(NmsGoals.classes("t", new Class<?>[] {net.minecraft.world.entity.player.Player.class}).any().get(0).playersOnly());
            var all = NmsGoals.classes("t", new Class<?>[] {Object.class}).any().get(0);
            assertTrue(all.playersOnly() && all.types().contains("minecraft:chicken"), "Object 는 플레이어 포함 모두");

            // 클래스 목록: 인터페이스는 shim 계층으로, 엔티티와 무관한 클래스는 맞는 것 없음, 플러그인 엔티티 클래스는 이름 있는 거부
            var enemies = NmsGoals.classes("t", new Class<?>[] {net.minecraft.world.entity.monster.Enemy.class}).any().get(0);
            assertTrue(enemies.types().contains("minecraft:zombie") && !enemies.types().contains("minecraft:chicken") && !enemies.playersOnly());
            var none = NmsGoals.classes("t", new Class<?>[] {String.class}).any().get(0);
            assertTrue(none.types().isEmpty() && !none.playersOnly());
            var plugin = assertThrows(UnsupportedNmsCall.class, () -> NmsGoals.classes("t", new Class<?>[] {MyZombie.class}));
            assertTrue(plugin.getMessage().contains("plugin class"), plugin.getMessage());
            assertTrue(NmsGoals.exactClasses("t", new Class<?>[] {net.minecraft.world.entity.monster.Zombie.class}).any().get(0).types()
                    .equals(java.util.Set.of("minecraft:zombie")), "정확히 같은 클래스 — 하위 클래스(허스크 등)는 아니다");

            // setAlertOthers: 등록 목표(좀비: 좀비화 피글린 빼기)를 덮어쓴다 — 빈 목록이면 아무도 빼지 않는다
            var hurtBy = (net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal) zombie.targetSelector.getAvailableGoals().stream().map(WrappedGoal::getGoal)
                    .filter(g -> g instanceof net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal).findFirst().orElseThrow();
            var sz = serverMob(zombie);
            var sp = serverMob(piglin);
            var server = on(() -> sz.targetSelector().getAvailableGoals().stream().map(w -> w.getGoal())
                    .filter(g -> g instanceof org.domi.furnace.server.entity.ai.goal.HurtByTargetGoal).findFirst().orElseThrow());
            assertTrue(ignoresAlert(server, sp), "바닐라 좀비는 좀비화 피글린에게 알리지 않는다");
            assertSame(hurtBy, hurtBy.setAlertOthers());
            assertFalse(ignoresAlert(server, sp), "빈 목록 — 아무도 빼지 않는다");
            hurtBy.setAlertOthers(net.minecraft.world.entity.monster.ZombifiedPiglin.class);
            assertTrue(ignoresAlert(server, sp));

            // 번식 짝 클래스: 후보만 넓힌다(만들기·넣기)
            var breed = new net.minecraft.world.entity.ai.goal.BreedGoal((net.minecraft.world.entity.animal.Animal) m, 1.0, net.minecraft.world.entity.animal.Animal.class);
            m.goalSelector.addGoal(2, breed);
            assertFalse(breed.canUse(), "사랑 모드가 아니다");
        } finally {
            remove(m);
            remove(zombie);
            remove(piglin);
        }
    }

    @SuppressWarnings("unchecked")
    boolean ignoresAlert(Object serverGoal, org.domi.furnace.server.entity.Mob other) throws Exception {
        var f = org.domi.furnace.server.entity.ai.goal.HurtByTargetGoal.class.getDeclaredField("toIgnoreAlert");
        f.setAccessible(true);
        var p = (java.util.function.Predicate<org.domi.furnace.server.entity.Mob>) f.get(serverGoal);
        return p != null && on(() -> p.test(other));
    }

    /** 플러그인 엔티티 클래스(만들지 않는다 — 클래스 목록 거부 시험용). */
    abstract static class MyZombie extends net.minecraft.world.entity.monster.Zombie {
        MyZombie() { super(null, null); }
    }
    /** 검토 후속: TemptGoal 의 플러그인 아이템 판정도 유혹 거리(10) 밖 플레이어에게 불린다(바닐라는 거리보다 먼저 selector). */
    @Test
    void temptItemTestIsCalledForFarPlayersToo() {
        Mob m = spawnChicken(40.5);
        try {
            m.goalSelector.removeAllGoals(g -> true);
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            var tempt = new TemptGoal((net.minecraft.world.entity.PathfinderMob) m, 1.0, st -> calls.incrementAndGet() < 0, false);
            assertFalse(tempt.canUse());
            assertEquals(2, calls.get(), "주 손·보조 손 한 번씩(바닐라 shouldFollow)");
        } finally {
            remove(m);
        }
    }

    /** 검토 후속: Panic 태그 함수의 예외는 로그 한 번 뒤 다시 부르지 않고, 플러그인이 꺼지면 부르지 않고 목표가 빠진다. */
    @Test
    void panicTagFunctionFailureAndDisabledPlugin() {
        Mob m = spawnChicken(-10.5);
        try {
            m.goalSelector.removeAllGoals(g -> true);
            server.tick(2);
            hitByPlayer(m);
            var thrown = new java.util.concurrent.atomic.AtomicInteger();
            var throwing = new PanicGoal((net.minecraft.world.entity.PathfinderMob) m, 1.0, mob -> {
                thrown.incrementAndGet();
                throw new IllegalStateException("boom");
            });
            assertFalse(throwing.canUse());
            assertFalse(throwing.canUse());
            assertEquals(1, thrown.get(), "멈춘 뒤로는 부르지 않는다");

            var alive = new java.util.concurrent.atomic.AtomicBoolean(true);
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            var tag = new org.domi.furnace.api.internal.ChunkInternals.GoalDamageTag(null, () -> {
                calls.incrementAndGet();
                return "minecraft:panic_causes";
            }, "test", alive::get);
            var s = serverMob(m);
            Object goal = on(() -> {
                var g = NmsChunks.bridge().mobGoals(((CraftEntity) m.getBukkitEntity()).furnaceHandle());
                Object made = g.vanillaGoal("PanicGoal", 1.0, tag);
                g.addGoal(false, 1, made);
                return made;
            });
            on(() -> ((org.domi.furnace.server.entity.ai.Goal) goal).canUse());
            assertTrue(calls.get() > 0, "켜져 있으면 맞은 뒤 함수를 부른다");
            alive.set(false);
            int before = calls.get();
            assertFalse(on(() -> ((org.domi.furnace.server.entity.ai.Goal) goal).canUse()));
            assertEquals(before, calls.get(), "꺼지면 부르지 않는다");
            assertEquals(0, (int) on(() -> s.goalSelector().getAvailableGoals().size()), "그 목표는 빠진다");
        } finally {
            remove(m);
        }
    }
}
