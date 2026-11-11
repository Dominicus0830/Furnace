// 바닐라 CommandSourceStack — 실행 주체(콘솔·플레이어·엔티티), 출력 대상, 권한 레벨, 위치·회전·레벨·앵커, 결과 콜백, 결과 메시지 전달 규칙.
// 불변: 변형 메서드는 새 객체를 돌려주고, 바뀌는 것이 없으면 this 를 돌려준다(바닐라와 같이).
package org.domi.furnace.server.command;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.arguments.EntityAnchorArgument;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.exec.ExecutionContext;
import org.domi.furnace.server.command.exec.ResultCallback;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.world.World;
import org.domi.furnace.api.text.Text;

public final class CommandSource {

    public static final SimpleCommandExceptionType ERROR_NOT_PLAYER = new SimpleCommandExceptionType(Text.translatable("permissions.requires.player"));
    public static final SimpleCommandExceptionType ERROR_NOT_ENTITY = new SimpleCommandExceptionType(Text.translatable("permissions.requires.entity"));

    private final ServerCore server;
    /** 메시지 출력 대상(바닐라 CommandSource source 필드). null 이면 콘솔. execute as 로 바뀌지 않는다. */
    private final Player output;
    /** 실행 주체 엔티티(바닐라 entity 필드). 콘솔이면 null. */
    private final Rider entity;
    private final World level;
    private final String textName;
    private final Text displayName;
    private final int permissionLevel;
    private final boolean silent;
    private final double x, y, z;
    private final float yRot, xRot;
    private final EntityAnchorArgument.Anchor anchor;
    private final ResultCallback callback;
    /** 실행 중인 컨텍스트(바닐라는 ThreadLocal). 실행 코어가 명령 본문 직전에 묶고, 포크 복사에 그대로 전달된다. */
    private final ExecutionContext execution;
    // 서명된 명령 인자(바닐라 CommandSigningContext) — 인자 이름 → 검증된 메시지. 없으면 빈 맵(서명 없음).
    private final java.util.Map<String, org.domi.furnace.server.chat.PlayerChatMessage> signedArguments;
    /**
     * 추적 출력(/debug function 의 TracerSource): 있으면 메시지가 출력 대상 대신 여기로 간다. 바닐라 추적 소스처럼 성공·실패 메시지를 늘 받고
     * (sendCommandFeedback 무시, withSilent 로 숨겨지지 않는다) 다른 관리자에게 알리지 않는다.
     */
    private final java.util.function.Consumer<Text> traceOutput;
    /**
     * 명령 블록·명령 블록 수레(바닐라 BaseCommandBlock 이 CommandSource 인 소스): 있으면 성공/실패/관리자 알림 여부와 메시지가 이쪽 규칙을 따른다.
     * 이 필드가 있어도 output 은 null(플레이어 아님)이다.
     */
    private final org.domi.furnace.server.block.BlockCommandSink sink;

    private CommandSource(ServerCore server, Player output, Rider entity, World level, String textName, Text displayName, int permissionLevel,
                          boolean silent, double x, double y, double z, float yRot, float xRot, EntityAnchorArgument.Anchor anchor,
                          ResultCallback callback, ExecutionContext execution,
                          java.util.Map<String, org.domi.furnace.server.chat.PlayerChatMessage> signedArguments,
                          java.util.function.Consumer<Text> traceOutput, org.domi.furnace.server.block.BlockCommandSink sink) {
        this.server = server;
        this.output = output;
        this.entity = entity;
        this.level = level;
        this.textName = textName;
        this.displayName = displayName;
        this.permissionLevel = permissionLevel;
        this.silent = silent;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yRot = yRot;
        this.xRot = xRot;
        this.anchor = anchor;
        this.callback = callback;
        this.execution = execution;
        this.signedArguments = signedArguments;
        this.traceOutput = traceOutput;
        this.sink = sink;
    }

