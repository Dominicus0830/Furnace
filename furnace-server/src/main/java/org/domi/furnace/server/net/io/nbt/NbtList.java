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
}
