package org.domi.furnace.server.command.brigadier;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class LiteralCommandNode<S> extends CommandNode<S> {

    private final String literal;
    private final String literalLowerCase;

    public LiteralCommandNode(String literal, Command<S> command, Predicate<S> requirement, CommandNode<S> redirect,
                              RedirectModifier<S> modifier, boolean forks) {
        super(command, requirement, redirect, modifier, forks);
        this.literal = literal;
        this.literalLowerCase = literal.toLowerCase(Locale.ROOT);
    }

    public String getLiteral() { return literal; }

    @Override
    public String getName() { return literal; }

    @Override
    public void parse(StringReader reader, CommandContextBuilder<S> contextBuilder) throws CommandSyntaxException {
        int start = reader.getCursor();
        int end = parse(reader);
        if (end > -1) {
            contextBuilder.withNode(this, StringRange.between(start, end));
            return;
        }
        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect().createWithContext(reader, literal);
    }

    private int parse(StringReader reader) {
        int start = reader.getCursor();
        if (reader.canRead(literal.length())) {
            int end = start + literal.length();
            if (reader.getString().substring(start, end).equals(literal)) {
                reader.setCursor(end);
                if (!reader.canRead() || reader.peek() == ' ') return end;
                reader.setCursor(start);
            }
        }
        return -1;
    }

    @Override
    public CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (literalLowerCase.startsWith(builder.getRemainingLowerCase())) return builder.suggest(literal).buildFuture();
        return Suggestions.empty();
    }

    @Override
    public String getUsageText() { return literal; }

    @Override
    protected String getSortedKey() { return literal; }

    @Override
    public Collection<String> getExamples() { return List.of(literal); }

    @Override
    public String toString() { return "<literal " + literal + ">"; }
}
