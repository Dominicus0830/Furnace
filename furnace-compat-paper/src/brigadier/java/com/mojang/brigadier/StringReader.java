package com.mojang.brigadier;

/** Brigadier 1.3.10 대역(컴파일 전용) — 이 shim 이 부르는 멤버만. */
public class StringReader implements ImmutableStringReader {
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
}
