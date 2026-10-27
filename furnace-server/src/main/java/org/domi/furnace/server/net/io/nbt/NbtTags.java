// NBT 값 공용 동작(바닐라 Tag.equals·copy·CompoundTag.merge·NbtUtils.compareNbt·NumericTag 변환·TagType 이름).
// 값 모형은 NbtCompound 와 같다(Byte..Double·String·배열·NbtList·NbtCompound). NbtArray 는 경로 연산 동안 쓰는 바꿀 수 있는 배열.
package org.domi.furnace.server.net.io.nbt;

import java.util.Arrays;
import java.util.Map;

public final class NbtTags {
    private NbtTags() {}

    private static final String[] PRETTY = {"TAG_End", "TAG_Byte", "TAG_Short", "TAG_Int", "TAG_Long", "TAG_Float", "TAG_Double",
            "TAG_Byte_Array", "TAG_String", "TAG_List", "TAG_Compound", "TAG_Int_Array", "TAG_Long_Array"};

    public static byte typeOf(Object v) {
        if (v instanceof Byte) return NbtIo.TAG_BYTE;
        if (v instanceof Short) return NbtIo.TAG_SHORT;
        if (v instanceof Integer) return NbtIo.TAG_INT;
        if (v instanceof Long) return NbtIo.TAG_LONG;
        if (v instanceof Float) return NbtIo.TAG_FLOAT;
        if (v instanceof Double) return NbtIo.TAG_DOUBLE;
        if (v instanceof byte[]) return NbtIo.TAG_BYTE_ARRAY;
        if (v instanceof String) return NbtIo.TAG_STRING;
        if (v instanceof NbtList) return NbtIo.TAG_LIST;
        if (v instanceof NbtCompound) return NbtIo.TAG_COMPOUND;
        if (v instanceof int[]) return NbtIo.TAG_INT_ARRAY;
        if (v instanceof long[]) return NbtIo.TAG_LONG_ARRAY;
        if (v instanceof NbtArray a) return a.type();
        throw new IllegalArgumentException("NBT 값이 아님: " + (v == null ? "null" : v.getClass()));
    }

    /** TagType.getPrettyName. */
    public static String prettyName(byte type) { return type >= 0 && type < PRETTY.length ? PRETTY[type] : "UNKNOWN_" + type; }

    /** Tag.copy(깊은 복사). */
    public static Object copy(Object v) {
        if (v instanceof NbtArray a) return a.copy();
        if (v instanceof NbtList l) {
            NbtList c = new NbtList(l.elementType());
            for (Object o : l.items()) c.add(copy(o));
            return c;
        }
        if (v instanceof NbtCompound c) {
            NbtCompound out = new NbtCompound();
            for (var e : c.tags().entrySet()) out.tags().put(e.getKey(), copy(e.getValue()));
            return out;
        }
        return NbtList.copyValue(v);
    }

