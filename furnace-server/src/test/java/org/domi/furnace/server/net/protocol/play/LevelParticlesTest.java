// level_particles(0x29) 바이트 배치, 옵션 있는 입자 인코딩, 입자 레지스트리 id, 거리 필터.
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class LevelParticlesTest {

    private static ByteBuf encode(LevelParticles p) {
        ByteBuf b = Unpooled.buffer();
        p.write(b);
        return b;
    }

    @Test
    void id_and_registry_matchVanilla() {
        assertEquals(41, LevelParticles.ID);   // packets.json: minecraft:level_particles = 41
        assertEquals(109, ParticleOptions.Registry.names().size());
        assertEquals(13, ParticleOptions.Registry.id("minecraft:dust"));
        assertEquals(34, ParticleOptions.Registry.id("minecraft:sculk_soul"));
        assertEquals(27, ParticleOptions.Registry.id("minecraft:sonic_boom"));
        assertEquals(45, ParticleOptions.Registry.id("minecraft:vibration"));
        assertEquals(108, ParticleOptions.Registry.id("minecraft:trial_omen"));
    }

    @Test
    void simpleParticle_layout() {
        ByteBuf b = encode(new LevelParticles(false, 1.5, 2.5, -3.5, 0.2F, 0.0F, 0.2F, 0.1F, 2, ParticleOptions.simple("sculk_soul")));
        assertFalse(b.readBoolean());
        assertEquals(1.5, b.readDouble());
        assertEquals(2.5, b.readDouble());
        assertEquals(-3.5, b.readDouble());
        assertEquals(0.2F, b.readFloat());
        assertEquals(0.0F, b.readFloat());
        assertEquals(0.2F, b.readFloat());
        assertEquals(0.1F, b.readFloat());
        assertEquals(2, b.readInt());
        assertEquals(34, Types.readVarInt(b));
        assertEquals(0, b.readableBytes());
    }

    @Test
    void dust_floatsAndScaleClamp() {
        ByteBuf b = encode(new LevelParticles(true, 0, 0, 0, 0, 0, 0, 0, 1, new ParticleOptions.Dust(1.0F, 0.5F, 0.25F, 9.0F)));
        b.skipBytes(1 + 24 + 16 + 4);
        assertEquals(13, Types.readVarInt(b));
        assertEquals(1.0F, b.readFloat());
        assertEquals(0.5F, b.readFloat());
        assertEquals(0.25F, b.readFloat());
        assertEquals(4.0F, b.readFloat());   // 크기는 4 로 죈다
        assertEquals(0, b.readableBytes());
    }

    @Test
    void transition_blockShriekSculkColor() {
        ByteBuf b = Unpooled.buffer();
        new ParticleOptions.DustTransition(1, 0, 0, 0, 0, 1, 0.001F).write(b);
        assertEquals(14, Types.readVarInt(b));
        for (float f : new float[] {1, 0, 0, 0, 0, 1}) assertEquals(f, b.readFloat());
        assertEquals(0.01F, b.readFloat());
        assertEquals(0, b.readableBytes());

        b = Unpooled.buffer();
        new ParticleOptions.Block("minecraft:block", 1234).write(b);
        assertEquals(1, Types.readVarInt(b));
        assertEquals(1234, Types.readVarInt(b));

        b = Unpooled.buffer();
        new ParticleOptions.Shriek(5).write(b);
        assertEquals(99, Types.readVarInt(b));
        assertEquals(5, Types.readVarInt(b));

        b = Unpooled.buffer();
        new ParticleOptions.SculkCharge(1.5F).write(b);
        assertEquals(35, Types.readVarInt(b));
        assertEquals(1.5F, b.readFloat());

        b = Unpooled.buffer();
        new ParticleOptions.Color(0x80FF0000).write(b);
        assertEquals(20, Types.readVarInt(b));
        assertEquals(0x80FF0000, b.readInt());
    }

    @Test
    void vibration_blockAndEntitySources() {
        ByteBuf b = Unpooled.buffer();
        ParticleOptions.Vibration.toBlock(10, 64, -5, 20).write(b);
        assertEquals(45, Types.readVarInt(b));
        assertEquals(0, Types.readVarInt(b));
        assertArrayEquals(new int[] {10, 64, -5}, Types.readBlockPos(b));
        assertEquals(20, Types.readVarInt(b));
        assertEquals(0, b.readableBytes());

        b = Unpooled.buffer();
        ParticleOptions.Vibration.toEntity(77, 0.5F, 9).write(b);
        assertEquals(45, Types.readVarInt(b));
        assertEquals(1, Types.readVarInt(b));
        assertEquals(77, Types.readVarInt(b));
        assertEquals(0.5F, b.readFloat());
        assertEquals(9, Types.readVarInt(b));
        assertEquals(0, b.readableBytes());
    }

    @Test
    void distanceFilter_32_and_forced512() {
        Player p = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        p.setPosRaw(0.5, 64, 0.5);   // 칸 가운데 (0.5, 64.5, 0.5)
        assertTrue(ChunkLevel.particleInRange(p, false, 31.0, 64.5, 0.5));
        assertFalse(ChunkLevel.particleInRange(p, false, 32.5, 64.5, 0.5));
        assertFalse(ChunkLevel.particleInRange(p, false, 100.5, 64.5, 0.5));
        assertTrue(ChunkLevel.particleInRange(p, true, 100.5, 64.5, 0.5));
        assertFalse(ChunkLevel.particleInRange(p, true, 513.0, 64.5, 0.5));
    }
}
