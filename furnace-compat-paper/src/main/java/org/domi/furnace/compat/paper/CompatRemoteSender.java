package org.domi.furnace.compat.paper;

import org.bukkit.command.RemoteConsoleCommandSender;

/** RCON 발신자(Furnace {@link org.domi.furnace.api.command.RemoteConsoleSender}) 위의 Bukkit 발신자. */
final class CompatRemoteSender extends CompatSender implements RemoteConsoleCommandSender {

    CompatRemoteSender(org.domi.furnace.api.command.RemoteConsoleSender handle) { super(handle); }
}
