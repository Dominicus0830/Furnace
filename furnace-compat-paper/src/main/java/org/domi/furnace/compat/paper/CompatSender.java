package org.domi.furnace.compat.paper;


/** 플레이어·콘솔·RCON 이 아닌 Furnace 발신자(명령 블록 등). Paper 처럼 {@link org.bukkit.craftbukkit.command.ServerCommandSender} 아래. */
class CompatSender extends org.bukkit.craftbukkit.command.ServerCommandSender {

    private final org.domi.furnace.api.command.CommandSender handle;

    CompatSender(org.domi.furnace.api.command.CommandSender handle) { this.handle = handle; }

    org.domi.furnace.api.command.CommandSender handle() { return handle; }

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
    public boolean equals(Object o) { return o instanceof CompatSender s && s.handle == handle; }

    @Override
    public int hashCode() { return System.identityHashCode(handle); }
}
