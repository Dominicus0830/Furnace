// TOML v1.0.0 파서 — 단일 패스 문자 스캐너, 중복 키/테이블 재정의를 명세대로 오류 처리
package org.domi.furnace.api.config;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

final class TomlParser {

    private static final int MAX_DEPTH = 256;

    /** 헤더 경로 중간에서 암묵적으로 만들어진 테이블 (이후 [헤더]로 한 번 정의 가능). */
    private static final int IMPLICIT = 1;
    /** [헤더] 또는 루트로 명시 정의된 테이블. */
    private static final int EXPLICIT = 2;
    /** 점 키(a.b = 1)로 만들어진 테이블 — 헤더로 재정의 불가, 하위 테이블 추가만 가능. */
    private static final int DOTTED = 3;
    /** 인라인 테이블 — 닫힌 값이라 확장 불가. */
    private static final int INLINE = 4;

    private final String src;
    private final int len;
    private int i;

    private final ArrayList<String> pending = new ArrayList<>();
    private final IdentityHashMap<ConfigSection, Integer> state = new IdentityHashMap<>();
    private final IdentityHashMap<List<?>, Boolean> tableArrays = new IdentityHashMap<>();

    private TomlParser(String src) {
        this.src = src;
        this.len = src.length();
    }

    static ConfigSection parse(String text) {
        TomlParser p = new TomlParser(text);
        if (p.len > 0 && text.charAt(0) == '﻿') p.i = 1;
        return p.parseDocument();
    }

    private ConfigParseException err(int at, String msg) {
        return ConfigParseException.at(src, at, msg);
    }

    // ================================================================ 문서

    private ConfigSection parseDocument() {
        ConfigSection root = new ConfigSection();
        state.put(root, EXPLICIT);
        ConfigSection current = root;
        boolean first = true;
        while (true) {
            skipBlankAndComments();
            if (i >= len) break;
            if (first) {
                root.setHeader(Comments.extractHeader(pending, false));
                first = false;
            }
            if (src.charAt(i) == '[') current = tableHeader(root);
            else keyValue(current);
        }
        if (first) {
            root.setHeader(Comments.extractHeader(pending, true));
        } else {
            root.setFooter(Comments.trimTrailingBlanks(new ArrayList<>(pending)));
        }
        return root;
    }

    private boolean atNewline() {
        if (i >= len) return false;
        char c = src.charAt(i);
        return c == '\n' || (c == '\r' && i + 1 < len && src.charAt(i + 1) == '\n');
    }

    private void skipNewline() {
        if (src.charAt(i) == '\r') i++;
        i++;
    }

    private void skipSpaces() {
        while (i < len) {
            char c = src.charAt(i);
            if (c == ' ' || c == '\t') i++;
            else break;
        }
    }

    /** 문장 사이의 공백·빈 줄·주석 줄을 건너뛰며 주석을 pending 에 모은다. 항상 줄 시작에서 호출된다. */
    private void skipBlankAndComments() {
        while (i < len) {
            skipSpaces();
            if (i >= len) return;
            char c = src.charAt(i);
            if (c == '#') {
                int e = commentEnd();
                pending.add(Comments.text(src, i + 1, e));
                i = e;
                if (i < len) skipNewline();
            } else if (atNewline()) {
                Comments.addBlank(pending);
                skipNewline();
            } else {
                return;
            }
        }
    }

    /** 주석 끝(개행 직전) 오프셋. 제어 문자는 오류. */
    private int commentEnd() {
        int e = i;
        while (e < len) {
            char c = src.charAt(e);
            if (c == '\n') break;
            if (c == '\r' && e + 1 < len && src.charAt(e + 1) == '\n') break;
            if ((c < 0x20 && c != '\t') || c == 0x7f) throw err(e, "control characters are not allowed in comments");
            e++;
        }
        return e;
    }

