// 바닐라 CommandSourceStack — 실행 주체(콘솔·플레이어), 권한 레벨, 위치, 결과 메시지 전달 규칙.
package org.domi.furnace.server.command;

import java.util.function.Supplier;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.Text;
import org.domi.furnace.server.net.protocol.play.SystemChat;

public final class CommandSource {

    public static final SimpleCommandExceptionType ERROR_NOT_PLAYER = new SimpleCommandExceptionType(Text.translatable("permissions.requires.player"));
    public static final SimpleCommandExceptionType ERROR_NOT_ENTITY = new SimpleCommandExceptionType(Text.translatable("permissions.requires.entity"));

    private final ServerCore server;
    private final Player player;          // null 이면 콘솔
    private final String textName;
    private final Text displayName;
    private final int permissionLevel;
    private final boolean silent;
    private final double x, y, z;
    private final float yRot, xRot;

    private CommandSource(ServerCore server, Player player, String textName, Text displayName, int permissionLevel,
                          boolean silent, double x, double y, double z, float yRot, float xRot) {
        this.server = server;
        this.player = player;
        this.textName = textName;
        this.displayName = displayName;
        this.permissionLevel = permissionLevel;
        this.silent = silent;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yRot = yRot;
        this.xRot = xRot;
    }

    /** 콘솔: 이름 "Server", 레벨 4, 월드 스폰 위치. */
    public static CommandSource console(ServerCore server) {
        var spawn = server.world().defaultSpawn();
        return new CommandSource(server, null, "Server", Text.literal("Server"), 4, false, spawn.x(), spawn.y(), spawn.z(), 0f, 0f);
    }

    /** 플레이어(바닐라 ServerPlayer.createCommandSourceStack). */
    public static CommandSource player(ServerCore server, Player p) {
        Text name = Text.literal(p.name()).insertion(p.name()).click("suggest_command", "/tell " + p.name() + " ");
        return new CommandSource(server, p, p.name(), name, server.profilePermissions(ServerCore.profileOf(p)), false,
                p.x(), p.y(), p.z(), p.yaw(), p.pitch());
    }

    /** 명령 트리 필터용: 접속 직전 플레이어의 이름·권한만 가진 소스(Player 객체가 아직 없을 때). */
    public static CommandSource forProfile(ServerCore server, String name, int permissionLevel) {
        var spawn = server.world().defaultSpawn();
        return new CommandSource(server, null, name, Text.literal(name), permissionLevel, true, spawn.x(), spawn.y(), spawn.z(), 0f, 0f);
    }

    public CommandSource withSilent() {
        return new CommandSource(server, player, textName, displayName, permissionLevel, true, x, y, z, yRot, xRot);
    }

    public CommandSource withPermission(int level) {
        return new CommandSource(server, player, textName, displayName, level, silent, x, y, z, yRot, xRot);
    }

    public ServerCore getServer() { return server; }

    public boolean hasPermission(int level) { return permissionLevel >= level; }

    public int permissionLevel() { return permissionLevel; }

    public String getTextName() { return textName; }

    public Text getDisplayName() { return displayName; }

    public boolean isPlayer() { return player != null; }

    public Player getPlayer() { return player; }

    public Player getPlayerOrException() throws CommandSyntaxException {
        if (player == null) throw ERROR_NOT_PLAYER.create();
        return player;
    }

    public double x() { return x; }

    public double y() { return y; }

    public double z() { return z; }

    public float yRot() { return yRot; }

    public float xRot() { return xRot; }

    private boolean acceptsSuccess() { return player == null || server.gameRule("sendCommandFeedback"); }

    private boolean shouldInformAdmins() { return player != null || server.settings().get().broadcastConsoleToOps; }

    /** 성공 메시지: 자신에게 보내고, allowLogging 이면 다른 OP 와 서버 로그에 "[이름: 메시지]" 로 알린다. */
    public void sendSuccess(Supplier<Text> message, boolean allowLogging) {
        boolean self = acceptsSuccess() && !silent;
        boolean admins = allowLogging && shouldInformAdmins() && !silent;
        if (!self && !admins) return;
        Text t = message.get();
        if (self) sendSystemMessage(t);
        if (admins) broadcastToAdmins(t);
    }

    private void broadcastToAdmins(Text message) {
        Text admin = Text.translatable("chat.type.admin", displayName, message).color("gray").italic(true);
        if (server.gameRule("sendCommandFeedback")) {
            for (Player p : server.players()) {
                if (p != player && server.lists().isOp(ServerCore.profileOf(p))) p.send(new SystemChat(admin, false));
            }
        }
        if (player != null && server.gameRule("logAdminCommands")) server.sendSystemMessage(admin);
    }

    public void sendFailure(Text message) {
        if (!silent) sendSystemMessage(Text.empty().append(message).color("red"));
    }

    public void sendSystemMessage(Text message) {
        if (player != null) player.send(new SystemChat(message, false));
        else server.sendSystemMessage(message);
    }
}
