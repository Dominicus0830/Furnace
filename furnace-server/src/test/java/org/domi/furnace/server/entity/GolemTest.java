// 골렘: 호박 머리를 올리면 눈 골렘/철 골렘이 만들어지고, 철 골렘은 적대 몹을, 눈 골렘은 눈덩이로 적대 몹을 공격한다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GolemTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static void tick(BlockPlacementTest.TestLevel level, int n) {
        for (int t = 0; t < n; t++) {
            for (Entity e : level.entities().tickSnapshot()) {
                e.setOldPosAndRot();
                e.tickCount++;
                e.tick();
            }
        }
    }

    private static long count(BlockPlacementTest.TestLevel level, Class<?> cls) {
        return level.entities().tickSnapshot().stream().filter(cls::isInstance).count();
    }

    @Test
    void snowGolemFromPumpkinAndSnowBlocks() {
        var level = new BlockPlacementTest.TestLevel();
        int snow = B.defaultState(B.id("minecraft:snow_block"));
        level.setBlock(new Pos(3, -60, 3), snow, 3);
        level.setBlock(new Pos(3, -59, 3), snow, 3);
        level.setBlock(new Pos(3, -58, 3), B.defaultState(B.id("minecraft:carved_pumpkin")), 3);
        assertEquals(1, count(level, SnowGolem.class), "눈 골렘이 생겼다");
        assertTrue(B.isAir(level.getBlockState(new Pos(3, -60, 3))), "블록이 사라졌다");
    }

    @Test
    void ironGolemFromTShape() {
        var level = new BlockPlacementTest.TestLevel();
        int iron = B.defaultState(B.id("minecraft:iron_block"));
        level.setBlock(new Pos(3, -60, 3), iron, 3);
        level.setBlock(new Pos(3, -59, 3), iron, 3);
        level.setBlock(new Pos(2, -59, 3), iron, 3);
        level.setBlock(new Pos(4, -59, 3), iron, 3);
        level.setBlock(new Pos(3, -58, 3), B.defaultState(B.id("minecraft:carved_pumpkin")), 3);
        assertEquals(1, count(level, IronGolem.class), "철 골렘이 생겼다");
        var golem = (IronGolem) level.entities().tickSnapshot().stream().filter(e -> e instanceof IronGolem).findFirst().orElseThrow();
        assertTrue(golem.isPlayerCreated());
        assertEquals(100.0F, golem.maxHealth(), 1.0E-6F);
    }

    @Test
    void ironGolemAttacksZombieAndSnowGolemThrowsSnowballs() {
        var level = new BlockPlacementTest.TestLevel();
        var golem = new IronGolem(level, level.nextEntityId());
        golem.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(golem);
        var zombie = new Zombie(level, level.nextEntityId());
        zombie.moveTo(3.5, -60, 0.5, 0, 0);
        level.addFreshEntity(zombie);
        // 엔티티 번호(전역 카운터)의 홀짝에 따라 목표 갱신 박자가 달라 추격이 늦을 수 있어 넉넉히 기다린다.
        for (int t = 0; t < 1500 && zombie.isAlive(); t++) tick(level, 1);
        assertFalse(zombie.isAlive(), "철 골렘이 좀비를 잡았다");
        var snow = new SnowGolem(level, level.nextEntityId());
        snow.moveTo(0.5, -60, 5.5, 0, 0);
        level.addFreshEntity(snow);
        var z2 = new Zombie(level, level.nextEntityId());
        z2.moveTo(5.5, -60, 5.5, 0, 0);
        level.addFreshEntity(z2);
        boolean sawSnowball = false;
        for (int t = 0; t < 300 && !sawSnowball; t++) {
            tick(level, 1);
            sawSnowball = count(level, Snowball.class) > 0;
        }
        assertTrue(sawSnowball, "눈덩이를 던졌다");
    }
}
