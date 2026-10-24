// 목표 선택기: 우선순위 선점(숫자 작은 쪽)·제어 칸 충돌·계속 조건·멈춤, 이동 제어가 몹을 목표로 걷게 한다, 무작위 둘러보기가 머리를 돌린다.
package org.domi.furnace.server.entity.ai;

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
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final class Script extends Goal {
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
}
