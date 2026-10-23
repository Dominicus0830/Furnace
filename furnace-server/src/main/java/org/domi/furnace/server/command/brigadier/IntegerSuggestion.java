package org.domi.furnace.server.command.brigadier;

import org.domi.furnace.api.text.Text;

public final class IntegerSuggestion extends Suggestion {

    private final int value;

    public IntegerSuggestion(StringRange range, int value, Text tooltip) {
        super(range, Integer.toString(value), tooltip);
        this.value = value;
    }

    public int getValue() { return value; }

    @Override
    public int compareTo(Suggestion o) {
        return o instanceof IntegerSuggestion i ? Integer.compare(value, i.value) : super.compareTo(o);
    }

    @Override
    public int compareToIgnoreCase(Suggestion b) { return compareTo(b); }
}
