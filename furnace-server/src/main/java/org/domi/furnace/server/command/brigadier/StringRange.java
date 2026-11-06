package org.domi.furnace.server.command.brigadier;

public record StringRange(int start, int end) {

    public static StringRange at(int pos) { return new StringRange(pos, pos); }

    public static StringRange between(int start, int end) { return new StringRange(start, end); }

    public static StringRange encompassing(StringRange a, StringRange b) {
        return new StringRange(Math.min(a.start, b.start), Math.max(a.end, b.end));
    }

    public int getStart() { return start; }

    public int getEnd() { return end; }

    public String get(String string) { return string.substring(start, end); }

    public String get(StringReader reader) { return reader.getString().substring(start, end); }

    public boolean isEmpty() { return start == end; }

    public int getLength() { return end - start; }
}
