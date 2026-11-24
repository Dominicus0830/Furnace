package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.domi.furnace.api.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 몹 목표 M3: {@code EntityType#create} 로 만든 아직 월드에 없는 몹의 선택기. 넣기 전에는 샌드박스 몹을 보고, 바꾼 것은 넣을 때 같은 차례로 재생되며,
 * 넣기 전에 받은 shim 이 넣은 뒤(스폰 이벤트 안에서도) 진짜 목표를 본다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsMobGoalsDetachedTest {

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    BlockPos at;
    volatile Runnable onSpawn;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Detached");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()) + 3, (int) Math.floor(loc.y()), (int) Math.floor(loc.z()) + 3);
        level = ((CraftWorld) Bukkit.getWorlds().get(0)).getHandle();
        server.api().events().subscribe(server.plugin(CompatTestPlugin.class), org.domi.furnace.api.event.entity.CreatureSpawnEvent.class,
                EventPriority.NORMAL, false, e -> {
                    Runnable r = onSpawn;
                    if (r != null) r.run();
                });
    }

    @AfterAll
    void stop() { if (server != null) server.close(); }

    @AfterEach
    void reset() { onSpawn = null; }

    <T> T on(Supplier<T> task) { return server.onRegion(at, task); }

    Mob detached(EntityType<?> type, double dx) {
        Mob m = (Mob) type.create(level);
        m.moveTo(at.x() + dx, at.y(), at.z() + 0.5, 0.0F, 0.0F);
        return m;
    }

    void add(Mob m) { assertTrue(on(() -> level.addFreshEntity(m, SpawnReason.CUSTOM))); }

    org.domi.furnace.server.entity.Mob serverMob(Mob m) {
        return on(() -> org.domi.furnace.api.internal.Internals.get().entityNow(((CraftEntity) m.getBukkitEntity()).furnaceHandle(),
                org.domi.furnace.server.entity.Mob.class));
    }

    void remove(Mob m) { on(() -> { m.getBukkitEntity().remove(); return null; }); }

    static List<String> names(Iterable<WrappedGoal> goals) {
        List<String> out = new ArrayList<>();
        for (WrappedGoal w : goals) out.add(w.getPriority() + " " + w.getGoal().getClass().getSimpleName());
        return out;
    }

    List<String> serverNames(org.domi.furnace.server.entity.Mob s, boolean targets) {
        return on(() -> (targets ? s.targetSelector() : s.goalSelector()).getAvailableGoals().stream()
                .map(w -> w.getPriority() + " " + w.getGoal().getClass().getSimpleName()).toList());
    }

    static final List<String> CHICKEN = List.of("0 FloatGoal", "1 PanicGoal", "2 BreedGoal", "3 TemptGoal", "4 FollowParentGoal",
            "5 WaterAvoidingRandomStrollGoal", "6 LookAtPlayerGoal", "7 RandomLookAroundGoal");

    /** 넣기 전 기본 목록(registerGoals 뒤), 넣기 전 틱은 거부, 넣은 뒤 같은 shim 이 진짜 목표를 본다. */
    @Test
    void defaultListBeforeAddAndShimsFollowTheMob() {
        Mob m = detached(EntityType.CHICKEN, 0.5);
        List<WrappedGoal> before = new ArrayList<>(m.goalSelector.getAvailableGoals());   // 아무 스레드(시험 스레드)
        assertEquals(CHICKEN, names(before));
        assertEquals(8, m.goalSelector.getAvailableGoals().size());
        assertFalse(m.goalSelector.hasTasks(), "돌고 있는 목표 없음");
        assertTrue(m.targetSelector.getAvailableGoals().isEmpty());
        assertFalse(before.get(0).isRunning());
        assertTrue(before.get(0).getGoal().getFlags().hasElement(Goal.Flag.JUMP), "샌드박스 목표의 깃발");
        assertTrue(m.goalSelector.getAvailableGoals().contains(before.get(3)));
        assertThrows(UnsupportedNmsCall.class, () -> m.goalSelector.tick());
        assertThrows(UnsupportedNmsCall.class, () -> before.get(1).start());
        add(m);
        try {
            var s = serverMob(m);
            List<WrappedGoal> after = new ArrayList<>(m.goalSelector.getAvailableGoals());
            for (int i = 0; i < after.size(); i++) assertSame(before.get(i), after.get(i), "넣기 전 shim 이 진짜 목표의 shim");
            // 서버에서 시작하면 넣기 전에 받은 shim 이 실행 중을 본다
            on(() -> {
                s.goalSelector().getAvailableGoals().stream().filter(w -> w.getPriority() == 7).findFirst().orElseThrow().start();
                return null;
            });
            assertTrue(before.get(7).isRunning());
            m.goalSelector.removeGoal(before.get(7).getGoal());   // 월드 길: 멈추고 뺀다
            assertEquals(7, serverNames(s, false).size());
        } finally {
            remove(m);
        }
    }

    /** removeAllGoals 의 판정 함수는 한 번만(재생 때 다시 불리지 않는다), 생성자 목표·플러그인 목표를 넣으면 넣은 뒤 서버 목록이 같다. */
    @Test
    void editsBeforeAddReplayInOrder() {
        Mob m = detached(EntityType.CHICKEN, 1.5);
        AtomicInteger calls = new AtomicInteger();
        m.goalSelector.removeAllGoals(g -> {
            calls.incrementAndGet();
            return true;
        });
        assertEquals(8, calls.get());
        assertEquals(0, m.goalSelector.getAvailableGoals().size());
        FloatGoal f = new FloatGoal(m);
        LookAtPlayerGoal look = new LookAtPlayerGoal(m, net.minecraft.world.entity.player.Player.class, 6.0F);
        List<String> log = new ArrayList<>();
        Goal plugin = new Goal() {
            @Override public boolean canUse() { return false; }
            @Override public void start() { log.add("start"); }
        };
        m.goalSelector.addGoal(3, f);
        m.goalSelector.addGoal(1, look);
        m.goalSelector.addGoal(2, plugin);
        m.goalSelector.removeGoal(look);
        m.goalSelector.addGoal(5, look);   // 뺐다가 다시 — 뒤에 붙는다
        m.targetSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal((PathfinderMob) m));
        m.goalSelector.disableControlFlag(Goal.Flag.MOVE);
        List<String> want = List.of("3 FloatGoal", "2 " + plugin.getClass().getSimpleName(), "5 LookAtPlayerGoal");
        assertEquals(want, names(m.goalSelector.getAvailableGoals()));
        add(m);
        try {
            var s = serverMob(m);
            assertEquals(8, calls.get(), "판정 함수는 다시 불리지 않는다");
            assertEquals(List.of("3 FloatGoal", "2 CompatGoal", "5 LookAtPlayerGoal"), serverNames(s, false));
            assertEquals(List.of("0 HurtByTargetGoal"), serverNames(s, true));
            assertEquals(want, names(m.goalSelector.getAvailableGoals()));
            assertSame(f, m.goalSelector.getAvailableGoals().iterator().next().getGoal(), "생성자로 만든 그 객체");
            assertTrue(on(() -> disabled(s.goalSelector()).contains(org.domi.furnace.server.entity.ai.Goal.Flag.MOVE)), "막은 깃발도 재생");
            m.goalSelector.removeGoal(plugin);
            assertEquals(List.of("3 FloatGoal", "5 LookAtPlayerGoal"), serverNames(s, false));
        } finally {
            remove(m);
        }
    }

    @SuppressWarnings("unchecked")
    static java.util.Set<org.domi.furnace.server.entity.ai.Goal.Flag> disabled(org.domi.furnace.server.entity.ai.GoalSelector sel) {
        try {
            var f = org.domi.furnace.server.entity.ai.GoalSelector.class.getDeclaredField("disabledFlags");
            f.setAccessible(true);
            return (java.util.Set<org.domi.furnace.server.entity.ai.Goal.Flag>) f.get(sel);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 쓰지 않은 생성자 목표도 재생해 즉시 부작용(FloatGoal 의 물에 뜨기 켜기)이 넣은 몹에 남는다 — 바닐라와 같다. */
    @Test
    void unusedConstructorSideEffectsAreReplayed() {
        Mob z = detached(EntityType.ZOMBIE, 2.5);
        new FloatGoal(z);
        add(z);
        try {
            var s = serverMob(z);
            assertTrue(on(() -> s.getNavigation().canFloat()));
            assertFalse(names(z.goalSelector.getAvailableGoals()).stream().anyMatch(n -> n.endsWith("FloatGoal")));
        } finally {
            remove(z);
        }
        Mob plain = detached(EntityType.ZOMBIE, 3.5);
        add(plain);
        try {
            var s = serverMob(plain);
            assertFalse(on(() -> s.getNavigation().canFloat()), "대조: 좀비는 원래 물에 뜨지 않는다");
        } finally {
            remove(plain);
        }
    }

    /** 판정 함수는 잠금 밖에서 돈다 — 그 안에서 다른 스레드가 같은 몹을 불러도 막히지 않는다. 같은 스레드 재진입도 된다. */
    @Test
    void predicateRunsOutsideTheLock() throws Exception {
        Mob m = detached(EntityType.CHICKEN, 4.5);
        boolean[] done = {false};
        m.goalSelector.getAvailableGoals().removeIf(w -> {
            if (done[0]) return false;
            done[0] = true;
            try {
                int n = CompletableFuture.supplyAsync(() -> m.goalSelector.getAvailableGoals().size()).get(10, TimeUnit.SECONDS);
                assertEquals(8, n);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            assertEquals(8, m.goalSelector.getAvailableGoals().size(), "같은 스레드 재진입");
            return true;
        });
        assertEquals(7, m.goalSelector.getAvailableGoals().size());
        add(m);
        try {
            assertEquals(CHICKEN.subList(1, 8), serverNames(serverMob(m), false));
        } finally {
            remove(m);
        }
    }

    /** 스폰 이벤트 안에서 넣기 전 shim 을 쓰면 넣을 몹을 본다. 넣는 동안 다른 스레드의 호출은 넣은 뒤 진짜 몹에 닿는다. */
    @Test
    void preAddShimsInsideTheSpawnEvent() throws Exception {
        Mob m = detached(EntityType.CHICKEN, 5.5);
        WrappedGoal first = m.goalSelector.getAvailableGoals().iterator().next();
        List<Object> seen = new ArrayList<>();
        CompletableFuture<Void>[] other = new CompletableFuture[1];
        onSpawn = () -> {
            onSpawn = null;
            seen.add(m.goalSelector.getAvailableGoals().size());
            seen.add(first.isRunning());
            m.goalSelector.removeGoal(first.getGoal());
            // 넣는 동안 다른 스레드: 주인 region 을 기다렸다가 넣은 몹에 한다(이벤트 처리기는 기다리지 않는다)
            other[0] = CompletableFuture.runAsync(() -> m.goalSelector.addGoal(9, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(m)));
        };
        add(m);
        try {
            other[0].get(20, TimeUnit.SECONDS);
            assertEquals(List.of(8, false), seen);
            var s = serverMob(m);
            List<String> want = new ArrayList<>(CHICKEN.subList(1, 8));
            want.add("9 RandomLookAroundGoal");
            assertEquals(want, serverNames(s, false));
        } finally {
            remove(m);
        }
    }

    /** 넣기가 실패하면(같은 UUID) 넣기 전 shim 은 샌드박스로 돌아가고, 다시 넣으면 그 몹을 본다. */
    @Test
    void failedAddKeepsTheSandbox() {
        Mob a = detached(EntityType.CHICKEN, 6.5);
        add(a);
        Mob b = detached(EntityType.CHICKEN, 7.5);
        try {
            b.setUUID(a.getUUID());
            WrappedGoal w = b.goalSelector.getAvailableGoals().iterator().next();
            b.goalSelector.removeGoal(b.goalSelector.getAvailableGoals().stream().toList().get(7).getGoal());
            assertFalse(on(() -> level.addFreshEntity(b, SpawnReason.CUSTOM)));
            assertEquals(7, b.goalSelector.getAvailableGoals().size(), "샌드박스 그대로");
            assertFalse(w.isRunning());
        } finally {
            remove(a);
        }
        Mob c = detached(EntityType.CHICKEN, 8.5);
        c.goalSelector.removeAllGoals(g -> g instanceof FloatGoal);
        add(c);
        try {
            assertEquals(CHICKEN.subList(1, 8), serverNames(serverMob(c), false));
        } finally {
            remove(c);
        }
    }

    /** 바닐라 몹 전 종류: 넣기 전 목록의 shim 이 넣은 몹의 목록과 하나도 빠짐없이 짝지어진다(샌드박스와 진짜의 구조가 같다). */
    @Test
    void sandboxAndWorldStructuresMatchForAllMobs() {
        List<String> unpaired = new ArrayList<>();
        int mobs = 0;
        for (String id : new java.util.TreeSet<>(NmsKinds.entityClasses().keySet())) {
            if (id.equals("minecraft:ender_dragon") || id.equals("minecraft:wither")) continue;   // 보스 막대·폭발 — 이 시험의 대상이 아니다
            var type = EntityType.byString(id).orElse(null);
            if (type == null) continue;
            Object made;
            try {
                made = type.create(level);
            } catch (RuntimeException notMade) {
                continue;
            }
            if (!(made instanceof Mob m)) continue;
            mobs++;
            m.moveTo(at.x() + 0.5, at.y() + 2, at.z() + 10.5, 0.0F, 0.0F);
            List<WrappedGoal> goals = new ArrayList<>(m.goalSelector.getAvailableGoals());
            List<WrappedGoal> targets = new ArrayList<>(m.targetSelector.getAvailableGoals());
            if (!on(() -> level.addFreshEntity(m, SpawnReason.CUSTOM))) {
                unpaired.add(id + " not added");
                continue;
            }
            try {
                List<WrappedGoal> g2 = new ArrayList<>(m.goalSelector.getAvailableGoals());
                List<WrappedGoal> t2 = new ArrayList<>(m.targetSelector.getAvailableGoals());
                if (!sameObjects(goals, g2) || !sameObjects(targets, t2)) unpaired.add(id + " " + names(goals) + " vs " + names(g2));
            } finally {
                remove(m);
            }
        }
        assertTrue(mobs > 50, "몹 종류 " + mobs);
        assertEquals(List.of(), unpaired);
    }

    private static boolean sameObjects(List<WrappedGoal> a, List<WrappedGoal> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    /** 짝이 없는 넣기 전 객체는 아무것에도 닿지 않는다(조용히 틀린 목표를 가리키지 않는다). */
    @Test
    void goalFromAnotherDetachedMobIsRejected() {
        Mob a = detached(EntityType.CHICKEN, 9.5);
        Mob b = detached(EntityType.CHICKEN, 10.5);
        FloatGoal f = new FloatGoal(a);
        var e = assertThrows(UnsupportedNmsCall.class, () -> b.goalSelector.addGoal(0, f));
        assertTrue(e.getMessage().contains("another mob"), e.getMessage());
    }

    /** 서버 재생: 묶기는 바닐라 클래스·우선순위·깃발이 같을 때만, 짝 없는 ref 를 쓰는 조작은 실패, 지원 안 하는 생성자는 재생 상태를 잠그지 않는다. */
    @Test
    void replayPinsOnlyMatchingGoals() {
        var b = NmsChunks.bridge();
        var session = b.openDetachedEntityData("minecraft:chicken", List.of());
        Object w = new Object(), g = new Object(), w2 = new Object(), g2 = new Object();
        String floatGoal = "net.minecraft.world.entity.ai.goal.FloatGoal";
        session.apply(new org.domi.furnace.api.internal.ChunkInternals.EntityOp.GoalPin(false, 0, w2, g2, floatGoal, 1, 4));   // 우선순위가 다르다
        assertTrue(session.goalRef(w2) == null && session.goalRef(g2) == null);
        session.apply(new org.domi.furnace.api.internal.ChunkInternals.EntityOp.GoalPin(false, 0, w, g, floatGoal, 0, 4));
        assertTrue(session.goalRef(w) != null && session.goalRef(g) != null);
        var none = assertThrows(UnsupportedOperationException.class, () -> session.apply(
                new org.domi.furnace.api.internal.ChunkInternals.EntityOp.GoalCreate(new Object(), "net.minecraft.world.entity.ai.goal.NoSuchGoal", List.of())));
        assertTrue(none.getMessage().contains("no vanilla goal constructor"));
        assertEquals(8, session.mobGoals().availableSize(false), "재생 상태는 그대로 쓴다");
        assertThrows(IllegalArgumentException.class, () -> session.apply(new org.domi.furnace.api.internal.ChunkInternals.EntityOp.GoalAddWrapped(false, w2)));
    }

    /** 검토 권장 3: 기본 목록의 목표를 뺐다 다시 넣으면 같은 진짜 목표가 뒤에 붙는다. 스폰 이벤트를 취소하면 넣기 전 shim 은 샌드박스로 돌아간다. */
    @Test
    void defaultGoalReAddedAndCancelledSpawn() {
        Mob m = detached(EntityType.CHICKEN, 11.5);
        WrappedGoal panic = m.goalSelector.getAvailableGoals().stream().toList().get(1);
        Goal inner = panic.getGoal();
        m.goalSelector.removeGoal(inner);
        m.goalSelector.addGoal(9, inner);
        List<String> want = new ArrayList<>(CHICKEN);
        want.remove(1);
        want.add("9 PanicGoal");
        assertEquals(want, names(m.goalSelector.getAvailableGoals()));
        add(m);
        try {
            var s = serverMob(m);
            assertEquals(want, serverNames(s, false));
            assertSame(inner, m.goalSelector.getAvailableGoals().stream().toList().get(7).getGoal(), "다시 넣은 기본 목표도 같은 shim");
        } finally {
            remove(m);
        }

        Mob c = detached(EntityType.CHICKEN, 12.5);
        WrappedGoal first = c.goalSelector.getAvailableGoals().iterator().next();
        org.domi.furnace.api.event.entity.CreatureSpawnEvent[] cancel = {null};
        onSpawn = () -> {
            onSpawn = null;
            assertEquals(8, c.goalSelector.getAvailableGoals().size(), "이벤트 안: 넣을 몹");
        };
        var sub = server.api().events().subscribe(server.plugin(CompatTestPlugin.class), org.domi.furnace.api.event.entity.CreatureSpawnEvent.class,
                EventPriority.HIGH, false, e -> { if (cancel[0] == null) { cancel[0] = e; e.setCancelled(true); } });
        try {
            assertFalse(on(() -> level.addFreshEntity(c, SpawnReason.CUSTOM)));
        } finally {
            sub.unsubscribe();
        }
        assertFalse(first.isRunning(), "취소 뒤에는 샌드박스");
        assertEquals(8, c.goalSelector.getAvailableGoals().size());
    }

    /** 검토 권장 4: 샌드박스에서 목표 조작이 거절돼도 그 몹의 재생 상태(칸 보기)는 잠기지 않는다. */
    @Test
    void rejectedGoalEditDoesNotLockTheSession() {
        var session = NmsChunks.bridge().openDetachedEntityData("minecraft:chicken", List.of());
        assertThrows(IllegalArgumentException.class, () -> session.apply(new org.domi.furnace.api.internal.ChunkInternals.EntityOp.GoalAdd(false, 0, new Object())));
        assertThrows(IllegalArgumentException.class, () -> session.apply(new org.domi.furnace.api.internal.ChunkInternals.EntityOp.GoalRemove(false,
                (org.domi.furnace.api.internal.ChunkInternals.GoalRef) srv -> null)));
        assertFalse(session.slots().isEmpty());
        assertEquals(8, session.mobGoals().availableSize(false));
    }
}
