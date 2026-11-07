package org.bukkit.command;

import org.bukkit.Server;
import org.bukkit.permissions.Permissible;

public interface CommandSender extends Permissible {

    void sendMessage(String message);

    void sendMessage(String... messages);

    Server getServer();

    String getName();
}
