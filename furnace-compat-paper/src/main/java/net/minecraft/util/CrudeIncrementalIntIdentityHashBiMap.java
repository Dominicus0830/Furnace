package net.minecraft.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import net.minecraft.core.IdMap;

/**
 * 정체성(==) 기준 값 ↔ 작은 번호 표(Mojang 이름 shim, N2b-1) — {@code HashMapPalette} 의 바탕. 바닐라 1.21.1 포팅: 번호는 비어 있는 가장 작은 번호부터
 * 차례로 매기고(같은 순서로 넣으면 같은 번호), 열린 주소 표의 크기·늘리기 규칙(채움률 0.8)도 같다.
 */
public class CrudeIncrementalIntIdentityHashBiMap<K> implements IdMap<K> {

    private static final float LOADFACTOR = 0.8F;

    private K[] keys;
    private int[] values;
    private K[] byId;
    private int nextId;
    private int size;

    @SuppressWarnings("unchecked")
    private CrudeIncrementalIntIdentityHashBiMap(int capacity) {
        this.keys = (K[]) new Object[capacity];
        this.values = new int[capacity];
        this.byId = (K[]) new Object[capacity];
    }

    private CrudeIncrementalIntIdentityHashBiMap(K[] keys, int[] values, K[] byId, int nextId, int size) {
        this.keys = keys;
        this.values = values;
        this.byId = byId;
        this.nextId = nextId;
        this.size = size;
    }

    public static <A> CrudeIncrementalIntIdentityHashBiMap<A> create(int expected) {
        return new CrudeIncrementalIntIdentityHashBiMap<>((int) ((float) expected / LOADFACTOR));
    }

    @Override
    public int getId(K value) { return getValue(indexOf(value, hash(value))); }

    @Override
    public K byId(int id) { return id >= 0 && id < byId.length ? byId[id] : null; }

    private int getValue(int slot) { return slot == -1 ? -1 : values[slot]; }

    public boolean contains(K value) { return getId(value) != -1; }

    public boolean contains(int id) { return byId(id) != null; }

    public int add(K value) {
        int id = nextId();
        addMapping(value, id);
        return id;
    }

    private int nextId() {
        while (nextId < byId.length && byId[nextId] != null) nextId++;
        return nextId;
    }

    private void grow(int capacity) {
        K[] oldKeys = keys;
        int[] oldValues = values;
        CrudeIncrementalIntIdentityHashBiMap<K> bigger = new CrudeIncrementalIntIdentityHashBiMap<>(capacity);
        for (int i = 0; i < oldKeys.length; i++) if (oldKeys[i] != null) bigger.addMapping(oldKeys[i], oldValues[i]);
        keys = bigger.keys;
        values = bigger.values;
        byId = bigger.byId;
        nextId = bigger.nextId;
        size = bigger.size;
    }

    public void addMapping(K value, int id) {
        int need = Math.max(id, size + 1);
        if ((float) need >= (float) keys.length * LOADFACTOR) {
            int capacity = keys.length << 1;
            while (capacity < id) capacity <<= 1;
            grow(capacity);
        }
        int slot = findEmpty(hash(value));
        keys[slot] = value;
        values[slot] = id;
        byId[id] = value;
        size++;
        if (id == nextId) nextId++;
    }

    private int hash(K value) { return (murmurHash3Mixer(System.identityHashCode(value)) & Integer.MAX_VALUE) % keys.length; }

    /** 바닐라 Mth.murmurHash3Mixer. */
    static int murmurHash3Mixer(int v) {
        v ^= v >>> 16;
        v *= -2048144789;
        v ^= v >>> 13;
        v *= -1028477387;
        v ^= v >>> 16;
        return v;
    }

    private int indexOf(K value, int start) {
        for (int i = start; i < keys.length; i++) {
            if (keys[i] == value) return i;
            if (keys[i] == null) return -1;
        }
        for (int i = 0; i < start; i++) {
            if (keys[i] == value) return i;
            if (keys[i] == null) return -1;
        }
        return -1;
    }

    private int findEmpty(int start) {
        for (int i = start; i < keys.length; i++) if (keys[i] == null) return i;
        for (int i = 0; i < start; i++) if (keys[i] == null) return i;
        throw new RuntimeException("Overflowed :(");
    }

    /** 번호 순으로 비어 있지 않은 값. */
    @Override
    public Iterator<K> iterator() {
        K[] snapshot = byId;
        return new Iterator<>() {
            int i = skip(0);

            private int skip(int from) {
                while (from < snapshot.length && snapshot[from] == null) from++;
                return from;
            }

            @Override
            public boolean hasNext() { return i < snapshot.length; }

            @Override
            public K next() {
                if (i >= snapshot.length) throw new NoSuchElementException();
                K v = snapshot[i];
                i = skip(i + 1);
                return v;
            }
        };
    }

    public void clear() {
        Arrays.fill(keys, null);
        Arrays.fill(byId, null);
        nextId = 0;
        size = 0;
    }

    @Override
    public int size() { return size; }

    public CrudeIncrementalIntIdentityHashBiMap<K> copy() {
        return new CrudeIncrementalIntIdentityHashBiMap<>(keys.clone(), values.clone(), byId.clone(), nextId, size);
    }
}
