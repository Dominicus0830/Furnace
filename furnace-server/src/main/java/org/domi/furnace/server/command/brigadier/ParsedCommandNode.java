package org.domi.furnace.server.command.brigadier;

import org.domi.furnace.server.VanillaPort;

@VanillaPort("com.mojang.brigadier.context.ParsedCommandNode")
public record ParsedCommandNode<S>(CommandNode<S> node, StringRange range) {

    public CommandNode<S> getNode() { return node; }

    public StringRange getRange() { return range; }
}
