// 신호기 효과 시험: 단수별 범위·지속·증폭, 4단 secondary, 범위 밖 제외.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.block.entity.BeaconBlockEntity;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Player;
import org.junit.jupiter.api.Test;

class BeaconEffectsTest {

    @org.junit.jupiter.api.BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final class Lvl extends BlockPlacementTest.TestLevel {
        final List<Player> players = new ArrayList<>();
        AABB lastBox;
        @Override public void forEachPlayerIn(AABB box, Consumer<Player> c) {
            lastBox = box;
            for (Player p : players) if (p.boundingBox().intersects(box)) c.accept(p);
        }
    }

    static Player player(Lvl l, double x, double y, double z) {
        Player p = new Player(1 + l.players.size(), UUID.randomUUID(), "P", new EmbeddedChannel());
        p.publishPosition(x, y, z, 0, 0, true);
        p.drainPending();
        l.players.add(p);
        return p;
    }

    @Test
    void rangeDurationAndAmplifierPerTier() {
        Lvl l = new Lvl();
        Pos pos = new Pos(0, -60, 0);
        Player near = player(l, 15.5, -60, 0.5);   // 1단 범위 20 안
        Player far = player(l, 25.5, -60, 0.5);
        BeaconBlockEntity.applyEffects(l, pos, 1, "minecraft:speed", null);
        var e = near.vitals().getEffect(MobEffect.SPEED);
        assertNotNull(e);
        assertEquals(11 * 20, e.duration());
        assertEquals(0, e.amplifier());
        assertNull(far.vitals().getEffect(MobEffect.SPEED));
        assertEquals(-20.0, l.lastBox.minX());
        assertEquals(21.0, l.lastBox.maxX());
        assertEquals(-80.0, l.lastBox.minY());
        assertEquals(-59 + 20 + (l.maxBuildHeight() - l.minBuildHeight()), l.lastBox.maxY());
    }

    @Test
    void tier4SamePrimaryAndSecondaryAmplifies() {
        Lvl l = new Lvl();
        Player p = player(l, 0.5, -60, 0.5);
        BeaconBlockEntity.applyEffects(l, new Pos(0, -62, 0), 4, "minecraft:speed", "minecraft:speed");
        var e = p.vitals().getEffect(MobEffect.SPEED);
        assertEquals(1, e.amplifier());
        assertEquals((9 + 8) * 20, e.duration());
    }

    @Test
    void tier4DifferentSecondaryAppliedSeparately() {
        Lvl l = new Lvl();
        Player p = player(l, 0.5, -60, 0.5);
        BeaconBlockEntity.applyEffects(l, new Pos(0, -62, 0), 4, "minecraft:speed", "minecraft:regeneration");
        assertEquals(0, p.vitals().getEffect(MobEffect.SPEED).amplifier());
        assertEquals(0, p.vitals().getEffect(MobEffect.REGENERATION).amplifier());
        Lvl l2 = new Lvl();
        Player q = player(l2, 0.5, -60, 0.5);
        BeaconBlockEntity.applyEffects(l2, new Pos(0, -62, 0), 3, "minecraft:speed", "minecraft:regeneration");
        assertNull(q.vitals().getEffect(MobEffect.REGENERATION));
    }
}
