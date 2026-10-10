package org.domi.furnace.server.command.brigadier;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class ArgumentCommandNode<S, T> extends CommandNode<S> {

    private final String name;
    private final ArgumentType<T> type;
    private final SuggestionProvider<S> customSuggestions;

    public ArgumentCommandNode(String name, ArgumentType<T> type, Command<S> command, Predicate<S> requirement, CommandNode<S> redirect,
                               RedirectModifier<S> modifier, boolean forks, SuggestionProvider<S> customSuggestions) {
        super(command, requirement, redirect, modifier, forks);
        this.name = name;
        this.type = type;
        this.customSuggestions = customSuggestions;
    }

    public ArgumentType<T> getType() { return type; }

    public SuggestionProvider<S> getCustomSuggestions() { return customSuggestions; }

    @Override
    public String getName() { return name; }

    @Override
    public String getUsageText() { return "<" + name + ">"; }

    @Override
    public void parse(StringReader reader, CommandContextBuilder<S> contextBuilder) throws CommandSyntaxException {
        int start = reader.getCursor();
        T result = type.parse(reader, contextBuilder.getSource());
        ParsedArgument<S, T> parsed = new ParsedArgument<>(start, reader.getCursor(), result);
        contextBuilder.withArgument(name, parsed);
        contextBuilder.withNode(this, parsed.getRange());
    }

    @Override
    public CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) throws CommandSyntaxException {
        if (customSuggestions == null) return type.listSuggestions(context, builder);
        return customSuggestions.getSuggestions(context, builder);
    }

    @Override
    protected String getSortedKey() { return name; }

    @Override
    public Collection<String> getExamples() { return type.getExamples(); }

    @Override
    public String toString() { return "<argument " + name + ":" + type + ">"; }
}