    private static CommandSource create(ServerCore server, Player p, String name, Text display, int level, boolean silent,
                                        double x, double y, double z, float yRot, float xRot) {
        World w = server.world();
        if (p != null) {
            World pw = server.world(p.dimension());
            if (pw != null) w = pw;
        }
        return new CommandSource(server, p, p, w, name, display, level, silent, x, y, z, yRot, xRot, EntityAnchorArgument.Anchor.FEET,
                ResultCallback.EMPTY, null, java.util.Map.of(), null, null);
    }

    /** 콘솔: 이름 "Server", 레벨 4, 월드 스폰 위치. */
    public static CommandSource console(ServerCore server) {
        var spawn = server.world().defaultSpawn();
        return create(server, null, "Server", Text.text("Server"), 4, false, spawn.x(), spawn.y(), spawn.z(), 0f, 0f);
    }

    /** RCON 소스(바닐라 RconConsoleSource.createCommandSourceStack): 이름 "Rcon", 권한 4, 월드 스폰 위치, 출력은 sink 로. */
    public static CommandSource rcon(ServerCore server, org.domi.furnace.server.block.BlockCommandSink sink) {
        return new CommandSource(server, null, null, server.world(), sink.textName(), sink.displayName(), 4, false, sink.x(), sink.y(), sink.z(),
                0f, 0f, EntityAnchorArgument.Anchor.FEET, ResultCallback.EMPTY, null, java.util.Map.of(), null, sink);
    }

    /** 플레이어(바닐라 ServerPlayer.createCommandSourceStack): 표시 이름은 getDisplayName(팀 서식·클릭·호버·삽입). */
    public static CommandSource player(ServerCore server, Player p) {
        return create(server, p, p.name(), p.displayName(), server.profilePermissions(ServerCore.profileOf(p)), false,
                p.x(), p.y(), p.z(), p.yaw(), p.pitch());
    }

    /** 표지판 클릭 명령(SignBlockEntity.createCommandSourceStack): 권한 2, 칸 가운데, 회전 0, 이름은 플레이어(없으면 "Sign"). */
    public static CommandSource sign(ServerCore server, Player p, int x, int y, int z) {
        String name = p == null ? "Sign" : p.name();
        Text display = p == null ? Text.text("Sign") : p.displayName();
        return create(server, p, name, display, 2, false, x + 0.5, y + 0.5, z + 0.5, 0f, 0f);
    }

    /** 표지판이 레벨에서 글을 읽을 때의 소스(createCommandSourceStack(null, level, pos)): 플레이어 없음, 그 표지판의 월드. */
    public static CommandSource sign(ServerCore server, World w, int x, int y, int z) {
        return new CommandSource(server, null, null, w, "Sign", Text.text("Sign"), 2, false, x + 0.5, y + 0.5, z + 0.5, 0f, 0f,
                EntityAnchorArgument.Anchor.FEET, ResultCallback.EMPTY, null, java.util.Map.of(), null, null);
    }

    /** 독서대가 책을 풀 때의 소스(LecternBlockEntity.createCommandSourceStack(null)): 플레이어 없음, 이름 "Lectern", 권한 2, 칸 가운데. */
    public static CommandSource lectern(ServerCore server, World w, int x, int y, int z) {
        return new CommandSource(server, null, null, w, "Lectern", Text.text("Lectern"), 2, false, x + 0.5, y + 0.5, z + 0.5, 0f, 0f,
                EntityAnchorArgument.Anchor.FEET, ResultCallback.EMPTY, null, java.util.Map.of(), null, null);
    }

    /**
     * 명령 블록·명령 블록 수레의 소스(BaseCommandBlock.createCommandSourceStack): 권한 2, 위치·회전·이름·엔티티는 sink 에서,
     * 출력은 sink(마지막 출력 기록). w 는 그 블록이 있는 차원의 월드.
     */
    public static CommandSource block(ServerCore server, World w, org.domi.furnace.server.block.BlockCommandSink sink) {
        return new CommandSource(server, null, sink.entity(), w, sink.textName(), sink.displayName(), 2, false, sink.x(), sink.y(), sink.z(),
                sink.yRot(), sink.xRot(), EntityAnchorArgument.Anchor.FEET, ResultCallback.EMPTY, null, java.util.Map.of(), null, sink);
    }

