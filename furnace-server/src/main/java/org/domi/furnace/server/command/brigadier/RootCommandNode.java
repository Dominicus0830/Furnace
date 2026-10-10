package org.domi.furnace.server.command.brigadier;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RootCommandNode<S> extends CommandNode<S> {

    public RootCommandNode() {
        super(null, s -> true, null, s -> List.of(s.getSource()), false);
    }

    @Override
    public String getName() { return ""; }

    @Override
    public String getUsageText() { return ""; }

    @Override
    public void parse(StringReader reader, CommandContextBuilder<S> contextBuilder) {}

    @Override
    public CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) { return Suggestions.empty(); }

    @Override
    protected String getSortedKey() { return ""; }

    @Override
    public Collection<String> getExamples() { return List.of(); }

    @Override
    public String toString() { return "<root>"; }
}
