// paletted container(단일/간접/직접) 와이어 인코딩. 블록·바이옴 공용.
package org.domi.furnace.server.world;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        // 고유값 수집(삽입 순서 = 팰릿 인덱스)
        Map<Integer, Integer> indexOf = new HashMap<>();
        List<Integer> palette = new ArrayList<>();
        for (int v : values) {
            if (!indexOf.containsKey(v)) {
                indexOf.put(v, palette.size());
                palette.add(v);
            }
        }

        if (palette.size() == 1) { // 단일값
            buf.writeByte(0);
            Types.writeVarInt(buf, palette.get(0));
            Types.writeVarInt(buf, 0); // long 수 0
            return;
        }

        int neededBits = 32 - Integer.numberOfLeadingZeros(palette.size() - 1);
        if (neededBits <= maxIndirectBits) { // 간접
            int bits = Math.max(neededBits, minIndirectBits);
            buf.writeByte(bits);
            Types.writeVarInt(buf, palette.size());
            for (int entry : palette) Types.writeVarInt(buf, entry);
            BitStorage store = new BitStorage(bits, values.length);
            for (int i = 0; i < values.length; i++) store.set(i, indexOf.get(values[i]));
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
