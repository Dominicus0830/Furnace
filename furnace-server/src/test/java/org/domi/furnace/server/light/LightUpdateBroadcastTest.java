// 청크 전송 뒤 블록 변경으로 빛이 바뀌면 바뀐 섹션만 담은 빛 갱신(0x2A)을 보낸다.
package org.domi.furnace.server.light;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.LightUpdate;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LightUpdateBroadcastTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final class Level extends BlockPlacementTest.TestLevel {
        final List<LightUpdate> light = new ArrayList<>();
        @Override protected void broadcastLightToChunk(int x, int z, Packet p) { light.add((LightUpdate) p); }
    }

    @Test
    void blockChange_sendsChangedSectionsOnly() {
        Level level = new Level();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.lightNewChunk(x, z);
        level.broadcastChanges();
        level.light.clear();

        int glow = BlockRegistry.load("blocks/1.21.1.txt").defaultStateId("minecraft:glowstone");
        level.setBlock(new Pos(8, -40, 8), glow, 3);
        level.runLightUpdates();
        level.broadcastChanges();

        LightUpdate u = level.light.stream().filter(p -> p.chunkX() == 0 && p.chunkZ() == 0).findFirst().orElseThrow();
        int idx = (-40 >> 4) - (level.minBuildHeight() >> 4) + 1;   // 빛 섹션 첨자
        assertNotEquals(0, u.blockMask() & 1L << idx);
        assertEquals(0, u.blockMask() & 1L << (idx + 3));   // 멀리 떨어진 섹션은 안 담는다
        assertEquals(Long.bitCount(u.blockMask()), u.blockData().length);

        // 한 번 보낸 변경은 다시 안 간다
        level.light.clear();
        level.broadcastChanges();
        assertTrue(level.light.isEmpty());

        ByteBuf buf = Unpooled.buffer();
        u.write(buf);
        assertEquals(0, Types.readVarInt(buf));
        assertEquals(0, Types.readVarInt(buf));
        buf.release();
    }

    @Test
    void initialChunkLight_isNotMarked() {
        Level level = new Level();
        level.lightNewChunk(2, 2);   // 둘레에 켜진 청크가 없다 — 바뀐 것은 새 청크뿐
        level.broadcastChanges();
        assertTrue(level.light.isEmpty(), "아직 안 보낸 새 청크의 첫 빛은 빛 갱신을 만들지 않는다");
    }

    @Test
    void emptyBitSet_hasZeroLongs() {
        LightUpdate u = new LightUpdate(1, -2, 0, 0, 0, 0, new byte[0][], new byte[0][]);
        ByteBuf buf = Unpooled.buffer();
        u.write(buf);
        // VarInt 1, VarInt -2(5바이트), BitSet 4개 각 0, 배열 2개 각 0
        assertEquals(1 + 5 + 4 + 2, buf.readableBytes());
        assertEquals(0x2A, u.id());
        buf.release();
    }
}