    /** 문장 끝: 공백, 줄 끝 주석, 개행(또는 EOF)을 소비하고 주석 텍스트를 반환한다. */
    private String endOfStatement() {
        skipSpaces();
        String ic = null;
        if (i < len && src.charAt(i) == '#') {
            int e = commentEnd();
            ic = Comments.text(src, i + 1, e);
            i = e;
        }
        if (i < len) {
            if (!atNewline()) throw err(i, "expected a newline after the value");
            skipNewline();
        }
        return ic;
    }

    private List<String> takePending() {
        if (pending.isEmpty()) return null;
        ArrayList<String> out = new ArrayList<>(pending);
        pending.clear();
        return out;
    }

    // ================================================================ 키

    private static boolean bareChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    private List<String> keyPath() {
        ArrayList<String> parts = new ArrayList<>(2);
        while (true) {
            skipSpaces();
            if (i >= len) throw err(len, "expected a key");
            char c = src.charAt(i);
            if (c == '"') {
                if (src.startsWith("\"\"\"", i)) throw err(i, "multi-line strings cannot be used as keys");
                parts.add(basicString());
            } else if (c == '\'') {
                if (src.startsWith("'''", i)) throw err(i, "multi-line strings cannot be used as keys");
                parts.add(literalString());
            } else {
                int st = i;
                while (i < len && bareChar(src.charAt(i))) i++;
                if (i == st) throw err(i, "expected a key (bare keys may only contain A-Za-z0-9_-)");
                parts.add(src.substring(st, i));
            }
            skipSpaces();
            if (i < len && src.charAt(i) == '.') {
                i++;
                continue;
            }
            return parts;
        }
    }

    private int stateOf(ConfigSection s) {
        Integer v = state.get(s);
        return v == null ? EXPLICIT : v;
    }

    // ================================================================ key = value

    private void keyValue(ConfigSection table) {
        int keyAt = i;
        List<String> path = keyPath();
        skipSpaces();
        if (i >= len || src.charAt(i) != '=') throw err(Math.min(i, len), "expected '=' after the key");
        i++;
        skipSpaces();
        Object v = value(0);
        String ic = endOfStatement();
        ConfigSection owner = assign(table, path, v, keyAt);
        String last = path.get(path.size() - 1);
        List<String> cm = takePending();
        if (cm != null) owner.setCommentsRaw(last, cm);
        if (ic != null) owner.setInlineRaw(last, ic);
    }

    /** 점 키 경로를 따라 table 아래에 값을 넣는다. 값이 들어간 섹션을 반환. */
    private ConfigSection assign(ConfigSection table, List<String> path, Object v, int at) {
        ConfigSection t = table;
        int n = path.size();
        for (int k = 0; k < n - 1; k++) {
            String part = path.get(k);
            Object child = t.rawGet(part);
            if (child == null && !t.hasKey(part)) {
                ConfigSection ns = new ConfigSection();
                state.put(ns, DOTTED);
                t.putRaw(part, ns);
                t = ns;
            } else if (child instanceof ConfigSection cs && stateOf(cs) == DOTTED) {
                t = cs;
            } else {
                throw err(at, "key '" + part + "' is already defined and cannot be extended by a dotted key");
            }
        }
        String last = path.get(n - 1);
        if (t.hasKey(last)) throw err(at, "duplicate key '" + last + "'");
        t.putRaw(last, v);
        return t;
    }

    // ================================================================ 테이블 헤더

