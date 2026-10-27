// 해골: 활을 들고 플레이어를 겨냥해 화살을 쏘고, 화살이 날아가 플레이어를 맞춘다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SkeletonCombatTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final class ArenaLevel extends BlockPlacementTest.TestLevel {
        long time;
        long dayTime;
        Player player;

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public long gameTime() { return time; }
                @Override public long dayTime() { return dayTime; }
                @Override public void forEachPlayer(Consumer<Player> c) { if (player != null) c.accept(player); }
            };
        }
    }

    private static final PlayerVitals.Surroundings DRY = new PlayerVitals.Surroundings(true, false, false, false, null, false, -64);

    @Test
    void skeletonShootsArrowsThatHitSurvivalPlayer() {
        var level = new ArenaLevel();
        level.dayTime = 18000;   // 낮이면 스켈레톤이 햇빛에 타 죽어(난수) 화살을 못 쏠 수 있다
        var p = new Player(7, UUID.randomUUID(), "Steve", new EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(10.5, -60, 0.5, 90, 0, true);
        for (int i = 0; i < 100; i++) p.vitals().tick(DRY);
        level.player = p;
        var s = new Skeleton(level, level.nextEntityId());
        s.moveTo(0.5, -60, 0.5, 0, 0);
        s.setOnGround(true);
        s.setItemSlot(EquipmentSlot.MAINHAND, Mob.stackOf("minecraft:bow"));
        level.addFreshEntity(s);
        float before = p.vitals().health();
        boolean sawArrow = false;
        for (int t = 0; t < 2000 && p.vitals().health() >= before; t++) {
            level.time++;
            for (Entity e : level.entities().tickSnapshot()) {
                if (e instanceof Arrow) sawArrow = true;
                e.setOldPosAndRot();
                e.tickCount++;
                e.tick();
            }
            p.vitals().tick(DRY);
        }
        assertTrue(sawArrow, "화살을 쐈다");
        assertTrue(p.vitals().health() < before, "맞았다: " + p.vitals().health());
    }

    @Test
    void arrowStopsInBlockAndSaves() {
        var level = new ArenaLevel();
        var a = new Arrow(level, level.nextEntityId(), 0.5, -60, 0.5);
        a.shoot(1, 0, 0, 1.6F, 0);
        for (int i = 0; i < 5; i++) a.tick();
        var tag = a.save();
        assertEquals("minecraft:arrow", tag.tags().get("id"));
        assertNotNull(Entities.load(level, tag));
    }

    @Test
    void creeperSwellsAndExplodesNearPlayer() {
        var level = new ArenaLevel();
        var p = new Player(7, UUID.randomUUID(), "Steve", new EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(2.5, -60, 0.5, 90, 0, true);
        for (int i = 0; i < 100; i++) p.vitals().tick(DRY);
        level.player = p;
        var c = new Creeper(level, level.nextEntityId());
        c.moveTo(0.5, -60, 0.5, 0, 0);
        c.setOnGround(true);
        level.addFreshEntity(c);
        float before = p.vitals().health();
        for (int t = 0; t < 120 && !c.isRemoved(); t++) {
            level.time++;
            for (Entity e : level.entities().tickSnapshot()) {
                e.setOldPosAndRot();
                e.tickCount++;
                e.tick();
            }
            p.vitals().tick(DRY);
        }
        assertTrue(c.isRemoved(), "폭발했다");
        assertTrue(p.vitals().health() < before, "폭발 피해: " + p.vitals().health());
    }

    @Test
    void bigSlimeSplitsIntoSmallerOnes() {
        var level = new ArenaLevel();
        var sl = new Slime(level, level.nextEntityId());
        sl.setSize(4, true);
        assertEquals(16.0F, sl.health(), 0.001);
        sl.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(sl);
        sl.hurt(Entity.Damage.OUT_OF_WORLD, 100.0F);
        for (int t = 0; t < 40 && !sl.isRemoved(); t++) {
            level.time++;
            sl.setOldPosAndRot();
            sl.tickCount++;
            sl.tick();
        }
        assertTrue(sl.isRemoved());
        var kids = level.getEntitiesOfClass(Slime.class, new AABB(-5, -70, -5, 5, -50, 5), e -> e != sl);
        assertTrue(kids.size() >= 2 && kids.size() <= 4, "갈라졌다: " + kids.size());
        assertEquals(2, kids.get(0).getSize());
    }
}
