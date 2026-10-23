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
    /** 바닐라 CompoundTag 의 숫자 getter 처럼 타입이 달라도 숫자면 변환하고, 없으면 0. */
    public int getIntOr0(String k) { return tags.get(k) instanceof Number n ? n.intValue() : 0; }
    public float getFloat(String k) { return tags.get(k) instanceof Number n ? n.floatValue() : 0f; }
    public short getShort(String k) { return tags.get(k) instanceof Number n ? n.shortValue() : 0; }
    public boolean getBoolean(String k) { return tags.get(k) instanceof Number n && n.byteValue() != 0; }

    public byte getByte(String k) { return (Byte) tags.get(k); }
    public int getInt(String k) { return (Integer) tags.get(k); }
    public long getLong(String k) { return (Long) tags.get(k); }
    public byte[] getByteArray(String k) { return (byte[]) tags.get(k); }
    public String getString(String k) { return (String) tags.get(k); }
    public long[] getLongArray(String k) { return (long[]) tags.get(k); }
    public NbtList getList(String k) { return (NbtList) tags.get(k); }
    public NbtCompound getCompound(String k) { return (NbtCompound) tags.get(k); }
}