    private ConfigSection tableHeader(ConfigSection root) {
        int at = i;
        i++; // [
        boolean array = false;
        if (i < len && src.charAt(i) == '[') {
            array = true;
            i++;
        }
        List<String> path = keyPath();
        skipSpaces();
        if (i >= len || src.charAt(i) != ']') throw err(Math.min(i, len), "expected ']' to close the table header");
        i++;
        if (array) {
            if (i >= len || src.charAt(i) != ']') throw err(Math.min(i, len), "expected ']]' to close the array-of-tables header");
            i++;
        }
        String ic = endOfStatement();
        List<String> cm = takePending();

        ConfigSection t = root;
        int n = path.size();
        for (int k = 0; k < n - 1; k++) {
            String part = path.get(k);
            Object child = t.rawGet(part);
            if (child == null && !t.hasKey(part)) {
                ConfigSection ns = new ConfigSection();
                state.put(ns, IMPLICIT);
                t.putRaw(part, ns);
                t = ns;
            } else if (child instanceof ConfigSection cs) {
                if (stateOf(cs) == INLINE) throw err(at, "inline table '" + part + "' cannot be extended");
                t = cs;
            } else if (child instanceof List<?> l && tableArrays.containsKey(l) && !l.isEmpty()) {
                t = (ConfigSection) l.get(l.size() - 1);
            } else {
                throw err(at, "'" + part + "' is already defined as a non-table value");
            }
        }
        String last = path.get(n - 1);
        Object existing = t.rawGet(last);
        boolean present = existing != null || t.hasKey(last);
        if (!array) {
            ConfigSection table;
            if (!present) {
                table = new ConfigSection();
                state.put(table, EXPLICIT);
                t.putRaw(last, table);
            } else if (existing instanceof ConfigSection s && stateOf(s) == IMPLICIT) {
                state.put(s, EXPLICIT);
                table = s;
            } else {
                throw err(at, "table '" + String.join(".", path) + "' is already defined");
            }
            if (cm != null) t.setCommentsRaw(last, cm);
            if (ic != null) t.setInlineRaw(last, ic);
            return table;
        }
        List<Object> list;
        if (!present) {
            list = new ArrayList<>();
            tableArrays.put(list, Boolean.TRUE);
            t.putRaw(last, list);
        } else if (existing instanceof List<?> l && tableArrays.containsKey(l)) {
            @SuppressWarnings("unchecked")
            List<Object> casted = (List<Object>) l;
            list = casted;
        } else {
            throw err(at, "'" + String.join(".", path) + "' is already defined and is not an array of tables");
        }
        ConfigSection element = new ConfigSection();
        state.put(element, EXPLICIT);
        list.add(element);
        if (cm != null) element.setHeader(Comments.trimBlanks(new ArrayList<>(cm)));
        return element;
    }

    // ================================================================ 값

    private Object value(int depth) {
        if (i >= len) throw err(len, "expected a value");
        char c = src.charAt(i);
        switch (c) {
            case '"':
                return src.startsWith("\"\"\"", i) ? multiBasic() : basicString();
            case '\'':
                return src.startsWith("'''", i) ? multiLiteral() : literalString();
            case '[':
                return array(depth);
            case '{':
                return inlineTable(depth);
            default:
                return scalarToken();
        }
    }

    private static boolean delimiter(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == ',' || c == ']' || c == '}' || c == '#';
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }

    private Object scalarToken() {
        int start = i;
        while (i < len && !delimiter(src.charAt(i))) i++;
        // 날짜와 시간 사이의 공백 허용: 1979-05-27 07:32:00
        if (i - start == 10 && src.charAt(start + 4) == '-' && i + 1 < len && src.charAt(i) == ' ' && digit(src.charAt(i + 1))) {
            i++;
            while (i < len && !delimiter(src.charAt(i))) i++;
        }
        if (i == start) throw err(i, i < len ? "expected a value but found '" + src.charAt(i) + "'" : "expected a value");
        String t = src.substring(start, i);
        switch (t) {
            case "true":
                return Boolean.TRUE;
            case "false":
                return Boolean.FALSE;
            case "inf", "+inf":
                return Double.POSITIVE_INFINITY;
            case "-inf":
                return Double.NEGATIVE_INFINITY;
            case "nan", "+nan", "-nan":
                return Double.NaN;
            default:
                break;
        }
        int n = t.length();
        if (n >= 8 && digit(t.charAt(0)) && digit(t.charAt(1)) && t.charAt(2) == ':') return time(t, start);
        if (n >= 10 && digit(t.charAt(0)) && digit(t.charAt(1)) && digit(t.charAt(2)) && digit(t.charAt(3)) && t.charAt(4) == '-') {
            return dateTime(t, start);
        }
        return number(t, start);
    }

