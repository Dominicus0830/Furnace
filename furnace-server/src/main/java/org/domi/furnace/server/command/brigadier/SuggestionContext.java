package org.domi.furnace.server.command.brigadier;

import org.domi.furnace.server.VanillaPort;

@VanillaPort("com.mojang.brigadier.suggestion.SuggestionContext")
public record SuggestionContext<S>(CommandNode<S> parent, int startPos) {}
