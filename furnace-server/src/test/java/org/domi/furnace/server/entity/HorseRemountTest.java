// 말에서 내린 뒤 재탑승: 바닐라 Entity.removePassenger 가 boardingCooldown=60 을 걸고, baseTick 이 틱마다 1씩 줄인다(canRide: 웅크림 아님 && 0 이하).
// 그리고 말 능력치 난수: AbstractHorse.randomizeAttributes / setOffspringAttributes 의 호출 순서와 범위.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HorseRemountTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Horse horse(BlockPlacementTest.TestLevel level) {
        Horse h = new Horse(level, level.nextEntityId());
        h.moveTo(0.5, -60, 0.5, 0, 0);
        h.setOnGround(true);
        return h;
    }

    private static Player player() {
        Player p = new Player(951, UUID.randomUUID(), "rider", new EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.teleporter(t -> {});
        p.applyMove(2.5, -60, 0.5, 30.0F, 20.0F, true);
        return p;
    }

    @Test
    void cooldownIs60TicksAfterDismount() {
        var level = new BlockPlacementTest.TestLevel();
        Horse h = horse(level);
        Player p = player();
        assertTrue(p.startRiding(h, false));
        p.stopRiding();
        assertNull(p.vehicle());
        assertFalse(p.startRiding(h, false), "내린 직후에는 다시 못 탄다");
        for (int i = 0; i < 59; i++) p.tickRiding();
        assertFalse(p.startRiding(h, false), "59틱째까지는 못 탄다");
        p.tickRiding();
        assertTrue(p.startRiding(h, false), "60틱 뒤에는 탄다");
        assertSame(h, p.vehicle());
    }

    @Test
    void sneakingBlocksBoardingEvenAfterCooldown() {
        var level = new BlockPlacementTest.TestLevel();
        Horse h = horse(level);
        Player p = player();
        p.sneaking(true);
        assertFalse(p.startRiding(h, false));
        p.sneaking(false);
        assertTrue(p.startRiding(h, false));
    }

    @Test
    void sneakDismountClearsSneakSoNextInteractRides() {
        var level = new BlockPlacementTest.TestLevel();
        Horse h = horse(level);
        Player p = player();
        h.setTamed(true);
        p.startRiding(h, true);
        p.setPlayerInput(0, 0, false, true);
        p.rideTick();
        assertNull(p.vehicle(), "웅크리면 내린다");
        assertFalse(p.sneaking(), "내리면서 웅크림을 푼다");
    }

    @Test
    void randomizedHorseAttributesAreInVanillaRange() {
        for (long seed = 0; seed < 200; seed++) {
            var level = new BlockPlacementTest.TestLevel();
            Horse h = horse(level);
            h.randomizeAttributes(new LegacyRandomSource(seed));
            double hp = h.attributes().get(Attribute.MAX_HEALTH).baseValue();
            double sp = h.attributes().get(Attribute.MOVEMENT_SPEED).baseValue();
            double jp = h.attributes().get(Attribute.JUMP_STRENGTH).baseValue();
            assertTrue(hp >= 15.0 && hp <= 30.0 && hp == Math.floor(hp), "체력 15..30 정수: " + hp);
            assertTrue(sp >= 0.45F * 0.25 && sp <= (0.45F + 0.9) * 0.25, "속도: " + sp);
            assertTrue(jp >= 0.4F && jp <= 1.0F + 1e-6, "점프: " + jp);
        }
    }

    @Test
    void randomizeAttributesConsumesInVanillaOrder() {
        // 바닐라: 체력 nextInt(8), nextInt(9) → 속도 nextDouble×3 → 점프 nextDouble×3
        var level = new BlockPlacementTest.TestLevel();
        Horse h = horse(level);
        h.randomizeAttributes(new LegacyRandomSource(42L));
        var r = new LegacyRandomSource(42L);
        double hp = 15.0F + r.nextInt(8) + r.nextInt(9);
        double sp = (0.45F + r.nextDouble() * 0.3 + r.nextDouble() * 0.3 + r.nextDouble() * 0.3) * 0.25;
        double jp = 0.4F + r.nextDouble() * 0.2 + r.nextDouble() * 0.2 + r.nextDouble() * 0.2;
        assertEquals(hp, h.attributes().get(Attribute.MAX_HEALTH).baseValue());
        assertEquals(sp, h.attributes().get(Attribute.MOVEMENT_SPEED).baseValue());
        assertEquals(jp, h.attributes().get(Attribute.JUMP_STRENGTH).baseValue());
    }

    @Test
    void offspringAttributeFollowsVanillaFormula() {
        double min = 15.0, max = 30.0;
        for (long seed = 0; seed < 100; seed++) {
            var a = new LegacyRandomSource(seed);
            double got = AbstractHorse.createOffspringAttribute(20.0, 28.0, min, max, a);
            var r = new LegacyRandomSource(seed);
            double d = 0.15 * (max - min), d1 = Math.abs(20.0 - 28.0) + d * 2.0, d2 = 24.0;
            double d4 = d2 + d1 * ((r.nextDouble() + r.nextDouble() + r.nextDouble()) / 3.0 - 0.5);
            double exp = d4 > max ? max - (d4 - max) : d4 < min ? min + (min - d4) : d4;
            assertEquals(exp, got);
            assertTrue(got >= min && got <= max);
        }
    }
}
