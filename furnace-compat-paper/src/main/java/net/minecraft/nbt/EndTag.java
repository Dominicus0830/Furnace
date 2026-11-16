package net.minecraft.nbt;

/** 끝 태그(빈 리스트의 원소 종류). */
public final class EndTag implements Tag {

    /** 바닐라 {@code EndTag.TYPE}: 이 클래스 안의 익명 종류(이름·표시 이름·isValue 는 바닐라 1.21.1 jar 와 대조). */
    public static final TagType<EndTag> TYPE = new TagType<>() {
        @Override public String getName() { return "END"; }
        @Override public String getPrettyName() { return "TAG_End"; }
        @Override public boolean isValue() { return true; }
    };

    public static final EndTag INSTANCE = new EndTag();

    private EndTag() {}

    @Override
    public byte getId() { return TAG_END; }

    @Override
    public EndTag copy() { return this; }

    @Override
    public String toString() { return "END"; }
}
