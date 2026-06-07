// ChunkColumn을 level_chunk_with_light body로 직렬화(섹션 팰릿 + heightmap NBT + 풀 skylight)
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtWriter;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.Palette;

public final class ChunkSerializer {

    private static final int LIGHT_SECTIONS = ChunkColumn.SECTION_COUNT + 2; // 26

    private ChunkSerializer() {}

    public static void serialize(ByteBuf buf, int chunkX, int chunkZ, ChunkColumn col,
                                 int blockDirectBits, int biomeDirectBits) {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);

        // heightmap NBT
        NbtWriter nbt = new NbtWriter(buf);
        nbt.beginRootCompound();
        nbt.writeLongArray("MOTION_BLOCKING", col.motionBlockingHeightmap());
        nbt.endCompound();

        // 섹션 데이터(별도 버퍼 후 길이 prefix)
        ByteBuf data = Unpooled.buffer();
        for (ChunkSection s : col.sections()) {
            data.writeShort(s.nonAirCount());
            Palette.write(data, s.blocks(), blockDirectBits, 4, 8);  // 블록
            Palette.write(data, s.biomes(), biomeDirectBits, 1, 3);  // 바이옴
        }
        Types.writeVarInt(buf, data.readableBytes());
        buf.writeBytes(data);
        data.release();

        // 블록 엔티티 0개
        Types.writeVarInt(buf, 0);

        // --- 라이트 ---
        long allSectionsMask = (1L << LIGHT_SECTIONS) - 1; // 26비트
        writeBitSet(buf, new long[]{allSectionsMask});  // skyLightMask
        writeBitSet(buf, new long[0]);                  // blockLightMask
        writeBitSet(buf, new long[0]);                  // emptySkyLightMask
        writeBitSet(buf, new long[]{allSectionsMask});  // emptyBlockLightMask

        Types.writeVarInt(buf, LIGHT_SECTIONS); // skyLight 배열 수
        byte[] full = new byte[2048];
        java.util.Arrays.fill(full, (byte) 0xFF);
        for (int i = 0; i < LIGHT_SECTIONS; i++) {
            Types.writeVarInt(buf, 2048);
            buf.writeBytes(full);
        }
        Types.writeVarInt(buf, 0); // blockLight 배열 수
    }

    private static void writeBitSet(ByteBuf buf, long[] longs) {
        Types.writeVarInt(buf, longs.length);
        for (long l : longs) buf.writeLong(l);
    }
}
