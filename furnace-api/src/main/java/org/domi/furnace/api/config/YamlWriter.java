// ConfigSection 을 블록 스타일 YAML 로 직렬화 — 2칸 들여쓰기, 필요할 때만 따옴표, 주석 보존
package org.domi.furnace.api.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

final class YamlWriter {

    private YamlWriter() {}

    static String write(ConfigSection root) {
        StringBuilder sb = new StringBuilder(256);
        List<String> header = root.getHeader();
        if (!header.isEmpty()) {
            writeComments(sb, header, 0);
            if (!root.isEmpty()) sb.append('\n');
        }
        if (root.isEmpty()) {
            if (header.isEmpty() && root.getFooter().isEmpty()) sb.append("{}\n");
        } else {
            writeMap(sb, root, 0, false);
        }
        List<String> footer = root.getFooter();
        writeComments(sb, footer, 0);
        return sb.toString();
    }

    private static void writeComments(StringBuilder sb, List<String> lines, int indent) {
        if (lines == null) return;
        for (String line : lines) {
            if (line == null) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n'
                        && !(sb.length() > 1 && sb.charAt(sb.length() - 2) == '\n')) {
                    sb.append('\n');
                }
                continue;
            }
            pad(sb, indent);
            sb.append('#');
            if (!line.isEmpty()) sb.append(' ').append(line);
            sb.append('\n');
        }
    }

    private static void pad(StringBuilder sb, int n) {
        for (int i = 0; i < n; i++) sb.append(' ');
    }

    private static void writeMap(StringBuilder sb, ConfigSection s, int indent, boolean firstInline) {
        boolean first = true;
        for (Map.Entry<String, Object> e : s.rawMap().entrySet()) {
            String key = e.getKey();
            boolean inline = first && firstInline;
            if (!inline) {
                writeComments(sb, s.rawComments(key), indent);
                pad(sb, indent);
            }
            first = false;
            writeKey(sb, key);
            sb.append(':');
            writeValue(sb, e.getValue(), indent, s.rawInline(key));
        }
    }

    /** "키:" 뒤에 오는 값 (개행으로 끝난다). */
    private static void writeValue(StringBuilder sb, Object v, int indent, String inline) {
        if (v instanceof ConfigSection sec) {
            if (sec.isEmpty()) {
                sb.append(" {}");
                writeInline(sb, inline);
                sb.append('\n');
            } else {
                writeInline(sb, inline);
                sb.append('\n');
                writeMap(sb, sec, indent + 2, false);
            }
        } else if (v instanceof List<?> list) {
            if (list.isEmpty()) {
                sb.append(" []");
                writeInline(sb, inline);
                sb.append('\n');
            } else {
                writeInline(sb, inline);
                sb.append('\n');
                writeSeq(sb, list, indent + 2);
            }
        } else if (v instanceof String str && isLiteralBlock(str)) {
            sb.append(' ');
            writeLiteral(sb, str, indent + 2, inline);
        } else {
            sb.append(' ');
            writeScalar(sb, v);
            writeInline(sb, inline);
            sb.append('\n');
        }
    }

    private static void writeSeq(StringBuilder sb, List<?> list, int indent) {
        for (Object item : list) {
            if (item instanceof ConfigSection sec && !sec.isEmpty()) {
                String firstKey = sec.rawMap().keySet().iterator().next();
                writeComments(sb, sec.rawComments(firstKey), indent);
                pad(sb, indent);
                sb.append("- ");
                writeMap(sb, sec, indent + 2, true);
            } else if (item instanceof List<?> nested && !nested.isEmpty()) {
                pad(sb, indent);
                sb.append("-\n");
                writeSeq(sb, nested, indent + 2);
            } else if (item instanceof String str && isLiteralBlock(str)) {
                pad(sb, indent);
                sb.append("- ");
                writeLiteral(sb, str, indent + 2, null);
            } else {
                pad(sb, indent);
                sb.append("- ");
                if (item instanceof ConfigSection) sb.append("{}");
                else if (item instanceof List) sb.append("[]");
                else writeScalar(sb, item);
                sb.append('\n');
            }
        }
    }

    private static void writeInline(StringBuilder sb, String inline) {
        if (inline != null) {
            sb.append(" #");
            if (!inline.isEmpty()) sb.append(' ').append(inline);
        }
    }

    // ---------------------------------------------------------------- 스칼라

    private static void writeKey(StringBuilder sb, String key) {
        writeString(sb, key);
    }

    private static void writeScalar(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof Double d) {
            if (d.isNaN()) sb.append(".nan");
            else if (d.isInfinite()) sb.append(d > 0 ? ".inf" : "-.inf");
            else sb.append(d.toString());
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
        if (!YamlScalars.needsQuoting(s)) {
            sb.append(s);
        } else if (YamlScalars.needsDoubleQuote(s)) {
            writeDoubleQuoted(sb, s);
        } else {
            sb.append('\'');
            for (int i = 0, n = s.length(); i < n; i++) {
                char c = s.charAt(i);
                if (c == '\'') sb.append('\'');
                sb.append(c);
            }
            sb.append('\'');
        }
    }

    private static void writeDoubleQuoted(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                case '\0' -> sb.append("\\0");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    boolean bad = c < 0x20 || c == 0x7f || c == 0x85 || c == 0x2028 || c == 0x2029 || c == 0xFEFF
                            || (Character.isSurrogate(c) && !(Character.isHighSurrogate(c) && i + 1 < n && Character.isLowSurrogate(s.charAt(i + 1)))
                            && !(Character.isLowSurrogate(c) && i > 0 && Character.isHighSurrogate(s.charAt(i - 1))));
                    if (bad) {
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

    // ---------------------------------------------------------------- 리터럴 블록(|)

    /** 여러 줄 문자열을 | 블록으로 안전하게 표현할 수 있는지. */
    private static boolean isLiteralBlock(String s) {
        int n = s.length();
        if (n == 0 || s.indexOf('\n') < 0) return false;
        char f = s.charAt(0);
        if (f == '\n' || f == ' ' || f == '\t') return false;
        int core = n;
        while (core > 0 && s.charAt(core - 1) == '\n') core--;
        if (core == 0) return false;
        int lineStart = 0;
        boolean onlyWs = true;
        for (int i = 0; i <= core; i++) {
            char c = i < core ? s.charAt(i) : '\n';
            if (c == '\n') {
                if (i > lineStart && onlyWs) return false; // 공백만 있는 줄은 읽을 때 빈 줄이 된다
                lineStart = i + 1;
                onlyWs = true;
                continue;
            }
            if (c < 0x20 && c != '\t') return false;
            if (c == 0x7f || c == 0x85 || c == 0x2028 || c == 0x2029 || c == 0xFEFF) return false;
            if (Character.isSurrogate(c)) {
                if (Character.isHighSurrogate(c) && i + 1 < core && Character.isLowSurrogate(s.charAt(i + 1))) i++;
                else return false;
            }
            if (c != ' ' && c != '\t') onlyWs = false;
        }
        return true;
    }

    private static void writeLiteral(StringBuilder sb, String s, int indent, String inline) {
        int n = s.length();
        int core = n;
        while (core > 0 && s.charAt(core - 1) == '\n') core--;
        int trailing = n - core;
        sb.append('|');
        if (trailing == 0) sb.append('-');
        else if (trailing > 1) sb.append('+');
        writeInline(sb, inline);
        sb.append('\n');
        int start = 0;
        while (start <= core) {
            int e = s.indexOf('\n', start);
            if (e < 0 || e > core) e = core;
            if (e > start) {
                pad(sb, indent);
                sb.append(s, start, e);
            }
            sb.append('\n');
            start = e + 1;
        }
        for (int i = 1; i < trailing; i++) sb.append('\n');
    }
}
