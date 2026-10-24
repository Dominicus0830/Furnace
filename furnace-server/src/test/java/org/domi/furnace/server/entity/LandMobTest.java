// 땅 위 몹(여우 등): 스폰·저장·AI 틱이 예외 없이 돌고 핵심 행동이 나온다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LandMobTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static void tick(SkeletonCombatTest.ArenaLevel level, int n) {
        for (int t = 0; t < n; t++) {
            level.time++;
            for (Entity e : level.entities().tickSnapshot()) {
                e.setOldPosAndRot();
                e.tickCount++;
                e.tick();
            }
        }
    }

    @Test
    void foxSpawnsTicksAndSaves() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        var fox = new Fox(level, level.nextEntityId());
        fox.moveTo(0.5, -60, 0.5, 0, 0);
        fox.setOnGround(true);
        fox.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(fox.blockPosition()), (AgeableMob.GroupData) null);
        level.addFreshEntity(fox);
        var chicken = new Chicken(level, level.nextEntityId());
        chicken.moveTo(6.5, -60, 0.5, 0, 0);
        level.addFreshEntity(chicken);
        tick(level, 600);
        assertTrue(fox.isAlive());
        var loaded = (Fox) Entities.load(level, fox.save());
        assertEquals(fox.foxType(), loaded.foxType());
    }

    @Test
    void zombieGearPickup() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        var zombie = new Zombie(level, level.nextEntityId());
        zombie.moveTo(0.5, -60, 0.5, 0, 0);
        zombie.setCanPickUpLoot(true);
        level.addFreshEntity(zombie);
        var item = new ItemEntity(level, level.nextEntityId(), 0.5, -60, 0.5, Mob.stackOf("minecraft:iron_helmet"));
        item.setNoPickUpDelay();
        level.addFreshEntity(item);
        tick(level, 5);
        assertFalse(zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "투구를 주워 썼다");
        assertTrue(item.isRemoved());
    }

    @Test
    void striderWalksOnLava() {
        var level = new SkeletonCombatTest.ArenaLevel();
        int lava = B.defaultState(B.id("minecraft:lava"));
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) for (int y = -62; y <= -61; y++) level.setBlock(new Pos(x, y, z), lava, 2);
        var strider = new Strider(level, level.nextEntityId());
        strider.moveTo(0.5, -61, 0.5, 0, 0);
        level.addFreshEntity(strider);
        tick(level, 200);
        assertTrue(strider.isAlive(), "용암 위에서 산다");
        assertTrue(strider.y() > -62.0, "용암에 가라앉지 않는다: " + strider.y());
        assertNotNull(Entities.load(level, strider.save()));
    }

    @Test
    void horseFamilySpawnTickSave() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 6000;
        Mob[] mobs = {new Horse(level, level.nextEntityId()), new Donkey(level, level.nextEntityId()), new Mule(level, level.nextEntityId()),
                new SkeletonHorse(level, level.nextEntityId()), new ZombieHorse(level, level.nextEntityId())};
        double x = 0.5;
        for (Mob m : mobs) {
            m.moveTo(x, -60, 0.5, 0, 0);
            x += 3;
            m.setOnGround(true);
            if (m instanceof AgeableMob a) a.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(m.blockPosition()), (AgeableMob.GroupData) null); else m.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(m.blockPosition()));
            level.addFreshEntity(m);
        }
        tick(level, 200);
        for (Mob m : mobs) {
            assertTrue(m.isAlive());
            assertNotNull(Entities.load(level, m.save()));
        }
        var child = ((Horse) mobs[0]).getBreedOffspring(level, (AgeableMob) mobs[1]);
        assertTrue(child instanceof Mule);
    }
}
