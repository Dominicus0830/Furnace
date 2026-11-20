package net.minecraft.nbt;

/** long 태그. */
public final class LongTag extends NumericTag {

    /** 바닐라 {@code LongTag.TYPE}: 이 클래스 안의 익명 종류(이름·표시 이름·isValue 는 바닐라 1.21.1 jar 와 대조). */
    public static final TagType<LongTag> TYPE = new TagType<>() {
        @Override public String getName() { return "LONG"; }
        @Override public String getPrettyName() { return "TAG_Long"; }
        @Override public boolean isValue() { return true; }
    };

    private final long data;

    LongTag(long data) { this.data = data; }

    /** 바닐라처럼 -128..1023 은 미리 만든 객체를 돌려준다. */
    private static final LongTag[] CACHE = new LongTag[1024 + 128];

    static {
        for (int i = 0; i < CACHE.length; i++) CACHE[i] = new LongTag((long) (i - 128));
    }

    public static LongTag valueOf(long v) { return v >= -128 && v <= 1023 ? CACHE[(int) v + 128] : new LongTag(v); }

    @Override
    public byte getId() { return TAG_LONG; }

    /** 바닐라: 메모리 크기 어림(NbtAccounter 가 쓰는 값과 같은 셈). */
    @Override
    public int sizeInBytes() {
        return 16;
    }

    @Override
    public LongTag copy() { return this; }

    @Override
    public long getAsLong() { return (long) data; }

    @Override
    public int getAsInt() { return (int) data; }

    @Override
    public short getAsShort() { return (short) data; }

    @Override
    public byte getAsByte() { return (byte) data; }

    @Override
    public double getAsDouble() { return data; }

    @Override
    public float getAsFloat() { return (float) data; }

    @Override
    public Number getAsNumber() { return data; }

    @Override
    public boolean equals(Object o) { return this == o || o instanceof LongTag t && data == t.data; }

    @Override
    public int hashCode() { return (int) (data ^ data >>> 32); }
}
