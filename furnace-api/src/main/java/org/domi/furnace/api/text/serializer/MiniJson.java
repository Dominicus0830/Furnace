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

    private MiniJson(String s) { this.s = s; }

    /** 값 하나를 읽는다(뒤에 공백 외의 글자가 남으면 오류). null 은 JSON null. */
    static Object parse(String text) {
        MiniJson p = new MiniJson(text);
        Object v = p.value(0);
        p.ws();
        if (p.pos != text.length()) throw p.error("JSON 값 뒤에 글자가 남았다");
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
        if (depth > MAX_DEPTH) throw error("중첩이 너무 깊다");
        ws();
        if (pos >= s.length()) throw error("값이 필요하다");
        char c = s.charAt(pos);
        switch (c) {
            case '{': return object(depth);
            case '[': return array(depth);
            case '"': return string();
            case 't': literal("true"); return Boolean.TRUE;
            case 'f': literal("false"); return Boolean.FALSE;
            case 'n': literal("null"); return null;
            default:
                if (c == '-' || c >= '0' && c <= '9') return number();
                throw error("예상치 못한 글자 '" + c + "'");
        }
    }

    private void literal(String word) {
        if (!s.startsWith(word, pos)) throw error("'" + word + "' 이어야 한다");
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
            if (pos >= s.length() || s.charAt(pos) != '"') throw error("키(문자열)가 필요하다");
            String k = string();
            ws();
            if (pos >= s.length() || s.charAt(pos) != ':') throw error("':' 가 필요하다");
            pos++;
            m.put(k, value(depth + 1));
            ws();
            if (pos >= s.length()) throw error("객체가 닫히지 않았다");
            char c = s.charAt(pos++);
            if (c == '}') return m;
            if (c != ',') throw error("',' 또는 '}' 가 필요하다");
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
            if (pos >= s.length()) throw error("배열이 닫히지 않았다");
            char c = s.charAt(pos++);
            if (c == ']') return l;
            if (c != ',') throw error("',' 또는 ']' 가 필요하다");
        }
    }

    private String string() {
        pos++; // 여는 따옴표
        StringBuilder sb = null;
        int seg = pos;
        while (true) {
            if (pos >= s.length()) throw error("문자열이 닫히지 않았다");
            char c = s.charAt(pos);
            if (c == '"') {
                String r = sb == null ? s.substring(seg, pos) : sb.append(s, seg, pos).toString();
                pos++;
                return r;
            }
            if (c == '\\') {
                if (sb == null) sb = new StringBuilder();
                sb.append(s, seg, pos);
                pos++;
                if (pos >= s.length()) throw error("이스케이프가 끝났다");
                char e = s.charAt(pos++);
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > s.length()) throw error("\\u 뒤에 16진수 4자리가 필요하다");
                        int v = 0;
                        for (int i = 0; i < 4; i++) {
                            int d = Character.digit(s.charAt(pos + i), 16);
                            if (d < 0) throw error("\\u 뒤에 16진수 4자리가 필요하다");
                            v = v << 4 | d;
                        }
                        sb.append((char) v);
                        pos += 4;
                    }
                    default -> throw error("알 수 없는 이스케이프 \\" + e);
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
                throw error("숫자가 아니다: " + t);
            }
        }
    }
}
