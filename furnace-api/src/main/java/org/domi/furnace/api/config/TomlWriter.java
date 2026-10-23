// ConfigSection 을 TOML 로 직렬화 — 하위 섹션은 [테이블], 섹션 리스트는 [[테이블 배열]], 주석 보존, null 은 생략
package org.domi.furnace.api.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

final class TomlWriter {

    private TomlWriter() {}

    static String write(ConfigSection root) {
        StringBuilder sb = new StringBuilder(256);
        List<String> header = root.getHeader();
        if (!header.isEmpty()) {
            writeComments(sb, header);
            if (!root.isEmpty()) sb.append('\n');
        }
        writeTable(sb, root, "");
        List<String> footer = root.getFooter();
        writeComments(sb, footer);
        return sb.toString();
    }

    private static boolean isTableArray(Object v) {
        if (!(v instanceof List<?> l) || l.isEmpty()) return false;
        for (Object o : l) if (!(o instanceof ConfigSection)) return false;
        return true;
    }

    private static boolean isBodyValue(Object v) {
        return v != null && !(v instanceof ConfigSection) && !isTableArray(v);
    }

    private static void writeComments(StringBuilder sb, List<String> lines) {
        if (lines == null) return;
        for (String line : lines) {
            if (line == null) {
                int n = sb.length();
                if (n > 0 && sb.charAt(n - 1) == '\n' && !(n > 1 && sb.charAt(n - 2) == '\n')) sb.append('\n');
                continue;
            }
            sb.append('#');
            if (!line.isEmpty()) sb.append(' ').append(line);
            sb.append('\n');
        }
    }

    private static void blankLine(StringBuilder sb) {
        int n = sb.length();
        if (n > 0 && !(n > 1 && sb.charAt(n - 1) == '\n' && sb.charAt(n - 2) == '\n')) sb.append('\n');
    }

    private static void writeTable(StringBuilder sb, ConfigSection s, String path) {
        Map<String, Object> map = s.rawMap();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!isBodyValue(e.getValue())) continue;
            writeComments(sb, s.rawComments(e.getKey()));
            writeKey(sb, e.getKey());
            sb.append(" = ");
            writeValue(sb, e.getValue());
            writeInline(sb, s.rawInline(e.getKey()));
            sb.append('\n');
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = e.getKey();
            Object v = e.getValue();
            String childPath = path.isEmpty() ? keyText(key) : path + "." + keyText(key);
            if (v instanceof ConfigSection sub) {
                List<String> cm = s.rawComments(key);
                boolean container = !hasBody(sub) && hasTables(sub) && (cm == null || cm.isEmpty()) && s.rawInline(key) == null;
                if (!container) {
                    blankLine(sb);
                    writeComments(sb, cm);
                    sb.append('[').append(childPath).append(']');
                    writeInline(sb, s.rawInline(key));
                    sb.append('\n');
                }
                writeTable(sb, sub, childPath);
            } else if (isTableArray(v)) {
                for (Object o : (List<?>) v) {
                    ConfigSection el = (ConfigSection) o;
                    blankLine(sb);
                    writeComments(sb, el.getHeader());
                    sb.append("[[").append(childPath).append("]]\n");
                    writeTable(sb, el, childPath);
                }
            }
        }
    }

    private static boolean hasBody(ConfigSection s) {
        for (Object v : s.rawMap().values()) if (isBodyValue(v)) return true;
        return false;
    }

    private static boolean hasTables(ConfigSection s) {
        for (Object v : s.rawMap().values()) if (v instanceof ConfigSection || isTableArray(v)) return true;
        return false;
    }

    private static void writeInline(StringBuilder sb, String inline) {
        if (inline != null) {
            sb.append(" #");
            if (!inline.isEmpty()) sb.append(' ').append(inline);
        }
    }

    // ---------------------------------------------------------------- 키/값

    private static String keyText(String key) {
        StringBuilder sb = new StringBuilder();
        writeKey(sb, key);
        return sb.toString();
    }

    private static void writeKey(StringBuilder sb, String key) {
        boolean bare = !key.isEmpty();
        for (int i = 0; bare && i < key.length(); i++) {
            char c = key.charAt(i);
            bare = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
        }
        if (bare) sb.append(key);
        else writeString(sb, key);
    }

    private static void writeValue(StringBuilder sb, Object v) {
        if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof Double d) {
            if (d.isNaN()) sb.append("nan");
            else if (d.isInfinite()) sb.append(d > 0 ? "inf" : "-inf");
            else sb.append(d.toString());
        } else if (v instanceof List<?> l) {
            sb.append('[');
            boolean first = true;
            for (Object o : l) {
                if (o == null) continue; // TOML 에는 null 이 없다
                if (!first) sb.append(", ");
                first = false;
                writeValue(sb, o);
            }
            sb.append(']');
        } else if (v instanceof ConfigSection sec) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : sec.rawMap().entrySet()) {
                if (e.getValue() == null) continue;
                sb.append(first ? " " : ", ");
                first = false;
                writeKey(sb, e.getKey());
                sb.append(" = ");
                writeValue(sb, e.getValue());
            }
            sb.append(first ? "}" : " }");
        } else if (v instanceof OffsetDateTime o) {
            sb.append(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(o));
        } else if (v instanceof LocalDateTime o) {
            sb.append(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(o));
        } else if (v instanceof LocalDate o) {
            sb.append(DateTimeFormatter.ISO_LOCAL_DATE.format(o));
        } else if (v instanceof LocalTime o) {
            sb.append(DateTimeFormatter.ISO_LOCAL_TIME.format(o));
        } else {
            sb.append(v); // Integer, Long, Boolean
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20 || c == 0x7f) {
                        sb.append("\\u");
                        String h = Integer.toHexString(c);
                        for (int k = h.length(); k < 4; k++) sb.append('0');
                        sb.append(h);
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
