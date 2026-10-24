// 좀비: 플레이어를 대상으로 삼아 다가와 때리고(난이도 보통 3), 생성자에서 아기 속성·저장이 맞고, 크리에이티브/관전자는 겨냥하지 않는다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ZombieCombatTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final class ArenaLevel extends BlockPlacementTest.TestLevel {
        long time;
        Player player;

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public long gameTime() { return time; }
                @Override public void forEachPlayer(Consumer<Player> c) { if (player != null) c.accept(player); }
            };
        }
    }

    private static final org.domi.furnace.server.entity.PlayerVitals.Surroundings DRY =
            new org.domi.furnace.server.entity.PlayerVitals.Surroundings(true, false, false, false, null, false, -64);

    private static Player player(ArenaLevel level, double x, GameMode mode) {
        var p = new Player(7, UUID.randomUUID(), "Steve", new EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, mode, false);
        p.applyMove(x, -60, 0.5, 90, 0, true);
        for (int i = 0; i < 100; i++) p.vitals().tick(DRY);   // 접속 직후 무적 시간 지나기
        level.player = p;
        return p;
    }

    private static void run(ArenaLevel level, Zombie z, int ticks) {
        for (int t = 0; t < ticks; t++) {
            level.time++;
            z.setOldPosAndRot();
            z.tickCount++;
            z.tick();
            if (level.player != null) level.player.vitals().tick(DRY);
        }
    }

    @Test
    void zombieChasesAndHitsSurvivalPlayer() {
        var level = new ArenaLevel();
        var p = player(level, 7.5, GameMode.SURVIVAL);
        var z = new Zombie(level, level.nextEntityId());
        z.moveTo(0.5, -60, 0.5, 0, 0);
        z.setOnGround(true);
        float before = p.vitals().health();
        run(level, z, 120);
        assertSame(p, z.getTarget(), "플레이어를 대상으로 삼는다");
        assertTrue(z.x() > 3.0, "다가왔다: " + z.x());
        assertTrue(p.vitals().health() < before, "맞았다: " + p.vitals().health());
    }

    @Test
    void zombieIgnoresCreativePlayer() {
        var level = new ArenaLevel();
        player(level, 5.5, GameMode.CREATIVE);
        var z = new Zombie(level, level.nextEntityId());
        z.moveTo(0.5, -60, 0.5, 0, 0);
        z.setOnGround(true);
        run(level, z, 100);
        assertNull(z.getTarget());
    }

    @Test
    void babyZombieIsFasterAndSmallerAndSaves() {
        var level = new ArenaLevel();
        var z = new Zombie(level, level.nextEntityId());
        double adult = z.attributeValue(org.domi.furnace.server.attribute.Attribute.MOVEMENT_SPEED);
        z.setBaby(true);
        assertEquals(adult * 1.5, z.attributeValue(org.domi.furnace.server.attribute.Attribute.MOVEMENT_SPEED), 1e-6);
        assertEquals(0.975F, z.bbHeight(), 1e-6);
        var back = new Zombie(level, level.nextEntityId());
        back.load(z.saveWithoutId(new NbtCompound()));
        assertTrue(back.isBaby());
        assertEquals(0.3F, back.bbWidth(), 1e-6);
    }

    @Test
    void zombieAttributesMatchVanilla() {
        var level = new ArenaLevel();
        var z = new Zombie(level, level.nextEntityId());
        var A = org.domi.furnace.server.attribute.Attribute.class;
        assertEquals(35.0, z.attributeValue(org.domi.furnace.server.attribute.Attribute.FOLLOW_RANGE), 1e-9);
        assertEquals(3.0, z.attributeValue(org.domi.furnace.server.attribute.Attribute.ATTACK_DAMAGE), 1e-9);
        assertEquals(2.0, z.attributeValue(org.domi.furnace.server.attribute.Attribute.ARMOR), 1e-9);
        assertEquals(20f, z.health());
        assertEquals(5, z.xpRewardForTest());
    }
}
