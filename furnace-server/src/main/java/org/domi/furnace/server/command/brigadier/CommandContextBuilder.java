package org.domi.furnace.server.command.brigadier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CommandContextBuilder<S> {

    private final Map<String, ParsedArgument<S, ?>> arguments = new LinkedHashMap<>();
    private final CommandNode<S> rootNode;
    private final List<ParsedCommandNode<S>> nodes = new ArrayList<>();
    private final CommandDispatcher<S> dispatcher;
    private S source;
    private Command<S> command;
    private CommandContextBuilder<S> child;
    private StringRange range;
    private RedirectModifier<S> modifier;
    private boolean forks;

    public CommandContextBuilder(CommandDispatcher<S> dispatcher, S source, CommandNode<S> rootNode, int start) {
        this.rootNode = rootNode;
        this.dispatcher = dispatcher;
        this.source = source;
        this.range = StringRange.at(start);
    }

    public CommandContextBuilder<S> withSource(S source) {
        this.source = source;
        return this;
    }

    public S getSource() { return source; }

    public CommandNode<S> getRootNode() { return rootNode; }

    public CommandContextBuilder<S> withArgument(String name, ParsedArgument<S, ?> argument) {
        arguments.put(name, argument);
        return this;
    }

    public Map<String, ParsedArgument<S, ?>> getArguments() { return arguments; }

    public CommandContextBuilder<S> withCommand(Command<S> command) {
        this.command = command;
        return this;
    }

    public CommandContextBuilder<S> withNode(CommandNode<S> node, StringRange range) {
        nodes.add(new ParsedCommandNode<>(node, range));
        this.range = StringRange.encompassing(this.range, range);
        this.modifier = node.getRedirectModifier();
        this.forks = node.isFork();
        return this;
    }

    public CommandContextBuilder<S> copy() {
        CommandContextBuilder<S> copy = new CommandContextBuilder<>(dispatcher, source, rootNode, range.getStart());
        copy.command = command;
        copy.arguments.putAll(arguments);
        copy.nodes.addAll(nodes);
        copy.child = child;
        copy.range = range;
        copy.forks = forks;
        copy.modifier = modifier;
        return copy;
    }

    public CommandContextBuilder<S> withChild(CommandContextBuilder<S> child) {
        this.child = child;
        return this;
    }

    public CommandContextBuilder<S> getChild() { return child; }

    public CommandContextBuilder<S> getLastChild() {
        CommandContextBuilder<S> result = this;
        while (result.getChild() != null) result = result.getChild();
        return result;
    }

    public Command<S> getCommand() { return command; }

    public List<ParsedCommandNode<S>> getNodes() { return nodes; }

    public CommandContext<S> build(String input) {
        return new CommandContext<>(source, input, arguments, command, rootNode, nodes, range,
                child == null ? null : child.build(input), modifier, forks);
    }

    public CommandDispatcher<S> getDispatcher() { return dispatcher; }

    public StringRange getRange() { return range; }

    public SuggestionContext<S> findSuggestionContext(int cursor) {
        if (range.getStart() <= cursor) {
            if (range.getEnd() < cursor) {
                if (child != null) return child.findSuggestionContext(cursor);
                if (!nodes.isEmpty()) {
                    ParsedCommandNode<S> last = nodes.get(nodes.size() - 1);
                    return new SuggestionContext<>(last.getNode(), last.getRange().getEnd() + 1);
                }
                return new SuggestionContext<>(rootNode, range.getStart());
            }
            CommandNode<S> prev = rootNode;
            for (ParsedCommandNode<S> node : nodes) {
                StringRange nodeRange = node.getRange();
                if (nodeRange.getStart() <= cursor && cursor <= nodeRange.getEnd()) return new SuggestionContext<>(prev, nodeRange.getStart());
                prev = node.getNode();
            }
            if (prev == null) throw new IllegalStateException("Can't find node before cursor");
            return new SuggestionContext<>(prev, range.getStart());
        }
        throw new IllegalStateException("Can't find node before cursor");
    }
}
