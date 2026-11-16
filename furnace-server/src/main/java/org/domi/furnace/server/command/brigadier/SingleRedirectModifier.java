package org.domi.furnace.server.command.brigadier;

import org.domi.furnace.server.VanillaPort;

@VanillaPort("com.mojang.brigadier.SingleRedirectModifier")
@FunctionalInterface
public interface SingleRedirectModifier<S> {
    S apply(CommandContext<S> context) throws CommandSyntaxException;
}
