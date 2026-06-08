// 청크 직렬화 헤더(좌표) + 섹션/라이트 카운트 스모크 검증
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSnapshot;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ChunkSerializerTest {

    @Test
    void serialize_writesCoordsAndIsNonEmpty() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ChunkColumn col = new SuperflatWorldSource(reg, 6).generate(3, -5);
        ByteBuf buf = Unpooled.buffer();
        ChunkSerializer.serialize(buf, ChunkSnapshot.of(3, -5, col), reg.directBits(), 7); // biomeDirectBits=7(128 biome 가정)

        assertEquals(3, buf.readInt());   // chunkX
        assertEquals(-5, buf.readInt());  // chunkZ
        assertEquals(0x0a, buf.readByte());// heightmap NBT 루트 compound
        assertTrue(buf.readableBytes() > 100, "section/light 데이터 있어야");
        buf.release();
    }

    @Test
    void setCenterChunk_writesVarIntCoords() {
        SetCenterChunk p = new SetCenterChunk(3, -5);
        assertEquals(0x54, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(3, Types.readVarInt(buf));
        assertEquals(-5, Types.readVarInt(buf));
        buf.release();
    }
}
