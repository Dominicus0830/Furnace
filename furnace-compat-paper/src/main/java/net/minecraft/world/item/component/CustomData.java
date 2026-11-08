package net.minecraft.world.item.component;

import net.minecraft.nbt.CompoundTag;

/**
 * 아이템의 사용자 데이터(Mojang 이름 shim, 불변). 바닐라처럼 안에 든 묶음은 사본으로만 꺼낸다(copyTag).
 */
public final class CustomData {

    public static final CustomData EMPTY = new CustomData(new CompoundTag());

    private final CompoundTag tag;

    private CustomData(CompoundTag tag) { this.tag = tag; }

    /** 바닐라 of: 사본을 든다. */
    public static CustomData of(CompoundTag tag) { return new CustomData(tag.copy()); }

    public CompoundTag copyTag() { return tag.copy(); }

    public boolean isEmpty() { return tag.isEmpty(); }

    public boolean contains(String key) { return tag.contains(key); }


    @Override
    public boolean equals(Object o) { return o == this || o instanceof CustomData d && tag.equals(d.tag); }

    @Override
    public int hashCode() { return tag.hashCode(); }

    @Override
    public String toString() { return tag.toString(); }
}
