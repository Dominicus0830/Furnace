// Brigadier CommandDispatcher — 파싱(가장 멀리 읽은 후보 우선), ContextChain 실행(리다이렉트·포크), 자동완성, 사용법.
package org.domi.furnace.server.command.brigadier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class CommandDispatcher<S> {

    public static final String ARGUMENT_SEPARATOR = " ";
    public static final char ARGUMENT_SEPARATOR_CHAR = ' ';
    private static final String USAGE_OPTIONAL_OPEN = "[";
    private static final String USAGE_OPTIONAL_CLOSE = "]";
    private static final String USAGE_REQUIRED_OPEN = "(";
    private static final String USAGE_REQUIRED_CLOSE = ")";
    private static final String USAGE_OR = "|";

    private final RootCommandNode<S> root;
    private ResultConsumer<S> consumer = (c, s, r) -> {};

    public CommandDispatcher(RootCommandNode<S> root) { this.root = root; }

    public CommandDispatcher() { this(new RootCommandNode<>()); }

    public LiteralCommandNode<S> register(LiteralArgumentBuilder<S> command) {
        LiteralCommandNode<S> build = command.build();
        root.addChild(build);
        return build;
    }

    public void setConsumer(ResultConsumer<S> consumer) { this.consumer = consumer; }

    public int execute(String input, S source) throws CommandSyntaxException { return execute(new StringReader(input), source); }

    public int execute(StringReader input, S source) throws CommandSyntaxException { return execute(parse(input, source)); }

    public int execute(ParseResults<S> parse) throws CommandSyntaxException {
        if (parse.getReader().canRead()) {
            if (parse.getExceptions().size() == 1) throw parse.getExceptions().values().iterator().next();
            if (parse.getContext().getRange().isEmpty()) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parse.getReader());
            }
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(parse.getReader());
        }
        String command = parse.getReader().getString();
        CommandContext<S> original = parse.getContext().build(command);
        // ContextChain.tryFlatten
        List<CommandContext<S>> modifiers = new ArrayList<>();
        CommandContext<S> executable = original;
        while (executable.getChild() != null) {
            modifiers.add(executable);
            executable = executable.getChild();
        }
        if (executable.getCommand() == null) {
            consumer.onCommandComplete(original, false, 0);
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parse.getReader());
        }
        return executeAll(modifiers, executable, original.getSource());
    }

    private int executeAll(List<CommandContext<S>> modifiers, CommandContext<S> executable, S source) throws CommandSyntaxException {
        if (modifiers.isEmpty()) return runExecutable(executable, source, false);
        boolean forkedMode = false;
        List<S> currentSources = Collections.singletonList(source);
        for (CommandContext<S> modifier : modifiers) {
            forkedMode |= modifier.isForked();
            List<S> nextSources = new ArrayList<>();
            for (S sourceToRun : currentSources) {
                try {
                    nextSources.addAll(runModifier(modifier, sourceToRun, forkedMode));
                } catch (CommandSyntaxException ex) {
                    if (!forkedMode) throw ex;
                }
            }
            if (nextSources.isEmpty()) return 0;
            currentSources = nextSources;
        }
        int result = 0;
        for (S executionSource : currentSources) result += runExecutable(executable, executionSource, forkedMode);
        return result;
    }

    private Collection<S> runModifier(CommandContext<S> modifier, S source, boolean forked) throws CommandSyntaxException {
        RedirectModifier<S> m = modifier.getRedirectModifier();
        if (m == null) return Collections.singleton(source);
        CommandContext<S> ctx = modifier.copyFor(source);
        try {
            return m.apply(ctx);
        } catch (CommandSyntaxException ex) {
            consumer.onCommandComplete(ctx, false, 0);
            if (forked) return Collections.emptyList();
            throw ex;
        }
    }

    private int runExecutable(CommandContext<S> executable, S source, boolean forked) throws CommandSyntaxException {
        CommandContext<S> ctx = executable.copyFor(source);
        try {
            int result = executable.getCommand().run(ctx);
            consumer.onCommandComplete(ctx, true, result);
            return forked ? 1 : result;
        } catch (CommandSyntaxException ex) {
            consumer.onCommandComplete(ctx, false, 0);
            if (forked) return 0;
            throw ex;
        }
    }

    public ParseResults<S> parse(String command, S source) { return parse(new StringReader(command), source); }

    public ParseResults<S> parse(StringReader command, S source) {
        CommandContextBuilder<S> context = new CommandContextBuilder<>(this, source, root, command.getCursor());
        return parseNodes(root, command, context);
    }

    private ParseResults<S> parseNodes(CommandNode<S> node, StringReader originalReader, CommandContextBuilder<S> contextSoFar) {
        S source = contextSoFar.getSource();
        Map<CommandNode<S>, CommandSyntaxException> errors = null;
        List<ParseResults<S>> potentials = null;
        int cursor = originalReader.getCursor();
        for (CommandNode<S> child : node.getRelevantNodes(originalReader)) {
            if (!child.canUse(source)) continue;
            CommandContextBuilder<S> context = contextSoFar.copy();
            StringReader reader = new StringReader(originalReader);
            try {
                try {
                    child.parse(reader, context);
                } catch (RuntimeException ex) {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException().createWithContext(reader, ex.getMessage());
                }
                if (reader.canRead() && reader.peek() != ARGUMENT_SEPARATOR_CHAR) {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherExpectedArgumentSeparator().createWithContext(reader);
                }
            } catch (CommandSyntaxException ex) {
                if (errors == null) errors = new LinkedHashMap<>();
                errors.put(child, ex);
                reader.setCursor(cursor);
                continue;
            }
            context.withCommand(child.getCommand());
            if (reader.canRead(child.getRedirect() == null ? 2 : 1)) {
                reader.skip();
                if (child.getRedirect() != null) {
                    CommandContextBuilder<S> childContext = new CommandContextBuilder<>(this, source, child.getRedirect(), reader.getCursor());
                    ParseResults<S> parse = parseNodes(child.getRedirect(), reader, childContext);
                    context.withChild(parse.getContext());
                    return new ParseResults<>(context, parse.getReader(), parse.getExceptions());
                }
                ParseResults<S> parse = parseNodes(child, reader, context);
                if (potentials == null) potentials = new ArrayList<>(1);
                potentials.add(parse);
            } else {
                if (potentials == null) potentials = new ArrayList<>(1);
                potentials.add(new ParseResults<>(context, reader, Collections.emptyMap()));
            }
        }
        if (potentials != null) {
            if (potentials.size() > 1) {
                potentials.sort((a, b) -> {
                    if (!a.getReader().canRead() && b.getReader().canRead()) return -1;
                    if (a.getReader().canRead() && !b.getReader().canRead()) return 1;
                    if (a.getExceptions().isEmpty() && !b.getExceptions().isEmpty()) return -1;
                    if (!a.getExceptions().isEmpty() && b.getExceptions().isEmpty()) return 1;
                    return 0;
                });
            }
            return potentials.get(0);
        }
        return new ParseResults<>(contextSoFar, originalReader, errors == null ? Collections.emptyMap() : errors);
    }

    public CompletableFuture<Suggestions> getCompletionSuggestions(ParseResults<S> parse) {
        return getCompletionSuggestions(parse, parse.getReader().getTotalLength());
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Suggestions> getCompletionSuggestions(ParseResults<S> parse, int cursor) {
        CommandContextBuilder<S> context = parse.getContext();
        SuggestionContext<S> nodeBeforeCursor = context.findSuggestionContext(cursor);
        CommandNode<S> parent = nodeBeforeCursor.parent();
        int start = Math.min(nodeBeforeCursor.startPos(), cursor);
        String fullInput = parse.getReader().getString();
        String truncatedInput = fullInput.substring(0, cursor);
        String truncatedInputLowerCase = truncatedInput.toLowerCase(Locale.ROOT);
        CompletableFuture<Suggestions>[] futures = new CompletableFuture[parent.getChildren().size()];
        int i = 0;
        for (CommandNode<S> node : parent.getChildren()) {
            CompletableFuture<Suggestions> future = Suggestions.empty();
            try {
                future = node.listSuggestions(context.build(truncatedInput), new SuggestionsBuilder(truncatedInput, truncatedInputLowerCase, start));
            } catch (CommandSyntaxException ignored) {
            }
            futures[i++] = future;
        }
        CompletableFuture<Suggestions> result = new CompletableFuture<>();
        CompletableFuture.allOf(futures).thenRun(() -> {
            List<Suggestions> suggestions = new ArrayList<>();
            for (CompletableFuture<Suggestions> f : futures) suggestions.add(f.join());
            result.complete(Suggestions.merge(fullInput, suggestions));
        });
        return result;
    }

    public RootCommandNode<S> getRoot() { return root; }

    public CommandNode<S> findNode(Collection<String> path) {
        CommandNode<S> node = root;
        for (String name : path) {
            node = node.getChild(name);
            if (node == null) return null;
        }
        return node;
    }

    public Map<CommandNode<S>, String> getSmartUsage(CommandNode<S> node, S source) {
        Map<CommandNode<S>, String> result = new LinkedHashMap<>();
        boolean optional = node.getCommand() != null;
        for (CommandNode<S> child : node.getChildren()) {
            String usage = getSmartUsage(child, source, optional, false);
            if (usage != null) result.put(child, usage);
        }
        return result;
    }

    private String getSmartUsage(CommandNode<S> node, S source, boolean optional, boolean deep) {
        if (!node.canUse(source)) return null;
        String self = optional ? USAGE_OPTIONAL_OPEN + node.getUsageText() + USAGE_OPTIONAL_CLOSE : node.getUsageText();
        boolean childOptional = node.getCommand() != null;
        String open = childOptional ? USAGE_OPTIONAL_OPEN : USAGE_REQUIRED_OPEN;
        String close = childOptional ? USAGE_OPTIONAL_CLOSE : USAGE_REQUIRED_CLOSE;
        if (!deep) {
            if (node.getRedirect() != null) {
                String redirect = node.getRedirect() == root ? "..." : "-> " + node.getRedirect().getUsageText();
                return self + ARGUMENT_SEPARATOR + redirect;
            }
            List<CommandNode<S>> children = new ArrayList<>();
            for (CommandNode<S> c : node.getChildren()) if (c.canUse(source)) children.add(c);
            if (children.size() == 1) {
                String usage = getSmartUsage(children.get(0), source, childOptional, childOptional);
                if (usage != null) return self + ARGUMENT_SEPARATOR + usage;
            } else if (children.size() > 1) {
                Set<String> childUsage = new LinkedHashSet<>();
                for (CommandNode<S> child : children) {
                    String usage = getSmartUsage(child, source, childOptional, true);
                    if (usage != null) childUsage.add(usage);
                }
                if (childUsage.size() == 1) {
                    String usage = childUsage.iterator().next();
                    return self + ARGUMENT_SEPARATOR + (childOptional ? USAGE_OPTIONAL_OPEN + usage + USAGE_OPTIONAL_CLOSE : usage);
                } else if (childUsage.size() > 1) {
                    StringBuilder builder = new StringBuilder(open);
                    int count = 0;
                    for (CommandNode<S> child : children) {
                        if (count > 0) builder.append(USAGE_OR);
                        builder.append(child.getUsageText());
                        count++;
                    }
                    if (count > 0) {
                        builder.append(close);
                        return self + ARGUMENT_SEPARATOR + builder;
                    }
                }
            }
        }
        return self;
    }
}