    /** 명령 트리 필터용: 접속 직전 플레이어의 이름·권한만 가진 소스(Player 객체가 아직 없을 때). */
    public static CommandSource forProfile(ServerCore server, String name, int permissionLevel) {
        var spawn = server.world().defaultSpawn();
        return create(server, null, name, Text.text(name), permissionLevel, true, spawn.x(), spawn.y(), spawn.z(), 0f, 0f);
    }

    public CommandSource withSignedArguments(java.util.Map<String, org.domi.furnace.server.chat.PlayerChatMessage> args) {
        return new CommandSource(server, output, entity, level, textName, displayName, permissionLevel, silent, x, y, z, yRot, xRot, anchor,
                callback, execution, java.util.Map.copyOf(args), traceOutput, sink);
    }

    public org.domi.furnace.server.chat.PlayerChatMessage signedArgument(String name) { return signedArguments.get(name); }

    /** 바닐라 CommandSourceStack.sendChatMessage — 플레이어면 채팅 패킷, 콘솔이면 장식한 문장을 로그로. */
    public void sendChatMessage(org.domi.furnace.server.chat.PlayerChatMessage message, boolean filtered,
                                org.domi.furnace.server.chat.ChatType.Bound bound) {
        if (silent) return;
        if (output != null) server.sendChatMessage(output, message, filtered, bound);
        else server.sendSystemMessage(bound.decorate(message.decoratedContent()));
    }

    /** CommandSourceStack.getLevel. */
    public World level() { return level; }

    // ---------------------------------------------------------------- 변형(바뀌는 것이 없으면 this)

    /** 바닐라 withEntity: 이름·표시명도 엔티티 것으로. 위치·회전은 그대로(execute as). */
    public CommandSource withEntity(Rider e) {
        if (entity == e) return this;
        String name = e == null ? textName : e instanceof Player p ? p.name() : e.displayName().getString();
        Text display = e == null ? displayName : e.displayName();
        return new CommandSource(server, output, e, level, name, display, permissionLevel, silent, x, y, z, yRot, xRot, anchor, callback,
                execution, signedArguments, traceOutput, sink);
    }

    public CommandSource withPosition(double nx, double ny, double nz) {
        if (nx == x && ny == y && nz == z) return this;
        return new CommandSource(server, output, entity, level, textName, displayName, permissionLevel, silent, nx, ny, nz, yRot, xRot, anchor,
                callback, execution, signedArguments, traceOutput, sink);
    }

    public CommandSource withRotation(float nyRot, float nxRot) {
        if (nyRot == yRot && nxRot == xRot) return this;
        return new CommandSource(server, output, entity, level, textName, displayName, permissionLevel, silent, x, y, z, nyRot, nxRot, anchor,
                callback, execution, signedArguments, traceOutput, sink);
    }

    public CommandSource withCallback(ResultCallback cb) {
        if (Objects.equals(callback, cb)) return this;
        return new CommandSource(server, output, entity, level, textName, displayName, permissionLevel, silent, x, y, z, yRot, xRot, anchor,
                cb, execution, signedArguments, traceOutput, sink);
    }

    public CommandSource withCallback(ResultCallback cb, BinaryOperator<ResultCallback> combiner) {
        return withCallback(combiner.apply(callback, cb));
    }

    /** 바닐라 clearCallbacks. */
    public CommandSource clearCallbacks() { return withCallback(ResultCallback.EMPTY); }

    public ResultCallback callback() { return callback; }

    public CommandSource withSilent() {
        if (silent || traceOutput != null) return this;   // 바닐라 withSuppressedOutput: alwaysAccepts 인 출력(추적 소스)은 숨기지 않는다
        return new CommandSource(server, output, entity, level, textName, displayName, permissionLevel, true, x, y, z, yRot, xRot, anchor,
                callback, execution, signedArguments, traceOutput, sink);
    }

