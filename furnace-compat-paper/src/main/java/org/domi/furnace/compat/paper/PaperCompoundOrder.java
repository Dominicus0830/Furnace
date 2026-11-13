// Paper CompoundTag(패치 Reduce-memory-footprint-of-CompoundTag: fastutil Object2ObjectOpenHashMap(8, 0.8f))의 키 순회 순서만 흉내 낸 최소 구현.
// server 의 world/gen/structure/ReferenceOrder.LongSet 과 같은 방식(삽입·재해시·배열 뒤→앞 순회).
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.List;

final class PaperCompoundOrder {

    private static final float F = 0.8f;

    private PaperCompoundOrder() {}

    /** keys 를 이 순서로 put 했을 때 CompoundTag.write 가 쓰는 순서. */
    static List<String> order(Iterable<String> keys) {
        int n = arraySize(8), mask = n - 1, maxFill = maxFill(n), size = 0;
        String[] key = new String[n];
        outer:
        for (String k : keys) {
            int pos = mix(k.hashCode()) & mask;
            String cur;
            while ((cur = key[pos]) != null) {
                if (cur.equals(k)) continue outer;
                pos = pos + 1 & mask;
            }
            key[pos] = k;
            if (size++ >= maxFill) {
                int newN = arraySize(size + 1), newMask = newN - 1;
                String[] nk = new String[newN];
                int i = n;
                for (int j = size; j-- != 0; ) {
                    while (key[--i] == null) { }
                    int p = mix(key[i].hashCode()) & newMask;
                    while (nk[p] != null) p = p + 1 & newMask;
                    nk[p] = key[i];
                }
                n = newN;
                mask = newMask;
                maxFill = maxFill(n);
                key = nk;
            }
        }
        List<String> out = new ArrayList<>(size);
        for (int pos = n; pos-- != 0; ) if (key[pos] != null) out.add(key[pos]);
        return out;
    }

    private static int mix(int x) {
        int h = x * 0x9E3779B9;
        return h ^ (h >>> 16);
    }

    private static int arraySize(int expected) {
        long s = Math.max(2, Long.highestOneBit((long) Math.ceil(expected / F) - 1) << 1);
        return (int) s;
    }

    private static int maxFill(int n) { return Math.min((int) Math.ceil(n * F), n - 1); }
}
