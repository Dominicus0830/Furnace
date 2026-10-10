package org.domi.furnace.server.command.brigadier;

public record ParsedArgument<S, T>(StringRange range, T result) {

    public ParsedArgument(int start, int end, T result) {
        this(StringRange.between(start, end), result);
    }

    public StringRange getRange() { return range; }

    public T getResult() { return result; }
}
