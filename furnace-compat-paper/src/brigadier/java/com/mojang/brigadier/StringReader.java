package com.mojang.brigadier;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * Brigadier 1.3.10 대역(컴파일·시험 전용 — jar 에 넣지 않는다). 이 shim 이 부르는 멤버만, 진짜와 같은 서술자·동작(따옴표·이스케이프·오류 문구)으로 손으로 썼다.
 * 진짜와 맞는지는 {@code -PbrigadierJar=<진짜 brigadier-1.3.10.jar>} 로 시험을 진짜 위에서 돌려 본다.
 */
public class StringReader implements ImmutableStringReader {
    private static final char SYNTAX_ESCAPE = '\\';
    private static final char SYNTAX_DOUBLE_QUOTE = '"';
    private static final char SYNTAX_SINGLE_QUOTE = '\'';

    private final String string;
    private int cursor;

    public StringReader(String string) { this.string = string; }

    public StringReader(StringReader other) {
        this.string = other.string;
        this.cursor = other.cursor;
    }

    @Override public String getString() { return string; }
    public void setCursor(int cursor) { this.cursor = cursor; }
    @Override public int getRemainingLength() { return string.length() - cursor; }
    @Override public int getTotalLength() { return string.length(); }
    @Override public int getCursor() { return cursor; }
    @Override public String getRead() { return string.substring(0, cursor); }
    @Override public String getRemaining() { return string.substring(cursor); }
    @Override public boolean canRead(int length) { return cursor + length <= string.length(); }
    @Override public boolean canRead() { return canRead(1); }
    @Override public char peek() { return string.charAt(cursor); }
    @Override public char peek(int offset) { return string.charAt(cursor + offset); }
    public char read() { return string.charAt(cursor++); }
    public void skip() { cursor++; }

    public static boolean isAllowedNumber(char c) { return c >= '0' && c <= '9' || c == '.' || c == '-'; }

    public static boolean isQuotedStringStart(char c) { return c == SYNTAX_DOUBLE_QUOTE || c == SYNTAX_SINGLE_QUOTE; }

    public void skipWhitespace() {
        while (canRead() && Character.isWhitespace(peek())) skip();
    }

    public static boolean isAllowedInUnquotedString(char c) {
        return c >= '0' && c <= '9' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '_' || c == '-' || c == '.' || c == '+';
    }

    public String readUnquotedString() {
        int start = cursor;
        while (canRead() && isAllowedInUnquotedString(peek())) skip();
        return string.substring(start, cursor);
    }

    public String readQuotedString() throws CommandSyntaxException {
        if (!canRead()) return "";
        char next = peek();
        if (!isQuotedStringStart(next)) throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedStartOfQuote().createWithContext(this);
        skip();
        return readStringUntil(next);
    }

    public String readStringUntil(char terminator) throws CommandSyntaxException {
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        while (canRead()) {
            char c = read();
            if (escaped) {
                if (c == terminator || c == SYNTAX_ESCAPE) {
                    result.append(c);
                    escaped = false;
                } else {
                    setCursor(getCursor() - 1);
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidEscape().createWithContext(this, String.valueOf(c));
                }
            } else if (c == SYNTAX_ESCAPE) {
                escaped = true;
            } else if (c == terminator) {
                return result.toString();
            } else {
                result.append(c);
            }
        }
        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedEndOfQuote().createWithContext(this);
    }

    public String readString() throws CommandSyntaxException {
        if (!canRead()) return "";
        char next = peek();
        if (isQuotedStringStart(next)) {
            skip();
            return readStringUntil(next);
        }
        return readUnquotedString();
    }

    public void expect(char c) throws CommandSyntaxException {
        if (!canRead() || peek() != c) throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedSymbol().createWithContext(this, String.valueOf(c));
        skip();
    }
}
