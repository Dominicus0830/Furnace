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

            // 바닐라 목표를 상속한 플러그인 클래스(T-237), 다른 몹의 목표, M2b-2 생성자는 거부
            var sub = assertThrows(UnsupportedNmsCall.class, () -> m.goalSelector.addGoal(1, new MyFloat(m)));
            assertTrue(sub.getMessage().contains("T-237"), sub.getMessage());
            var foreign = assertThrows(UnsupportedNmsCall.class, () -> other.goalSelector.addGoal(1, floatGoal));
            assertTrue(foreign.getMessage().contains("another mob"), foreign.getMessage());
            assertThrows(UnsupportedNmsCall.class, () -> new net.minecraft.world.entity.ai.goal.BreedGoal(
                    (net.minecraft.world.entity.animal.Animal) m, 1.0, net.minecraft.world.entity.animal.Animal.class));

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
}