    public CommandSource withPermission(int lvl) {
        if (lvl == permissionLevel) return this;
        return new CommandSource(server, output, entity, level, textName, displayName, lvl, silent, x, y, z, yRot, xRot, anchor, callback,
                execution, signedArguments, traceOutput, sink);
    }

    /** 바닐라 withMaximumPermission: 올리기만 한다. */
    public CommandSource withMaximumPermission(int lvl) { return lvl <= permissionLevel ? this : withPermission(lvl); }

    public CommandSource withAnchor(EntityAnchorArgument.Anchor a) {
        if (a == anchor) return this;
        return new CommandSource(server, output, entity, level, textName, displayName, permissionLevel, silent, x, y, z, yRot, xRot, a,
                callback, execution, signedArguments, traceOutput, sink);
    }

    public EntityAnchorArgument.Anchor getAnchor() { return anchor; }

    /** 바닐라 withLevel: 레벨이 바뀌면 좌표를 coordinateScale 비율(from/to)로 바꾼다(네더 8). */
    public CommandSource withLevel(World w) {
        if (w == level) return this;
        double scale = org.domi.furnace.server.world.DimensionType.teleportationScale(level.dimensionType(), w.dimensionType());
        return new CommandSource(server, output, entity, w, textName, displayName, permissionLevel, silent, x * scale, y, z * scale, yRot, xRot,
                anchor, callback, execution, signedArguments, traceOutput, sink);
    }

    /** 바닐라 EntityAnchorArgument.Anchor.apply(source): 엔티티가 있으면 눈 높이를 더한다. */
    public double[] anchorPosition() {
        if (anchor == EntityAnchorArgument.Anchor.EYES && entity != null) return new double[] {x, y + (entity.eyeY() - entity.y()), z};
        return new double[] {x, y, z};
    }

