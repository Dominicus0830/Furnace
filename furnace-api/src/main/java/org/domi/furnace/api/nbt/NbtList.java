// NBT 리스트(바닐라 ListTag 규칙: 모든 원소가 같은 종류). 빈 리스트는 첫 원소로 종류가 정해진다.
// 바꿀 수 있고 스레드 안전하지 않다. 게터는 바닐라처럼 범위 밖이거나 형이 맞지 않으면 기본값.
package org.domi.furnace.api.nbt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class NbtList {

    private final List<Object> items = new ArrayList<>();
    private NbtType elementType = NbtType.END;

    public NbtList() {}

    /** 원소들로 만든다(모두 같은 종류여야 한다). */
    public static NbtList of(Object... values) {
        NbtList l = new NbtList();
        for (Object v : values) l.add(v);
        return l;
    }

    /** 문자열 리스트(엔티티 Tags 등). */
    public static NbtList ofStrings(Iterable<String> values) {
        NbtList l = new NbtList();
        for (String s : values) l.add(s);
        return l;
    }

    /** 원소 종류. 비어 있으면 END. */
    public NbtType elementType() { return items.isEmpty() ? NbtType.END : elementType; }

    public int size() { return items.size(); }

    public boolean isEmpty() { return items.isEmpty(); }

    /** 바꿀 수 없는 보기(원소 자체 — 묶음·리스트·배열 — 는 살아 있다). */
    public List<Object> values() { return Collections.unmodifiableList(items); }

    private void check(Object v) {
        NbtType t = NbtType.of(v);
        if (v == this) throw new IllegalArgumentException("자기 자신을 넣을 수 없다");
        if (!items.isEmpty() && t != elementType) {
            throw new IllegalArgumentException("리스트 원소 종류가 다르다: " + elementType + " 리스트에 " + t + " 를 넣을 수 없다");
        }
    }

    /** 끝에 넣는다. 종류가 다르면 IllegalArgumentException. 자기 자신을 돌려준다. */
    public NbtList add(Object v) {
        check(v);
        if (items.isEmpty()) elementType = NbtType.of(v);
        items.add(v);
        return this;
    }

    public NbtList add(int index, Object v) {
        check(v);
        if (items.isEmpty()) elementType = NbtType.of(v);
        items.add(index, v);
        return this;
    }

    /** 바꾼 이전 값. 하나뿐인 원소를 바꿀 때만 종류가 달라질 수 있다. */
    public Object set(int index, Object v) {
        Objects.checkIndex(index, items.size());
        if (items.size() == 1) {
            NbtType.of(v);
            elementType = NbtType.of(v);
            return items.set(0, v);
        }
        check(v);
        return items.set(index, v);
    }

    public Object remove(int index) { return items.remove(index); }

    public void clear() { items.clear(); }

    /** 날 값(범위 밖이면 IndexOutOfBoundsException). */
    public Object get(int index) { return items.get(index); }

    private Object at(int i) { return i >= 0 && i < items.size() ? items.get(i) : null; }

    public int getInt(int i) { return at(i) instanceof Integer n ? n : 0; }

    public short getShort(int i) { return at(i) instanceof Short n ? n : 0; }

    public float getFloat(int i) { return at(i) instanceof Float n ? n : 0f; }

    public double getDouble(int i) { return at(i) instanceof Double n ? n : 0.0; }

    /** 문자열 원소. 문자열 리스트가 아니면 원소의 SNBT(바닐라 ListTag.getString). 범위 밖이면 "". */
    public String getString(int i) {
        Object o = at(i);
        if (o == null) return "";
        return o instanceof String s ? s : Snbt.write(o);
    }

    /** 살아 있는 묶음 원소, 아니면 새 빈 묶음. */
    public NbtCompound getCompound(int i) { return at(i) instanceof NbtCompound c ? c : new NbtCompound(); }

    public NbtList getList(int i) { return at(i) instanceof NbtList l ? l : new NbtList(); }

    public int[] getIntArray(int i) { return at(i) instanceof int[] a ? a : new int[0]; }

    /** 깊은 복사. */
    public NbtList copy() {
        NbtList l = new NbtList();
        l.elementType = elementType;
        for (Object o : items) l.items.add(NbtType.copy(o));
        return l;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NbtList l) || l.items.size() != items.size()) return false;
        for (int i = 0; i < items.size(); i++) if (!NbtType.equal(items.get(i), l.items.get(i))) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int h = 1;
        for (Object o : items) h = 31 * h + NbtType.hash(o);
        return h;
    }

    @Override
    public String toString() { return Snbt.write(this); }
}
