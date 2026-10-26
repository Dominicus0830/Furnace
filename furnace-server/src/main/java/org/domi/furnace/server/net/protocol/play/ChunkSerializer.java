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

    private static final byte[] FULL_SKYLIGHT = new byte[2048];
    static {
        java.util.Arrays.fill(FULL_SKYLIGHT, (byte) 0xFF);
    }

    private ChunkSerializer() {}

    public static void serialize(ByteBuf buf, ChunkSnapshot snap,
                                 int blockDirectBits, int biomeDirectBits) {
        serialize(buf, snap, blockDirectBits, biomeDirectBits, ChunkColumn.MIN_Y, true);
    }

    /**
     * 차원별: 빛 섹션은 (minY>>4)-1 부터 섹션 수 + 2 개. 하늘빛이 없는 차원(네더·엔드)은 바닐라처럼 하늘빛 마스크를 모두 비운다
     * (하늘빛 층 리스너가 없어 채움·빈 표시 둘 다 없다).
     */
    public static void serialize(ByteBuf buf, ChunkSnapshot snap, int blockDirectBits, int biomeDirectBits,
                                 int minY, boolean hasSkyLight) {
        final int LIGHT_SECTIONS = snap.sections().length + 2;
        final int lightBase = (minY >> 4) - 1 - org.domi.furnace.server.light.ChunkLight.MIN_SECTION;
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

        // --- 라이트 --- (ClientboundLightUpdatePacketData: 모두 0 이면 empty 마스크, 아니면 데이터)
        byte[][] sky = snap.light() == null ? null : snap.light().sky();
        byte[][] blk = snap.light() == null ? null : snap.light().block();
        long skyMask = 0, blockMask = 0, emptySky = 0, emptyBlock = 0;
        for (int i = 0; i < LIGHT_SECTIONS; i++) {
            if (sky == null) {
                if (hasSkyLight) skyMask |= 1L << i;     // 빛이 없는 청크: 예전 동작(하늘빛 전부 15)
                continue;
            }
            if (hasSkyLight) {
                if (isZero(sky[lightBase + i])) emptySky |= 1L << i; else skyMask |= 1L << i;
            }
            if (isZero(blk[lightBase + i])) emptyBlock |= 1L << i; else blockMask |= 1L << i;
        }
        if (sky == null) emptyBlock = (1L << LIGHT_SECTIONS) - 1;
        writeBitSet(buf, new long[]{skyMask});
        writeBitSet(buf, new long[]{blockMask});
        writeBitSet(buf, new long[]{emptySky});
        writeBitSet(buf, new long[]{emptyBlock});
        writeLayers(buf, sky, skyMask, lightBase, LIGHT_SECTIONS);
        writeLayers(buf, blk, blockMask, lightBase, LIGHT_SECTIONS);
    }

    private static boolean isZero(byte[] a) {
        for (byte b : a) if (b != 0) return false;
        return true;
    }

    private static void writeLayers(ByteBuf buf, byte[][] layers, long mask, int base, int count) {
        Types.writeVarInt(buf, Long.bitCount(mask));
        for (int i = 0; i < count; i++) {
            if ((mask & (1L << i)) == 0) continue;
            Types.writeVarInt(buf, 2048);
            buf.writeBytes(layers == null ? FULL_SKYLIGHT : layers[base + i]);
        }
    }

    private static void writeBitSet(ByteBuf buf, long[] longs) {
        Types.writeVarInt(buf, longs.length);
        for (long l : longs) buf.writeLong(l);
    }
}
