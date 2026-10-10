// Brigadier CommandContext — 파싱된 인자·노드·소스·리다이렉트 사슬.
package org.domi.furnace.server.command.brigadier;

import java.util.List;
import java.util.Map;

public class CommandContext<S> {

    private final S source;
    private final String input;
    private final Command<S> command;
    private final Map<String, ParsedArgument<S, ?>> arguments;
    private final CommandNode<S> rootNode;
    private final List<ParsedCommandNode<S>> nodes;
    private final StringRange range;
    private final CommandContext<S> child;
    private final RedirectModifier<S> modifier;
    private final boolean forks;

    public CommandContext(S source, String input, Map<String, ParsedArgument<S, ?>> arguments, Command<S> command,
                          CommandNode<S> rootNode, List<ParsedCommandNode<S>> nodes, StringRange range, CommandContext<S> child,
                          RedirectModifier<S> modifier, boolean forks) {
        this.source = source;
        this.input = input;
        this.arguments = arguments;
        this.command = command;
        this.rootNode = rootNode;
        this.nodes = nodes;
        this.range = range;
        this.child = child;
        this.modifier = modifier;
        this.forks = forks;
    }

    public CommandContext<S> copyFor(S source) {
        if (this.source == source) return this;
        return new CommandContext<>(source, input, arguments, command, rootNode, nodes, range, child, modifier, forks);
    }

    public CommandContext<S> getChild() { return child; }

    public CommandContext<S> getLastChild() {
        CommandContext<S> result = this;
        while (result.getChild() != null) result = result.getChild();
        return result;
    }

    public Command<S> getCommand() { return command; }

    public S getSource() { return source; }

    @SuppressWarnings("unchecked")
    public <V> V getArgument(String name, Class<V> clazz) {
        ParsedArgument<S, ?> argument = arguments.get(name);
        if (argument == null) throw new IllegalArgumentException("No such argument '" + name + "' exists on this command");
        Object result = argument.getResult();
        if (clazz.isAssignableFrom(result.getClass()) || boxed(clazz).isAssignableFrom(result.getClass())) return (V) result;
        throw new IllegalArgumentException("Argument '" + name + "' is defined as " + result.getClass().getSimpleName() + ", not " + clazz);
    }

    private static Class<?> boxed(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        if (c == boolean.class) return Boolean.class;
        return c;
    }

    public boolean hasArgument(String name) { return arguments.containsKey(name); }

    public RedirectModifier<S> getRedirectModifier() { return modifier; }

    public StringRange getRange() { return range; }

    public String getInput() { return input; }

    public CommandNode<S> getRootNode() { return rootNode; }

    public List<ParsedCommandNode<S>> getNodes() { return nodes; }

    public boolean hasNodes() { return !nodes.isEmpty(); }

    public boolean isForked() { return forks; }
}
