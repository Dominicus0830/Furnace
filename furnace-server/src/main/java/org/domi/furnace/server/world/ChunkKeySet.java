// region 이 가진 "있는 청크" 키 집합(X1): ConcurrentHashMap 키 집합과 같은 동작에, 매 틱 순회용 long[] 사본을 붙인 것.
// 청크 로드·퇴출은 드물고 순회는 매 틱이라, 바뀐 뒤 첫 순회에서만 사본을 다시 만든다(순서는 그때의 해시 집합 순회 순서 그대로).
package org.domi.furnace.server.world;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ChunkKeySet extends AbstractSet<Long> {

    private final Set<Long> set = ConcurrentHashMap.newKeySet();
    private volatile long[] snapshot = new long[0];
    private volatile boolean stale = true;

    @Override public boolean add(Long k) {
        boolean r = set.add(k);
        if (r) stale = true;
        return r;
    }

    @Override public boolean remove(Object k) {
        boolean r = set.remove(k);
        if (r) stale = true;
        return r;
    }

    @Override public boolean contains(Object k) { return set.contains(k); }

    @Override public int size() { return set.size(); }

    @Override public boolean isEmpty() { return set.isEmpty(); }

    @Override public void clear() {
        set.clear();
        stale = true;
    }

    @Override
    public Iterator<Long> iterator() {
        Iterator<Long> it = set.iterator();
        return new Iterator<>() {
            @Override public boolean hasNext() { return it.hasNext(); }

            @Override public Long next() { return it.next(); }

            @Override public void remove() {
                it.remove();
                stale = true;
            }
        };
    }

    /** 지금 키들의 사본(읽기 전용으로 쓸 것). 순서는 집합 순회 순서. 순회하는 동안 집합이 바뀌어도 이 사본은 그대로다. */
    long[] snapshot() {
        if (stale) {
            synchronized (this) {
                if (stale) {
                    stale = false;   // 이 뒤의 변경은 다시 stale 로 만든다
                    long[] a = new long[set.size() + 8];
                    int n = 0;
                    for (long k : set) {
                        if (n == a.length) a = java.util.Arrays.copyOf(a, n * 2);
                        a[n++] = k;
                    }
                    snapshot = java.util.Arrays.copyOf(a, n);
                }
            }
        }
        return snapshot;
    }
}
