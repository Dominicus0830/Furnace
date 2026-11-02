// 돌고래 보물 찾기(DolphinSwimToTreasureGoal)와 플레이어와 헤엄(DolphinSwimWithPlayerGoal) 시험.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.map.ExplorerMaps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DolphinTreasurePlayerTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static PlayerSleepingTest.Lvl pool() {
        var level = new PlayerSleepingTest.Lvl();
        for (int x = -8; x < 16; x++) for (int z = -8; z < 16; z++) {
            level.setBlock(new Pos(x, -61, z), B.defaultState("minecraft:sand"), 3);
            for (int y = -60; y <= -55; y++) level.setBlock(new Pos(x, y, z), B.defaultState("minecraft:water"), 3);
        }
        return level;
    }

    private static org.domi.furnace.server.entity.ai.Goal goal(Dolphin d, String name) {
        return d.goalSelector.getAvailableGoals().stream().map(w -> w.getGoal()).filter(g -> g.getClass().getSimpleName().equals(name)).findFirst().orElseThrow();
    }

    private static ExplorerMaps.Backend backend(int[] result, String[] tagOut) {
        return new ExplorerMaps.Backend() {
            public int[] findNearestMapStructure(String t, int x, int y, int z, int r) { return result == null ? null : new int[] {result[0], result[2]}; }
            public int[] findNearestStructurePos(String t, int x, int y, int z, int r) { tagOut[0] = t + "@" + r; return result; }
            public boolean waterOnMapOutlines(int bx, int bz) { return false; }
            public String dimension() { return "minecraft:overworld"; }
        };
    }

    @Test
    void goalTableHasSwimWithPlayer() {
        var level = pool();
        var table = MobGoalTableTest.table(new Dolphin(level, level.nextEntityId()).goalSelector);
        assertTrue(table.contains("2 DolphinSwimWithPlayerGoal"), table.toString());
        assertTrue(table.contains("1 DolphinSwimToTreasureGoal"), table.toString());
    }

    @Test
    void treasureGoalStoresFoundPosAndFinishesNearIt() {
        var level = pool();
        var tag = new String[1];
        level.maps = backend(new int[] {10, -40, 3}, tag);
        var dolphin = new Dolphin(level, level.nextEntityId());
        dolphin.moveTo(2.5, -58, 2.5, 0, 0);
        level.addFreshEntity(dolphin);
        dolphin.setAirSupply(dolphin.getMaxAirSupply());
        var g = goal(dolphin, "DolphinSwimToTreasureGoal");
        assertFalse(g.canUse(), "물고기를 안 먹었다");
        dolphin.setGotFish(true);
        assertTrue(g.canUse());
        g.start();
        assertEquals("minecraft:dolphin_located@50", tag[0]);
        assertEquals(new Pos(10, -40, 3), dolphin.getTreasurePos());
        assertTrue(g.canContinueToUse(), "아직 멀다");
        dolphin.moveTo(10.5, -40, 3.5, 0, 0);
        assertFalse(g.canContinueToUse(), "도착(4칸 안)");
        g.stop();
        assertFalse(dolphin.gotFish(), "도착하면 물고기 상태 해제");
    }

    @Test
    void treasureGoalGivesUpWhenNothingFound() {
        var level = pool();
        level.maps = backend(null, new String[1]);
        var dolphin = new Dolphin(level, level.nextEntityId());
        dolphin.moveTo(2.5, -58, 2.5, 0, 0);
        level.addFreshEntity(dolphin);
        dolphin.setAirSupply(dolphin.getMaxAirSupply());
        dolphin.setGotFish(true);
        var g = goal(dolphin, "DolphinSwimToTreasureGoal");
        g.start();
        assertFalse(g.canContinueToUse(), "구조물이 없으면 막힘");
        g.stop();
        assertFalse(dolphin.gotFish());
    }

    @Test
    void swimWithPlayerGivesDolphinsGrace() {
        var level = pool();
        var dolphin = new Dolphin(level, level.nextEntityId());
        dolphin.moveTo(2.5, -58, 2.5, 0, 0);
        level.addFreshEntity(dolphin);
        var player = PlayerSleepingTest.player(level, 6.5, -58, 2.5);
        var g = goal(dolphin, "DolphinSwimWithPlayerGoal");
        assertFalse(g.canUse(), "플레이어가 헤엄치지 않는다");
        player.swimming = true;
        assertTrue(g.canUse());
        g.start();
        player.drainOwnerTasks();   // 효과는 플레이어 주인 스레드 작업으로 넘어간다
        assertTrue(player.vitals().hasEffect(MobEffect.DOLPHINS_GRACE));
        assertTrue(g.canContinueToUse());
        player.swimming = false;
        assertFalse(g.canContinueToUse());
        player.swimming = true;
        player.applyMove(40.5, -58, 2.5, 0, 0, true);
        assertFalse(g.canContinueToUse(), "16칸 넘게 멀어짐");
    }
}
