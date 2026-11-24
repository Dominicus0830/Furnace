// 목표 선택기: 우선순위 선점(숫자 작은 쪽)·제어 칸 충돌·계속 조건·멈춤, 이동 제어가 몹을 목표로 걷게 한다, 무작위 둘러보기가 머리를 돌린다.
package org.domi.furnace.server.entity.ai;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.goal.RandomLookAroundGoal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GoalSelectorTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
    }

    static class Script extends Goal {
        final List<String> log;
        final String name;
        boolean usable = true;
        Script(List<String> log, String name, EnumSet<Flag> flags) { this.log = log; this.name = name; setFlags(flags); }
        @Override public boolean canUse() { return usable; }
        @Override public void start() { log.add("start " + name); }
        @Override public void stop() { log.add("stop " + name); }
        @Override public void tick() { log.add("tick " + name); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
    }

    @Test
    void lowerNumberPreemptsSameFlag() {
        List<String> log = new ArrayList<>();
        var sel = new GoalSelector();
        var low = new Script(log, "low", EnumSet.of(Goal.Flag.MOVE));
        var high = new Script(log, "high", EnumSet.of(Goal.Flag.MOVE));
        sel.addGoal(5, low);
        sel.tick();
        assertEquals(List.of("start low", "tick low"), log);
        log.clear();
        sel.addGoal(2, high);
        sel.tick();
        assertEquals(List.of("stop low", "start high", "tick high"), log);
    }

    @Test
    void differentFlagsRunTogetherAndStopWhenUnusable() {
        List<String> log = new ArrayList<>();
        var sel = new GoalSelector();
        var a = new Script(log, "a", EnumSet.of(Goal.Flag.MOVE));
        var b = new Script(log, "b", EnumSet.of(Goal.Flag.LOOK));
        sel.addGoal(1, a);
        sel.addGoal(1, b);
        sel.tick();
        assertEquals(List.of("start a", "start b", "tick a", "tick b"), log);
        log.clear();
        a.usable = false;
        sel.tick();
        assertEquals(List.of("stop a", "tick b"), log);
    }

    @Test
    void lowerPriorityCannotReplaceRunningHigherOne() {
        List<String> log = new ArrayList<>();
        var sel = new GoalSelector();
        sel.addGoal(1, new Script(log, "first", EnumSet.of(Goal.Flag.MOVE)));
        sel.addGoal(3, new Script(log, "second", EnumSet.of(Goal.Flag.MOVE)));
        sel.tick();
        sel.tick();
        assertFalse(log.contains("start second"));
    }

    static final class Walker extends Mob {
        Walker(org.domi.furnace.server.block.ChunkLevel level, int id) { super(EntityType.VILLAGER, level, id); }
        @Override protected void registerGoals() { }
        @Override protected void addAdditionalSaveData(org.domi.furnace.server.net.io.nbt.NbtCompound tag) { super.addAdditionalSaveData(tag); }
    }

    @Test
    void moveControlWalksTowardWantedPoint() {
        var level = new BlockPlacementTest.TestLevel();
        var m = new Walker(level, level.nextEntityId());
        m.moveTo(0.5, -60, 0.5, 0, 0);
        m.setOnGround(true);
        for (int t = 0; t < 60; t++) {
            m.getMoveControl().setWantedPosition(4.5, -60, 0.5, 1.0);
            m.setOldPosAndRot();
            m.tickCount++;
            m.tick();
        }
        assertTrue(m.x() > 1.5, "동쪽으로 걸어가야 한다: " + m.x());
    }

    @Test
    void randomLookAroundEventuallyTurnsHead() {
        var level = new BlockPlacementTest.TestLevel();
        var m = new Walker(level, level.nextEntityId());
        m.moveTo(0.5, -60, 0.5, 0, 0);
        m.goalSelector().addGoal(1, new RandomLookAroundGoal(m));
        float h0 = m.yHeadRot();
        boolean turned = false;
        for (int t = 0; t < 2000 && !turned; t++) {
            m.setOldPosAndRot();
            m.tickCount++;
            m.tick();
            turned = Math.abs(m.yHeadRot() - h0) > 1.0F;
        }
        assertTrue(turned);
    }

    /** 몹 목표 설계 M1 검토 1: 집합에서만 빼면(바닐라 getAvailableGoals().removeIf·removeAllGoals) 돌던 목표는 멈추지 않고 잠근 칸도 남는다. */
    @Test
    void removingFromTheSetOnlyKeepsTheRunningGoalAndItsLock() {
        List<String> log = new ArrayList<>();
        var sel = new GoalSelector();
        var held = new Script(log, "held", EnumSet.of(Goal.Flag.MOVE));
        sel.addGoal(0, held);
        sel.tick();
        assertEquals(List.of("start held", "tick held"), log);
        log.clear();
        sel.removeAllGoals(g -> g == held);
        assertTrue(sel.getAvailableGoals().isEmpty());
        assertEquals(List.of(), log, "멈추지 않는다");
        var later = new Script(log, "later", EnumSet.of(Goal.Flag.MOVE));
        sel.addGoal(5, later);
        sel.tick();
        assertEquals(List.of(), log, "빠진 목표가 MOVE 칸을 쥔 채라 더 낮은 우선순위는 시작하지 못한다(바닐라와 같음)");
        // removeGoal 은 멈추고 뺀다
        var sel2 = new GoalSelector();
        var g2 = new Script(log, "g2", EnumSet.of(Goal.Flag.LOOK));
        sel2.addGoal(1, g2);
        sel2.tick();
        log.clear();
        sel2.removeGoal(g2);
        assertEquals(List.of("stop g2"), log);
    }

    /** 몹 목표 설계 M1 검토 2: 돌기 도중 집합에서 뺀 목표는 빠질 예정 표시로 같은 패스에서 시작·틱하지 않는다(바닐라 집합은 바로 빠진다). */
    @Test
    void goalsRemovedDuringThePassAreSkippedInThatPass() {
        List<String> log = new ArrayList<>();
        var sel = new GoalSelector();
        var victim = new Script(log, "victim", EnumSet.of(Goal.Flag.LOOK));
        var remover = new Script(log, "remover", EnumSet.of(Goal.Flag.MOVE)) {
            @Override public void start() {
                super.start();
                sel.removeFromSet(w -> w.getGoal() == victim);
            }
        };
        sel.addGoal(1, remover);
        sel.addGoal(2, victim);
        sel.tick();
        assertEquals(List.of("start remover", "tick remover"), log, "뒤쪽 목표는 그 패스에서 시작하지 않는다");
        assertEquals(1, sel.getAvailableGoals().size(), "돌기가 끝난 뒤 빠졌다");

        // 돌던 목표를 도는 중 집합에서만 빼면 멈추지 않고 그 패스의 틱도 건너뛴다(바닐라: 집합에서 빠져 훑기에 없다)
        log.clear();
        var sel2 = new GoalSelector();
        var running = new Script(log, "running", EnumSet.of(Goal.Flag.LOOK));
        var cutter = new Script(log, "cutter", EnumSet.of(Goal.Flag.MOVE)) {
            @Override public void tick() {
                super.tick();
                sel2.removeFromSet(w -> w.getGoal() == running);
            }
        };
        sel2.addGoal(1, cutter);
        sel2.addGoal(2, running);
        sel2.tick();
        assertEquals(List.of("start cutter", "start running", "tick cutter"), log, "앞 목표의 틱이 뺀 뒤쪽 목표는 같은 패스에서 틱하지 않고 멈추지도 않는다");
        assertEquals(1, sel2.getAvailableGoals().size());
        log.clear();
        sel2.tick();
        assertEquals(List.of("tick cutter"), log);
    }
}
