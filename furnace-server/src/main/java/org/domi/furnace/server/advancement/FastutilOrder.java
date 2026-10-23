// fastutil ObjectOpenHashSet 의 순회 순서(바닐라 AdvancementRequirements.names()) — 진행 맵(HashMap)에 넣는 순서가
// 같아야 같은 칸에 부딪힌 조건 이름의 순서까지 바닐라와 같다. 열린 주소법·선형 탐사·높은 칸부터 순회.
package org.domi.furnace.server.advancement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class FastutilOrder {

    private static final float F = 0.75f;
    private String[] key;
    private int mask, n, size, maxFill;

    FastutilOrder() {
        n = arraySize(16);
        mask = n - 1;
        maxFill = maxFill(n);
        key = new String[n];
    }

    private static int mix(int x) {
        int h = x * 0x9E3779B9;
        return h ^ (h >>> 16);
    }

    private static int nextPowerOfTwo(long x) { return (int) Math.max(1, Long.highestOneBit(x - 1) << 1); }

    private static int arraySize(int expected) { return (int) Math.max(2, nextPowerOfTwo((long) Math.ceil(expected / F))); }

    private static int maxFill(int n) { return Math.min((int) Math.ceil(n * F), n - 1); }

    /** AbstractCollection.addAll 앞의 tryCapacity(size + c.size()). */
    void addAll(Collection<String> c) {
        int needed = (int) Math.min(1 << 30, Math.max(2, nextPowerOfTwo((long) Math.ceil((size + c.size()) / F))));
        if (needed > n) rehash(needed);
        for (String s : c) add(s);
    }

    void add(String k) {
        int pos = mix(k.hashCode()) & mask;
        while (key[pos] != null) {
            if (key[pos].equals(k)) return;
            pos = pos + 1 & mask;
        }
        key[pos] = k;
        if (size++ >= maxFill) rehash(arraySize(size + 1));
    }

    private void rehash(int newN) {
        String[] newKey = new String[newN];
        int newMask = newN - 1;
        int i = n;
        for (int j = size; j-- != 0; ) {
            while (key[--i] == null) { }
            int pos = mix(key[i].hashCode()) & newMask;
            while (newKey[pos] != null) pos = pos + 1 & newMask;
            newKey[pos] = key[i];
        }
        n = newN;
        mask = newMask;
        maxFill = maxFill(n);
        key = newKey;
    }

    /** 순회 순서: 높은 칸에서 낮은 칸으로. */
    List<String> order() {
        List<String> out = new ArrayList<>(size);
        for (int pos = n; --pos >= 0; ) if (key[pos] != null) out.add(key[pos]);
        return out;
    }

    static List<String> names(List<List<String>> requirements) {
        FastutilOrder s = new FastutilOrder();
        for (List<String> g : requirements) s.addAll(g);
        return s.order();
    }
}
