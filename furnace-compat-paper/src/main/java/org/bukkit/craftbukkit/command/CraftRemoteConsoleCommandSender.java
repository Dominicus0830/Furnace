package org.bukkit.craftbukkit.command;

import org.bukkit.command.RemoteConsoleCommandSender;
import org.domi.furnace.compat.paper.Compat;

/** RCON 발신자(Furnace {@link org.domi.furnace.api.command.RemoteConsoleSender}) 위의 Bukkit 발신자(Paper 와 같은 이름 — T-264). */
public class CraftRemoteConsoleCommandSender extends ServerCommandSender implements RemoteConsoleCommandSender {

    private final org.domi.furnace.api.command.RemoteConsoleSender handle;

    public CraftRemoteConsoleCommandSender(org.domi.furnace.api.command.RemoteConsoleSender handle) { this.handle = handle; }

    /** Furnace 발신자(호환 계층이 명령을 넘길 때 쓴다). */
    public org.domi.furnace.api.command.RemoteConsoleSender furnaceHandle() { return handle; }

    @Override
    public void sendMessage(String message) { handle.sendMessage(Compat.message(message)); }

    @Override
    public void sendMessage(String... messages) {
        for (String m : messages) sendMessage(m);
    }

    @Override
    public String getName() { return handle.name(); }

    @Override
    public boolean isOp() { return handle.isOp(); }

    @Override
    public void setOp(boolean value) { throw new UnsupportedOperationException("the operator status of this sender cannot be changed"); }

    @Override
    public boolean isPermissionSet(String name) { return handle.isPermissionSet(name); }

    @Override
    public boolean hasPermission(String name) { return handle.hasPermission(name); }

    @Override
    public boolean equals(Object o) { return o instanceof CraftRemoteConsoleCommandSender s && s.handle == handle; }

    @Override
    public int hashCode() { return System.identityHashCode(handle); }
}
