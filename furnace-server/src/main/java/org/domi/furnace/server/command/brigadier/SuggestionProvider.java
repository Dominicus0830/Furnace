package org.domi.furnace.server.command.brigadier;

import java.util.concurrent.CompletableFuture;
import org.domi.furnace.server.VanillaPort;

@VanillaPort("com.mojang.brigadier.suggestion.SuggestionProvider")
@FunctionalInterface
public interface SuggestionProvider<S> {
    CompletableFuture<Suggestions> getSuggestions(CommandContext<S> context, SuggestionsBuilder builder) throws CommandSyntaxException;
}
