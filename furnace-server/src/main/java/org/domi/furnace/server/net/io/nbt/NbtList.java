// 타입드 NBT 리스트(모든 요소 동일 태그 타입).
package org.domi.furnace.server.net.io.nbt;

import java.util.ArrayList;
import java.util.List;

public final class NbtList {

    private byte elementType;
    private final List<Object> items = new ArrayList<>();

    public NbtList(byte elementType) {
        this.elementType = elementType;
    }

    public void add(Object v) { items.add(v); }
    public Object get(int i) { return items.get(i); }
    public int size() { return items.size(); }
    public byte elementType() { return elementType; }
    public List<Object> items() { return items; }

    // ---- 바닐라 ListTag 의 종류 규칙(빈 리스트는 첫 원소로 종류가 정해지고, 비면 TAG_END 로 돌아간다)

    /** ListTag.updateType: 이 종류의 원소를 받을 수 있는가(빈 리스트면 종류를 정한다). */
    private boolean updateType(byte type) {
        if (type == NbtIo.TAG_END) return false;
        if (elementType == NbtIo.TAG_END || items.isEmpty()) {
            elementType = type;
            return true;
        }
        return elementType == type;
    }

    /** ListTag.addTag(i, tag): 종류가 맞으면 끼워 넣고 true. */
    public boolean addTag(int i, Object v, byte type) {
        if (!updateType(type)) return false;
        items.add(i, v);
        return true;
    }

    /** ListTag.setTag(i, tag): 종류가 맞으면 바꾸고 true. */
    public boolean setTag(int i, Object v, byte type) {
        if (!updateType(type)) return false;
        items.set(i, v);
        return true;
    }

    /** ListTag.remove(i): 비면 종류를 TAG_END 로. */
    public Object remove(int i) {
        Object o = items.remove(i);
        if (items.isEmpty()) elementType = NbtIo.TAG_END;
        return o;
    }

    /** ListTag.clear. */
    public void clear() {
        items.clear();
        elementType = NbtIo.TAG_END;
    }

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
