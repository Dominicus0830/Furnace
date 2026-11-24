package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.api.internal.ChunkInternals;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.Goal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 플러그인이 만든 몹 목표의 서버 어댑터(몹 목표 설계 M2a): 깃발은 확인마다, 예외는 그 목표만 멈춤, 플러그인이 꺼지면 두 선택기에서 빠짐. */
class CompatGoalAdapterTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
    }

    static final class Walker extends Mob {
        Walker(org.domi.furnace.server.block.ChunkLevel level, int id) { super(EntityType.VILLAGER, level, id); }
        @Override protected void registerGoals() { }
    }

    /** 시험용 플러그인 목표 콜백. */
    static final class Script implements ChunkInternals.GoalCallbacks {
        final Object target = new Object();
        final List<String> log;
        final String name;
        boolean enabled = true, usable = true, throwOnTick;
        int flags;
        int canUseCalls;
        Script(List<String> log, String name, int flags) { this.log = log; this.name = name; this.flags = flags; }
        @Override public Object target() { return target; }
        @Override public String owner() { return "TestPlugin"; }
        @Override public boolean alive() { return enabled; }
        @Override public boolean canUse() { canUseCalls++; return usable; }
        @Override public boolean canContinueToUse() { return usable; }
        @Override public boolean isInterruptable() { return true; }
        @Override public void start() { log.add("start " + name); }
        @Override public void stop() { log.add("stop " + name); }
        @Override public boolean requiresUpdateEveryTick() { return false; }
        @Override public void tick() {
            if (throwOnTick) throw new IllegalStateException("플러그인 오류");
            log.add("tick " + name);
        }
        @Override public int flags() { return flags; }
    }

    static final int MOVE = 1, LOOK = 2;

    Mob mob() {
        var level = new BlockPlacementTest.TestLevel();
        return new Walker(level, level.nextEntityId());
    }

    @Test
    void flagsAreReadOnEveryCheckAndEqualityFollowsTheTarget() {
        List<String> log = new ArrayList<>();
        Mob m = mob();
        var a = new Script(log, "a", LOOK);
        var b = new Script(log, "b", LOOK);
        var m2 = mob();
        m2.goalSelector().addGoal(1, new ChunkInternalsImpl.CompatGoal(m2, b));
        m2.goalSelector().addGoal(2, new ChunkInternalsImpl.CompatGoal(m2, a));
        m2.goalSelector().addGoal(5, new ChunkInternalsImpl.CompatGoal(m2, a));   // 같은 플러그인 목표 — 집합에 한 번만
        assertEquals(2, m2.goalSelector().getAvailableGoals().size());
        m2.goalSelector().tick();
        assertEquals(List.of("start b", "tick b"), log, "같은 칸(LOOK)은 앞 우선순위만");

        log.clear();
        var c = new Script(log, "c", LOOK);
        var d = new Script(log, "d", LOOK);
        m.goalSelector().addGoal(1, new ChunkInternalsImpl.CompatGoal(m, c));
        m.goalSelector().addGoal(2, new ChunkInternalsImpl.CompatGoal(m, d));
        c.flags = MOVE;   // 넣은 뒤 깃발을 바꿔도 확인할 때 읽는다
        m.goalSelector().tick();
        assertEquals(List.of("start c", "start d", "tick c", "tick d"), log);
    }

    @Test
    void aThrowingGoalStopsAloneAndIsNotCalledAgain() {
        List<String> log = new ArrayList<>();
        Mob m = mob();
        var bad = new Script(log, "bad", MOVE);
        var good = new Script(log, "good", LOOK);
        m.goalSelector().addGoal(1, new ChunkInternalsImpl.CompatGoal(m, bad));
        m.goalSelector().addGoal(2, new ChunkInternalsImpl.CompatGoal(m, good));
        bad.throwOnTick = true;
        m.goalSelector().tick();
        assertEquals(List.of("start bad", "start good", "tick good"), log, "오류는 틱 밖으로 번지지 않는다");
        log.clear();
        int calls = bad.canUseCalls;
        for (int i = 0; i < 3; i++) m.goalSelector().tick();
        assertEquals(List.of("tick good", "tick good", "tick good"), log, "고장 난 목표는 멈추고(stop 도 부르지 않는다) 다시 불리지 않는다");
        assertEquals(calls, bad.canUseCalls);
        assertEquals(2, m.goalSelector().getAvailableGoals().size(), "고장 난 목표는 남는다(플러그인이 빼거나 꺼질 때 빠진다)");
    }

    @Test
    void aDisabledPluginsGoalsLeaveBothSelectorsWithoutBeingCalled() {
        List<String> log = new ArrayList<>();
        Mob m = mob();
        var s = new Script(log, "s", MOVE);
        m.goalSelector().addGoal(1, new ChunkInternalsImpl.CompatGoal(m, s));
        m.targetSelector().addGoal(1, new ChunkInternalsImpl.CompatGoal(m, s));
        m.goalSelector().tick();
        assertEquals(List.of("start s", "tick s"), log);
        log.clear();
        s.enabled = false;
        m.goalSelector().tick();
        assertEquals(List.of(), log, "꺼진 플러그인의 코드는 부르지 않는다(stop 도)");
        assertTrue(m.goalSelector().getAvailableGoals().isEmpty());
        assertTrue(m.targetSelector().getAvailableGoals().isEmpty());
    }
}
