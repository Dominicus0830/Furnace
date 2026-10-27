// paletted container(단일/간접/직접) 와이어 인코딩. 블록·바이옴 공용.
package org.domi.furnace.server.world;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;

public final class Palette {

    private Palette() {}

    /**
     * values를 paletted container로 buf에 기록.
     * @param directBits      직접 팰릿 비트수
     * @param minIndirectBits 간접 최소 비트수(블록 4, 바이옴 1)
     * @param maxIndirectBits 간접 최대 비트수(블록 8, 바이옴 3)
     */
    public static void write(ByteBuf buf, int[] values, int directBits,
                             int minIndirectBits, int maxIndirectBits) {
        // 고유값 수집(삽입 순서 = 팰릿 인덱스). 간접 한도(1<<maxIndirectBits)를 넘으면 직접이라 거기서 멈춘다.
        int limit = 1 << maxIndirectBits;
        int cap = Integer.highestOneBit(Math.max(limit, 1) * 2) << 1;   // 적재율 <= 1/2
        int mask = cap - 1;
        int[] keys = new int[cap];
        int[] slot = new int[cap];   // 팰릿 인덱스 + 1(0 = 빈 칸)
        int[] palette = new int[limit + 1];
        int[] idx = new int[values.length];
        int size = 0;
        int lastV = 0, lastI = -1;
        for (int i = 0; i < values.length; i++) {
            int v = values[i];
            if (lastI >= 0 && v == lastV) { idx[i] = lastI; continue; }
            int h = (v * 0x9E3779B9) >>> 16 & mask;
            int found = -1;
            while (true) {
                int sl = slot[h];
                if (sl == 0) {
                    if (size == limit) { size = limit + 1; break; }   // 한도 초과 → 직접
                    keys[h] = v;
                    slot[h] = size + 1;
                    palette[size] = v;
                    found = size++;
                    break;
                }
                if (keys[h] == v) { found = sl - 1; break; }
                h = (h + 1) & mask;
            }
            if (size > limit) break;
            idx[i] = found;
            lastV = v;
            lastI = found;
        }

        if (size == 1) { // 단일값
            buf.writeByte(0);
            Types.writeVarInt(buf, palette[0]);
            Types.writeVarInt(buf, 0); // long 수 0
            return;
        }

        if (size != 0 && size <= limit) { // 간접
            int neededBits = 32 - Integer.numberOfLeadingZeros(size - 1);
            int bits = Math.max(neededBits, minIndirectBits);
            buf.writeByte(bits);
            Types.writeVarInt(buf, size);
            for (int p = 0; p < size; p++) Types.writeVarInt(buf, palette[p]);
            BitStorage store = new BitStorage(bits, values.length);
            for (int i = 0; i < values.length; i++) store.set(i, idx[i]);
            writeLongs(buf, store.data());
        } else { // 직접
            buf.writeByte(directBits);
            BitStorage store = new BitStorage(directBits, values.length);
            for (int i = 0; i < values.length; i++) store.set(i, values[i]);
            writeLongs(buf, store.data());
        }
    }

    private static void writeLongs(ByteBuf buf, long[] data) {
        Types.writeVarInt(buf, data.length);
        for (long l : data) buf.writeLong(l);
    }
}
