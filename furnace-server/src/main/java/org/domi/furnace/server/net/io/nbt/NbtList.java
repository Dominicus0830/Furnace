// 타입드 NBT 리스트(모든 요소 동일 태그 타입).
package org.domi.furnace.server.net.io.nbt;

import java.util.ArrayList;
import java.util.List;

public final class NbtList {

    private final byte elementType;
    private final List<Object> items = new ArrayList<>();

    public NbtList(byte elementType) {
        this.elementType = elementType;
    }

    public void add(Object v) { items.add(v); }
    public Object get(int i) { return items.get(i); }
    public int size() { return items.size(); }
    public byte elementType() { return elementType; }
    public List<Object> items() { return items; }

    static Object copyValue(Object v) {
        if (v instanceof NbtCompound c) return c.copy();
        if (v instanceof NbtList l) return l.copy();
        if (v instanceof byte[] a) return a.clone();
        if (v instanceof int[] a) return a.clone();
        if (v instanceof long[] a) return a.clone();
        return v;
    }

    public NbtList copy() {
        NbtList l = new NbtList(elementType);
        for (Object o : items) l.add(copyValue(o));
        return l;
    }
}
