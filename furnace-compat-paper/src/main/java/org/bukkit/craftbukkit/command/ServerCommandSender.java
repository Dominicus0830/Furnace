package org.bukkit.craftbukkit.command;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;

/** Paper 와 같은 자리의 발신자 공통 상위(콘솔·RCON·그 밖 Furnace 발신자 — T-264). */
public abstract class ServerCommandSender implements CommandSender {

    protected ServerCommandSender() {}

    @Override
    public Server getServer() { return org.bukkit.Bukkit.getServer(); }
}
