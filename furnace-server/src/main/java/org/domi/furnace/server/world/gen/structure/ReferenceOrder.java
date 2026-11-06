// 바닐라 청크의 구조물 참조 집합(fastutil LongOpenHashSet) 순회 순서를 재현 — 같은 구조물의 시작점 여러 개가 한 청크에 걸치면
// StructureManager.startsForStructure 가 이 순서로 placeInChunk 하며 장식 난수를 이어 쓰기 때문에 순서가 결과를 바꾼다.
package org.domi.furnace.server.world.gen.structure;

import java.util.ArrayList;
import java.util.List;

public final class ReferenceOrder {

    private ReferenceOrder() {}

    /** ChunkPos.asLong(x, z). */
    public static long chunkKey(int x, int z) { return (x & 0xFFFFFFFFL) | (z & 0xFFFFFFFFL) << 32; }

    /**
     * 같은 구조물의 시작점들을 바닐라 순서로 정렬해 돌려준다. 참조는 ChunkGenerator.createReferences 처럼
     * 시작 청크 x(바깥)·z(안쪽) 오름차순으로 집합에 넣고, LongOpenHashSet 반복자 순서(null 키 먼저, 그다음 배열 뒤→앞)로 꺼낸다.
     */
    public static List<StructureStart> order(List<StructureStart> starts) {
        if (starts.size() < 2) return starts;
        List<StructureStart> byInsert = new ArrayList<>(starts);
        byInsert.sort((a, b) -> a.chunkX() != b.chunkX() ? Integer.compare(a.chunkX(), b.chunkX()) : Integer.compare(a.chunkZ(), b.chunkZ()));
        LongSet set = new LongSet();
        for (StructureStart s : byInsert) set.add(chunkKey(s.chunkX(), s.chunkZ()));
        List<StructureStart> out = new ArrayList<>(starts.size());
        for (long k : set.iterationOrder()) {
            for (StructureStart s : byInsert) if (chunkKey(s.chunkX(), s.chunkZ()) == k) { out.add(s); break; }
        }
        return out;
    }

    /** fastutil LongOpenHashSet(기본 용량 16, 적재율 0.75) 의 삽입·재해시·반복 순서만 흉내 낸 최소 구현. */
    static final class LongSet {
        private static final float F = 0.75f;
        private long[] key;
        private int n, mask, maxFill, size;
        private boolean containsNull;

        LongSet() {
            n = arraySize(16, F);
            mask = n - 1;
            maxFill = maxFill(n, F);
            key = new long[n + 1];
        }

        void add(long k) {
            if (k == 0) {
                if (containsNull) return;
                containsNull = true;
            } else {
                int pos = (int) mix(k) & mask;
                long cur = key[pos];
                if (cur != 0) {
                    if (cur == k) return;
                    while ((cur = key[pos = pos + 1 & mask]) != 0) if (cur == k) return;
                }
                key[pos] = k;
            }
            if (size++ >= maxFill) rehash(arraySize(size + 1, F));
        }

        private void rehash(int newN) {
            long[] newKey = new long[newN + 1];
            int newMask = newN - 1;
            int i = n;
            for (int j = size - (containsNull ? 1 : 0); j-- != 0; ) {
                while (key[--i] == 0) { }
                int pos = (int) mix(key[i]) & newMask;
                if (newKey[pos] != 0) while (newKey[pos = pos + 1 & newMask] != 0) { }
                newKey[pos] = key[i];
            }
            n = newN;
            mask = newMask;
            maxFill = maxFill(n, F);
            key = newKey;
        }

        List<Long> iterationOrder() {
            List<Long> out = new ArrayList<>(size);
            if (containsNull) out.add(0L);
            for (int pos = n - 1; pos >= 0; pos--) if (key[pos] != 0) out.add(key[pos]);
            return out;
        }

        /** it.unimi.dsi.fastutil.HashCommon.mix(long). */
        static long mix(long x) {
            long h = x * 0x9E3779B97F4A7C15L;
            h ^= h >>> 32;
            return h ^ h >>> 16;
        }

        static int arraySize(int expected, float f) {
            long s = Math.max(2, nextPowerOfTwo((long) Math.ceil(expected / f)));
            return (int) s;
        }

        static int maxFill(int n, float f) { return Math.min((int) Math.ceil(n * f), n - 1); }

        static long nextPowerOfTwo(long x) { return 1L << (64 - Long.numberOfLeadingZeros(x - 1)); }
    }
}
