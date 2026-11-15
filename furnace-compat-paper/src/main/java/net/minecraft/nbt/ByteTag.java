package net.minecraft.nbt;

/** 바이트 태그(바닐라처럼 256 값 모두 공유 객체). */
public final class ByteTag extends NumericTag {

    /** 바닐라 {@code ByteTag.TYPE}: 이 클래스 안의 익명 종류(이름·표시 이름·isValue 는 바닐라 1.21.1 jar 와 대조). */
    public static final TagType<ByteTag> TYPE = new TagType<>() {
        @Override public String getName() { return "BYTE"; }
        @Override public String getPrettyName() { return "TAG_Byte"; }
        @Override public boolean isValue() { return true; }
    };

    private final byte data;

    ByteTag(byte data) { this.data = data; }

    private static final ByteTag[] CACHE = new ByteTag[256];

    static {
        for (int i = 0; i < 256; i++) CACHE[i] = new ByteTag((byte) (i - 128));
    }

    public static final ByteTag ZERO = valueOf((byte) 0);
    public static final ByteTag ONE = valueOf((byte) 1);

    public static ByteTag valueOf(byte v) { return CACHE[128 + v]; }

    public static ByteTag valueOf(boolean v) { return v ? ONE : ZERO; }

    @Override
    public byte getId() { return TAG_BYTE; }

    @Override
    public ByteTag copy() { return this; }

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
    public boolean equals(Object o) { return this == o || o instanceof ByteTag t && data == t.data; }

    @Override
    public int hashCode() { return data; }
}
