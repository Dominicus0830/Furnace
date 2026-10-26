// 물고기: 물속에서 헤엄치며 살아 있고, 물 밖에서는 숨이 다해 죽는다. 떼 물고기는 우두머리를 따라간다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FishSwimTest {

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
                // 근처에 플레이어가 있는 것처럼 — 없으면 noActionTime 이 100 을 넘어 무작위 산책이 멈춘다(바닐라 동작이라 테스트가 흔들렸다)
                if (e instanceof LivingEntity le) le.setNoActionTime(0);
                e.tick();
            }
        }
    }

    private static void fillWater(BlockPlacementTest.TestLevel level, int x0, int y0, int z0, int x1, int y1, int z1) {
        int water = B.defaultState(B.id("minecraft:water"));
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) level.setBlock(new Pos(x, y, z), water, 2);
    }

    @Test
    void codSwimsInWaterAndSurvives() {
        var level = new BlockPlacementTest.TestLevel();
        fillWater(level, -8, -61, -8, 8, -54, 8);
        var cod = new Cod(level, level.nextEntityId());
        cod.moveTo(0.5, -58, 0.5, 0, 0);
        level.addFreshEntity(cod);
        double sx = cod.x(), sz = cod.z();
        for (int t = 0; t < 2000 && Math.abs(cod.x() - sx) + Math.abs(cod.z() - sz) <= 0.5; t++) tick(level, 1);
        tick(level, 20);
        assertTrue(cod.isAlive(), "물속에서 산다");
        assertEquals(300, cod.getAirSupply());
        assertTrue(Math.abs(cod.x() - sx) + Math.abs(cod.z() - sz) > 0.5, "헤엄쳐 움직였다");
        assertTrue(cod.y() >= -61.0 && cod.y() < -53, "물 안에 있다: " + cod.y());
    }

    @Test
    void fishOutOfWaterSuffocates() {
        var level = new BlockPlacementTest.TestLevel();
        var salmon = new Salmon(level, level.nextEntityId());
        salmon.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(salmon);
        tick(level, 500);
        assertFalse(salmon.isAlive(), "물 밖에서 죽는다");
    }

    @Test
    void pufferfishPuffsNearPlayerlikeMobAndSaves() {
        var level = new BlockPlacementTest.TestLevel();
        fillWater(level, -8, -61, -8, 8, -54, 8);
        var puffer = new Pufferfish(level, level.nextEntityId());
        puffer.moveTo(0.5, -58, 0.5, 0, 0);
        level.addFreshEntity(puffer);
        var zombie = new Zombie(level, level.nextEntityId());
        zombie.moveTo(1.0, -58, 0.5, 0, 0);
        level.addFreshEntity(zombie);
        int max = 0;
        for (int t = 0; t < 400; t++) {
            // 복어가 부푸는 건 가까운 몹이 40틱 넘게 곁에 있을 때 — 무작위 헤엄으로 둘이 멀어지면 흔들리니 제자리에 붙들어 둔다
            puffer.moveTo(0.5, -58, 0.5, 0, 0);
            puffer.setDeltaMovement(0.0, 0.0, 0.0);
            zombie.moveTo(1.0, -58, 0.5, 0, 0);
            zombie.setDeltaMovement(0.0, 0.0, 0.0);
            tick(level, 1);
            max = Math.max(max, puffer.getPuffState());
            if (puffer.getPuffState() == 2) {
                assertEquals(0.7F, puffer.bbWidth(), 1.0E-6F);
                var loaded = (Pufferfish) Entities.load(level, puffer.save());
                assertEquals(2, loaded.getPuffState());
            }
        }
        assertEquals(2, max, "완전히 부풀었다");
    }

    @Test
    void tropicalFishGetsVariantAndSaves() {
        var level = new BlockPlacementTest.TestLevel();
        var fish = new TropicalFish(level, level.nextEntityId());
        fish.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(fish.blockPosition()), (AbstractSchoolingFish.SchoolSpawnGroupData) null);
        int v = fish.getVariant();
        var loaded = (TropicalFish) Entities.load(level, fish.save());
        assertEquals(v, loaded.getVariant());
    }

    @Test
    void squidDriftsInWaterAndGlowSquidSaves() {
        var level = new BlockPlacementTest.TestLevel();
        fillWater(level, -8, -61, -8, 8, -54, 8);
        var squid = new Squid(level, level.nextEntityId());
        squid.moveTo(0.5, -58, 0.5, 0, 0);
        level.addFreshEntity(squid);
        double sx = squid.x(), sy = squid.y(), sz = squid.z();
        tick(level, 200);
        assertTrue(squid.isAlive());
        assertTrue(Math.abs(squid.x() - sx) + Math.abs(squid.y() - sy) + Math.abs(squid.z() - sz) > 0.3, "움직였다");
        var glow = new GlowSquid(level, level.nextEntityId());
        var loaded = (GlowSquid) Entities.load(level, glow.save());
        assertNotNull(loaded);
    }

    @Test
    void dolphinAndGuardianSwimInWaterAndSave() {
        var level = new BlockPlacementTest.TestLevel();
        fillWater(level, -8, -61, -8, 8, -50, 8);
        var dolphin = new Dolphin(level, level.nextEntityId());
        dolphin.moveTo(0.5, -56, 0.5, 0, 0);
        level.addFreshEntity(dolphin);
        var guardian = new Guardian(level, level.nextEntityId());
        guardian.moveTo(3.5, -56, 3.5, 0, 0);
        level.addFreshEntity(guardian);
        double dx = dolphin.x(), gx = guardian.x();
        tick(level, 300);
        assertTrue(dolphin.isAlive() && guardian.isAlive(), "dolphin=" + dolphin.isAlive() + " guardian=" + guardian.isAlive());
        assertEquals(Dolphin.TOTAL_AIR_SUPPLY - 0, dolphin.getMaxAirSupply());
        assertTrue(dolphin.getAirSupply() > 0, "air=" + dolphin.getAirSupply());
        assertTrue(Math.abs(dolphin.x() - dx) + Math.abs(guardian.x() - gx) > 0.2, "움직였다");
        assertNotNull(Entities.load(level, dolphin.save()));
        assertNotNull(Entities.load(level, guardian.save()));
        assertNotNull(Entities.load(level, new ElderGuardian(level, level.nextEntityId()).save()));
    }

    @Test
    void turtleWalksSavesAndHatchesFromEgg() {
        var level = new BlockPlacementTest.TestLevel();
        var turtle = new Turtle(level, level.nextEntityId());
        turtle.moveTo(0.5, -60, 0.5, 0, 0);
        turtle.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(turtle.blockPosition()));
        level.addFreshEntity(turtle);
        tick(level, 200);
        assertTrue(turtle.isAlive());
        var loaded = (Turtle) Entities.load(level, turtle.save());
        assertEquals(turtle.getHomePos(), loaded.getHomePos());
        int before = level.entities().tickSnapshot().size();
        level.spawnBabyTurtle(new org.domi.furnace.server.block.Pos(2, -60, 2), 0);
        assertEquals(before + 1, level.entities().tickSnapshot().size());
        var baby = level.entities().tickSnapshot().stream().filter(e -> e instanceof Turtle t && t.isBaby()).findFirst().orElseThrow();
        assertEquals(0.4F * 0.3F, baby.bbHeight(), 1.0E-6F);
    }
}
