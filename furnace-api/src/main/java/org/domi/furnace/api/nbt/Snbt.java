// SNBT 읽기·쓰기 — 바닐라 TagParser·Tag.toString(1.21.1) 규칙. 서버 내부 item/Snbt 와 같은 결과.
// 읽기: 따옴표 없는 값은 수 접미사(b s l f d)·소수점·true/false 로 종류를 정하고, 수 모양이 아니거나 범위를 넘으면 문자열.
// 쓰기: 한 줄, 묶음 키는 정렬, 따옴표는 내용에 덜 걸리는 쪽.
package org.domi.furnace.api.nbt;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Snbt {

    private static final Pattern DOUBLE_NS = Pattern.compile("[-+]?(?:[0-9]+[.]|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOUBLE = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?d", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLOAT = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?f", Pattern.CASE_INSENSITIVE);
    private static final Pattern BYTE = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LONG = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)l", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)s", Pattern.CASE_INSENSITIVE);
    private static final Pattern INT = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)");
    private static final int MAX_DEPTH = 512;

    private final String s;
    private int i;
    private int depth;

    private Snbt(String s) { this.s = s; }

    /** 태그 하나(앞뒤 공백 허용, 뒤에 남는 글자가 있으면 오류). */
    public static Object parse(String s) {
        java.util.Objects.requireNonNull(s, "snbt");
        Snbt p = new Snbt(s);
        Object v = p.value();
        p.ws();
        if (p.i != s.length()) throw p.error("Unexpected trailing data");
        return v;
    }

    private SnbtParseException error(String msg) { return new SnbtParseException(msg + " at position " + i + ": " + context(), i); }

    private String context() {
        int from = Math.max(0, i - 35);
        return (from > 0 ? "..." : "") + s.substring(from, Math.min(s.length(), i)) + "<--[HERE]";
    }

    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

    private boolean peek(char c) { ws(); return i < s.length() && s.charAt(i) == c; }

    private void expect(char c) {
        ws();
        if (i >= s.length() || s.charAt(i) != c) throw error("Expected '" + c + "'");
        i++;
    }

    private Object value() {
        ws();
        if (i >= s.length()) throw error("Expected value");
        char c = s.charAt(i);
        if (c == '{') return compound();
        if (c == '[') return listOrArray();
        return typedValue();
    }

    private Object compound() {
        if (++depth > MAX_DEPTH) throw error("Too deep");
        expect('{');
        NbtCompound c = new NbtCompound();
        ws();
        while (i < s.length() && s.charAt(i) != '}') {
            int start = i;
            String key = key();
            if (key.isEmpty()) { i = start; throw error("Expected key"); }
            expect(':');
            c.put(key, value());
            if (!separator()) break;
            ws();
        }
        expect('}');
        depth--;
        return c;
    }

    private boolean separator() {
        ws();
        if (i < s.length() && s.charAt(i) == ',') { i++; ws(); return true; }
        return false;
    }

    private String key() {
        ws();
        if (i < s.length() && (s.charAt(i) == '"' || s.charAt(i) == '\'')) return quoted();
        return unquoted();
    }

    private static boolean unquotedChar(char c) {
        return c >= '0' && c <= '9' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '_' || c == '-' || c == '.' || c == '+';
    }

    private String unquoted() {
        int start = i;
        while (i < s.length() && unquotedChar(s.charAt(i))) i++;
        return s.substring(start, i);
    }

    private String quoted() {
        char q = s.charAt(i++);
        StringBuilder b = new StringBuilder();
        boolean esc = false;
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (esc) {
                if (c != q && c != '\\') { i--; throw error("Invalid escape sequence '" + c + "' in quoted string"); }
                b.append(c);
                esc = false;
            } else if (c == '\\') esc = true;
            else if (c == q) return b.toString();
            else b.append(c);
        }
        throw error("Unclosed quoted string");
    }

    private Object typedValue() {
        ws();
        int start = i;
        if (s.charAt(i) == '"' || s.charAt(i) == '\'') return quoted();
        String u = unquoted();
        if (u.isEmpty()) { i = start; throw error("Expected value"); }
        return type(u);
    }

    /** 바닐라 TagParser.type: 수 모양이면 그 종류, 아니면 문자열. */
    private static Object type(String u) {
        try {
            if (FLOAT.matcher(u).matches()) return Float.parseFloat(u.substring(0, u.length() - 1));
            if (BYTE.matcher(u).matches()) return Byte.parseByte(u.substring(0, u.length() - 1));
            if (LONG.matcher(u).matches()) return Long.parseLong(u.substring(0, u.length() - 1));
            if (SHORT.matcher(u).matches()) return Short.parseShort(u.substring(0, u.length() - 1));
            if (INT.matcher(u).matches()) return Integer.parseInt(u);
            if (DOUBLE.matcher(u).matches()) return Double.parseDouble(u.substring(0, u.length() - 1));
            if (DOUBLE_NS.matcher(u).matches()) return Double.parseDouble(u);
            if ("true".equalsIgnoreCase(u)) return (byte) 1;
            if ("false".equalsIgnoreCase(u)) return (byte) 0;
        } catch (NumberFormatException ignored) {
            // 범위를 넘는 수는 문자열로 남는다(바닐라와 같다)
        }
        return u;
    }

    private Object listOrArray() {
        if (i + 2 < s.length() && s.charAt(i + 1) != '"' && s.charAt(i + 2) == ';') {
            char t = s.charAt(i + 1);
            i += 3;
            return array(t);
        }
        if (++depth > MAX_DEPTH) throw error("Too deep");
        expect('[');
        List<Object> items = new ArrayList<>();
        NbtType type = NbtType.END;
        ws();
        while (i < s.length() && s.charAt(i) != ']') {
            int start = i;
            Object v = value();
            NbtType t = NbtType.of(v);
            if (type == NbtType.END) type = t;
            else if (t != type) { i = start; throw error("Can't insert " + t + " into list of " + type); }
            items.add(v);
            if (!separator()) break;
            ws();
        }
        expect(']');
        depth--;
        NbtList l = new NbtList();
        for (Object o : items) l.add(o);
        return l;
    }

    private Object array(char t) {
        List<Object> items = new ArrayList<>();
        ws();
        while (i < s.length() && s.charAt(i) != ']') {
            int start = i;
            Object v = value();
            boolean ok = switch (Character.toUpperCase(t)) {
                case 'B' -> v instanceof Byte || v instanceof Short || v instanceof Integer || v instanceof Long;
                case 'I' -> v instanceof Byte || v instanceof Short || v instanceof Integer;
                case 'L' -> v instanceof Byte || v instanceof Short || v instanceof Integer || v instanceof Long;
                default -> { i = start; throw error("Invalid array type '" + t + "'"); }
            };
            if (!ok) { i = start; throw error("Can't insert " + NbtType.of(v) + " into array of " + t); }
            items.add(v);
            if (!separator()) break;
            ws();
        }
        expect(']');
        switch (Character.toUpperCase(t)) {
            case 'B' -> { byte[] a = new byte[items.size()]; for (int k = 0; k < a.length; k++) a[k] = ((Number) items.get(k)).byteValue(); return a; }
            case 'I' -> { int[] a = new int[items.size()]; for (int k = 0; k < a.length; k++) a[k] = ((Number) items.get(k)).intValue(); return a; }
            default -> { long[] a = new long[items.size()]; for (int k = 0; k < a.length; k++) a[k] = ((Number) items.get(k)).longValue(); return a; }
        }
    }

    // ------------------------------------------------------------------ 쓰기(바닐라 SnbtPrinter 가 아닌 Tag.toString 꼴)

    /** 바닐라 Tag.toString(StringTagVisitor) 과 같은 한 줄 SNBT. */
    public static String write(Object v) {
        StringBuilder b = new StringBuilder();
        write(v, b);
        return b.toString();
    }

    private static final Pattern SIMPLE_KEY = Pattern.compile("[A-Za-z0-9._+-]+");

    private static void write(Object v, StringBuilder b) {
        switch (v) {
            case Byte x -> b.append(x).append('b');
            case Short x -> b.append(x).append('s');
            case Integer x -> b.append(x);
            case Long x -> b.append(x).append('L');
            case Float x -> b.append(x).append('f');
            case Double x -> b.append(x).append('d');
            case String x -> quote(x, b);
            case byte[] a -> { b.append("[B;"); for (int k = 0; k < a.length; k++) { if (k > 0) b.append(','); b.append(a[k]).append('B'); } b.append(']'); }
            case int[] a -> { b.append("[I;"); for (int k = 0; k < a.length; k++) { if (k > 0) b.append(','); b.append(a[k]); } b.append(']'); }
            case long[] a -> { b.append("[L;"); for (int k = 0; k < a.length; k++) { if (k > 0) b.append(','); b.append(a[k]).append('L'); } b.append(']'); }
            case NbtList l -> {
                b.append('[');
                for (int k = 0; k < l.size(); k++) { if (k > 0) b.append(','); write(l.get(k), b); }
                b.append(']');
            }
            case NbtCompound c -> {
                b.append('{');
                java.util.List<String> keys = new ArrayList<>(c.keys());
                java.util.Collections.sort(keys);
                boolean first = true;
                for (String k : keys) {
                    if (!first) b.append(',');
                    first = false;
                    if (SIMPLE_KEY.matcher(k).matches()) b.append(k);
                    else quote(k, b);
                    b.append(':');
                    write(c.get(k), b);
                }
                b.append('}');
            }
            default -> throw new IllegalArgumentException("NBT 값이 아님: " + v);
        }
    }

    /** 바닐라 StringTag.quoteAndEscape: 작은따옴표/큰따옴표 중 내용에 덜 걸리는 쪽. */
    private static void quote(String s, StringBuilder out) {
        StringBuilder b = new StringBuilder(" ");
        char q = 0;
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '\\') b.append('\\');
            else if (c == '"' || c == '\'') {
                if (q == 0) q = c == '"' ? '\'' : '"';
                if (q == c) b.append('\\');
            }
            b.append(c);
        }
        if (q == 0) q = '"';
        b.setCharAt(0, q);
        b.append(q);
        out.append(b);
    }
}
