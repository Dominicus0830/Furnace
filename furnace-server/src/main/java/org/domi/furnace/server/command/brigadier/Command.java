package org.domi.furnace.server.command.brigadier;

import org.domi.furnace.server.VanillaPort;

@VanillaPort("com.mojang.brigadier.Command")
@FunctionalInterface
public interface Command<S> {
    int SINGLE_SUCCESS = 1;

    int run(CommandContext<S> context) throws CommandSyntaxException;
}
