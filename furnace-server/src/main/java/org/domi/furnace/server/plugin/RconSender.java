// RCON 명령 하나의 발신자(이름 "Rcon", OP — 바닐라 RconConsoleSource 와 같은 권한 레벨 4). 메시지는 그 요청의 응답 버퍼로.
package org.domi.furnace.server.plugin;

import java.util.function.Consumer;
import org.domi.furnace.api.command.RemoteConsoleSender;
import org.domi.furnace.api.permission.PermissionAttachment;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;

final class RconSender implements RemoteConsoleSender {

    private final PermissibleBase perms;
    private final Consumer<Text> out;

    RconSender(PermissionManagerImpl permissions, Consumer<Text> out) {
        this.perms = new PermissibleBase(permissions, () -> true);
        this.out = out;
    }

    @Override
    public String name() { return "Rcon"; }

    @Override
    public void sendMessage(Text message) { out.accept(message); }

    @Override
    public boolean isOp() { return true; }

    @Override
    public boolean hasPermission(String node) {
        Boolean o = perms.manager().overrideHas(this, node);
        return o != null ? o : perms.hasPermission(node);
    }

    @Override
    public boolean isPermissionSet(String node) {
        Boolean o = perms.manager().overrideIsSet(this, node);
        return o != null ? o : perms.isPermissionSet(node);
    }

    @Override
    public PermissionAttachment addAttachment(FurnacePlugin plugin) { return perms.addAttachment(plugin); }
}
