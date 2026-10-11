// 테스트용 SNBT 읽기(바닐라 /data get 출력) — 값은 Byte/Short/Integer/Long/Float/Double/String/List/Map(키 정렬)으로.
package org.domi.furnace.server.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class Snbt {

    private final String s;
    private int i;

    private Snbt(String s) { this.s = s; }

    static Object parse(String s) {
        Snbt p = new Snbt(s);
        Object v = p.value();
        p.ws();
        if (p.i != s.length()) throw new IllegalArgumentException("남은 입력: " + s.substring(p.i));
        return v;
    }

    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

    private void expect(char c) {
        ws();
        if (i >= s.length() || s.charAt(i) != c) throw new IllegalArgumentException("'" + c + "' 기대 @" + i + ": " + s);
        i++;
    }

    private Object value() {
        ws();
        char c = s.charAt(i);
        if (c == '{') return compound();
        if (c == '[') return list();
        if (c == '"' || c == '\'') return quoted();
        return number();
    }

    private Map<String, Object> compound() {
        expect('{');
        Map<String, Object> m = new TreeMap<>();
        ws();
        if (s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            String key = s.charAt(i) == '"' || s.charAt(i) == '\'' ? quoted() : bare();
            expect(':');
            m.put(key, value());
            ws();
            if (s.charAt(i) == ',') { i++; continue; }
            expect('}');
            return m;
        }
    }

    private Object list() {
        expect('[');
        ws();
        // 배열 접두사 [I; ...] [B; ...] [L; ...]
        if (i + 1 < s.length() && s.charAt(i + 1) == ';') i += 2;
        List<Object> l = new ArrayList<>();
        ws();
        if (s.charAt(i) == ']') { i++; return l; }
        while (true) {
            l.add(value());
            ws();
            if (s.charAt(i) == ',') { i++; continue; }
            expect(']');
            return l;
        }
    }

    private String quoted() {
        char q = s.charAt(i++);
        StringBuilder b = new StringBuilder();
        while (s.charAt(i) != q) {
            char c = s.charAt(i++);
            if (c == '\\') c = s.charAt(i++);
            b.append(c);
        }
        i++;
        return b.toString();
    }

    private String bare() {
        int st = i;
        while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || "_-.+".indexOf(s.charAt(i)) >= 0)) i++;
        return s.substring(st, i);
    }

    private Object number() {
        String t = bare();
        char last = Character.toLowerCase(t.charAt(t.length() - 1));
        String body = t.substring(0, t.length() - 1);
        return switch (last) {
            case 'b' -> t.equals("true") ? (Object) (byte) 1 : Byte.parseByte(body);
            case 's' -> Short.parseShort(body);
            case 'l' -> Long.parseLong(body);
            case 'f' -> Float.parseFloat(body);
            case 'd' -> Double.parseDouble(body);
            case 'e' -> t.equals("true") ? (byte) 1 : t.equals("false") ? (Object) (byte) 0 : Double.parseDouble(t);
            default -> t.contains(".") ? Double.parseDouble(t) : Integer.parseInt(t);
        };
    }
}
