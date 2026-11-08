package org.domi.furnace.compat.paper;

import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;

/** 서버 콘솔. 모든 권한을 가진다(Bukkit 과 같다). */
public final class CompatConsole implements ConsoleCommandSender {

    public static final CompatConsole INSTANCE = new CompatConsole();

    private CompatConsole() {}

    @Override
    public void sendMessage(String message) { Compat.furnace().console().sendMessage(Compat.text(message)); }

    @Override
    public void sendMessage(String... messages) {
        for (String m : messages) sendMessage(m);
    }

    @Override
    public Server getServer() { return org.bukkit.Bukkit.getServer(); }

    @Override
    public String getName() { return "CONSOLE"; }

    @Override
    public boolean isOp() { return true; }

    @Override
    public void setOp(boolean value) { throw new UnsupportedOperationException("콘솔의 OP 는 바꿀 수 없다"); }

    @Override
    public boolean isPermissionSet(String name) { return Compat.furnace().console().isPermissionSet(name); }

    @Override
    public boolean hasPermission(String name) { return Compat.furnace().console().hasPermission(name); }
}
