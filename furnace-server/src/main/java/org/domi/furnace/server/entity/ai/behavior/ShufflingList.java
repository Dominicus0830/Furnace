// 바닐라 ShufflingList: 가중치 목록. shuffle() 은 항목마다 -pow(난수, 1/가중치) 키를 뽑아 오름차순으로 정렬한다(가중치가 클수록 앞에 올 확률이 높다).
package org.domi.furnace.server.entity.ai.behavior;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public final class ShufflingList<U> implements Iterable<U> {
    private static final org.domi.furnace.server.block.RandomSource RANDOM = org.domi.furnace.server.block.RandomSource.create();

    private static final class Entry<T> {
        final T data;
        final int weight;
        double randWeight;

        Entry(T data, int weight) {
            this.data = data;
            this.weight = weight;
        }
    }

    private final List<Entry<U>> entries = new ArrayList<>();

    public ShufflingList<U> add(U data, int weight) {
        entries.add(new Entry<>(data, weight));
        return this;
    }

    public ShufflingList<U> shuffle() {
        for (Entry<U> e : entries) e.randWeight = -Math.pow(RANDOM.nextFloat(), 1.0F / e.weight);
        entries.sort(Comparator.comparingDouble(e -> e.randWeight));
        return this;
    }

    public List<U> asList() {
        List<U> out = new ArrayList<>(entries.size());
        for (Entry<U> e : entries) out.add(e.data);
        return out;
    }

    @Override
    public Iterator<U> iterator() { return asList().iterator(); }
}
