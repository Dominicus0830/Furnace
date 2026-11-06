// 콘솔(이름 "Server", 모든 권한) — 메시지는 서버 로그로.
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.permission.PermissionAttachment;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;

final class ConsoleSender implements CommandSender {

    private final ServerCore core;
    private final PermissibleBase perms;

    ConsoleSender(ServerCore core, PermissionManagerImpl permissions) {
        this.core = core;
        this.perms = new PermissibleBase(permissions, () -> true);
    }

    PermissibleBase perms() { return perms; }

    @Override
    public String name() { return "Server"; }

    @Override
    public void sendMessage(Text message) { core.sendSystemMessage(message); }

    @Override
    public boolean isOp() { return true; }

    @Override
    public boolean hasPermission(String node) { return perms.hasPermission(node); }

    @Override
    public boolean isPermissionSet(String node) { return perms.isPermissionSet(node); }

    @Override
    public PermissionAttachment addAttachment(FurnacePlugin plugin) { return perms.addAttachment(plugin); }
}
