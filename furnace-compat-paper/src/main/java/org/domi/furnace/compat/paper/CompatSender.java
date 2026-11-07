package org.domi.furnace.compat.paper;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;

/** 플레이어·콘솔이 아닌 Furnace 발신자(명령 블록 등). */
final class CompatSender implements CommandSender {

    private final org.domi.furnace.api.command.CommandSender handle;

    CompatSender(org.domi.furnace.api.command.CommandSender handle) { this.handle = handle; }

    org.domi.furnace.api.command.CommandSender handle() { return handle; }

    @Override
    public void sendMessage(String message) { handle.sendMessage(Compat.text(message)); }

    @Override
    public void sendMessage(String... messages) {
        for (String m : messages) sendMessage(m);
    }

    @Override
    public Server getServer() { return org.bukkit.Bukkit.getServer(); }

    @Override
    public String getName() { return handle.name(); }

    @Override
    public boolean isOp() { return handle.isOp(); }

    @Override
    public void setOp(boolean value) { throw new UnsupportedOperationException("이 발신자의 OP 는 바꿀 수 없다"); }

    @Override
    public boolean isPermissionSet(String name) { return handle.isPermissionSet(name); }

    @Override
    public boolean hasPermission(String name) { return handle.hasPermission(name); }

    @Override
    public boolean equals(Object o) { return o instanceof CompatSender s && s.handle == handle; }

    @Override
    public int hashCode() { return System.identityHashCode(handle); }
}
