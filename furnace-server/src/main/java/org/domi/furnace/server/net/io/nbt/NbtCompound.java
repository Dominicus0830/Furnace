// 명명 NBT 태그 맵 + 타입 게터. 값은 Byte/Short/Integer/Long/Float/Double/String/byte[]/int[]/long[]/NbtCompound/NbtList.
package org.domi.furnace.server.net.io.nbt;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NbtCompound {

    private final Map<String, Object> tags = new LinkedHashMap<>();

    public Map<String, Object> tags() { return tags; }

    /** 깊은 복사. */
    public NbtCompound copy() {
        NbtCompound c = new NbtCompound();
        for (Map.Entry<String, Object> e : tags.entrySet()) c.tags.put(e.getKey(), NbtList.copyValue(e.getValue()));
        return c;
    }
    public boolean contains(String k) { return tags.containsKey(k); }

    public void putByte(String k, byte v) { tags.put(k, v); }
    public void putInt(String k, int v) { tags.put(k, v); }
    public void putDouble(String k, double v) { tags.put(k, v); }
    public void putLong(String k, long v) { tags.put(k, v); }
    public void putByteArray(String k, byte[] v) { tags.put(k, v); }
    public void putString(String k, String v) { tags.put(k, v); }
    public void putLongArray(String k, long[] v) { tags.put(k, v); }
    public void putIntArray(String k, int[] v) { tags.put(k, v); }
    public void putList(String k, NbtList v) { tags.put(k, v); }
    public void putCompound(String k, NbtCompound v) { tags.put(k, v); }
    public void put(String k, Object v) { tags.put(k, v); }
    public void putShort(String k, short v) { tags.put(k, v); }
    public void putFloat(String k, float v) { tags.put(k, v); }
    public void putBoolean(String k, boolean v) { tags.put(k, (byte) (v ? 1 : 0)); }

    /** 바닐라 contains(k, 99): 숫자 태그(byte..double)인가. */
    public boolean containsNumber(String k) { return tags.get(k) instanceof Number; }
    // ---- 바닐라 1.21.1 CompoundTag 게터: 키가 없거나 형이 맞지 않으면 기본값(0/false/""/빈 컬렉션)을 돌려준다.
    // 숫자 태그끼리는 변환한다. 정수 태그는 좁히기(캐스팅), 부동소수 태그는 floor 후 좁히기(FloatTag/DoubleTag.getAs*).

    private static long floorLong(double v) {
        long l = (long) v;
        return v < (double) l ? l - 1L : l;
    }
    private static int floorInt(double v) {
        int i = (int) v;
        return v < (double) i ? i - 1 : i;
    }

    public int getInt(String k) {
        Object o = tags.get(k);
        if (o instanceof Float || o instanceof Double) return floorInt(((Number) o).doubleValue());
        return o instanceof Number n ? n.intValue() : 0;
    }
    /** 이전 이름 호환. */
    public int getIntOr0(String k) { return getInt(k); }
    public long getLong(String k) {
        Object o = tags.get(k);
        if (o instanceof Float || o instanceof Double) return floorLong(((Number) o).doubleValue());
        return o instanceof Number n ? n.longValue() : 0L;
    }
    public short getShort(String k) {
        Object o = tags.get(k);
        if (o instanceof Float || o instanceof Double) return (short) (floorInt(((Number) o).doubleValue()) & 0xFFFF);
        return o instanceof Number n ? n.shortValue() : 0;
    }
    public byte getByte(String k) {
        Object o = tags.get(k);
        if (o instanceof Float || o instanceof Double) return (byte) (floorInt(((Number) o).doubleValue()) & 0xFF);
        return o instanceof Number n ? n.byteValue() : 0;
    }
    public float getFloat(String k) { return tags.get(k) instanceof Number n ? n.floatValue() : 0f; }
    public double getDouble(String k) { return tags.get(k) instanceof Number n ? n.doubleValue() : 0.0; }
    public boolean getBoolean(String k) { return getByte(k) != 0; }

    public String getString(String k) { return tags.get(k) instanceof String s ? s : ""; }
    public byte[] getByteArray(String k) { return tags.get(k) instanceof byte[] a ? a : new byte[0]; }
    public int[] getIntArray(String k) { return tags.get(k) instanceof int[] a ? a : new int[0]; }
    public long[] getLongArray(String k) { return tags.get(k) instanceof long[] a ? a : new long[0]; }
    public NbtCompound getCompound(String k) { return tags.get(k) instanceof NbtCompound c ? c : new NbtCompound(); }
    /** 리스트가 아니면 빈 리스트. */
    public NbtList getList(String k) { return tags.get(k) instanceof NbtList l ? l : new NbtList(NbtIo.TAG_END); }
    /** 바닐라 getList(k, type): 비어 있지 않고 원소 종류가 다르면 빈 리스트. */
    public NbtList getList(String k, byte type) {
        if (tags.get(k) instanceof NbtList l) {
            if (!l.items().isEmpty() && l.elementType() != type) return new NbtList(NbtIo.TAG_END);
            return l;
        }
        return new NbtList(NbtIo.TAG_END);
    }
}
