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

    @Test
    void llamaSpawnSpitSave() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 6000;
        Llama[] llamas = {new Llama(level, level.nextEntityId()), new TraderLlama(level, level.nextEntityId())};
        double x = 0.5;
        for (Llama l : llamas) {
            l.moveTo(x, -60, 0.5, 0, 0);
            x += 3;
            l.setOnGround(true);
            l.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(l.blockPosition()), (AgeableMob.GroupData) null);
            level.addFreshEntity(l);
            assertTrue(l.getStrength() >= 1 && l.getStrength() <= 5);
        }
        var chicken = new Chicken(level, level.nextEntityId());
        chicken.moveTo(8.5, -60, 0.5, 0, 0);
        level.addFreshEntity(chicken);
        llamas[0].performRangedAttack(chicken, 1.0F);
        tick(level, 100);
        for (Llama l : llamas) {
            assertTrue(l.isAlive());
            var loaded = (Llama) Entities.load(level, l.save());
            assertEquals(l.getStrength(), loaded.getStrength());
            assertEquals(l.variant(), loaded.variant());
        }
        assertTrue(llamas[0].getBreedOffspring(level, llamas[0]) instanceof Llama);
        assertTrue(llamas[1].getBreedOffspring(level, llamas[1]) instanceof TraderLlama);
    }

    @Test
    void pillagerChargesAndShoots() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 6000;
        var pillager = new Pillager(level, level.nextEntityId());
        pillager.moveTo(0.5, -60, 0.5, 0, 0);
        pillager.setOnGround(true);
        pillager.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(pillager.blockPosition()));
        level.addFreshEntity(pillager);
        var villager = new Villager(level, level.nextEntityId());
        villager.moveTo(7.5, -60, 0.5, 0, 0);
        level.addFreshEntity(villager);
        boolean charged = false, arrowSeen = false;
        for (int t = 0; t < 600; t++) {
            tick(level, 1);
            charged |= Crossbow.isCharged(pillager.getItemBySlot(EquipmentSlot.MAINHAND));
            for (Entity e : level.entities().tickSnapshot()) arrowSeen |= e instanceof Arrow;
        }
        assertTrue(pillager.isAlive());
        assertTrue(charged, "석궁을 장전했다");
        assertTrue(arrowSeen, "화살을 쐈다");
        assertNotNull(Entities.load(level, pillager.save()));
    }

    @Test
    void ravagerVindicatorVexSpawnTickSave() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        Mob[] mobs = {new Ravager(level, level.nextEntityId()), new Vindicator(level, level.nextEntityId()), new Vex(level, level.nextEntityId())};
        double x = 0.5;
        for (Mob m : mobs) {
            m.moveTo(x, -60, 0.5, 0, 0);
            x += 4;
            m.setOnGround(true);
            m.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(m.blockPosition()));
            level.addFreshEntity(m);
        }
        var villager = new Villager(level, level.nextEntityId());
        villager.moveTo(6.5, -60, 8.5, 0, 0);
        level.addFreshEntity(villager);
        tick(level, 200);
        for (Mob m : mobs) {
            assertTrue(m.isAlive());
            assertNotNull(Entities.load(level, m.save()));
        }
    }

    @Test
    void evokerCastsFangsAndSummons() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 6000;
        var evoker = new Evoker(level, level.nextEntityId());
        evoker.moveTo(0.5, -60, 0.5, 0, 0);
        evoker.setOnGround(true);
        evoker.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(evoker.blockPosition()));
        level.addFreshEntity(evoker);
        var villager = new Villager(level, level.nextEntityId());
        villager.moveTo(6.5, -60, 0.5, 0, 0);
        level.addFreshEntity(villager);
        boolean fangs = false, vex = false;
        for (int t = 0; t < 700; t++) {
            tick(level, 1);
            for (Entity e : level.entities().tickSnapshot()) {
                fangs |= e instanceof EvokerFangs;
                if (e instanceof Vex v) {
                    vex = true;
                    if (fangs || v.tickCount > 5) v.discard();   // 벡스가 주민을 죽이지 않도록 치운다
                }
            }
        }
        assertTrue(fangs, "송곳니를 소환했다");
        assertTrue(vex, "벡스를 소환했다");
        assertNotNull(Entities.load(level, evoker.save()));
        for (Entity e : level.entities().tickSnapshot()) if (e instanceof Vex v) assertNotNull(Entities.load(level, v.save()));
    }

    @Test
    void illusionerShootsArrows() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 6000;
        var ill = new Illusioner(level, level.nextEntityId());
        ill.moveTo(0.5, -60, 0.5, 0, 0);
        ill.setOnGround(true);
        ill.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(ill.blockPosition()));
        level.addFreshEntity(ill);
        var villager = new Villager(level, level.nextEntityId());
        villager.moveTo(9.5, -60, 0.5, 0, 0);
        level.addFreshEntity(villager);
        boolean arrow = false, invisible = false;
        for (int t = 0; t < 600; t++) {
            tick(level, 1);
            for (Entity e : level.entities().tickSnapshot()) arrow |= e instanceof Arrow;
            invisible |= ill.hasEffect(org.domi.furnace.server.effect.MobEffect.INVISIBILITY);
        }
        assertTrue(arrow, "화살을 쐈다");
        assertTrue(invisible, "투명해졌다");
        assertNotNull(Entities.load(level, ill.save()));
    }

    @Test
    void shulkerShootsBullet() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 6000;
        var shulker = new Shulker(level, level.nextEntityId());
        shulker.moveTo(0.5, -60, 0.5, 0, 0);
        shulker.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(shulker.blockPosition()));
        level.addFreshEntity(shulker);
        var villager = new Villager(level, level.nextEntityId());
        villager.moveTo(5.5, -60, 0.5, 0, 0);
        level.addFreshEntity(villager);
        shulker.setTarget(villager);
        boolean bullet = false, levitating = false;
        for (int t = 0; t < 200; t++) {
            tick(level, 1);
            for (Entity e : level.entities().tickSnapshot()) bullet |= e instanceof ShulkerBullet;
            levitating |= villager.hasEffect(org.domi.furnace.server.effect.MobEffect.LEVITATION);
        }
        assertTrue(shulker.isAlive());
        assertTrue(bullet, "탄환을 쐈다");
        assertTrue(levitating, "탄환에 맞아 공중 부양");
        assertNotNull(Entities.load(level, shulker.save()));
    }

    @Test
    void drownedThrowsTridentAndSwims() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        int water = B.defaultState(B.id("minecraft:water"));
        for (int x = -2; x <= 14; x++) for (int z = -4; z <= 4; z++) for (int y = -62; y <= -58; y++) level.setBlock(new Pos(x, y, z), water, 2);
        var drowned = new Drowned(level, level.nextEntityId());
        drowned.moveTo(0.5, -60, 0.5, 0, 0);
        drowned.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(drowned.blockPosition()));
        drowned.setItemSlot(EquipmentSlot.MAINHAND, Mob.stackOf("minecraft:trident"));
        level.addFreshEntity(drowned);
        var villager = new Villager(level, level.nextEntityId());
        villager.moveTo(11.5, -60, 0.5, 0, 0);
        level.addFreshEntity(villager);
        drowned.setTarget(villager);
        boolean trident = false;
        for (int t = 0; t < 400; t++) {
            tick(level, 1);
            for (Entity e : level.entities().tickSnapshot()) trident |= e instanceof ThrownTrident;
        }
        assertTrue(drowned.isAlive());
        assertTrue(trident, "삼지창을 던졌다");
        assertNotNull(Entities.load(level, drowned.save()));
    }

    @Test
    void pandaSpawnTickSaveBreed() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 6000;
        var a = new Panda(level, level.nextEntityId());
        a.moveTo(0.5, -60, 0.5, 0, 0);
        a.setOnGround(true);
        a.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(a.blockPosition()), (AgeableMob.GroupData) null);
        level.addFreshEntity(a);
        var b = new Panda(level, level.nextEntityId());
        b.moveTo(3.5, -60, 0.5, 0, 0);
        b.setOnGround(true);
        b.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(b.blockPosition()), (AgeableMob.GroupData) null);
        level.addFreshEntity(b);
        var baby = new Panda(level, level.nextEntityId());
        baby.moveTo(-3.5, -60, 0.5, 0, 0);
        baby.setOnGround(true);
        baby.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(baby.blockPosition()), (AgeableMob.GroupData) null);
        baby.setBaby(true);
        level.addFreshEntity(baby);
        tick(level, 600);
        assertTrue(a.isAlive() && b.isAlive() && baby.isAlive());
        var loaded = (Panda) Entities.load(level, a.save());
        assertEquals(a.getMainGene(), loaded.getMainGene());
        assertEquals(a.getHiddenGene(), loaded.getHiddenGene());
        assertTrue(a.getBreedOffspring(level, b) instanceof Panda);
    }

    @Test
    void frogBrainEatsSlimeAndSaves() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 6000;
        var frog = new Frog(level, level.nextEntityId());
        frog.moveTo(0.5, -60, 0.5, 0, 0);
        frog.setOnGround(true);
        frog.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(frog.blockPosition()));
        level.addFreshEntity(frog);
        var slime = new Slime(level, level.nextEntityId());
        slime.moveTo(4.5, -60, 0.5, 0, 0);
        slime.setNoAi(true);
        level.addFreshEntity(slime);
        for (int t = 0; t < 600; t++) {
            tick(level, 1);
        }
        assertFalse(slime.isAlive(), "개구리가 슬라임을 잡아먹었다");
        assertTrue(frog.isAlive());
        assertNotNull(Entities.load(level, frog.save()));
        var tadpole = new Tadpole(level, level.nextEntityId());
        tadpole.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(tadpole);
        tick(level, 50);
        assertTrue(tadpole.isAlive());
        var loaded = Entities.load(level, tadpole.save());
        assertNotNull(loaded);
        assertEquals(frog.variant(), ((Frog) Entities.load(level, frog.save())).variant());
    }
}
