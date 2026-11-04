// 바닐라 Level.playSound(except, entity, …) → sound_entity: 엔티티 번호를 실어 범위(16, 크기>1 이면 16×크기) 안 플레이어에게. 털 깎기가 이 길로 간다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.SoundEntity;
import org.domi.furnace.server.net.protocol.play.SoundPacket;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SoundEntityBroadcastTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final class Lvl extends BlockPlacementTest.TestLevel {
        final List<Player> players = new ArrayList<>();
        private final LevelServices svc = new LevelServices.Delegating(LevelServices.DEFAULT) {
            @Override public void forEachPlayer(Consumer<Player> c) { players.forEach(c); }
        };
        @Override protected LevelServices services() { return svc; }
    }

    private record Got(int packetId, int soundHolder, int source, int entityId, float volume) {}

    private static Player player(Lvl l, EmbeddedChannel ch, double x, double y, double z) {
        Player p = new Player(900 + l.players.size(), UUID.randomUUID(), "P", ch);
        p.publishPosition(x, y, z, 0, 0, true);
        p.drainPending();
        l.players.add(p);
        return p;
    }

    private static List<Got> read(EmbeddedChannel ch) {
        ch.runPendingTasks();
        List<Got> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf b = (ByteBuf) o;
            int id = Types.readVarInt(b);
            if (id != SoundEntity.ID) { out.add(new Got(id, 0, 0, 0, 0)); continue; }
            int holder = Types.readVarInt(b);
            if (holder == 0) { Types.readString(b, 32767); b.readBoolean(); }
            int source = Types.readVarInt(b);
            int eid = Types.readVarInt(b);
            out.add(new Got(id, holder, source, eid, b.readFloat()));
        }
        return out;
    }

    @Test
    void entitySoundCarriesEntityIdAndRespectsRangeAndExcept() {
        Lvl level = new Lvl();
        Sheep sheep = new Sheep(level, level.nextEntityId());
        sheep.moveTo(0.5, -60, 0.5, 0, 0);
        EmbeddedChannel nearCh = new EmbeddedChannel(), farCh = new EmbeddedChannel();
        Player near = player(level, nearCh, 4, -60, 4);
        player(level, farCh, 30, -60, 0);   // 16 밖

        level.playSound(null, sheep, "entity.sheep.ambient", "neutral", 1.0F, 1.0F);
        int holder = ItemContext.id("minecraft:sound_event", "entity.sheep.ambient") + 1;
        assertEquals(List.of(new Got(SoundEntity.ID, holder, SoundPacket.source("neutral"), sheep.id(), 1.0F)), read(nearCh));
        assertEquals(List.of(), read(farCh));

        level.playSound(null, sheep, "entity.sheep.ambient", "neutral", 2.0F, 1.0F);   // 범위 32
        assertEquals(1, read(farCh).size());
        read(nearCh);

        level.playSound(new org.domi.furnace.server.block.PlayerActor(near), near, "item.trident.throw", "players", 1.0F, 1.0F);
        assertEquals(List.of(), read(nearCh), "except 플레이어는 받지 않는다");
    }

    @Test
    void shearingSendsSoundEntityNotPositionalSound() {
        Lvl level = new Lvl();
        Sheep sheep = new Sheep(level, level.nextEntityId());
        sheep.moveTo(0.5, -60, 0.5, 0, 0);
        EmbeddedChannel ch = new EmbeddedChannel();
        player(level, ch, 2, -60, 2);
        sheep.shear("players");
        List<Got> got = read(ch);
        assertTrue(got.stream().anyMatch(g -> g.packetId() == SoundEntity.ID && g.entityId() == sheep.id()
                && g.soundHolder() == ItemContext.id("minecraft:sound_event", "entity.sheep.shear") + 1
                && g.source() == SoundPacket.source("players")), got.toString());
        assertTrue(got.stream().noneMatch(g -> g.packetId() == SoundPacket.ID), "위치 소리(sound)로는 보내지 않는다");
    }
}