    /** Tag.equals: 같은 종류의 같은 값. 리스트는 원소만(빈 리스트는 종류와 상관없이 같다). */
    public static boolean equal(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a instanceof Float x) return b instanceof Float y && x.floatValue() == y.floatValue();
        if (a instanceof Double x) return b instanceof Double y && x.doubleValue() == y.doubleValue();
        if (a instanceof Number || a instanceof String) return a.equals(b);
        if (isArray(a)) return isArray(b) && typeOf(a) == typeOf(b) && arrayEquals(a, b);
        if (a instanceof NbtList x) {
            if (!(b instanceof NbtList y) || x.size() != y.size()) return false;
            for (int i = 0; i < x.size(); i++) if (!equal(x.get(i), y.get(i))) return false;
            return true;
        }
        if (a instanceof NbtCompound x) {
            if (!(b instanceof NbtCompound y) || x.tags().size() != y.tags().size()) return false;
            for (Map.Entry<String, Object> e : x.tags().entrySet()) {
                if (!y.tags().containsKey(e.getKey()) || !equal(e.getValue(), y.tags().get(e.getKey()))) return false;
            }
            return true;
        }
        return false;
    }

    private static boolean isArray(Object v) { return v instanceof byte[] || v instanceof int[] || v instanceof long[] || v instanceof NbtArray; }

    private static boolean arrayEquals(Object a, Object b) {
        Object x = a instanceof NbtArray na ? na.toArray() : a;
        Object y = b instanceof NbtArray nb ? nb.toArray() : b;
        return switch (x) {
            case byte[] p -> Arrays.equals(p, (byte[]) y);
            case int[] p -> Arrays.equals(p, (int[]) y);
            default -> Arrays.equals((long[]) x, (long[]) y);
        };
    }

    /** NbtUtils.compareNbt(expected, actual, partial). */
    public static boolean compareNbt(Object expected, Object actual, boolean partial) {
        if (expected == actual) return true;
        if (expected == null) return true;
        if (actual == null) return false;
        if (expected instanceof NbtCompound e) {
            if (!(actual instanceof NbtCompound a)) return false;
            for (var en : e.tags().entrySet()) {
                if (!compareNbt(en.getValue(), a.tags().get(en.getKey()), partial)) return false;
            }
            return true;
        }
        if (expected instanceof NbtList e && partial) {
            if (!(actual instanceof NbtList a)) return false;
            if (e.size() == 0) return a.size() == 0;
            for (Object x : e.items()) {
                boolean found = false;
                for (Object y : a.items()) {
                    if (compareNbt(x, y, partial)) { found = true; break; }
                }
                if (!found) return false;
            }
            return true;
        }
        if (typeOf(expected) != typeOf(actual) && !(expected instanceof NbtList && actual instanceof NbtList)) return false;
        return equal(expected, actual);
    }

    /** CompoundTag.merge: 양쪽이 compound 인 키는 안으로 합치고, 나머지는 복사해 덮는다. 자기 자신을 돌려준다. */
    public static NbtCompound merge(NbtCompound into, NbtCompound other) {
        for (var e : other.tags().entrySet()) {
            Object v = e.getValue();
            if (v instanceof NbtCompound oc && into.tags().get(e.getKey()) instanceof NbtCompound mine) merge(mine, oc);
            else into.tags().put(e.getKey(), copy(v));
        }
        return into;
    }

    // ---- 배열을 바꿀 수 있는 꼴로(경로 연산 동안) / 되돌리기

    /** 안쪽 배열을 NbtArray 로 바꾼다(compound·list 는 제자리). */
    public static Object thaw(Object v) {
        if (v instanceof byte[] || v instanceof int[] || v instanceof long[]) return NbtArray.of(v);
        if (v instanceof NbtList l) {
            for (int i = 0; i < l.size(); i++) l.items().set(i, thaw(l.get(i)));
        } else if (v instanceof NbtCompound c) {
            for (var e : c.tags().entrySet()) e.setValue(thaw(e.getValue()));
        }
        return v;
    }

    /** thaw 의 반대. */
    public static Object freeze(Object v) {
        if (v instanceof NbtArray a) return a.toArray();
        if (v instanceof NbtList l) {
            for (int i = 0; i < l.size(); i++) l.items().set(i, freeze(l.get(i)));
        } else if (v instanceof NbtCompound c) {
            for (var e : c.tags().entrySet()) e.setValue(freeze(e.getValue()));
        }
        return v;
    }

    // ---- NumericTag 변환

    public static byte asByte(Number n) {
        if (n instanceof Float f) return (byte) (floor(f) & 255);
        if (n instanceof Double d) return (byte) (floor(d) & 255);
        return n.byteValue();
    }

    public static int asInt(Number n) {
        if (n instanceof Float f) return floor(f);
        if (n instanceof Double d) return floor(d);
        return n.intValue();
    }

    public static long asLong(Number n) {
        if (n instanceof Float f) return (long) f.floatValue();
        if (n instanceof Double d) return (long) Math.floor(d);
        return n.longValue();
    }

    /** Mth.floor. */
    public static int floor(double d) {
        int i = (int) d;
        return d < i ? i - 1 : i;
    }
}
