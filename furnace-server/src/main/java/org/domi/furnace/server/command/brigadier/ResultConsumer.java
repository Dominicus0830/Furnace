package org.domi.furnace.server.command.brigadier;

import org.domi.furnace.server.VanillaPort;

@VanillaPort("com.mojang.brigadier.ResultConsumer")
@FunctionalInterface
public interface ResultConsumer<S> {
    void onCommandComplete(CommandContext<S> context, boolean success, int result);
}
