package net.minecraft.nbt;

/** 수 태그의 공통 모양. 바닐라처럼 다른 종류로 읽으면 자바 기본형 변환(좁히기는 잘림)이다. */
public abstract class NumericTag implements Tag {

    protected NumericTag() {}

    public abstract long getAsLong();

    public abstract int getAsInt();

    public abstract short getAsShort();

    public abstract byte getAsByte();

    public abstract double getAsDouble();

    public abstract float getAsFloat();

    public abstract Number getAsNumber();

    @Override
    public String toString() { return org.domi.furnace.api.nbt.Snbt.write(NbtBridge.toFurnace(this)); }
}
