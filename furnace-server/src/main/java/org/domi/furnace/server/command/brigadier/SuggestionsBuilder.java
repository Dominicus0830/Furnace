package org.domi.furnace.server.command.brigadier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.server.net.protocol.Text;

public class SuggestionsBuilder {

    private final String input;
    private final String inputLowerCase;
    private final int start;
    private final String remaining;
    private final String remainingLowerCase;
    private final List<Suggestion> result = new ArrayList<>();

    public SuggestionsBuilder(String input, String inputLowerCase, int start) {
        this.input = input;
        this.inputLowerCase = inputLowerCase;
        this.start = start;
        this.remaining = input.substring(start);
        this.remainingLowerCase = inputLowerCase.substring(start);
    }

    public SuggestionsBuilder(String input, int start) {
        this(input, input.toLowerCase(Locale.ROOT), start);
    }

    public String getInput() { return input; }

    public int getStart() { return start; }

    public String getRemaining() { return remaining; }

    public String getRemainingLowerCase() { return remainingLowerCase; }

    public Suggestions build() { return Suggestions.create(input, result); }

    public CompletableFuture<Suggestions> buildFuture() { return CompletableFuture.completedFuture(build()); }

    public SuggestionsBuilder suggest(String text) {
        if (text.equals(remaining)) return this;
        result.add(new Suggestion(StringRange.between(start, input.length()), text));
        return this;
    }

    public SuggestionsBuilder suggest(String text, Text tooltip) {
        if (text.equals(remaining)) return this;
        result.add(new Suggestion(StringRange.between(start, input.length()), text, tooltip));
        return this;
    }

    public SuggestionsBuilder suggest(int value) {
        result.add(new IntegerSuggestion(StringRange.between(start, input.length()), value, null));
        return this;
    }

    public SuggestionsBuilder suggest(int value, Text tooltip) {
        result.add(new IntegerSuggestion(StringRange.between(start, input.length()), value, tooltip));
        return this;
    }

    public SuggestionsBuilder add(SuggestionsBuilder other) {
        result.addAll(other.result);
        return this;
    }

    public SuggestionsBuilder createOffset(int start) { return new SuggestionsBuilder(input, inputLowerCase, start); }

    public SuggestionsBuilder restart() { return createOffset(start); }
}
