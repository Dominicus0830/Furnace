// ConfigSection 을 2칸 들여쓰기 pretty JSON 으로 직렬화 (주석은 JSON 에 없으므로 무시)
package org.domi.furnace.api.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

final class JsonWriter {

    private JsonWriter() {}

    static String write(ConfigSection root) {
        StringBuilder sb = new StringBuilder(256);
        writeObject(sb, root, 0);
        sb.append('\n');
        return sb.toString();
    }

    private static void pad(StringBuilder sb, int n) {
        for (int i = 0; i < n; i++) sb.append(' ');
    }

    private static void writeObject(StringBuilder sb, ConfigSection s, int indent) {
        if (s.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        boolean first = true;
        for (Map.Entry<String, Object> e : s.rawMap().entrySet()) {
            if (!first) sb.append(",\n");
            first = false;
            pad(sb, indent + 2);
            writeString(sb, e.getKey());
            sb.append(": ");
            writeValue(sb, e.getValue(), indent + 2);
        }
        sb.append('\n');
        pad(sb, indent);
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, List<?> list, int indent) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        boolean first = true;
        for (Object o : list) {
            if (!first) sb.append(",\n");
            first = false;
            pad(sb, indent + 2);
            writeValue(sb, o, indent + 2);
        }
        sb.append('\n');
        pad(sb, indent);
        sb.append(']');
    }

    private static void writeValue(StringBuilder sb, Object v, int indent) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof ConfigSection sec) {
            writeObject(sb, sec, indent);
        } else if (v instanceof List<?> l) {
            writeArray(sb, l, indent);
        } else if (v instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) {
                throw new IllegalArgumentException("JSON 은 NaN/Infinity 를 표현할 수 없다: " + d);
            }
            sb.append(d.toString());
        } else if (v instanceof OffsetDateTime o) {
            writeString(sb, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(o));
        } else if (v instanceof LocalDateTime o) {
            writeString(sb, DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(o));
        } else if (v instanceof LocalDate o) {
            writeString(sb, DateTimeFormatter.ISO_LOCAL_DATE.format(o));
        } else if (v instanceof LocalTime o) {
            writeString(sb, DateTimeFormatter.ISO_LOCAL_TIME.format(o));
        } else {
            sb.append(v); // Integer, Long, Boolean
        }
    }

    static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append("\\u00");
                        sb.append(Character.forDigit(c >> 4, 16)).append(Character.forDigit(c & 15, 16));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