    // ---------------------------------------------------------------- 숫자

    /** '_' 가 숫자 사이에만 있는지 검사하고 '_' 를 뺀 문자열을 반환한다. 잘못되면 null. */
    private static String digits(String t, int from, int to, int radix) {
        if (from >= to) return null;
        StringBuilder sb = null;
        for (int k = from; k < to; k++) {
            char c = t.charAt(k);
            if (c == '_') {
                if (k == from || k + 1 >= to || t.charAt(k - 1) == '_' || t.charAt(k + 1) == '_') return null;
                if (sb == null) sb = new StringBuilder(t.substring(from, k));
            } else {
                if (c > 0x7f || Character.digit(c, radix) < 0) return null;
                if (sb != null) sb.append(c);
            }
        }
        return sb == null ? t.substring(from, to) : sb.toString();
    }

    private Object number(String t, int at) {
        int n = t.length();
        char c0 = t.charAt(0);
        if (c0 == '0' && n > 1) {
            char x = t.charAt(1);
            int radix = x == 'x' ? 16 : x == 'o' ? 8 : x == 'b' ? 2 : 0;
            if (radix != 0) {
                String d = digits(t, 2, n, radix);
                if (d == null) throw err(at, "invalid number '" + t + "'");
                try {
                    return ConfigSection.intOrLong(Long.parseLong(d, radix));
                } catch (NumberFormatException e) {
                    throw err(at, "integer is out of the 64-bit range: " + t);
                }
            }
        }
        int p = 0;
        if (c0 == '+' || c0 == '-') p = 1;
        // 정수부
        int intEnd = p;
        while (intEnd < n && (digit(t.charAt(intEnd)) || t.charAt(intEnd) == '_')) intEnd++;
        String intPart = digits(t, p, intEnd, 10);
        if (intPart == null) throw err(at, "invalid number '" + t + "'");
        if (intPart.length() > 1 && intPart.charAt(0) == '0') throw err(at, "leading zeros are not allowed in numbers: " + t);
        if (intEnd == n) {
            try {
                return ConfigSection.intOrLong(Long.parseLong((c0 == '-' ? "-" : "") + intPart));
            } catch (NumberFormatException e) {
                throw err(at, "integer is out of the 64-bit range: " + t);
            }
        }
        StringBuilder f = new StringBuilder(n);
        if (c0 == '-') f.append('-');
        f.append(intPart);
        int q = intEnd;
        if (t.charAt(q) == '.') {
            int fs = q + 1;
            int fe = fs;
            while (fe < n && (digit(t.charAt(fe)) || t.charAt(fe) == '_')) fe++;
            String frac = digits(t, fs, fe, 10);
            if (frac == null) throw err(at, "invalid number '" + t + "' (digits are required after the decimal point)");
            f.append('.').append(frac);
            q = fe;
        }
        if (q < n) {
            char e = t.charAt(q);
            if (e != 'e' && e != 'E') throw err(at, "invalid number '" + t + "'");
            int es = q + 1;
            if (es < n && (t.charAt(es) == '+' || t.charAt(es) == '-')) es++;
            String exp = digits(t, es, n, 10);
            if (exp == null) throw err(at, "invalid number '" + t + "' (bad exponent)");
            f.append('e').append(t, q + 1, es).append(exp);
        }
        try {
            return Double.parseDouble(f.toString());
        } catch (NumberFormatException e) {
            throw err(at, "invalid number '" + t + "'");
        }
    }

    // ---------------------------------------------------------------- 날짜/시간

