package net.minecraft.nbt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 리스트 태그(가변, Mojang 이름 shim). 원소 종류는 처음 넣은 것으로 정해지고(빈 리스트는 0), 다른 종류를 넣으면 바닐라와 같은 문구로
 * UnsupportedOperationException(set·add) 또는 false(setTag·addTag). 비면 종류가 0 으로 돌아간다. 번호로 읽는 getXxx(i) 는 범위 밖이거나
 * 종류가 다르면 기본값(바닐라와 같다).
 */
public final class ListTag extends CollectionTag<Tag> {

    private final List<Tag> list;
    private byte type;

    public ListTag() { this(new ArrayList<>(), TAG_END); }

    ListTag(List<Tag> list, byte type) {
        this.list = list;
        this.type = type;
    }

    @Override
    public byte getId() { return TAG_LIST; }

    @Override
    public byte getElementType() { return type; }

    @Override
    public boolean isEmpty() { return list.isEmpty(); }

    @Override
    public int size() { return list.size(); }

    @Override
    public Tag get(int i) { return list.get(i); }

    @Override
    public Tag set(int i, Tag tag) {
        Tag old = get(i);
        if (!setTag(i, tag)) throw new UnsupportedOperationException(String.format(Locale.ROOT, "Trying to set tag of type %d in list of %d", tag.getId(), type));
        return old;
    }

    @Override
    public void add(int i, Tag tag) {
        if (!addTag(i, tag)) throw new UnsupportedOperationException(String.format(Locale.ROOT, "Trying to add tag of type %d to list of %d", tag.getId(), type));
    }

    @Override
    public boolean setTag(int i, Tag tag) {
        if (updateType(tag)) {
            list.set(i, tag);
            return true;
        }
        return false;
    }

    @Override
    public boolean addTag(int i, Tag tag) {
        if (updateType(tag)) {
            list.add(i, tag);
            return true;
        }
        return false;
    }

    private boolean updateType(Tag tag) {
        if (tag.getId() == TAG_END) return false;
        if (type == TAG_END) {
            type = tag.getId();
            return true;
        }
        return type == tag.getId();
    }

    @Override
    public Tag remove(int i) {
        Tag t = list.remove(i);
        if (list.isEmpty()) type = TAG_END;
        return t;
    }

    @Override
    public void clear() {
        list.clear();
        type = TAG_END;
    }

    private Tag at(int i, byte want) {
        if (i < 0 || i >= list.size()) return null;
        Tag t = list.get(i);
        return t.getId() == want ? t : null;
    }

    public CompoundTag getCompound(int i) { return at(i, TAG_COMPOUND) instanceof CompoundTag c ? c : new CompoundTag(); }

    public ListTag getList(int i) { return at(i, TAG_LIST) instanceof ListTag l ? l : new ListTag(); }

    public short getShort(int i) { return at(i, TAG_SHORT) instanceof ShortTag t ? t.getAsShort() : 0; }

    public int getInt(int i) { return at(i, TAG_INT) instanceof IntTag t ? t.getAsInt() : 0; }

    public int[] getIntArray(int i) { return at(i, TAG_INT_ARRAY) instanceof IntArrayTag t ? t.getAsIntArray() : new int[0]; }

    public long[] getLongArray(int i) { return at(i, TAG_LONG_ARRAY) instanceof LongArrayTag t ? t.getAsLongArray() : new long[0]; }

    public double getDouble(int i) { return at(i, TAG_DOUBLE) instanceof DoubleTag t ? t.getAsDouble() : 0.0; }

    public float getFloat(int i) { return at(i, TAG_FLOAT) instanceof FloatTag t ? t.getAsFloat() : 0.0F; }

    /** 문자열이면 그 값, 다른 종류면 그 SNBT, 범위 밖이면 "". */
    public String getString(int i) {
        if (i < 0 || i >= list.size()) return "";
        Tag t = list.get(i);
        return t.getId() == TAG_STRING ? t.getAsString() : t.toString();
    }

    @Override
    public ListTag copy() {
        List<Tag> c = new ArrayList<>(list.size());
        for (Tag t : list) c.add(t.copy());
        return new ListTag(c, type);
    }

    @Override
    public boolean equals(Object o) { return this == o || o instanceof ListTag l && list.equals(l.list); }

    @Override
    public int hashCode() { return list.hashCode(); }
}