    /** 바닐라 facing(Vec3): 앵커 위치에서 그 점을 보는 회전. */
    public CommandSource facing(double tx, double ty, double tz) {
        double[] from = anchorPosition();
        double dx = tx - from[0], dy = ty - from[1], dz = tz - from[2];
        double dxz = Math.sqrt(dx * dx + dz * dz);
        float nxRot = wrapDegrees((float) (-(Math.atan2(dy, dxz) * 180.0F / (float) Math.PI)));
        float nyRot = wrapDegrees((float) (Math.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F);
        return withRotation(nyRot, nxRot);
    }

    private static float wrapDegrees(float v) {
        float f = v % 360.0F;
        if (f >= 180.0F) f -= 360.0F;
        if (f < -180.0F) f += 360.0F;
        return f;
    }

    // ---------------------------------------------------------------- 실행 코어

    /** 실행 코어 전용: 명령 본문을 돌리기 직전에 지금 컨텍스트를 묶는다. */
    public CommandSource withExecutionContext(ExecutionContext ctx) {
        if (ctx == execution) return this;
        return new CommandSource(server, output, entity, level, textName, displayName, permissionLevel, silent, x, y, z, yRot, xRot, anchor,
                callback, ctx, signedArguments, traceOutput, sink);
    }

    public ExecutionContext executionContext() { return execution; }

    /**
     * 지연 결과: 명령 결과가 region 에서 나중에 정해질 때 그 future 를 맡긴다. 컨텍스트는 그 완료까지 멈췄다가 전역 스레드에서 이어 간다.
     * 명령 실행 하나에 한 번만. 실행 컨텍스트 밖(직접 dispatcher.execute)에서는 쓸 수 없다.
     */
    public void defer(CompletableFuture<Integer> future) {
        if (execution == null) throw new IllegalStateException("실행 컨텍스트 밖에서 defer — Commands.performCommand 로 실행해야 한다");
        execution.defer(future);
    }

    /** 바닐라 handleError: forked 가 아니면 사용자에게 빨간 메시지. */
    public void handleError(CommandSyntaxException e, boolean forked) {
        handleError(e, forked, null);
    }

    /** 바닐라 handleError(…, tracer): 추적 중이면 오류 메시지의 평문을 먼저 추적기에 알린다. */
    public void handleError(CommandSyntaxException e, boolean forked, org.domi.furnace.server.command.exec.TraceCallbacks tracer) {
        if (tracer != null) tracer.onError(e.getRawMessage().plainText(java.util.Locale.US));
        if (!forked) sendFailure(e.getRawMessage());
    }

    /** 바닐라 withSource(TracerSource): 메시지를 out 으로 보내는 소스. 이름·권한·위치 등은 그대로. */
    public CommandSource withTraceOutput(java.util.function.Consumer<Text> out) {
        return new CommandSource(server, output, entity, level, textName, displayName, permissionLevel, silent, x, y, z, yRot, xRot, anchor,
                callback, execution, signedArguments, out, sink);
    }

    // ---------------------------------------------------------------- 읽기

    public ServerCore getServer() { return server; }

    public boolean hasPermission(int lvl) { return permissionLevel >= lvl; }

    public int permissionLevel() { return permissionLevel; }

    public String getTextName() { return textName; }

    public Text getDisplayName() { return displayName; }

    /** 바닐라 isPlayer: 실행 주체가 플레이어인가. */
    public boolean isPlayer() { return entity instanceof Player; }

    /** 바닐라 getPlayer: 실행 주체가 플레이어면 그 플레이어. */
    public Player getPlayer() { return entity instanceof Player p ? p : null; }

    public Player getPlayerOrException() throws CommandSyntaxException {
        if (!(entity instanceof Player p)) throw ERROR_NOT_PLAYER.create();
        return p;
    }

    /** 바닐라 getEntity. */
    public Rider entity() { return entity; }

    /** 바닐라 getEntityOrException: 실행 주체 엔티티, 없으면 "permissions.requires.entity". */
    public Rider getEntityOrException() throws CommandSyntaxException {
        if (entity == null) throw ERROR_NOT_ENTITY.create();
        return entity;
    }

    public boolean isSilent() { return silent; }

    public double x() { return x; }

    public double y() { return y; }

    public double z() { return z; }

    public float yRot() { return yRot; }

    public float xRot() { return xRot; }

    private boolean acceptsSuccess() {
        if (traceOutput != null) return true;
        if (sink != null) return sink.acceptsSuccess();
        return output == null || server.gameRule("sendCommandFeedback");
    }

    private boolean shouldInformAdmins() {
        if (traceOutput != null) return false;
        if (sink != null) return sink.shouldInformAdmins();
        return output != null || server.settings().get().broadcastConsoleToOps;
    }

    /** sendSuccess 가 실제로 누군가에게 보낼지(region 에서 메시지 본문을 미리 만들 때 낭비를 피하는 검사). */
    public boolean wouldSendSuccess(boolean allowLogging) {
        return (acceptsSuccess() && !silent) || (allowLogging && shouldInformAdmins() && !silent);
    }

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
                if (p != output && server.lists().isOp(ServerCore.profileOf(p))) p.sendSystemMessage(admin, false);
            }
        }
        if ((output != null || sink != null) && server.gameRule("logAdminCommands")) server.sendSystemMessage(admin);   // 바닐라: source != server
    }

    public void sendFailure(Text message) {
        if (silent || (traceOutput == null && sink != null && !sink.acceptsFailure())) return;
        sendSystemMessage(Text.empty().append(message).color(org.domi.furnace.api.text.NamedTextColor.RED));
    }

    public void sendSystemMessage(Text message) {
        if (traceOutput != null) traceOutput.accept(message);
        else if (sink != null) sink.sendSystemMessage(message);
        else if (output != null) output.sendSystemMessage(message, false);
        else server.sendSystemMessage(message);
    }
}