    private int num(String t, int from, int count, int at) {
        int v = 0;
        if (from + count > t.length()) throw err(at, "invalid date/time '" + t + "'");
        for (int k = 0; k < count; k++) {
            char c = t.charAt(from + k);
            if (!digit(c)) throw err(at, "invalid date/time '" + t + "'");
            v = v * 10 + (c - '0');
        }
        return v;
    }

    private Object time(String t, int at) {
        int[] end = new int[1];
        LocalTime lt = parseTime(t, 0, at, end);
        if (end[0] != t.length()) throw err(at, "invalid time '" + t + "'");
        return lt;
    }

    private LocalTime parseTime(String t, int from, int at, int[] end) {
        int h = num(t, from, 2, at);
        if (from + 2 >= t.length() || t.charAt(from + 2) != ':') throw err(at, "invalid time '" + t + "'");
        int m = num(t, from + 3, 2, at);
        if (from + 5 >= t.length() || t.charAt(from + 5) != ':') throw err(at, "invalid time '" + t + "' (seconds are required)");
        int s = num(t, from + 6, 2, at);
        int p = from + 8;
        int nanos = 0;
        if (p < t.length() && t.charAt(p) == '.') {
            p++;
            int fs = p;
            int digitsRead = 0;
            while (p < t.length() && digit(t.charAt(p))) {
                if (digitsRead < 9) {
                    nanos = nanos * 10 + (t.charAt(p) - '0');
                    digitsRead++;
                }
                p++;
            }
            if (p == fs) throw err(at, "invalid time '" + t + "' (digits are required after the decimal point)");
            for (int k = digitsRead; k < 9; k++) nanos *= 10;
        }
        end[0] = p;
        try {
            return LocalTime.of(h, m, s, nanos);
        } catch (DateTimeException e) {
            throw err(at, "invalid time '" + t + "'");
        }
    }

    private Object dateTime(String t, int at) {
        int y = num(t, 0, 4, at);
        if (t.charAt(7) != '-') throw err(at, "invalid date '" + t + "'");
        int mo = num(t, 5, 2, at);
        int d = num(t, 8, 2, at);
        LocalDate date;
        try {
            date = LocalDate.of(y, mo, d);
        } catch (DateTimeException e) {
            throw err(at, "invalid date '" + t + "'");
        }
        if (t.length() == 10) return date;
        char sep = t.charAt(10);
        if (sep != 'T' && sep != 't' && sep != ' ') throw err(at, "invalid date-time '" + t + "'");
        int[] end = new int[1];
        LocalTime lt = parseTime(t, 11, at, end);
        LocalDateTime ldt = LocalDateTime.of(date, lt);
        int p = end[0];
        if (p == t.length()) return ldt;
        char z = t.charAt(p);
        ZoneOffset off;
        if (z == 'Z' || z == 'z') {
            if (p + 1 != t.length()) throw err(at, "invalid date-time '" + t + "'");
            off = ZoneOffset.UTC;
        } else if (z == '+' || z == '-') {
            if (p + 6 != t.length() || t.charAt(p + 3) != ':') throw err(at, "invalid UTC offset in '" + t + "'");
            int oh = num(t, p + 1, 2, at);
            int om = num(t, p + 4, 2, at);
            try {
                off = ZoneOffset.ofHoursMinutes(z == '-' ? -oh : oh, z == '-' ? -om : om);
            } catch (DateTimeException e) {
                throw err(at, "invalid UTC offset in '" + t + "'");
            }
        } else {
            throw err(at, "invalid date-time '" + t + "'");
        }
        return OffsetDateTime.of(ldt, off);
    }

    // ---------------------------------------------------------------- 문자열

