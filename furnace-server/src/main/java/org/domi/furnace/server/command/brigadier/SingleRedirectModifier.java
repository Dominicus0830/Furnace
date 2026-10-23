package org.domi.furnace.server.command.brigadier;

@FunctionalInterface
public interface SingleRedirectModifier<S> {
    S apply(CommandContext<S> context) throws CommandSyntaxException;
}
