package org.domi.furnace.server.command.brigadier;

public record ParsedCommandNode<S>(CommandNode<S> node, StringRange range) {

    public CommandNode<S> getNode() { return node; }

    public StringRange getRange() { return range; }
}
