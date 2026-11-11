package org.domi.furnace.compat.nms;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/** NMS shim 도우미: 바닐라 Guava·fastutil 모음과 같은 관측을 주는 바꿀 수 없는 모음. */
public final class NmsCollections {

    private NmsCollections() {}

    /** Guava ImmutableSet 처럼 넣은 순서를 지키는 집합(equals·hashCode 는 Set 규칙이라 같다). */
    public static <T> Collection<T> orderedSet(List<T> values) { return Collections.unmodifiableSet(new LinkedHashSet<>(values)); }

    /**
     * fastutil Reference2ObjectArrayMap 처럼 키를 <b>동일 객체</b>로만 찾고 넣은 순서를 지키는 바꿀 수 없는 표. 바닐라 StateHolder 의 값 표가 이것이라,
     * 따로 만든 같은 이름 속성으로는 값을 찾지 못한다.
     */
    public static <K, V> Map<K, V> identityOrderedMap(Map<K, V> source) {
        Object[] keys = new Object[source.size()];
        Object[] vals = new Object[source.size()];
        int i = 0;
        for (var e : source.entrySet()) {
            keys[i] = e.getKey();
            vals[i++] = e.getValue();
        }
        return new IdentityArrayMap<>(keys, vals);
    }

    private static final class IdentityArrayMap<K, V> extends AbstractMap<K, V> {
        private final Object[] keys;
        private final Object[] vals;

        IdentityArrayMap(Object[] keys, Object[] vals) {
            this.keys = keys;
            this.vals = vals;
        }

        private int find(Object k) {
            for (int i = 0; i < keys.length; i++) if (keys[i] == k) return i;
            return -1;
        }

        @SuppressWarnings("unchecked")
        @Override
        public V get(Object k) {
            int i = find(k);
            return i < 0 ? null : (V) vals[i];
        }

        @Override
        public boolean containsKey(Object k) { return find(k) >= 0; }

        @Override
        public int size() { return keys.length; }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public int size() { return keys.length; }

                @Override
                public Iterator<Entry<K, V>> iterator() {
                    return new Iterator<>() {
                        int i;

                        @Override
                        public boolean hasNext() { return i < keys.length; }

                        @SuppressWarnings("unchecked")
                        @Override
                        public Entry<K, V> next() {
                            if (i >= keys.length) throw new NoSuchElementException();
                            Entry<K, V> e = new SimpleImmutableEntry<>((K) keys[i], (V) vals[i]);
                            i++;
                            return e;
                        }
                    };
                }
            };
        }
    }
}
