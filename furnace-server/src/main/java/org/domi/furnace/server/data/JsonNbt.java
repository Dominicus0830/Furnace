// 데이터 팩 JSON → 네트워크 NBT(바닐라 JsonOps.convertTo(NbtOps) 규칙). 레지스트리 항목을 클라에 데이터째 보낼 때 쓴다.
//  - 문자열 → String, 불리언 → Byte(0/1)
//  - 정수 → byte/short/int/long 중 가장 작은 것, 소수 → float(정확히 담기면) 또는 double
//  - 배열: 모든 원소가 같은 종류면 그 종류의 리스트(바이트·정수·롱만이면 배열 태그 — NbtOps 의 리스트 수집 규칙),
//    섞여 있으면 원소마다 {"": 값} compound 로 싸서 담는다(NbtOps 가 읽을 때 벗긴다)
//  - null 은 빠진다(NBT 에 없다)
// 클라는 이 NBT 를 레지스트리 코덱으로 읽으므로 숫자 종류는 달라도 된다(NbtOps 의 숫자 읽기는 종류를 가리지 않는다).
package org.domi.furnace.server.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class JsonNbt {
    private JsonNbt() {}

    /** JSON 객체 → compound. */
    public static NbtCompound compound(JsonObject o) {
        NbtCompound c = new NbtCompound();
        for (var e : o.entrySet()) {
            Object v = convert(e.getValue());
            if (v != null) c.put(e.getKey(), v);
        }
        return c;
    }

    /** JSON 값 → NBT 값(null 이면 null). */
    public static Object convert(JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        if (e instanceof JsonObject o) return compound(o);
        if (e instanceof JsonArray a) return list(a);
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isString()) return p.getAsString();
        if (p.isBoolean()) return (byte) (p.getAsBoolean() ? 1 : 0);
        BigDecimal value = p.getAsBigDecimal();
        try {
            long l = value.longValueExact();
            if ((byte) l == l) return (byte) l;
            if ((short) l == l) return (short) l;
            if ((int) l == l) return (int) l;
            return l;
        } catch (ArithmeticException ex) {
            double d = value.doubleValue();
            if ((float) d == d) return (float) d;
            return d;
        }
    }

    private static Object list(JsonArray a) {
        List<Object> items = new ArrayList<>(a.size());
        for (JsonElement el : a) {
            Object v = convert(el);
            if (v != null) items.add(v);
        }
        if (items.isEmpty()) return new NbtList(NbtIo.TAG_END);
        // NbtOps 리스트 수집: 바이트·정수·롱만으로 된 리스트는 배열 태그
        Class<?> first = items.get(0).getClass();
        boolean same = true;
        for (Object o : items) if (o.getClass() != first) { same = false; break; }
        if (same && first == Byte.class) {
            byte[] out = new byte[items.size()];
            for (int i = 0; i < out.length; i++) out[i] = (Byte) items.get(i);
            return out;
        }
        if (same && first == Integer.class) return items.stream().mapToInt(o -> (Integer) o).toArray();
        if (same && first == Long.class) return items.stream().mapToLong(o -> (Long) o).toArray();
        if (same) {
            NbtList l = new NbtList(typeOf(items.get(0)));
            items.forEach(l::add);
            return l;
        }
        NbtList l = new NbtList(NbtIo.TAG_COMPOUND);
        for (Object o : items) {
            if (o instanceof NbtCompound c && !(c.tags().size() == 1 && c.contains(""))) {
                l.add(c);   // NbtOps.wrapIfNeeded: 감싼 꼴이 아닌 compound 는 그대로
            } else {
                NbtCompound w = new NbtCompound();
                w.put("", o);
                l.add(w);
            }
        }
        return l;
    }

    private static byte typeOf(Object v) {
        if (v instanceof Byte) return NbtIo.TAG_BYTE;
        if (v instanceof Short) return NbtIo.TAG_SHORT;
        if (v instanceof Integer) return NbtIo.TAG_INT;
        if (v instanceof Long) return NbtIo.TAG_LONG;
        if (v instanceof Float) return NbtIo.TAG_FLOAT;
        if (v instanceof Double) return NbtIo.TAG_DOUBLE;
        if (v instanceof byte[]) return NbtIo.TAG_BYTE_ARRAY;
        if (v instanceof String) return NbtIo.TAG_STRING;
        if (v instanceof int[]) return NbtIo.TAG_INT_ARRAY;
        if (v instanceof long[]) return NbtIo.TAG_LONG_ARRAY;
        if (v instanceof NbtList) return NbtIo.TAG_LIST;
        return NbtIo.TAG_COMPOUND;
    }
}
