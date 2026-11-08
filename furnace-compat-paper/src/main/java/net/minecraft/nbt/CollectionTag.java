package net.minecraft.nbt;

import java.util.AbstractList;

/** 리스트·배열 태그의 공통 모양(바닐라처럼 java.util.List). 종류가 맞지 않는 원소는 setTag/addTag 가 false 를 돌려준다. */
public abstract class CollectionTag<T extends Tag> extends AbstractList<T> implements Tag {

    protected CollectionTag() {}

    @Override
    public abstract T set(int i, T tag);

    @Override
    public abstract void add(int i, T tag);

    @Override
    public abstract T remove(int i);

    public abstract boolean setTag(int i, Tag tag);

    public abstract boolean addTag(int i, Tag tag);

    public abstract byte getElementType();

    @Override
    public String toString() { return org.domi.furnace.api.nbt.Snbt.write(NbtBridge.toFurnace(this)); }
}
