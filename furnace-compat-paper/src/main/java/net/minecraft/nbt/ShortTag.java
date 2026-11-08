package net.minecraft.nbt;

/** short 태그. */
public final class ShortTag extends NumericTag {

    private final short data;

    ShortTag(short data) { this.data = data; }

    /** 바닐라처럼 -128..1023 은 미리 만든 객체를 돌려준다. */
    private static final ShortTag[] CACHE = new ShortTag[1024 + 128];

    static {
        for (int i = 0; i < CACHE.length; i++) CACHE[i] = new ShortTag((short) (i - 128));
    }

    public static ShortTag valueOf(short v) { return v >= -128 && v <= 1023 ? CACHE[(int) v + 128] : new ShortTag(v); }

    @Override
    public byte getId() { return TAG_SHORT; }

    @Override
    public ShortTag copy() { return this; }

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
    public boolean equals(Object o) { return this == o || o instanceof ShortTag t && data == t.data; }

    @Override
    public int hashCode() { return data; }
}
