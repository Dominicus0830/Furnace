// ChunkColumn을 level_chunk_with_light body로 직렬화(섹션 팰릿 + heightmap NBT + 풀 skylight)
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtWriter;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSnapshot;
import org.domi.furnace.server.world.Palette;

public final class ChunkSerializer {

    private static final int LIGHT_SECTIONS = ChunkColumn.SECTION_COUNT + 2; // 26
    private static final byte[] FULL_SKYLIGHT = new byte[2048];
    static {
        java.util.Arrays.fill(FULL_SKYLIGHT, (byte) 0xFF);
    }

    private ChunkSerializer() {}

    public static void serialize(ByteBuf buf, ChunkSnapshot snap,
                                 int blockDirectBits, int biomeDirectBits) {
        buf.writeInt(snap.chunkX());
        buf.writeInt(snap.chunkZ());

        // heightmap NBT
        NbtWriter nbt = new NbtWriter(buf);
        nbt.beginRootCompound();
        nbt.writeLongArray("MOTION_BLOCKING", snap.heightmap());
        nbt.writeLongArray("WORLD_SURFACE", snap.worldSurfaceHeightmap());
        nbt.endCompound();

        // 섹션 데이터(별도 버퍼 후 길이 prefix)
        ByteBuf data = Unpooled.buffer();
        for (ChunkSnapshot.Section s : snap.sections()) {
            data.writeShort(s.nonAirCount());
            Palette.write(data, s.blocks(), blockDirectBits, 4, 8);  // 블록
            Palette.write(data, s.biomes(), biomeDirectBits, 1, 3);  // 바이옴
        }
        Types.writeVarInt(buf, data.readableBytes());
        buf.writeBytes(data);
        data.release();

        // 블록 엔티티: [byte packedXZ][short y][VarInt typeId][NBT tag-or-TAG_END]
        Types.writeVarInt(buf, snap.blockEntities().size());
        for (ChunkSnapshot.BlockEntitySnapshot be : snap.blockEntities()) {
            buf.writeByte(((be.localX() & 0xF) << 4) | (be.localZ() & 0xF));
            buf.writeShort(be.y());
            Types.writeVarInt(buf, be.typeId());
            NbtCompound tag = be.tag();
            if (tag == null) {
                buf.writeByte(0x00); // TAG_END = 빈 tag(vanilla null 관례)
            } else {
                new NbtWriter(buf).writeCompound(tag);
            }
        }

        // --- 라이트 ---
        long allSectionsMask = (1L << LIGHT_SECTIONS) - 1; // 26비트
        writeBitSet(buf, new long[]{allSectionsMask});  // skyLightMask
        writeBitSet(buf, new long[0]);                  // blockLightMask
        writeBitSet(buf, new long[0]);                  // emptySkyLightMask
        writeBitSet(buf, new long[]{allSectionsMask});  // emptyBlockLightMask

        Types.writeVarInt(buf, LIGHT_SECTIONS); // skyLight 배열 수
        for (int i = 0; i < LIGHT_SECTIONS; i++) {
            Types.writeVarInt(buf, 2048);
            buf.writeBytes(FULL_SKYLIGHT);
        }
        Types.writeVarInt(buf, 0); // blockLight 배열 수
    }

    private static void writeBitSet(ByteBuf buf, long[] longs) {
        Types.writeVarInt(buf, longs.length);
        for (long l : longs) buf.writeLong(l);
    }
}