    private String basicString() {
        int start = i;
        i++; // "
        int plain = i;
        StringBuilder sb = null;
        while (true) {
            if (i >= len) throw err(start, "unterminated string");
            char c = src.charAt(i);
            if (c == '"') {
                String s = sb == null ? src.substring(plain, i) : sb.append(src, plain, i).toString();
                i++;
                return s;
            }
            if (c == '\n' || c == '\r') throw err(i, "newlines are not allowed in a basic string (use \"\"\" for multi-line)");
            if ((c < 0x20 && c != '\t') || c == 0x7f) throw err(i, "control characters must be escaped in strings");
            if (c != '\\') {
                i++;
                continue;
            }
            if (sb == null) sb = new StringBuilder();
            sb.append(src, plain, i);
            escape(sb);
            plain = i;
        }
    }

    /** i 는 역슬래시를 가리킨다. 이스케이프를 sb 에 풀어 넣고 i 를 전진시킨다. */
    private void escape(StringBuilder sb) {
        int at = i;
        if (i + 1 >= len) throw err(at, "unterminated escape sequence");
        char e = src.charAt(i + 1);
        i += 2;
        switch (e) {
            case 'b' -> sb.append('\b');
            case 't' -> sb.append('\t');
            case 'n' -> sb.append('\n');
            case 'f' -> sb.append('\f');
            case 'r' -> sb.append('\r');
            case '"' -> sb.append('"');
            case '\\' -> sb.append('\\');
            case 'u', 'U' -> {
                int count = e == 'u' ? 4 : 8;
                if (i + count > len) throw err(at, "incomplete unicode escape");
                long v = 0;
                for (int k = 0; k < count; k++) {
                    int d = Character.digit(src.charAt(i + k), 16);
                    if (d < 0) throw err(i + k, "invalid hexadecimal digit in unicode escape");
                    v = (v << 4) | d;
                }
                if (v > 0x10FFFF || (v >= 0xD800 && v <= 0xDFFF)) throw err(at, "invalid unicode scalar value in escape");
                sb.appendCodePoint((int) v);
                i += count;
            }
            default -> throw err(at, "invalid escape sequence '\\" + e + "'");
        }
    }

    private String literalString() {
        int start = i;
        i++; // '
        int st = i;
        while (true) {
            if (i >= len) throw err(start, "unterminated string");
            char c = src.charAt(i);
            if (c == '\'') {
                String s = src.substring(st, i);
                i++;
                return s;
            }
            if (c == '\n' || c == '\r') throw err(i, "newlines are not allowed in a literal string (use ''' for multi-line)");
            if ((c < 0x20 && c != '\t') || c == 0x7f) throw err(i, "control characters are not allowed in strings");
            i++;
        }
    }

    private void skipFirstNewline() {
        if (i < len) {
            if (src.charAt(i) == '\n') i++;
            else if (src.charAt(i) == '\r' && i + 1 < len && src.charAt(i + 1) == '\n') i += 2;
        }
    }

