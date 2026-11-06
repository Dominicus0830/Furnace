// Brigadier StringReader 와 같은 규칙의 커서 리더(클라이언트가 같은 트리를 Brigadier 로 해석하므로 토큰 규칙이 같아야 한다).
package org.domi.furnace.server.command.brigadier;

public final class StringReader {

    private static final char SYNTAX_ESCAPE = '\\';
    private static final char SYNTAX_DOUBLE_QUOTE = '"';
    private static final char SYNTAX_SINGLE_QUOTE = '\'';

    private final String string;
    private int cursor;

    public StringReader(StringReader other) {
        this.string = other.string;
        this.cursor = other.cursor;
    }

    public StringReader(String string) {
        this.string = string;
    }

    public String getString() { return string; }

    public void setCursor(int cursor) { this.cursor = cursor; }

    public int getRemainingLength() { return string.length() - cursor; }

    public int getTotalLength() { return string.length(); }

    public int getCursor() { return cursor; }

    public String getRead() { return string.substring(0, cursor); }

    public String getRemaining() { return string.substring(cursor); }

    public boolean canRead(int length) { return cursor + length <= string.length(); }

    public boolean canRead() { return canRead(1); }

    public char peek() { return string.charAt(cursor); }

    public char peek(int offset) { return string.charAt(cursor + offset); }

    public char read() { return string.charAt(cursor++); }

    public void skip() { cursor++; }

    public static boolean isAllowedNumber(char c) { return c >= '0' && c <= '9' || c == '.' || c == '-'; }

    public static boolean isQuotedStringStart(char c) { return c == SYNTAX_DOUBLE_QUOTE || c == SYNTAX_SINGLE_QUOTE; }

    public void skipWhitespace() {
        while (canRead() && Character.isWhitespace(peek())) skip();
    }

    public int readInt() throws CommandSyntaxException {
        int start = cursor;
        while (canRead() && isAllowedNumber(peek())) skip();
        String number = string.substring(start, cursor);
        if (number.isEmpty()) throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedInt().createWithContext(this);
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            cursor = start;
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt().createWithContext(this, number);
        }
    }

    public long readLong() throws CommandSyntaxException {
        int start = cursor;
        while (canRead() && isAllowedNumber(peek())) skip();
        String number = string.substring(start, cursor);
        if (number.isEmpty()) throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedLong().createWithContext(this);
        try {
            return Long.parseLong(number);
        } catch (NumberFormatException e) {
            cursor = start;
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidLong().createWithContext(this, number);
        }
    }

    public double readDouble() throws CommandSyntaxException {
        int start = cursor;
        while (canRead() && isAllowedNumber(peek())) skip();
        String number = string.substring(start, cursor);
        if (number.isEmpty()) throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedDouble().createWithContext(this);
        try {
            return Double.parseDouble(number);
        } catch (NumberFormatException e) {
            cursor = start;
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidDouble().createWithContext(this, number);
        }
    }

    public float readFloat() throws CommandSyntaxException {
        int start = cursor;
        while (canRead() && isAllowedNumber(peek())) skip();
        String number = string.substring(start, cursor);
        if (number.isEmpty()) throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedFloat().createWithContext(this);
        try {
            return Float.parseFloat(number);
        } catch (NumberFormatException e) {
            cursor = start;
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidFloat().createWithContext(this, number);
        }
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
        if (!isQuotedStringStart(next)) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedStartOfQuote().createWithContext(this);
        }
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

    public boolean readBoolean() throws CommandSyntaxException {
        int start = cursor;
        String value = readString();
        if (value.isEmpty()) throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedBool().createWithContext(this);
        if (value.equals("true")) return true;
        if (value.equals("false")) return false;
        cursor = start;
        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidBool().createWithContext(this, value);
    }

    public void expect(char c) throws CommandSyntaxException {
        if (!canRead() || peek() != c) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedSymbol().createWithContext(this, String.valueOf(c));
        }
        skip();
    }
}
