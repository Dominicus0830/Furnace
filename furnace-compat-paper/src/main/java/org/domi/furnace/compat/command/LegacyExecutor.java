// Bukkit CommandExecutor.
package org.domi.furnace.compat.command;

import org.domi.furnace.api.command.CommandSender;

@FunctionalInterface
public interface LegacyExecutor {

    /** @return false 면 사용법(usage)을 보낸다. */
    boolean onCommand(CommandSender sender, LegacyCommand command, String label, String[] args);
}
