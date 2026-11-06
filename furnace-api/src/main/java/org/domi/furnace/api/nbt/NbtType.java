// NBT 태그 종류(번호는 NBT 형식의 태그 id 와 같다). 값의 자바 형:
// BYTE=Byte, SHORT=Short, INT=Integer, LONG=Long, FLOAT=Float, DOUBLE=Double, STRING=String,
// BYTE_ARRAY=byte[], INT_ARRAY=int[], LONG_ARRAY=long[], LIST=NbtList, COMPOUND=NbtCompound. 참은 BYTE 1.
package org.domi.furnace.api.nbt;

public enum NbtType {
    END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY;

    /** NBT 형식의 태그 id(0..12). */
    public int id() { return ordinal(); }

    public boolean isNumber() { return this.ordinal() >= BYTE.ordinal() && this.ordinal() <= DOUBLE.ordinal(); }

    /** 값의 종류. NBT 값이 아니면 IllegalArgumentException. */
    public static NbtType of(Object v) {
        return switch (v) {
            case Byte x -> BYTE;
            case Short x -> SHORT;
            case Integer x -> INT;
            case Long x -> LONG;
            case Float x -> FLOAT;
            case Double x -> DOUBLE;
            case String x -> STRING;
            case byte[] x -> BYTE_ARRAY;
            case int[] x -> INT_ARRAY;
            case long[] x -> LONG_ARRAY;
            case NbtList x -> LIST;
            case NbtCompound x -> COMPOUND;
            case null -> throw new IllegalArgumentException("NBT 값은 null 일 수 없다");
            default -> throw new IllegalArgumentException("NBT 값이 아니다: " + v.getClass().getName()
                    + " (Byte·Short·Integer·Long·Float·Double·String·byte[]·int[]·long[]·NbtList·NbtCompound 만)");
        };
    }

    /** 이 값이 NBT 값인가. */
    public static boolean isValue(Object v) {
        return v instanceof Byte || v instanceof Short || v instanceof Integer || v instanceof Long || v instanceof Float || v instanceof Double
                || v instanceof String || v instanceof byte[] || v instanceof int[] || v instanceof long[] || v instanceof NbtList || v instanceof NbtCompound;
    }

    static Object copy(Object v) {
        return switch (v) {
            case byte[] a -> a.clone();
            case int[] a -> a.clone();
            case long[] a -> a.clone();
            case NbtList l -> l.copy();
            case NbtCompound c -> c.copy();
            default -> v;
        };
    }

    static boolean equal(Object a, Object b) {
        if (a instanceof byte[] x) return b instanceof byte[] y && java.util.Arrays.equals(x, y);
        if (a instanceof int[] x) return b instanceof int[] y && java.util.Arrays.equals(x, y);
        if (a instanceof long[] x) return b instanceof long[] y && java.util.Arrays.equals(x, y);
        return a.equals(b);
    }

    static int hash(Object v) {
        return switch (v) {
            case byte[] a -> java.util.Arrays.hashCode(a);
            case int[] a -> java.util.Arrays.hashCode(a);
            case long[] a -> java.util.Arrays.hashCode(a);
            default -> v.hashCode();
        };
    }
}
