// 문 목표: 좀비(어려움)가 길을 막는 나무문을 240틱 걸려 부수고, 보통 난이도·mobGriefing 끔이면 건드리지 않는다. 열기 목표는 문을 열고 닫는다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.goal.BreakDoorGoal;
import org.domi.furnace.server.entity.ai.goal.OpenDoorGoal;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DoorGoalsTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final class Arena extends BlockPlacementTest.TestLevel {
        int difficulty = 3;
        boolean griefing = true;

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public int difficulty() { return difficulty; }
                @Override public boolean gameRule(String name) { return name.equals("mobGriefing") ? griefing : super.gameRule(name); }
            };
        }
    }

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");

    private static Arena wallWithDoor(boolean open) {
        var level = new Arena();
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) level.setBlock(new Pos(x, -61, z), B.defaultState("minecraft:grass_block"), 3);
        for (int z = 4; z <= 16; z++) for (int y = -60; y <= -58; y++) level.setBlock(new Pos(10, y, z), B.defaultState("minecraft:stone"), 3);
        String o = open ? "true" : "false";
        level.setBlock(new Pos(10, -60, 10), REG.stateIdOrAir("minecraft:oak_door", java.util.Map.of("facing", "west", "half", "lower", "hinge", "left", "open", o, "powered", "false")), 3);
        level.setBlock(new Pos(10, -59, 10), REG.stateIdOrAir("minecraft:oak_door", java.util.Map.of("facing", "west", "half", "upper", "hinge", "left", "open", o, "powered", "false")), 3);
        return level;
    }

    private static Zombie zombieAtDoor(Arena level) {
        var z = new Zombie(level, level.nextEntityId());
        z.moveTo(9.2, -60, 10.5, 0, 0);   // 문 앞(문 칸 x=10 까지 1.5 칸 안)
        level.addFreshEntity(z);
        z.setCanBreakDoors(true);
        z.setOnGround(true);   // 길찾기는 땅에 선 몹만 만든다
        assertTrue(z.getNavigation().moveTo(12.5, -60, 10.5, 1.0), "문을 지나는 길이 만들어진다");
        z.horizontalCollision = true;
        return z;
    }

    @Test
    void zombieBreaksDoorOnHard() {
        var level = wallWithDoor(false);
        var z = zombieAtDoor(level);
        var goal = new BreakDoorGoal(z, d -> d == 3);
        assertTrue(goal.canUse());
        goal.start();
        for (int i = 0; i < 245 && goal.canContinueToUse(); i++) goal.tick();
        assertTrue(B.isAir(level.getBlockState(new Pos(10, -60, 10))), "문 아래칸이 사라졌다");
    }

    @Test
    void zombieLeavesDoorAloneOnNormalOrWithoutGriefing() {
        var level = wallWithDoor(false);
        var z = zombieAtDoor(level);
        level.difficulty = 2;
        assertFalse(new BreakDoorGoal(z, d -> d == 3).canUse(), "보통 난이도에서는 안 부순다");
        level.difficulty = 3;
        level.griefing = false;
        assertFalse(new BreakDoorGoal(z, d -> d == 3).canUse(), "mobGriefing 이 꺼지면 안 부순다");
    }

    @Test
    void openDoorGoalOpensAndCloses() {
        var level = wallWithDoor(false);
        var z = zombieAtDoor(level);
        var goal = new OpenDoorGoal(z, true);
        assertTrue(goal.canUse());
        goal.start();
        assertTrue(B.behavior(level.getBlockState(new Pos(10, -60, 10))) instanceof org.domi.furnace.server.block.types.DoorBlock d
                && d.isOpen(level.getBlockState(new Pos(10, -60, 10))), "열렸다");
        goal.stop();
        var s = level.getBlockState(new Pos(10, -60, 10));
        assertFalse(((org.domi.furnace.server.block.types.DoorBlock) B.behavior(s)).isOpen(s), "닫혔다");
    }
}
