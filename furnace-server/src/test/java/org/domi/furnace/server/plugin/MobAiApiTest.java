// 몹 AI API(E1): 목표 표 읽기·플러그인 목표 넣기/빼기·콜백 도중 목표 바꾸기·꺼진 플러그인/예외 목표 정리, 바닐라 목표 팩토리,
// 길 비용·칸 판정 훅, 소유 스레드 검사. 레벨은 스레드 없는 시험 레벨(늘 소유 스레드).
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.entity.ai.GoalControl;
import org.domi.furnace.api.entity.ai.GoalInfo;
import org.domi.furnace.api.entity.ai.GoalKey;
import org.domi.furnace.api.entity.ai.GoalSet;
import org.domi.furnace.api.entity.ai.MobAi;
import org.domi.furnace.api.entity.ai.MobGoal;
import org.domi.furnace.api.entity.ai.PathNodeType;
import org.domi.furnace.api.entity.ai.VanillaGoal;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Ghast;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Zombie;
import org.domi.furnace.server.entity.ai.goal.NearestAttackableTargetGoal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MobAiApiTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    /** 밤(좀비가 타지 않는다). */
    static final class NightLevel extends BlockPlacementTest.TestLevel {
        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public long dayTime() { return 18000; }
            };
        }
    }

    private static final TestPlugins.Gamma ON = new TestPlugins.Gamma();

    static {
        ON.setEnabled(true);
    }

    private static <T extends Entity> T put(BlockPlacementTest.TestLevel level, T e, double x, double z) {
        e.moveTo(x, -60, z, 0, 0);
        e.setOnGround(true);
        level.addFreshEntity(e);
        return e;
    }

    private static void tick(Entity e, int n) {
        for (int i = 0; i < n; i++) {
            e.setOldPosAndRot();
            e.tickCount++;
            e.tick();
        }
    }

    private static MobAi ai(Mob m) { return new MobAiImpl(null, m); }

    private static List<String> keys(MobAi ai, GoalSet set) {
        List<String> out = new ArrayList<>();
        for (GoalInfo g : ai.goals(set)) out.add(g.priority() + " " + g.key());
        return out;
    }

    /** 부른 횟수를 세는 목표. */
    static class Probe implements MobGoal {
        final GoalKey key;
        boolean want = true;
        int activate, starts, ticks, stops;
        Probe(String name) { key = GoalKey.of("test", name); }
        @Override public GoalKey key() { return key; }
        @Override public Set<GoalControl> controls() { return Set.of(GoalControl.MOVE, GoalControl.LOOK); }
        @Override public boolean shouldActivate(MobAi mob) { activate++; return want; }
        @Override public void start(MobAi mob) { starts++; }
        @Override public void stop(MobAi mob) { stops++; }
        @Override public void tick(MobAi mob) { ticks++; }
    }

    // ------------------------------------------------------------------ 목표 표

    @Test
    void vanillaGoalTableReadsAsVersionNeutralKeys() {
        var level = new BlockPlacementTest.TestLevel();
        Cow cow = put(level, new Cow(level, level.nextEntityId()), 0.5, 0.5);
        MobAi ai = ai(cow);
        assertEquals(List.of("0 minecraft:float", "1 minecraft:panic", "2 minecraft:breed", "3 minecraft:tempt", "4 minecraft:follow_parent",
                "5 minecraft:water_avoiding_random_stroll", "6 minecraft:look_at_player", "7 minecraft:random_look_around"), keys(ai, GoalSet.GOALS));
        assertEquals(List.of(), keys(ai, GoalSet.TARGETS));
        assertTrue(ai.hasGoal(GoalSet.GOALS, GoalKey.minecraft("tempt")));
        assertFalse(ai.hasGoal(GoalSet.TARGETS, GoalKey.minecraft("tempt")));
        GoalInfo stroll = ai.goals(GoalSet.GOALS).get(5);
        assertEquals(Set.of(GoalControl.MOVE), stroll.controls());
        assertFalse(stroll.plugin());
        assertFalse(ai.usesBrain());
        assertEquals("minecraft:cow", ai.type());
        assertEquals(VanillaGoal.panic(2.0).key(), GoalKey.minecraft("panic"), "팩토리 이름표 = 처음부터 걸린 바닐라 목표 이름표");
    }

    @Test
    void goalKeyNamesFollowVanillaGoalTypes() {
        assertEquals("melee_attack", GoalKeys.snake("MeleeAttackGoal"));
        assertEquals("water_avoiding_random_stroll", GoalKeys.snake("WaterAvoidingRandomStrollGoal"));
        assertEquals("float", GoalKeys.snake("FloatGoal"));
        assertEquals("ghast_shoot_fireball", GoalKeys.snake("GhastShootFireballGoal"));
        assertEquals("enderman_freeze_when_looked_at", GoalKeys.snake("EndermanFreezeWhenLookedAt"));
        var anon = new org.domi.furnace.server.entity.ai.goal.RandomLookAroundGoal(new Cow(new BlockPlacementTest.TestLevel(), 1)) {};
        assertEquals(GoalKey.minecraft("random_look_around"), GoalKeys.of(anon), "이름 없는 클래스는 부모 이름");
        assertThrows(IllegalArgumentException.class, () -> GoalKey.of("Bad Space", "x"));
        assertEquals(GoalKey.of("myplugin", "guard"), GoalKey.parse("MyPlugin:Guard"));
    }

    // ------------------------------------------------------------------ 플러그인 목표

    @Test
    void pluginGoalTakesControlAndStopsWhenItWantsTo() {
        var level = new BlockPlacementTest.TestLevel();
        Cow cow = put(level, new Cow(level, level.nextEntityId()), 0.5, 0.5);
        MobAi ai = ai(cow);
        Probe p = new Probe("probe");
        ai.addGoal(ON, GoalSet.GOALS, -1, p);
        ai.addGoal(ON, GoalSet.GOALS, -1, p);
        assertEquals(1, ai.goals(GoalSet.GOALS).stream().filter(GoalInfo::plugin).count(), "같은 목표 객체는 한 번만");
        tick(cow, 20);
        assertEquals(1, p.starts);
        assertTrue(p.ticks >= 9, "2틱에 한 번 이상: " + p.ticks);
        List<GoalInfo> running = ai.runningGoals(GoalSet.GOALS);
        assertTrue(running.stream().anyMatch(g -> g.key().equals(p.key)));
        assertTrue(running.stream().noneMatch(g -> !g.plugin() && g.controls().contains(GoalControl.MOVE)), "MOVE 를 쥔 동안 바닐라 이동 목표는 쉰다: " + running);

        p.want = false;
        tick(cow, 4);
        assertEquals(1, p.stops);
        assertTrue(ai.runningGoals(GoalSet.GOALS).stream().noneMatch(g -> g.key().equals(p.key)));

        assertEquals(1, ai.removeGoals(GoalSet.GOALS, p.key));
        assertFalse(ai.hasGoal(GoalSet.GOALS, p.key));
    }

    @Test
    void removingRunningGoalStopsItAndFreesItsControls() {
        var level = new BlockPlacementTest.TestLevel();
        Cow cow = put(level, new Cow(level, level.nextEntityId()), 0.5, 0.5);
        MobAi ai = ai(cow);
        Probe p = new Probe("hold");
        ai.addGoal(ON, GoalSet.GOALS, -1, p);
        tick(cow, 4);
        assertEquals(1, p.starts);
        assertTrue(ai.removeGoal(GoalSet.GOALS, p));
        assertEquals(1, p.stops, "돌던 목표는 멈춘 뒤 빠진다");
        assertFalse(ai.removeGoal(GoalSet.GOALS, p));
        // 잠긴 칸이 풀려 다른 목표가 다시 돈다(쳐다보기·두리번은 LOOK)
        ai.removeAllGoals(GoalSet.GOALS);
        Probe q = new Probe("next");
        ai.addGoal(ON, GoalSet.GOALS, 5, q);
        tick(cow, 4);
        assertEquals(1, q.starts, "빠진 목표의 MOVE·LOOK 잠금이 남아 있으면 시작하지 못한다");
    }

    @Test
    void goalsCanChangeTheTableFromInsideCallbacks() {
        var level = new BlockPlacementTest.TestLevel();
        Cow cow = put(level, new Cow(level, level.nextEntityId()), 0.5, 0.5);
        MobAi ai = ai(cow);
        MobGoal editor = new MobGoal() {
            boolean done;
            @Override public GoalKey key() { return GoalKey.of("test", "editor"); }
            @Override public boolean shouldActivate(MobAi mob) {
                if (!done) {
                    done = true;
                    mob.removeGoals(GoalSet.GOALS, GoalKey.minecraft("panic"));
                    mob.addGoal(GoalSet.GOALS, 9, VanillaGoal.leapAtTarget(0.4F));
                    mob.removeGoal(GoalSet.GOALS, this);
                }
                return false;
            }
        };
        ai.addGoal(ON, GoalSet.GOALS, 0, editor);
        assertDoesNotThrow(() -> tick(cow, 4));
        assertFalse(ai.hasGoal(GoalSet.GOALS, GoalKey.minecraft("panic")));
        assertTrue(ai.hasGoal(GoalSet.GOALS, GoalKey.minecraft("leap_at_target")));
        assertFalse(ai.hasGoal(GoalSet.GOALS, editor.key()), "자기 자신도 뺄 수 있다");
    }

    @Test
    void goalOfDisabledPluginOrThrowingGoalRemovesItself() {
        var level = new BlockPlacementTest.TestLevel();
        Cow cow = put(level, new Cow(level, level.nextEntityId()), 0.5, 0.5);
        MobAi ai = ai(cow);
        Probe off = new Probe("off");
        ai.addGoal(new TestPlugins.Gamma(), GoalSet.GOALS, 0, off);   // 켜지지 않은 플러그인
        MobGoal bad = new MobGoal() {
            @Override public GoalKey key() { return GoalKey.of("test", "bad"); }
            @Override public boolean shouldActivate(MobAi mob) { throw new IllegalStateException("고장"); }
        };
        ai.addGoal(ON, GoalSet.TARGETS, 0, bad);
        assertDoesNotThrow(() -> tick(cow, 4));
        assertEquals(0, off.activate, "꺼진 플러그인 목표는 부르지 않는다");
        assertFalse(ai.hasGoal(GoalSet.GOALS, off.key));
        assertFalse(ai.hasGoal(GoalSet.TARGETS, bad.key()));
    }

    // ------------------------------------------------------------------ 바닐라 목표 팩토리

    @Test
    void vanillaFactoryChecksMobKindAndValues() {
        var level = new NightLevel();
        Zombie zombie = put(level, new Zombie(level, level.nextEntityId()), 0.5, 0.5);
        Ghast ghast = new Ghast(level, level.nextEntityId());
        MobAi z = ai(zombie);
        assertThrows(IllegalArgumentException.class, () -> z.addGoal(GoalSet.GOALS, 1, VanillaGoal.breed(1.0)), "동물이 아니다");
        assertThrows(IllegalArgumentException.class, () -> ai(ghast).addGoal(GoalSet.GOALS, 1, VanillaGoal.meleeAttack(1.0, false)), "길찾는 몹이 아니다");
        assertThrows(IllegalArgumentException.class, () -> z.addGoal(GoalSet.GOALS, 1, VanillaGoal.tempt(1.0, List.of("minecraft:no_such_item"), false)));
        assertThrows(IllegalArgumentException.class, () -> z.addGoal(GoalSet.TARGETS, 1, VanillaGoal.nearestAttackableTarget("minecraft:nothing", false)));
        assertThrows(IllegalArgumentException.class, () -> z.addGoal(GoalSet.GOALS, 1, VanillaGoal.randomStroll(1.0, 0)));
        int before = z.goals(GoalSet.GOALS).size();
        z.addGoal(GoalSet.GOALS, 3, VanillaGoal.tempt(1.2, List.of("minecraft:wheat", "carrot"), false));
        z.addGoal(GoalSet.GOALS, 4, VanillaGoal.openDoor(true));
        z.addGoal(GoalSet.GOALS, 5, VanillaGoal.avoidEntity("minecraft:player", 6, 1.0, 1.2));
        assertEquals(before + 3, z.goals(GoalSet.GOALS).size());
        ai(ghast).addGoal(GoalSet.GOALS, 1, VanillaGoal.floatInWater());
    }

    @Test
    void zombieGivenCowTargetGoalHuntsTheCow() {
        var level = new NightLevel();
        Zombie zombie = put(level, new Zombie(level, level.nextEntityId()), 0.5, 0.5);
        Cow cow = put(level, new Cow(level, level.nextEntityId()), 6.5, 0.5);
        MobAi z = ai(zombie);
        z.removeAllGoals(GoalSet.TARGETS);
        z.addGoal(GoalSet.TARGETS, 1, VanillaGoal.nearestAttackableTarget("minecraft:cow", false));
        assertInstanceOf(NearestAttackableTargetGoal.class, zombie.targetSelector().getAvailableGoals().iterator().next().getGoal());
        for (int i = 0; i < 200 && z.target().isEmpty(); i++) tick(zombie, 1);
        assertEquals(cow.uuid(), z.target().orElseThrow());
        assertTrue(z.setTarget(null));
        assertTrue(z.target().isEmpty());
        assertTrue(z.setTarget(cow.uuid()));
        assertFalse(z.setTarget(java.util.UUID.randomUUID()), "없는 엔티티");
    }

    // ------------------------------------------------------------------ 길찾기

    @Test
    void pathCostAndTypeHookChangeRouteOnlyWhenSet() {
        var level = new BlockPlacementTest.TestLevel();
        Cow cow = put(level, new Cow(level, level.nextEntityId()), 0.5, 0.5);
        MobAi ai = ai(cow);
        var plain = cow.getNavigation().createPath(6.5, -60, 0.5, 1);
        assertNotNull(plain);
        for (int i = 0; i < plain.getNodeCount(); i++) assertEquals(0, plain.getNode(i).z, "훅이 없으면 곧게 간다(바닐라)");

        // x=3 줄의 |z|<=1 칸을 막는다 → 돌아간다(소 따라가기 범위 10 = 방문 노드 160 안에서)
        ai.setPathTypeHook(ON, (x, y, z, vanilla) -> x == 3 && Math.abs(z) <= 1 ? PathNodeType.BLOCKED : null);
        var around = cow.getNavigation().createPath(6.5, -60, 0.5, 1);
        assertNotNull(around);
        assertTrue(around.canReach());
        boolean aside = false;
        for (int i = 0; i < around.getNodeCount(); i++) {
            var n = around.getNode(i);
            if (n.x == 3) assertTrue(Math.abs(n.z) >= 2, "막은 칸을 지나면 안 된다: " + n.x + "," + n.z);
            if (Math.abs(n.z) >= 2) aside = true;
        }
        assertTrue(aside);

        ai.setPathTypeHook(ON, null);
        assertNull(cow.pathTypeHook());

        assertEquals(8.0F, ai.pathCost(PathNodeType.WATER));
        ai.setPathCost(PathNodeType.WATER, -1.0F);
        assertEquals(-1.0F, ai.pathCost(PathNodeType.WATER));
        assertThrows(IllegalArgumentException.class, () -> ai.pathCost(PathNodeType.of("future_type")));
    }

    @Test
    void throwingHookIsDetachedAndVanillaTypeUsed() {
        var level = new BlockPlacementTest.TestLevel();
        Cow cow = put(level, new Cow(level, level.nextEntityId()), 0.5, 0.5);
        ai(cow).setPathTypeHook(ON, (x, y, z, v) -> { throw new IllegalStateException("고장"); });
        assertNotNull(cow.getNavigation().createPath(6.5, -60, 0.5, 1));
        assertNull(cow.pathTypeHook());
        ai(cow).setPathTypeHook(new TestPlugins.Gamma(), (x, y, z, v) -> PathNodeType.BLOCKED);
        var p = cow.getNavigation().createPath(6.5, -60, 0.5, 1);
        assertNotNull(p);
        assertTrue(p.canReach(), "꺼진 플러그인의 훅은 쓰지 않는다");
        assertNull(cow.pathTypeHook());
    }

    // ------------------------------------------------------------------ 스레드

    @Test
    void foreignThreadIsRejected() {
        var level = new BlockPlacementTest.TestLevel() {
            @Override public boolean isOwnerThread() { return false; }
        };
        Cow cow = new Cow(level, level.nextEntityId());
        MobAi ai = ai(cow);
        assertThrows(ThreadOwnershipException.class, () -> ai.goals(GoalSet.GOALS));
        assertThrows(ThreadOwnershipException.class, () -> ai.addGoal(ON, GoalSet.GOALS, 0, new Probe("x")));
        assertThrows(ThreadOwnershipException.class, () -> ai.moveTo(1, 2, 3, 1.0));
        assertEquals(cow.uuid(), ai.uniqueId(), "바뀌지 않는 값은 어디서나 읽는다");
    }
}
