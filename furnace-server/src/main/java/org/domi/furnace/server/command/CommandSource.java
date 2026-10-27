// 바닐라 CommandSourceStack — 실행 주체(콘솔·플레이어), 권한 레벨, 위치, 결과 메시지 전달 규칙.
package org.domi.furnace.server.command;

import java.util.function.Supplier;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.api.text.Text;

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
    // 서명된 명령 인자(바닐라 CommandSigningContext) — 인자 이름 → 검증된 메시지. 없으면 빈 맵(서명 없음).
    private java.util.Map<String, org.domi.furnace.server.chat.PlayerChatMessage> signedArguments = java.util.Map.of();

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
        return new CommandSource(server, null, "Server", Text.text("Server"), 4, false, spawn.x(), spawn.y(), spawn.z(), 0f, 0f);
    }

    /** 플레이어(바닐라 ServerPlayer.createCommandSourceStack). */
    public static CommandSource player(ServerCore server, Player p) {
        Text name = Text.text(p.name()).insertion(p.name()).clickEvent(new org.domi.furnace.api.text.ClickEvent(org.domi.furnace.api.text.ClickEvent.Action.SUGGEST_COMMAND, "/tell " + p.name() + " "));
        return new CommandSource(server, p, p.name(), name, server.profilePermissions(ServerCore.profileOf(p)), false,
                p.x(), p.y(), p.z(), p.yaw(), p.pitch());
    }

    /** 표지판 클릭 명령(SignBlockEntity.createCommandSourceStack): 권한 2, 칸 가운데, 회전 0, 이름은 플레이어(없으면 "Sign"). */
    public static CommandSource sign(ServerCore server, Player p, int x, int y, int z) {
        String name = p == null ? "Sign" : p.name();
        Text display = p == null ? Text.text("Sign") : Text.text(p.name()).insertion(p.name()).clickEvent(
                new org.domi.furnace.api.text.ClickEvent(org.domi.furnace.api.text.ClickEvent.Action.SUGGEST_COMMAND, "/tell " + p.name() + " "));
        return new CommandSource(server, p, name, display, 2, false, x + 0.5, y + 0.5, z + 0.5, 0f, 0f);
    }

    /** 명령 트리 필터용: 접속 직전 플레이어의 이름·권한만 가진 소스(Player 객체가 아직 없을 때). */
    public static CommandSource forProfile(ServerCore server, String name, int permissionLevel) {
        var spawn = server.world().defaultSpawn();
        return new CommandSource(server, null, name, Text.text(name), permissionLevel, true, spawn.x(), spawn.y(), spawn.z(), 0f, 0f);
    }

    public CommandSource withSignedArguments(java.util.Map<String, org.domi.furnace.server.chat.PlayerChatMessage> args) {
        CommandSource c = new CommandSource(server, player, textName, displayName, permissionLevel, silent, x, y, z, yRot, xRot);
        c.signedArguments = java.util.Map.copyOf(args);
        return c;
    }

    public org.domi.furnace.server.chat.PlayerChatMessage signedArgument(String name) { return signedArguments.get(name); }

    /** 바닐라 CommandSourceStack.sendChatMessage — 플레이어면 채팅 패킷, 콘솔이면 장식한 문장을 로그로. */
    public void sendChatMessage(org.domi.furnace.server.chat.PlayerChatMessage message, boolean filtered,
                                org.domi.furnace.server.chat.ChatType.Bound bound) {
        if (silent) return;
        if (player != null) server.sendChatMessage(player, message, filtered, bound);
        else server.sendSystemMessage(bound.decorate(message.decoratedContent()));
    }

    /** CommandSourceStack.getLevel: 플레이어면 그 플레이어가 있는 차원, 콘솔이면 오버월드. */
    public org.domi.furnace.server.world.World level() {
        if (player != null) {
            var w = server.world(player.dimension());
            if (w != null) return w;
        }
        return server.world();
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

    /** 바닐라 getEntityOrException: 실행 주체 엔티티(지금은 플레이어뿐), 콘솔이면 "permissions.requires.entity". */
    public org.domi.furnace.server.entity.Rider getEntityOrException() throws CommandSyntaxException {
        if (player == null) throw ERROR_NOT_ENTITY.create();
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
        Text admin = Text.translatable("chat.type.admin", displayName, message).color(org.domi.furnace.api.text.NamedTextColor.GRAY).decoration(org.domi.furnace.api.text.TextDecoration.ITALIC, true);
        if (server.gameRule("sendCommandFeedback")) {
            for (Player p : server.players()) {
                if (p != player && server.lists().isOp(ServerCore.profileOf(p))) p.sendSystemMessage(admin, false);
            }
        }
        if (player != null && server.gameRule("logAdminCommands")) server.sendSystemMessage(admin);
    }

    public void sendFailure(Text message) {
        if (!silent) sendSystemMessage(Text.empty().append(message).color(org.domi.furnace.api.text.NamedTextColor.RED));
    }

    public void sendSystemMessage(Text message) {
        if (player != null) player.sendSystemMessage(message, false);
        else server.sendSystemMessage(message);
    }
}
