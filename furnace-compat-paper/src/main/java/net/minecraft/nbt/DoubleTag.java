package net.minecraft.nbt;

/** double 태그(바닐라처럼 +0 은 공유 객체). */
public final class DoubleTag extends NumericTag {

    /** 바닐라 {@code DoubleTag.TYPE}: 이 클래스 안의 익명 종류(이름·표시 이름·isValue 는 바닐라 1.21.1 jar 와 대조). */
    public static final TagType<DoubleTag> TYPE = new TagType<>() {
        @Override public String getName() { return "DOUBLE"; }
        @Override public String getPrettyName() { return "TAG_Double"; }
        @Override public boolean isValue() { return true; }
    };

    private final double data;

    DoubleTag(double data) { this.data = data; }

    public static final DoubleTag ZERO = new DoubleTag(0.0);

    public static DoubleTag valueOf(double v) { return v == 0.0 ? ZERO : new DoubleTag(v); }

    @Override
    public byte getId() { return TAG_DOUBLE; }

    /** 바닐라: 메모리 크기 어림(NbtAccounter 가 쓰는 값과 같은 셈). */
    @Override
    public int sizeInBytes() {
        return 16;
    }

    @Override
    public DoubleTag copy() { return this; }

    @Override
    /** 바닐라: 바닥 내림(Math.floor). */
    public long getAsLong() { return (long) Math.floor(data); }

    @Override
    /** 바닐라: 바닥 내림(Mth.floor). */
    public int getAsInt() { return (int) Math.floor(data); }

    @Override
    /** 바닐라: 바닥 내림 뒤 하위 16비트. */
    public short getAsShort() { return (short) (((int) Math.floor(data)) & 0xFFFF); }

    @Override
    /** 바닐라: 바닥 내림 뒤 하위 8비트. */
    public byte getAsByte() { return (byte) (((int) Math.floor(data)) & 0xFF); }

    @Override
    public double getAsDouble() { return data; }

    @Override
    public float getAsFloat() { return (float) data; }

    @Override
    public Number getAsNumber() { return data; }

    @Override
    public boolean equals(Object o) { return this == o || o instanceof DoubleTag t && data == t.data; }

    @Override
    public int hashCode() { return Long.hashCode(Double.doubleToLongBits(data)); }
}
