// 컴포넌트 값 다루기 — 코덱 입력은 JSON 꼴(Map·List·String·Boolean·수)이나 NBT 꼴(NbtCompound·NbtList·배열·타입 있는 수)
// 어느 쪽이든 받는다(바닐라 Codec 이 JsonOps·NbtOps 둘 다에서 읽는 것과 같다). 정규화된 값끼리의 같음 비교도 여기.
package org.domi.furnace.server.item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class Val {
    private Val() {}

    public static boolean isMap(Object o) { return o instanceof Map<?, ?> || o instanceof NbtCompound; }

    public static boolean isList(Object o) {
        return o instanceof List<?> || o instanceof NbtList || o instanceof int[] || o instanceof byte[] || o instanceof long[];
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object o) {
        if (o instanceof NbtCompound c) return c.tags();
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        throw new CodecException("Not a map: " + describe(o));
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o) {
        if (o instanceof List<?> l) return (List<Object>) l;
        if (o instanceof NbtList l) return l.items();
        if (o instanceof int[] a) { List<Object> r = new ArrayList<>(a.length); for (int x : a) r.add(x); return r; }
        if (o instanceof byte[] a) { List<Object> r = new ArrayList<>(a.length); for (byte x : a) r.add(x); return r; }
        if (o instanceof long[] a) { List<Object> r = new ArrayList<>(a.length); for (long x : a) r.add(x); return r; }
        throw new CodecException("Not a list: " + describe(o));
    }

    public static Number number(Object o) {
        if (o instanceof Number n) return n;
        if (o instanceof Boolean b) return b ? 1 : 0;   // JsonOps.getNumberValue 은 불리언도 받는다
        throw new CodecException("Not a number: " + describe(o));
    }

    public static boolean bool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.byteValue() != 0;   // NbtOps/JsonOps.getBooleanValue
        throw new CodecException("Not a boolean: " + describe(o));
    }

    public static String string(Object o) {
        if (o instanceof String s) return s;
        throw new CodecException("Not a string: " + describe(o));
    }

    static String describe(Object o) {
        if (o == null) return "null";
        if (o instanceof int[] a) return Arrays.toString(a);
        if (o instanceof byte[] a) return Arrays.toString(a);
        if (o instanceof long[] a) return Arrays.toString(a);
        String s = String.valueOf(o);
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    // ------------------------------------------------------------------ 같음(정규화된 값 기준)

    /**
     * 정규화된 값의 깊은 같음 — 바닐라 컴포넌트 레코드 equals 대응. 맵은 순서 무관, 리스트는 순서대로,
     * 수는 타입까지 같아야 한다(정규화가 필드마다 타입을 정한다), 배열은 내용 비교.
     */
    public static boolean same(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a instanceof NbtCompound ca) a = ca.tags();
        if (b instanceof NbtCompound cb) b = cb.tags();
        if (a instanceof Map<?, ?> ma) {
            if (!(b instanceof Map<?, ?> mb) || ma.size() != mb.size()) return false;
            for (var e : ma.entrySet()) {
                if (!mb.containsKey(e.getKey()) || !same(e.getValue(), mb.get(e.getKey()))) return false;
            }
            return true;
        }
        if (a instanceof NbtList la) {
            if (!(b instanceof NbtList lb) || la.elementType() != lb.elementType() && la.size() > 0) return false;
            return sameList(la.items(), lb.items());
        }
        if (a instanceof List<?> la) return b instanceof List<?> lb && sameList(la, lb);
        if (a instanceof int[] x) return b instanceof int[] y && Arrays.equals(x, y);
        if (a instanceof byte[] x) return b instanceof byte[] y && Arrays.equals(x, y);
        if (a instanceof long[] x) return b instanceof long[] y && Arrays.equals(x, y);
        return a.equals(b);
    }

    private static boolean sameList(List<?> a, List<?> b) {
        if (a.size() != b.size()) return false;
        Iterator<?> ia = a.iterator(), ib = b.iterator();
        while (ia.hasNext()) if (!same(ia.next(), ib.next())) return false;
        return true;
    }

    public static int hash(Object v) {
        if (v == null) return 0;
        if (v instanceof NbtCompound c) v = c.tags();
        if (v instanceof Map<?, ?> m) {
            int h = 0;
            for (var e : m.entrySet()) h += e.getKey().hashCode() ^ hash(e.getValue());
            return h;
        }
        if (v instanceof NbtList l) v = l.items();
        if (v instanceof List<?> l) {
            int h = 1;
            for (Object x : l) h = 31 * h + hash(x);
            return h;
        }
        if (v instanceof int[] a) return Arrays.hashCode(a);
        if (v instanceof byte[] a) return Arrays.hashCode(a);
        if (v instanceof long[] a) return Arrays.hashCode(a);
        return v.hashCode();
    }

    /** 정규화된 값의 깊은 복사(맵·리스트·NBT·배열). 문자열·수는 그대로. */
    public static Object copy(Object v) {
        if (v instanceof NbtCompound c) return c.copy();
        if (v instanceof NbtList l) return l.copy();
        if (v instanceof Map<?, ?> m) {
            java.util.LinkedHashMap<String, Object> r = new java.util.LinkedHashMap<>();
            for (var e : m.entrySet()) r.put((String) e.getKey(), copy(e.getValue()));
            return r;
        }
        if (v instanceof List<?> l) {
            ArrayList<Object> r = new ArrayList<>(l.size());
            for (Object x : l) r.add(copy(x));
            return r;
        }
        if (v instanceof int[] a) return a.clone();
        if (v instanceof byte[] a) return a.clone();
        if (v instanceof long[] a) return a.clone();
        return v;
    }
}
