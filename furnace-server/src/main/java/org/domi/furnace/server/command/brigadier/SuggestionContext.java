package org.domi.furnace.server.command.brigadier;

public record SuggestionContext<S>(CommandNode<S> parent, int startPos) {}
