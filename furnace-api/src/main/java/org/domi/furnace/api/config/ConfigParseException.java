// 설정 파일 구문 오류 — 줄/열 위치와 문제 줄 미리보기를 포함한다
package org.domi.furnace.api.config;

public class ConfigParseException extends RuntimeException {

    private static final int MAX_SNIPPET = 160;

    private final String detail;
    private final int line;
    private final int column;
    private final String lineText;
    private final String source;

    /** 위치 정보만 있는 오류. line/column은 1부터 시작한다. */
    public ConfigParseException(String detail, int line, int column) {
        this(detail, line, column, null, null);
    }

    private ConfigParseException(String detail, int line, int column, String lineText, String source) {
        super(buildMessage(detail, line, column, lineText, source));
        this.detail = detail;
        this.line = line;
        this.column = column;
        this.lineText = lineText;
        this.source = source;
    }

    /** 오류 설명 (위치 정보 제외). */
    public String detail() { return detail; }

    /** 오류가 난 줄 번호 (1부터). */
    public int line() { return line; }

    /** 오류가 난 열 번호 (1부터, UTF-16 단위). */
    public int column() { return column; }

    /** 오류가 난 파일 이름 (알 수 없으면 null). */
    public String source() { return source; }

    /** 파일 이름을 덧붙인 새 예외를 만든다. */
    public ConfigParseException withSource(String source) {
        ConfigParseException e = new ConfigParseException(detail, line, column, lineText, source);
        e.setStackTrace(getStackTrace());
        return e;
    }

    /** 원문 문자 오프셋 index 위치의 오류를 만든다. */
    static ConfigParseException at(String src, int index, String detail) {
        int len = src.length();
        if (index < 0) index = 0;
        if (index > len) index = len;
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < index; i++) {
            if (src.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        int column = index - lineStart + 1;
        int end = src.indexOf('\n', lineStart);
        if (end < 0) end = len;
        String text = src.substring(lineStart, end);
        if (text.endsWith("\r")) text = text.substring(0, text.length() - 1);
        if (column > MAX_SNIPPET || text.length() > MAX_SNIPPET) text = null;
        return new ConfigParseException(detail, line, column, text, null);
    }

    private static String buildMessage(String detail, int line, int column, String lineText, String source) {
        StringBuilder sb = new StringBuilder(detail.length() + 64);
        if (source != null) sb.append(source).append(": ");
        sb.append(detail).append(" (line ").append(line).append(", column ").append(column).append(')');
        if (lineText != null) {
            sb.append('\n').append("    ").append(lineText).append('\n').append("    ");
            for (int i = 1; i < column; i++) {
                sb.append(i - 1 < lineText.length() && lineText.charAt(i - 1) == '\t' ? '\t' : ' ');
            }
            sb.append('^');
        }
        return sb.toString();
    }
}
