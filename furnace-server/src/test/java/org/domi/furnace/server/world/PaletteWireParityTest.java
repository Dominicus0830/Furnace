// N-07: 배열 기반 Palette.write 가 예전 HashMap 구현과 와이어 바이트가 같은지 무작위 입력으로 대조.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class PaletteWireParityTest {

    /** 수정 전 구현 그대로(기준). */
    static void legacyWrite(ByteBuf buf, int[] values, int directBits, int minIndirectBits, int maxIndirectBits) {
        Map<Integer, Integer> indexOf = new HashMap<>();
        List<Integer> palette = new ArrayList<>();
        for (int v : values) {
            if (!indexOf.containsKey(v)) {
                indexOf.put(v, palette.size());
                palette.add(v);
            }
        }
        if (palette.size() == 1) {
            buf.writeByte(0);
            Types.writeVarInt(buf, palette.get(0));
            Types.writeVarInt(buf, 0);
            return;
        }
        int neededBits = 32 - Integer.numberOfLeadingZeros(palette.size() - 1);
        if (neededBits <= maxIndirectBits) {
            int bits = Math.max(neededBits, minIndirectBits);
            buf.writeByte(bits);
            Types.writeVarInt(buf, palette.size());
            for (int entry : palette) Types.writeVarInt(buf, entry);
            BitStorage store = new BitStorage(bits, values.length);
            for (int i = 0; i < values.length; i++) store.set(i, indexOf.get(values[i]));
            writeLongs(buf, store.data());
        } else {
            buf.writeByte(directBits);
            BitStorage store = new BitStorage(directBits, values.length);
            for (int i = 0; i < values.length; i++) store.set(i, values[i]);
            writeLongs(buf, store.data());
        }
    }

    /** 스레드 로컬 재사용 전의 배열 구현(1차 N-07 커밋) — 재사용 효과 측정·대조용. */
    static void arrayNoReuseWrite(ByteBuf buf, int[] values, int directBits,
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

    static int[] randomValues(Random r, int len, int distinct, int maxId, boolean runs) {
        int[] pool = new int[distinct];
        for (int i = 0; i < distinct; i++) pool[i] = r.nextInt(maxId);
        int[] v = new int[len];
        int cur = pool[0];
        for (int i = 0; i < len; i++) {
            if (!runs || r.nextInt(8) == 0) cur = pool[r.nextInt(distinct)];
            v[i] = cur;
        }
        return v;
    }

    static byte[] bytes(int[] v, int direct, int min, int max, boolean legacy) {
        ByteBuf b = Unpooled.buffer();
        if (legacy) legacyWrite(b, v, direct, min, max); else Palette.write(b, v, direct, min, max);
        byte[] out = ByteBufUtil.getBytes(b);
        b.release();
        return out;
    }

    @Test
    void randomSectionsMatchLegacyBytes() {
        Random r = new Random(0xC107);
        int[] distincts = {1, 2, 3, 4, 7, 8, 9, 16, 17, 100, 255, 256, 257, 300, 1000, 4096};
        for (int iter = 0; iter < 3000; iter++) {
            int d = distincts[r.nextInt(distincts.length)];
            boolean runs = r.nextBoolean();
            // 블록(4096, 직접 15비트, 4..8)
            int[] blocks = randomValues(r, 4096, d, 27_000, runs);
            assertArrayEquals(bytes(blocks, 15, 4, 8, true), bytes(blocks, 15, 4, 8, false), "블록 iter=" + iter);
            ByteBuf nb = Unpooled.buffer();
            arrayNoReuseWrite(nb, blocks, 15, 4, 8);
            assertArrayEquals(bytes(blocks, 15, 4, 8, true), ByteBufUtil.getBytes(nb), "블록(재사용 전 배열) iter=" + iter);
            nb.release();
            // 바이옴(64, 직접 7비트, 1..3)
            int[] biomes = randomValues(r, 64, Math.min(d, 64), 64, runs);
            assertArrayEquals(bytes(biomes, 7, 1, 3, true), bytes(biomes, 7, 1, 3, false), "바이옴 iter=" + iter);
        }
        // 경계: 빈 배열, 음수·극값
        assertArrayEquals(bytes(new int[0], 15, 4, 8, true), bytes(new int[0], 15, 4, 8, false));
        int[] odd = {Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE, -1, 0};
        assertArrayEquals(bytes(odd, 15, 4, 8, true), bytes(odd, 15, 4, 8, false));
    }

    static volatile long sink;

    // -Dfurnace.bench=true 일 때만. 전(legacy)/후(현재) 교대 5라운드, 각 워밍업 200회 + 측정 200회 x 64섹션.
    @Test
    void bench() {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        Random r = new Random(7);
        for (int d : new int[]{1, 8, 60, 200, 1000}) {
            int[][] sets = new int[64][];
            for (int i = 0; i < sets.length; i++) sets[i] = randomValues(r, 4096, d, 27_000, true);
            ByteBuf b = Unpooled.buffer(1 << 16);
            for (int round = 0; round < 5; round++) {
                for (int legacy = 0; legacy < 3; legacy++) {
                    boolean old = legacy == 0;
                    boolean noReuse = legacy == 1;
                    Runnable run = () -> {
                        for (int[] s : sets) {
                            b.clear();
                            if (old) legacyWrite(b, s, 15, 4, 8);
                            else if (noReuse) arrayNoReuseWrite(b, s, 15, 4, 8);
                            else Palette.write(b, s, 15, 4, 8);
                            sink += b.writerIndex();
                        }
                    };
                    for (int w = 0; w < 200; w++) run.run();
                    long t = System.nanoTime();
                    for (int w = 0; w < 200; w++) run.run();
                    double us = (System.nanoTime() - t) / 1000.0 / (200 * 64);
                    System.out.printf("[BENCH] N-07 distinct=%d round=%d %s %.2f us/섹션%n", d, round, old ? "전" : noReuse ? "후(재사용 없음)" : "후(스레드 로컬)", us);
                }
            }
            b.release();
        }
    }
}
