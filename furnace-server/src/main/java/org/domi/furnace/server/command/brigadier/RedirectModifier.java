package org.domi.furnace.server.command.brigadier;

import java.util.Collection;
import org.domi.furnace.server.VanillaPort;

@VanillaPort("com.mojang.brigadier.RedirectModifier")
@FunctionalInterface
public interface RedirectModifier<S> {
    Collection<S> apply(CommandContext<S> context) throws CommandSyntaxException;
}
