// 아주 작은 JSON 읽기(패키지 내부) — 텍스트 JSON 해석에만 쓴다. 객체는 LinkedHashMap, 배열은 ArrayList, 숫자는 Integer/Long/Double.
package org.domi.furnace.api.text.serializer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MiniJson {

    private static final int MAX_DEPTH = 256;

    private final String s;
    private int pos;
    private boolean lenient;   // 바닐라 명령 인자(Gson lenient JsonReader): 작은따옴표·따옴표 없는 이름/값 허용

    private MiniJson(String s) { this.s = s; }

    /** 앞부분의 값 하나를 lenient 로 읽는다(명령 인자용) — 값과 읽은 뒤 위치. 뒤 글자는 검사하지 않는다. */
    record Prefix(Object value, int end) {}

    static Prefix parsePrefix(String text, int start) {
        MiniJson p = new MiniJson(text);
        p.pos = start;
        p.lenient = true;
        Object v = p.value(0);
        return new Prefix(v, p.pos);
    }

    /** 값 하나를 읽는다(뒤에 공백 외의 글자가 남으면 오류). null 은 JSON null. */
    static Object parse(String text) {
        MiniJson p = new MiniJson(text);
        Object v = p.value(0);
        p.ws();
        if (p.pos != text.length()) throw p.error("unexpected characters after the JSON value");
        return v;
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException("JSON 오류: " + msg + " (위치 " + pos + ")");
    }

    private void ws() {
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') pos++;
            else break;
        }
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH) throw error("nesting is too deep");
        ws();
        if (pos >= s.length()) throw error("a value is required");
        char c = s.charAt(pos);
        switch (c) {
            case '{': return object(depth);
            case '[': return array(depth);
            case '"': return string();
            case '\'':
                if (lenient) return string();
                throw error("unexpected character '" + c + "'");
            case 't': if (lenient) return unquoted(); literal("true"); return Boolean.TRUE;
            case 'f': if (lenient) return unquoted(); literal("false"); return Boolean.FALSE;
            case 'n': if (lenient) return unquoted(); literal("null"); return null;
            default:
                if (lenient) return unquoted();
                if (c == '-' || c >= '0' && c <= '9') return number();
                throw error("unexpected character '" + c + "'");
        }
    }

    private void literal(String word) {
        if (!s.startsWith(word, pos)) throw error("'" + word + "' expected");
        pos += word.length();
    }

    private Map<String, Object> object(int depth) {
        pos++;
        Map<String, Object> m = new LinkedHashMap<>();
        ws();
        if (pos < s.length() && s.charAt(pos) == '}') {
            pos++;
            return m;
        }
        while (true) {
            ws();
            if (pos >= s.length()) throw error("a key (string) is required");
            char kc = s.charAt(pos);
            String k;
            if (kc == '"' || lenient && kc == '\'') k = string();
            else if (lenient) k = String.valueOf(unquoted());
            else throw error("a key (string) is required");
            ws();
            if (pos >= s.length() || s.charAt(pos) != ':') throw error("':' expected");
            pos++;
            m.put(k, value(depth + 1));
            ws();
            if (pos >= s.length()) throw error("object is not closed");
            char c = s.charAt(pos++);
            if (c == '}') return m;
            if (c != ',') throw error("',' or '}' expected");
        }
    }

    private List<Object> array(int depth) {
        pos++;
        List<Object> l = new ArrayList<>();
        ws();
        if (pos < s.length() && s.charAt(pos) == ']') {
            pos++;
            return l;
        }
        while (true) {
            l.add(value(depth + 1));
            ws();
            if (pos >= s.length()) throw error("array is not closed");
            char c = s.charAt(pos++);
            if (c == ']') return l;
            if (c != ',') throw error("',' or ']' expected");
        }
    }

    private String string() {
        char quote = s.charAt(pos);
        pos++; // 여는 따옴표
        StringBuilder sb = null;
        int seg = pos;
        while (true) {
            if (pos >= s.length()) throw error("string is not closed");
            char c = s.charAt(pos);
            if (c == quote) {
                String r = sb == null ? s.substring(seg, pos) : sb.append(s, seg, pos).toString();
                pos++;
                return r;
            }
            if (c == '\\') {
                if (sb == null) sb = new StringBuilder();
                sb.append(s, seg, pos);
                pos++;
                if (pos >= s.length()) throw error("escape sequence ended unexpectedly");
                char e = s.charAt(pos++);
                switch (e) {
                    case '"', '\'', '\\', '/' -> sb.append(e);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > s.length()) throw error("4 hex digits expected after \\u");
                        int v = 0;
                        for (int i = 0; i < 4; i++) {
                            int d = Character.digit(s.charAt(pos + i), 16);
                            if (d < 0) throw error("4 hex digits expected after \\u");
                            v = v << 4 | d;
                        }
                        sb.append((char) v);
                        pos += 4;
                    }
                    default -> throw error("unknown escape \\" + e);
                }
                seg = pos;
                continue;
            }
            pos++;
        }
    }

    private Number number() {
        int start = pos;
        boolean frac = false;
        if (s.charAt(pos) == '-') pos++;
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c >= '0' && c <= '9') {
                pos++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                frac = true;
                pos++;
            } else {
                break;
            }
        }
        String t = s.substring(start, pos);
        try {
            if (!frac) {
                long l = Long.parseLong(t);
                return l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? (Number) (int) l : (Number) l;
            }
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            try {
                return Double.parseDouble(t);
            } catch (NumberFormatException e2) {
                throw error("not a number: " + t);
            }
        }
    }

    /** Gson lenient 의 따옴표 없는 값: 구분 글자 앞까지. true/false/null/숫자면 그 값으로 읽는다. */
    private Object unquoted() {
        int start = pos;
        while (pos < s.length() && "/\\;#={}[]:, \t\f\r\n".indexOf(s.charAt(pos)) < 0) pos++;
        if (pos == start) throw error("a value is required");
        String t = s.substring(start, pos);
        switch (t) {
            case "true": return Boolean.TRUE;
            case "false": return Boolean.FALSE;
            case "null": return null;
            default: break;
        }
        char c = t.charAt(0);
        if (c == '-' || c >= '0' && c <= '9') {
            try {
                long l = Long.parseLong(t);
                return l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? (Number) (int) l : (Number) l;
            } catch (NumberFormatException e) {
                try {
                    return Double.parseDouble(t);
                } catch (NumberFormatException e2) {
                    return t;
                }
            }
        }
        return t;
    }
}
