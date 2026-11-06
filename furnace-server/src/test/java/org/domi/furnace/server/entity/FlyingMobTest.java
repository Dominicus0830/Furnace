// 날아다니는 몹: 박쥐가 쉬다가 날아오르고, 블레이즈가 불덩이를 쏘고, 가스트·팬텀이 저장/불러오기를 지킨다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FlyingMobTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final PlayerVitals.Surroundings DRY = new PlayerVitals.Surroundings(true, false, false, false, null, false, -64);

    private static void tick(SkeletonCombatTest.ArenaLevel level, int n, Player p) {
        for (int t = 0; t < n; t++) {
            level.time++;
            for (Entity e : level.entities().tickSnapshot()) {
                e.setOldPosAndRot();
                e.tickCount++;
                e.tick();
            }
            if (p != null) p.vitals().tick(DRY);
        }
    }

    private static Player survivalPlayer(SkeletonCombatTest.ArenaLevel level, double x, double y, double z) {
        var p = new Player(7, UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(x, y, z, 90, 0, true);
        for (int i = 0; i < 100; i++) p.vitals().tick(DRY);
        level.player = p;
        return p;
    }

    @Test
    void batWakesUpWhenNoCeiling() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var bat = new Bat(level, level.nextEntityId());
        bat.moveTo(0.5, -58, 0.5, 0, 0);
        level.addFreshEntity(bat);
        assertTrue(bat.isResting());
        tick(level, 20, null);
        assertFalse(bat.isResting(), "받침이 없으면 날아오른다");
        assertTrue(bat.isAlive());
        assertNotNull(Entities.load(level, bat.save()));
    }

    @Test
    void blazeShootsSmallFireballsAtPlayer() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var p = survivalPlayer(level, 8.5, -60, 0.5);
        var blaze = new Blaze(level, level.nextEntityId());
        blaze.moveTo(0.5, -60, 0.5, 0, 0);
        blaze.setOnGround(true);
        level.addFreshEntity(blaze);
        boolean sawFireball = false;
        for (int t = 0; t < 600 && !sawFireball; t++) {
            tick(level, 1, p);
            for (Entity e : level.entities().tickSnapshot()) if (e instanceof SmallFireball) sawFireball = true;
        }
        assertTrue(sawFireball, "불덩이를 쐈다");
    }

    @Test
    void ghastPhantomSaveAndLoad() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var ghast = new Ghast(level, level.nextEntityId());
        var g2 = (Ghast) Entities.load(level, ghast.save());
        assertEquals(1, g2.getExplosionPower());
        var phantom = new Phantom(level, level.nextEntityId());
        phantom.setPhantomSize(5);
        var p2 = (Phantom) Entities.load(level, phantom.save());
        assertEquals(5, p2.getPhantomSize());
        assertEquals(0.9F * 1.75F, p2.bbWidth(), 1.0E-5F);
        assertEquals(11.0, p2.attributeValue(org.domi.furnace.server.attribute.Attribute.ATTACK_DAMAGE), 1.0E-9);
    }

    @Test
    void phantomCirclesThenDivesAtNight() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.dayTime = 18000;
        var p = survivalPlayer(level, 4.5, -60, 4.5);
        var phantom = new Phantom(level, level.nextEntityId());
        phantom.moveTo(0.5, -50, 0.5, 0, 0);
        phantom.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(phantom.blockPosition()));
        level.addFreshEntity(phantom);
        boolean swooped = false;
        for (int t = 0; t < 1200 && !swooped; t++) {
            tick(level, 1, p);
            if (phantom.attackPhase == Phantom.AttackPhase.SWOOP) swooped = true;
        }
        assertTrue(phantom.isAlive(), "밤에는 불타지 않는다");
        assertTrue(swooped, "급강하 단계에 들어간다");
    }

    @Test
    void parrotFliesAndSaves() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var parrot = new Parrot(level, level.nextEntityId());
        parrot.moveTo(0.5, -55, 0.5, 0, 0);
        parrot.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(parrot.blockPosition()), (AgeableMob.GroupData) null);
        level.addFreshEntity(parrot);
        tick(level, 300, null);
        assertTrue(parrot.isAlive());
        var loaded = (Parrot) Entities.load(level, parrot.save());
        assertEquals(parrot.getVariant(), loaded.getVariant());
    }
}
