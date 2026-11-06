package org.domi.furnace.server.command.brigadier;

import java.util.Collection;

@FunctionalInterface
public interface RedirectModifier<S> {
    Collection<S> apply(CommandContext<S> context) throws CommandSyntaxException;
}
