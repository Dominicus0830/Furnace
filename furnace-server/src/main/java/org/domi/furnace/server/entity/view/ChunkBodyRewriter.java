// level_chunk_with_light 의 본문(바이트)과 chunks_biomes 항목에 한 사람의 가상 블록·바이옴 층을 덧입힌다.
// 본문 모양(ChunkSerializer): 청크 x,z(int×2) · 높이맵 NBT(긴 배열만) · VarInt 섹션 데이터 길이 · 섹션마다 [비공기 수 short][블록 컨테이너][바이옴 컨테이너] · 나머지(블록 엔티티·빛).
// 덮어쓸 칸이 없는 섹션은 바이트를 그대로 옮기고, 있는 섹션만 컨테이너를 풀어 고친 뒤 다시 인코딩한다(비공기 수도 다시 센다). 블록 엔티티·빛은 그대로 둔다.
// 해석에 실패하면 null — 호출하는 쪽이 원래 패킷을 보낸다.
package org.domi.furnace.server.entity.view;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Map;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.world.BitStorage;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.Palette;

public final class ChunkBodyRewriter {

    private ChunkBodyRewriter() {}

    /** 덮어쓴 본문, 덮을 것이 없거나 해석할 수 없으면 null. */
    public static byte[] rewrite(byte[] body, BlockLayer.Dim dim, BlockLayer.Chunk layer) {
        if (layer == null || layer.isEmpty()) return null;
        ByteBuf in = Unpooled.wrappedBuffer(body);
        ByteBuf out = null;
        try {
            in.skipBytes(8);   // 청크 x, z
            if (in.readByte() != 10) return null;          // 높이맵 NBT 루트
            while (true) {
                byte t = in.readByte();
                if (t == 0) break;
                if (t != 12) return null;                  // 긴 배열만 지원(ChunkSerializer 가 쓰는 것)
                in.skipBytes(in.readUnsignedShort());
                in.skipBytes(in.readInt() * 8);
            }
            int lenPos = in.readerIndex();
            int dataLen = Types.readVarInt(in);
            int dataStart = in.readerIndex();
            int dataEnd = dataStart + dataLen;
            int sections = dim.sections();
            int[][] blockCells = bucket(layer.blocks(), sections, 12);
            int[][] biomeCells = bucket(layer.biomes(), sections, 6);

            ByteBuf data = Unpooled.buffer(dataLen + 256);
            try {
                int index = 0;
                while (in.readerIndex() < dataEnd) {
                    int start = in.readerIndex();
                    short nonAir = in.readShort();
                    int[] blocks = readContainer(in, 4096, 8);
                    int[] biomes = readContainer(in, 64, 3);
                    boolean hasB = index < sections && blockCells[index] != null, hasBi = index < sections && biomeCells[index] != null;
                    if (!hasB && !hasBi) {
                        data.writeBytes(body, start, in.readerIndex() - start);
                    } else {
                        if (hasB) {
                            for (int k : blockCells[index]) blocks[k & 0xFFF] = layer.blocks().get(k).value();
                            nonAir = 0;
                            for (int v : blocks) if (!ChunkSection.isAir(v)) nonAir++;
                        }
                        if (hasBi) {
                            for (int k : biomeCells[index]) biomes[k & 63] = layer.biomes().get(k).value();
                        }
                        data.writeShort(nonAir);
                        Palette.write(data, blocks, dim.blockBits(), 4, 8);
                        Palette.write(data, biomes, dim.biomeBits(), 1, 3);
                    }
                    index++;
                }
                if (in.readerIndex() != dataEnd) return null;
                out = Unpooled.buffer(body.length + 256);
                out.writeBytes(body, 0, lenPos);
                Types.writeVarInt(out, data.readableBytes());
                out.writeBytes(data);
                out.writeBytes(body, dataEnd, body.length - dataEnd);
            } finally {
                data.release();
            }
            byte[] result = new byte[out.readableBytes()];
            out.readBytes(result);
            return result;
        } catch (RuntimeException e) {
            return null;
        } finally {
            in.release();
            if (out != null) out.release();
        }
    }

    /** 섹션별로 덮어쓴 칸 키 목록(없는 섹션은 null). 블록 키는 key>>12 가, 바이옴 칸 키는 key>>6 이 섹션 번호다. */
    private static int[][] bucket(Map<Integer, BlockLayer.Entry> map, int sections, int sectionShift) {
        int[][] out = new int[sections][];
        int[] fill = new int[sections];
        for (int key : map.keySet()) {
            int s = key >> sectionShift;
            if (s < 0 || s >= sections) continue;
            fill[s]++;
        }
        for (int s = 0; s < sections; s++) if (fill[s] > 0) out[s] = new int[fill[s]];
        java.util.Arrays.fill(fill, 0);
        for (int key : map.keySet()) {
            int s = key >> sectionShift;
            if (s < 0 || s >= sections) continue;
            out[s][fill[s]++] = key;
        }
        return out;
    }

    /** paletted container 를 값 배열로 푼다(maxIndirect 이하 비트면 간접, 더 크면 직접). */
    static int[] readContainer(ByteBuf in, int size, int maxIndirect) {
        int bits = in.readUnsignedByte();
        int[] values = new int[size];
        if (bits == 0) {
            int v = Types.readVarInt(in);
            java.util.Arrays.fill(values, v);
            in.skipBytes(Types.readVarInt(in) * 8);
            return values;
        }
        int[] palette = null;
        if (bits <= maxIndirect) {
            palette = new int[Types.readVarInt(in)];
            for (int i = 0; i < palette.length; i++) palette[i] = Types.readVarInt(in);
        }
        long[] longs = new long[Types.readVarInt(in)];
        for (int i = 0; i < longs.length; i++) longs[i] = in.readLong();
        BitStorage bs = BitStorage.wrap(bits, size, longs);
        for (int i = 0; i < size; i++) {
            int v = bs.get(i);
            values[i] = palette == null ? v : palette[v];
        }
        return values;
    }
}