    private String multiBasic() {
        int start = i;
        i += 3;
        skipFirstNewline();
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= len) throw err(start, "unterminated multi-line string");
            char c = src.charAt(i);
            if (c == '"') {
                int run = 0;
                while (i + run < len && src.charAt(i + run) == '"') run++;
                if (run < 3) {
                    for (int k = 0; k < run; k++) sb.append('"');
                    i += run;
                    continue;
                }
                if (run > 5) throw err(i, "too many quotes at the end of a multi-line string");
                for (int k = 3; k < run; k++) sb.append('"');
                i += run;
                return sb.toString();
            }
            if (c == '\\') {
                // 줄 끝 역슬래시: 이어지는 공백/개행을 모두 제거
                int p = i + 1;
                while (p < len && (src.charAt(p) == ' ' || src.charAt(p) == '\t')) p++;
                if (p < len && (src.charAt(p) == '\n' || (src.charAt(p) == '\r' && p + 1 < len && src.charAt(p + 1) == '\n'))) {
                    while (p < len) {
                        char w = src.charAt(p);
                        if (w == ' ' || w == '\t' || w == '\n') p++;
                        else if (w == '\r' && p + 1 < len && src.charAt(p + 1) == '\n') p += 2;
                        else break;
                    }
                    i = p;
                    continue;
                }
                escape(sb);
                continue;
            }
            if (c == '\r') {
                if (i + 1 < len && src.charAt(i + 1) == '\n') {
                    sb.append('\n');
                    i += 2;
                    continue;
                }
                throw err(i, "a bare carriage return is not allowed in strings");
            }
            if ((c < 0x20 && c != '\t' && c != '\n') || c == 0x7f) throw err(i, "control characters must be escaped in strings");
            sb.append(c);
            i++;
        }
    }

    private String multiLiteral() {
        int start = i;
        i += 3;
        skipFirstNewline();
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= len) throw err(start, "unterminated multi-line string");
            char c = src.charAt(i);
            if (c == '\'') {
                int run = 0;
                while (i + run < len && src.charAt(i + run) == '\'') run++;
                if (run < 3) {
                    for (int k = 0; k < run; k++) sb.append('\'');
                    i += run;
                    continue;
                }
                if (run > 5) throw err(i, "too many quotes at the end of a multi-line string");
                for (int k = 3; k < run; k++) sb.append('\'');
                i += run;
                return sb.toString();
            }
            if (c == '\r') {
                if (i + 1 < len && src.charAt(i + 1) == '\n') {
                    sb.append('\n');
                    i += 2;
                    continue;
                }
                throw err(i, "a bare carriage return is not allowed in strings");
            }
            if ((c < 0x20 && c != '\t' && c != '\n') || c == 0x7f) throw err(i, "control characters are not allowed in strings");
            sb.append(c);
            i++;
        }
    }

    // ---------------------------------------------------------------- 배열 / 인라인 테이블

    private void skipArrayWs() {
        while (i < len) {
            char c = src.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n') {
                i++;
            } else if (c == '\r' && i + 1 < len && src.charAt(i + 1) == '\n') {
                i += 2;
            } else if (c == '#') {
                i = commentEnd();
            } else {
                break;
            }
        }
    }

    private List<Object> array(int depth) {
        if (depth >= MAX_DEPTH) throw err(i, "arrays are nested too deeply");
        int start = i;
        i++; // [
        ArrayList<Object> list = new ArrayList<>();
        while (true) {
            skipArrayWs();
            if (i >= len) throw err(start, "unterminated array");
            if (src.charAt(i) == ']') {
                i++;
                return list;
            }
            list.add(value(depth + 1));
            skipArrayWs();
            if (i >= len) throw err(start, "unterminated array");
            char c = src.charAt(i);
            if (c == ',') {
                i++;
            } else if (c == ']') {
                i++;
                return list;
            } else {
                throw err(i, "expected ',' or ']' in array");
            }
        }
    }

    private ConfigSection inlineTable(int depth) {
        if (depth >= MAX_DEPTH) throw err(i, "inline tables are nested too deeply");
        int start = i;
        i++; // {
        ConfigSection table = new ConfigSection();
        state.put(table, INLINE);
        skipSpaces();
        if (i < len && src.charAt(i) == '}') {
            i++;
            return table;
        }
        while (true) {
            skipSpaces();
            if (i >= len || atNewline()) throw err(Math.min(i, len), "newlines are not allowed inside an inline table (unterminated '{' at line start)");
            int keyAt = i;
            List<String> path = keyPath();
            skipSpaces();
            if (i >= len || src.charAt(i) != '=') throw err(Math.min(i, len), "expected '=' after the key");
            i++;
            skipSpaces();
            Object v = value(depth + 1);
            assign(table, path, v, keyAt);
            skipSpaces();
            if (i >= len) throw err(start, "unterminated inline table");
            char c = src.charAt(i);
            if (c == ',') {
                i++;
                skipSpaces();
                if (i < len && src.charAt(i) == '}') throw err(i, "a trailing comma is not allowed in an inline table");
            } else if (c == '}') {
                i++;
                return table;
            } else {
                throw err(i, "expected ',' or '}' in inline table");
            }
        }
    }
}
