package net.minecraft.nbt;

/** double 태그(바닐라처럼 +0 은 공유 객체). */
public final class DoubleTag extends NumericTag {

    private final double data;

    DoubleTag(double data) { this.data = data; }

    public static final DoubleTag ZERO = new DoubleTag(0.0);

    public static DoubleTag valueOf(double v) { return v == 0.0 ? ZERO : new DoubleTag(v); }

    @Override
    public byte getId() { return TAG_DOUBLE; }

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
