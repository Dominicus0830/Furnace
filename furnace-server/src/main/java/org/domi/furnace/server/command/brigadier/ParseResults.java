package org.domi.furnace.server.command.brigadier;

import java.util.Map;

public class ParseResults<S> {

    private final CommandContextBuilder<S> context;
    private final Map<CommandNode<S>, CommandSyntaxException> exceptions;
    private final StringReader reader;

    public ParseResults(CommandContextBuilder<S> context, StringReader reader, Map<CommandNode<S>, CommandSyntaxException> exceptions) {
        this.context = context;
        this.reader = reader;
        this.exceptions = exceptions;
    }

    public CommandContextBuilder<S> getContext() { return context; }

    public StringReader getReader() { return reader; }

    public Map<CommandNode<S>, CommandSyntaxException> getExceptions() { return exceptions; }
}
