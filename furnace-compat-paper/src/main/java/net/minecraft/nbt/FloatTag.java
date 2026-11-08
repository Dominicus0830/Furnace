package net.minecraft.nbt;

/** float 태그(바닐라처럼 +0 은 공유 객체). */
public final class FloatTag extends NumericTag {

    private final float data;

    FloatTag(float data) { this.data = data; }

    public static final FloatTag ZERO = new FloatTag(0.0F);

    public static FloatTag valueOf(float v) { return v == 0.0F ? ZERO : new FloatTag(v); }

    @Override
    public byte getId() { return TAG_FLOAT; }

    @Override
    public FloatTag copy() { return this; }

    @Override
    /** 바닐라: 그냥 자르기(f2l) — DoubleTag 와 다르다(바이트코드 확인). */
    public long getAsLong() { return (long) data; }

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
    public boolean equals(Object o) { return this == o || o instanceof FloatTag t && data == t.data; }

    @Override
    public int hashCode() { return Float.floatToIntBits(data); }
}
