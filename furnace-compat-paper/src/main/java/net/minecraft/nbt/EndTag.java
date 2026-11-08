package net.minecraft.nbt;

/** 끝 태그(빈 리스트의 원소 종류). */
public final class EndTag implements Tag {

    public static final EndTag INSTANCE = new EndTag();

    private EndTag() {}

    @Override
    public byte getId() { return TAG_END; }

    @Override
    public EndTag copy() { return this; }

    @Override
    public String toString() { return "END"; }
}
