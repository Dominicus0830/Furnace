package org.domi.furnace.server.command.brigadier;

import java.util.Objects;
import org.domi.furnace.api.text.Text;

public class Suggestion implements Comparable<Suggestion> {

    private final StringRange range;
    private final String text;
    private final Text tooltip;

    public Suggestion(StringRange range, String text) { this(range, text, null); }

    public Suggestion(StringRange range, String text, Text tooltip) {
        this.range = range;
        this.text = text;
        this.tooltip = tooltip;
    }

    public StringRange getRange() { return range; }

    public String getText() { return text; }

    public Text getTooltip() { return tooltip; }

    public String apply(String input) {
        if (range.getStart() == 0 && range.getEnd() == input.length()) return text;
        StringBuilder result = new StringBuilder();
        if (range.getStart() > 0) result.append(input, 0, range.getStart());
        result.append(text);
        if (range.getEnd() < input.length()) result.append(input.substring(range.getEnd()));
        return result.toString();
    }

    public Suggestion expand(String command, StringRange range) {
        if (range.equals(this.range)) return this;
        StringBuilder result = new StringBuilder();
        if (range.getStart() < this.range.getStart()) result.append(command, range.getStart(), this.range.getStart());
        result.append(text);
        if (range.getEnd() > this.range.getEnd()) result.append(command, this.range.getEnd(), range.getEnd());
        return new Suggestion(range, result.toString(), tooltip);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Suggestion that)) return false;
        return Objects.equals(range, that.range) && Objects.equals(text, that.text)
                && Objects.equals(tooltip == null ? null : tooltip.getString(), that.tooltip == null ? null : that.tooltip.getString());
    }

    @Override
    public int hashCode() { return Objects.hash(range, text, tooltip == null ? null : tooltip.getString()); }

    @Override
    public int compareTo(Suggestion o) { return text.compareTo(o.text); }

    public int compareToIgnoreCase(Suggestion b) { return text.compareToIgnoreCase(b.text); }
}
