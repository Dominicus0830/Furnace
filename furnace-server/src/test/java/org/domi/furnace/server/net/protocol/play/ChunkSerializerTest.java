// 청크 직렬화 헤더(좌표) + 섹션/라이트 카운트 스모크 검증
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChestBlockEntity;
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
    void serialize_noBlockEntities_writesZeroCount() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ChunkColumn col = new SuperflatWorldSource(reg, 6).generate(0, 0);
        ByteBuf buf = Unpooled.buffer();
        ChunkSerializer.serialize(buf, ChunkSnapshot.of(0, 0, col), reg.directBits(), 7);

        skipToBlockEntities(buf);
        assertEquals(0, Types.readVarInt(buf));
        buf.release();
    }

    @Test
    void serialize_blockEntity_writesPackedXZYTypeIdAndEmptyTag() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ChunkColumn col = new SuperflatWorldSource(reg, 6).generate(0, 0);
        ItemStack[] items = new ItemStack[27];
        java.util.Arrays.fill(items, ItemStack.EMPTY);
        col.putChest(new ChestBlockEntity(5, 10, 9, items)); // localX=5, localZ=9, y=10 (빈 체스트)
        ByteBuf buf = Unpooled.buffer();
        ChunkSerializer.serialize(buf, ChunkSnapshot.of(0, 0, col), reg.directBits(), 7);

        skipToBlockEntities(buf);
        assertEquals(1, Types.readVarInt(buf)); // count
        int packedXZ = buf.readByte() & 0xFF;
        assertEquals((5 << 4) | 9, packedXZ);
        assertEquals(10, buf.readShort());
        assertEquals(ChunkSnapshot.CHEST_TYPE_ID, Types.readVarInt(buf));
        assertEquals(0x00, buf.readByte()); // 빈 체스트 = TAG_END 1바이트
        buf.release();
    }

    /** heightmap NBT(고정 MOTION_BLOCKING 1개 태그) + 섹션 블롭을 건너뛰어 블록엔티티 배열 시작 위치로 이동. */
    private static void skipToBlockEntities(ByteBuf buf) {
        buf.readInt(); // chunkX
        buf.readInt(); // chunkZ
        buf.readByte(); // TAG_COMPOUND (root)
        buf.readByte(); // TAG_LONG_ARRAY
        int nameLen = buf.readShort();
        buf.skipBytes(nameLen); // "MOTION_BLOCKING"
        int arrLen = buf.readInt();
        buf.skipBytes(arrLen * 8);
        buf.readByte(); // TAG_END(root 종료)
        int sectionDataLen = Types.readVarInt(buf);
        buf.skipBytes(sectionDataLen);
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
