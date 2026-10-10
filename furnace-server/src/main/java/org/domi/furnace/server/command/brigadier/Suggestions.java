package org.domi.furnace.server.command.brigadier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class Suggestions {

    private static final Suggestions EMPTY = new Suggestions(StringRange.at(0), new ArrayList<>());

    private final StringRange range;
    private final List<Suggestion> suggestions;

    public Suggestions(StringRange range, List<Suggestion> suggestions) {
        this.range = range;
        this.suggestions = suggestions;
    }

    public StringRange getRange() { return range; }

    public List<Suggestion> getList() { return suggestions; }

    public boolean isEmpty() { return suggestions.isEmpty(); }

    public static CompletableFuture<Suggestions> empty() { return CompletableFuture.completedFuture(EMPTY); }

    public static Suggestions merge(String command, Collection<Suggestions> input) {
        if (input.isEmpty()) return EMPTY;
        if (input.size() == 1) return input.iterator().next();
        Set<Suggestion> texts = new HashSet<>();
        for (Suggestions s : input) texts.addAll(s.getList());
        return create(command, texts);
    }

    public static Suggestions create(String command, Collection<Suggestion> suggestions) {
        if (suggestions.isEmpty()) return EMPTY;
        int start = Integer.MAX_VALUE, end = Integer.MIN_VALUE;
        for (Suggestion s : suggestions) {
            start = Math.min(s.getRange().getStart(), start);
            end = Math.max(s.getRange().getEnd(), end);
        }
        StringRange range = new StringRange(start, end);
        Set<Suggestion> texts = new HashSet<>();
        for (Suggestion s : suggestions) texts.add(s.expand(command, range));
        List<Suggestion> sorted = new ArrayList<>(texts);
        sorted.sort(Suggestion::compareToIgnoreCase);
        return new Suggestions(range, sorted);
    }
}
