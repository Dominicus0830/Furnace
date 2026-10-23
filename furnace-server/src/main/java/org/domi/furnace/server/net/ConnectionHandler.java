// 한 커넥션의 프로토콜 상태머신. 프레임을 받아 단계별로 해석하고 응답 패킷을 보낸다.
package org.domi.furnace.server.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.Arrays;
import java.util.UUID;
import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.CompressionDecoder;
import org.domi.furnace.server.net.io.CompressionEncoder;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.ConnectionState;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.configuration.BrandPayload;
import org.domi.furnace.server.net.protocol.configuration.ConfigDisconnect;
import org.domi.furnace.server.net.protocol.configuration.FeatureFlags;
import org.domi.furnace.server.net.protocol.configuration.UpdateTags;
import org.domi.furnace.server.net.protocol.configuration.FinishConfiguration;
import org.domi.furnace.server.net.protocol.configuration.KnownPacksClientbound;
import org.domi.furnace.server.net.protocol.configuration.RegistryData;
import org.domi.furnace.server.net.protocol.handshake.Handshake;
import org.domi.furnace.server.net.protocol.play.BlockChangedAck;
import org.domi.furnace.server.net.protocol.play.ContainerClose;
import org.domi.furnace.server.net.protocol.play.ContainerSetContent;
import org.domi.furnace.server.net.protocol.play.ContainerSetSlot;
import org.domi.furnace.server.net.protocol.play.Disconnect;
import org.domi.furnace.server.net.protocol.play.OpenScreen;
import org.domi.furnace.server.world.ChestBlockEntity;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.server.net.protocol.login.LoginDisconnect;
import org.domi.furnace.server.net.protocol.login.EncryptionRequest;
import org.domi.furnace.server.net.protocol.login.EncryptionResponse;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.protocol.login.LoginStart;
import org.domi.furnace.server.net.protocol.login.LoginSuccess;
import org.domi.furnace.server.net.protocol.login.SetCompression;
import org.domi.furnace.server.net.protocol.play.ChunkBatchFinished;
import org.domi.furnace.server.net.protocol.play.ChunkBatchStart;
import org.domi.furnace.server.net.protocol.play.GameEvent;
import org.domi.furnace.server.net.protocol.play.KeepAliveClientbound;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.protocol.play.SetCenterChunk;
import org.domi.furnace.server.net.protocol.play.ChangeDifficulty;
import org.domi.furnace.server.net.protocol.play.InitializeBorder;
import org.domi.furnace.server.net.protocol.play.PlayerAbilities;
import org.domi.furnace.server.net.protocol.play.SetExperience;
import org.domi.furnace.server.net.protocol.play.SetHealth;
import org.domi.furnace.server.net.protocol.play.SetHeldSlot;
import org.domi.furnace.server.net.protocol.play.SetTime;
import org.domi.furnace.server.net.protocol.play.LoginPlay;
import org.domi.furnace.server.net.protocol.play.SetDefaultSpawnPosition;
import org.domi.furnace.server.net.protocol.play.SyncPlayerPosition;
import org.domi.furnace.server.net.protocol.status.PongResponse;
import org.domi.furnace.server.net.protocol.status.StatusResponse;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.SpawnPoint;
import org.domi.furnace.server.world.World;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConnectionHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger log = LoggerFactory.getLogger(ConnectionHandler.class);
    private static final RegistrySet REGISTRIES = RegistrySet.load("registries/1.21.1.txt");
    private static final org.domi.furnace.server.net.registry.TagSet TAGS =
            org.domi.furnace.server.net.registry.TagSet.load("registries/tags-1.21.1.txt");

    private final ServerLoginContext login;
    private final String serverName;
    private final int compressionThreshold;
    private final FurnaceScheduler scheduler;
    private final World world;
    private final EntityIdAllocator idAllocator;

    private volatile ChannelHandlerContext ctx;
    private final AtomicBoolean awaitingKeepAlive = new AtomicBoolean(false);
    private volatile long lastKeepAliveId;

    private ConnectionState state = ConnectionState.HANDSHAKE;
    private static final int PROTOCOL_VERSION = 767;                 // 1.21.1
    private static final String VERSION_NAME = "1.21.1";
    // 패킷 속도 제한(바닐라 rate-limit/Paper packet-limiter 대응): 버스트 400개, 초당 200개 회복. 초과 시 킥.
    private final TokenBucket packetBucket = new TokenBucket(400, 200);
    // 이동 예산: 버스트 400블록, 초당 400블록 회복(합법적 최고 속도 약 100~200블록/초의 2배 이상). 초과 이동은 보정 텔레포트.
    private final TokenBucket moveBucket = new TokenBucket(400, 400);
    private boolean loginSuccessSent;       // Login Start 처리 후에만 Login Acknowledged 수락
    // 바닐라 ServerLoginPacketListenerImpl.State: HELLO → KEY → AUTHENTICATING → (검사) → PROTOCOL_SWITCHING
    private enum LoginPhase { HELLO, KEY, AUTHENTICATING, VERIFYING, PROTOCOL_SWITCHING, ACCEPTED }
    private LoginPhase loginPhase = LoginPhase.HELLO;
    private final byte[] verifyToken = java.nio.ByteBuffer.allocate(4).putInt(new java.security.SecureRandom().nextInt()).array();
    private boolean statusHandled;
    private boolean clientAllowsListing = true;
    private volatile String clientLocale = "en_us";
    private int chatVisibility = org.domi.furnace.server.chat.PlayerChatState.VISIBILITY_FULL;
    private int velocityMessageId;
    private java.net.InetSocketAddress forwardedAddress;   // Velocity 포워딩으로 받은 실제 주소          // 상태 요청은 연결당 1회(바닐라 ServerStatusPacketListenerImpl)
    private boolean transferred;            // 전송(intent 3)으로 들어온 연결
    private java.util.List<org.domi.furnace.server.players.GameProfile.Property> profileProperties = java.util.List.of();
    private io.netty.util.concurrent.ScheduledFuture<?> slowLoginTimer;
    private boolean knownPacksHandled;      // Known Packs 응답은 연결당 1회(레지스트리 재전송 증폭 방지)
    private boolean finishConfigSent;       // FinishConfiguration을 보낸 뒤에만 PLAY 진입 수락
    private String playerName;
    private UUID playerUuid;
    private Player player; // play 진입 후 생성
    // 스폰 텔레포트 게이트 ID(-1=해제). 설정 중엔 클라 이동 무시(vanilla awaitingPositionFromClient식) — 청크 로드 갭 중 떨어진 위치 수용 방지.
    // confirm이 아니라 스폰 청크 도착 후(타이머)에 해제 + 위치 재확정.
    private int pendingTeleportId = -1;
    // 스폰 게이트 해제 조건: confirm 왕복과 스폰 청크 배치 전송 완료 둘 다 필요(둘 다 채널 이벤트루프에서만 갱신 — 레이스 없음).
    private boolean spawnConfirmReceived = false;
    private boolean spawnChunksDelivered = false;
    /** 플레이어 인벤토리(창 칸 0..45·든 칸·커서) — play 진입 때 Player 에 넘겨 region 스레드와 함께 쓴다. */
    private final org.domi.furnace.server.entity.PlayerInventory inv = new org.domi.furnace.server.entity.PlayerInventory();
    private int nextContainerId = 1;         // 체스트 세션 id(1..100 순환, 0은 플레이어 자기 인벤 예약)
    private double spawnX, spawnY, spawnZ;
    private float spawnYaw, spawnPitch;
    // 이동 검증용(채널 이벤트루프 전용): 마지막으로 수락한 클라 위치. 스폰 좌표로 시작한다(월드 기본 스폰/저장 위치는 임의 좌표).
    // 회전·onGround도 같이 들고 있다 — 부분 패킷(회전만/위치만)이 region이 drain하기 전의 대기 값을 덮어쓰지 않도록
    // 항상 "마지막 수락 값들의 합"을 발행한다(Player.x() 등은 region이 아직 drain 안 한 옛 값일 수 있다).
    private double ix, iy, iz;
    private float iyaw, ipitch;
    private boolean ig = true;
    // 서버가 보낸 보정 텔레포트의 id(-1=없음). 보정 중엔 클라가 confirm할 때까지 위치 패킷을 무시한다(옛 위치 패킷이 연쇄 보정을 부르지 않게).
    private int correctionTpId = -1;
    private int teleportId = 2;                  // 다음 보정 id는 ++teleportId (스폰이 1·2를 썼다)
    private static final double MAX_COORD = 3.0e7;   // 월드 경계 수준
    private static final double MAX_STEP = 100;       // 패킷당 허용 이동(블록) — 넘으면 되돌림
    private static final double REACH_SQ = 8 * 8;     // 블록 요청 도달 거리²(크리에이티브 5 + 여유), 눈 높이 기준
    private volatile int clientViewDistance = Config.MAX_VIEW_DISTANCE; // 클라가 ClientInformation서 요청한 view distance. 기본 최대.

    public ConnectionHandler(String serverName, int compressionThreshold, FurnaceScheduler scheduler,
                             World world, EntityIdAllocator idAllocator) {
        this(ServerLoginContext.offline(serverName), compressionThreshold, scheduler, world, idAllocator);
    }

    public ConnectionHandler(ServerLoginContext login, int compressionThreshold, FurnaceScheduler scheduler,
                             World world, EntityIdAllocator idAllocator) {
        this.login = login;
        this.serverName = login.motd;
        this.compressionThreshold = compressionThreshold;
        this.scheduler = scheduler;
        this.world = world;
        this.idAllocator = idAllocator;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
        this.ctx = ctx;
        int packetId = Types.readVarInt(frame);
        switch (state) {
            case HANDSHAKE -> handleHandshake(ctx, packetId, frame);
            case STATUS -> handleStatus(ctx, packetId, frame);
            case LOGIN -> handleLogin(ctx, packetId, frame);
            case CONFIGURATION -> handleConfiguration(ctx, packetId, frame);
            case PLAY -> {
                if (!packetBucket.tryAcquire(1)) { kick(ctx, "Too many packets"); return; }
                handlePlay(ctx, packetId, frame);
            }
            default -> log.debug("미처리 패킷: state={}, id=0x{}", state, Integer.toHexString(packetId));
        }
    }

    private void handleHandshake(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        if (packetId != Handshake.ID) {
            ctx.close();
            return;
        }
        Handshake hs = Handshake.read(frame);
        // 바닐라 ServerHandshakePacketListenerImpl
        switch (hs.nextState()) {
            case 1 -> {
                state = ConnectionState.STATUS;
                // enable-status=false 면 응답하지 않고 끊는다(Ignoring status request). 상태 조회는 버전과 무관하게 허용.
                if (!login.enableStatus) ctx.close();
            }
            case 2 -> beginLogin(ctx, hs.protocolVersion(), false);
            case 3 -> {
                state = ConnectionState.LOGIN;
                if (!login.acceptsTransfers) {
                    disconnect(ctx, Text.translatable("multiplayer.disconnect.transfers_disabled"));
                } else {
                    beginLogin(ctx, hs.protocolVersion(), true);
                }
            }
            default -> ctx.close();
        }
    }

    private void beginLogin(ChannelHandlerContext ctx, int protocolVersion, boolean transfer) {
        state = ConnectionState.LOGIN;
        transferred = transfer;
        if (protocolVersion != PROTOCOL_VERSION) {
            String key = protocolVersion < 754 ? "multiplayer.disconnect.outdated_client" : "multiplayer.disconnect.incompatible";
            disconnect(ctx, Text.translatable(key, VERSION_NAME));
            return;
        }
        // 바닐라: 로그인 단계 600틱(30초) 안에 끝나지 않으면 끊는다.
        slowLoginTimer = ctx.executor().schedule(() -> {
            if (state == ConnectionState.LOGIN && loginPhase != LoginPhase.ACCEPTED) {
                disconnect(ctx, Text.translatable("multiplayer.disconnect.slow_login"));
            }
        }, 30, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void handleStatus(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (packetId) {
            case 0x00 -> { // Status Request — 연결당 한 번(두 번째는 "Status request has been handled" 로 종료)
                if (statusHandled) { ctx.close(); return; }
                statusHandled = true;
                java.util.List<org.domi.furnace.server.players.GameProfile> online = new java.util.ArrayList<>();
                java.util.List<Boolean> listing = new java.util.ArrayList<>();
                for (Player p : world.onlinePlayers()) {
                    if (p.isDisconnected()) continue;
                    online.add(new org.domi.furnace.server.players.GameProfile(p.uuid(), p.name()));
                    listing.add(p.allowsListing());
                }
                send(ctx, new StatusResponse(login.statusJson(online, listing)));
            }
            case 0x01 -> { // Ping Request → Pong 후 종료(바닐라)
                ByteBuf buf = ctx.alloc().buffer();
                Types.writeVarInt(buf, PongResponse.ID);
                new PongResponse(frame.readLong()).write(buf);
                ctx.writeAndFlush(buf).addListener(io.netty.channel.ChannelFutureListener.CLOSE);
            }
            default -> ctx.close();
        }
    }

    private void handleLogin(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (packetId) {
            case 0x00 -> { // Login Start(Hello)
                if (loginPhase != LoginPhase.HELLO) { protocolError(ctx, "Unexpected hello packet"); return; }
                LoginStart start = LoginStart.read(frame);
                if (!isValidPlayerName(start.name())) { protocolError(ctx, "Invalid characters in username"); return; }
                this.playerName = start.name();
                if (login.velocity != null) {
                    // Paper: 프록시에게 실제 주소·프로필을 묻는다(암호화·세션 인증 생략).
                    loginPhase = LoginPhase.KEY;
                    velocityMessageId = java.util.concurrent.ThreadLocalRandom.current().nextInt();
                    send(ctx, new org.domi.furnace.server.net.protocol.login.LoginPluginRequest(velocityMessageId,
                            org.domi.furnace.server.net.auth.VelocityForwarding.PLAYER_INFO_CHANNEL,
                            new byte[]{org.domi.furnace.server.net.auth.VelocityForwarding.MAX_SUPPORTED_FORWARDING_VERSION}));
                } else if (login.onlineMode) {
                    loginPhase = LoginPhase.KEY;
                    send(ctx, new EncryptionRequest("", login.keyPair.getPublic().getEncoded(), verifyToken, true));
                } else {
                    startVerifying(ctx, org.domi.furnace.server.players.GameProfile.offline(playerName));
                }
            }
            case 0x01 -> handleEncryptionResponse(ctx, frame);
            case 0x02 -> handleCustomQueryAnswer(ctx, frame);
            case 0x04 -> // Cookie Response — 요청한 적이 없다
                    disconnect(ctx, Text.translatable("multiplayer.disconnect.unexpected_query_response"));
            case 0x03 -> { // Login Acknowledged
                if (loginPhase != LoginPhase.PROTOCOL_SWITCHING) { protocolError(ctx, "Unexpected login acknowledgement packet"); return; }
                loginPhase = LoginPhase.ACCEPTED;
                if (slowLoginTimer != null) slowLoginTimer.cancel(false);
                state = ConnectionState.CONFIGURATION;
                onEnterConfiguration(ctx);
            }
            default -> ctx.close();
        }
    }

    // ------------------------------------------------------------------ 채팅(바닐라 ServerGamePacketListenerImpl)

    private static boolean isChatMessageIllegal(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' || c < ' ' || c == 127) return true;
        }
        return false;
    }

    /** 바닐라 tryHandleChat: 금지 문자면 끊고, 채팅을 숨긴 설정이면 알리고, 아니면 전역 스레드에서 실행. */
    private void tryHandleChat(String message, Runnable action) {
        Player p = player;
        if (isChatMessageIllegal(message)) {
            kick(ctx, Text.translatable("multiplayer.disconnect.illegal_characters"));
        } else if (p.chat().visibility() == org.domi.furnace.server.chat.PlayerChatState.VISIBILITY_HIDDEN) {
            p.send(new org.domi.furnace.server.net.protocol.play.SystemChat(Text.translatable("chat.disabled.options").color(org.domi.furnace.api.text.NamedTextColor.RED), false));
        } else {
            login.core.scheduler().global().execute(action);
        }
    }

    /** 바닐라 detectRateSpam. */
    private void detectRateSpam() {
        Player p = player;
        org.domi.furnace.server.ServerCore core = login.core;
        if (p.chat().incrementSpamAndCheck() && !core.lists().isOp(org.domi.furnace.server.ServerCore.profileOf(p))) {
            p.kick(new Disconnect(Text.translatable("disconnect.spam")));
        }
    }

    private java.util.Optional<org.domi.furnace.server.chat.LastSeenMessages> unpackAndApplyLastSeen(
            org.domi.furnace.server.chat.LastSeenMessages.Update update) {
        var r = player.chat().lastSeen.applyUpdate(update);
        if (r.isEmpty()) {
            log.warn("Failed to validate message acknowledgements from {}", player.name());
            kick(ctx, Text.translatable("multiplayer.disconnect.chat_validation_failed"));
        }
        return r;
    }

    private void handleChatAck(int offset) {
        Player p = player;
        if (p == null) return;
        if (!p.chat().lastSeen.applyOffset(offset)) {
            log.warn("Failed to validate message acknowledgements from {}", p.name());
            kick(ctx, Text.translatable("multiplayer.disconnect.chat_validation_failed"));
        }
    }

    private void handleMessageDecodeFailure(org.domi.furnace.server.chat.SignedMessageChain.DecodeException e) {
        log.warn("Failed to update secure chat state for {}: '{}'", player.name(), e.component().getString());
        player.sendSystemMessage(Text.empty().append(e.component()).color(org.domi.furnace.api.text.NamedTextColor.RED), false);
    }

    private void handleChat(ByteBuf frame) {
        Player p = player;
        org.domi.furnace.server.ServerCore core = login.core;
        if (p == null || core == null) return;
        String message = Types.readString(frame, 256);
        java.time.Instant timeStamp = java.time.Instant.ofEpochMilli(frame.readLong());
        long salt = frame.readLong();
        org.domi.furnace.server.chat.MessageSignature signature =
                frame.readBoolean() ? org.domi.furnace.server.chat.MessageSignature.read(frame) : null;
        var update = org.domi.furnace.server.chat.LastSeenMessages.Update.read(frame);
        var lastSeen = unpackAndApplyLastSeen(update);
        if (lastSeen.isEmpty()) return;
        tryHandleChat(message, () -> {
            org.domi.furnace.server.chat.PlayerChatMessage msg;
            try {
                msg = p.chat().decoder().unpack(signature,
                        new org.domi.furnace.server.chat.SignedMessageBody(message, timeStamp, salt, lastSeen.get()));
            } catch (org.domi.furnace.server.chat.SignedMessageChain.DecodeException e) {
                handleMessageDecodeFailure(e);
                return;
            }
            msg = msg.withUnsignedContent(msg.decoratedContent()); // 채팅 꾸밈(decorator)은 없다
            var events = core.events();
            if (events.hasSubscribers(org.domi.furnace.api.event.player.AsyncChatEvent.class)) {
                Text original = msg.decoratedContent();
                var ev = events.call(new org.domi.furnace.api.event.player.AsyncChatEvent(core.plugins().player(p), message, original));
                if (ev.isCancelled()) {
                    detectRateSpam();
                    return;
                }
                if (!ev.message().equals(original)) msg = msg.withUnsignedContent(ev.message());
            }
            core.broadcastChatMessage(msg, p, org.domi.furnace.server.chat.ChatType.CHAT.bind(
                    org.domi.furnace.server.command.CommandSource.player(core, p).getDisplayName()));
            detectRateSpam();
        });
    }

    /** 바닐라 handleChatSessionUpdate: 키가 바뀌었으면 Mojang 서비스 키로 검증하고 세션을 바꾼 뒤 모두에게 알린다. */
    private void handleChatSessionUpdate(org.domi.furnace.server.chat.RemoteChatSession.Data data) {
        Player p = player;
        org.domi.furnace.server.ServerCore core = login.core;
        if (p == null || core == null) return;
        var old = p.chat().session();
        var oldKey = old != null ? old.profilePublicKey().data() : null;
        var newKey = data.profilePublicKey();
        if (java.util.Objects.equals(oldKey, newKey)) return;
        if (oldKey != null && newKey.expiresAt().isBefore(oldKey.expiresAt())) {
            kick(ctx, org.domi.furnace.server.chat.ProfilePublicKey.EXPIRED_PROFILE_PUBLIC_KEY);
            return;
        }
        var validator = core.servicesKeys().profileKeySignatureValidator();
        if (validator == null) {
            log.warn("Ignoring chat session from {} due to missing Services public key", p.name());
            return;
        }
        try {
            var session = data.validate(p.uuid(), validator);
            core.scheduler().global().execute(() -> {
                p.chat().resetSession(session);
                var packet = org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate.initializeChat(p.uuid(), session.asData());
                for (Player other : core.players()) other.send(packet);
            });
        } catch (org.domi.furnace.server.chat.ProfilePublicKey.ValidationException e) {
            log.error("Failed to validate profile key: {}", e.getMessage());
            kick(ctx, e.component());
        }
    }

    /** chat_command(서명 없음): 바닐라 performUnsignedChatCommand. */
    /**
     * PlayerCommandPreprocessEvent(구독자가 있을 때만): 취소면 null, 아니면 실행할 명령('/' 없이). 바뀐 명령은 서명 없이 실행된다.
     */
    private String preprocessCommand(org.domi.furnace.server.ServerCore core, Player p, String command) {
        var events = core.events();
        if (!events.hasSubscribers(org.domi.furnace.api.event.player.PlayerCommandPreprocessEvent.class)) return command;
        var ev = events.call(new org.domi.furnace.api.event.player.PlayerCommandPreprocessEvent(core.plugins().player(p), "/" + command));
        if (ev.isCancelled()) return null;
        return ev.message().substring(1);
    }

    private void handleChatCommand(String rawCommand) {
        Player p = player;
        org.domi.furnace.server.ServerCore core = login.core;
        if (p == null || core == null) return;
        tryHandleChat(rawCommand, () -> {
            String command = preprocessCommand(core, p, rawCommand);
            if (command == null) {
                detectRateSpam();
                return;
            }
            log.info("{} issued server command: /{}", p.name(), command);
            var source = org.domi.furnace.server.command.CommandSource.player(core, p);
            var parse = core.commands().getDispatcher().parse(command, source);
            if (core.enforceSecureProfile() && org.domi.furnace.server.command.SignableCommand.hasSignableArguments(parse)) {
                log.error("Received unsigned command packet from {}, but the command requires signable arguments: {}", p.name(), command);
                p.sendSystemMessage(Text.translatable("chat.disabled.invalid_command_signature").color(org.domi.furnace.api.text.NamedTextColor.RED), false);
            } else {
                core.commands().performCommand(parse, command);
            }
            detectRateSpam();
        });
    }

    /** chat_command_signed: 바닐라 performSignedChatCommand — 인자 서명을 검증해 서명된 메시지로 명령에 넘긴다. */
    private void handleSignedChatCommand(ByteBuf frame) {
        Player p = player;
        org.domi.furnace.server.ServerCore core = login.core;
        if (p == null || core == null) return;
        String command = Types.readString(frame, 256);
        java.time.Instant timeStamp = java.time.Instant.ofEpochMilli(frame.readLong());
        long salt = frame.readLong();
        int n = Types.readVarInt(frame);
        if (n < 0 || n > 8) throw new org.domi.furnace.server.net.protocol.ProtocolException("인자 서명 개수 오류: " + n);
        java.util.LinkedHashMap<String, org.domi.furnace.server.chat.MessageSignature> sigs = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) sigs.put(Types.readString(frame, 16), org.domi.furnace.server.chat.MessageSignature.read(frame));
        var update = org.domi.furnace.server.chat.LastSeenMessages.Update.read(frame);
        var lastSeen = unpackAndApplyLastSeen(update);
        if (lastSeen.isEmpty()) return;
        tryHandleChat(command, () -> {
            String changed = preprocessCommand(core, p, command);
            var source = org.domi.furnace.server.command.CommandSource.player(core, p);
            var parse = core.commands().getDispatcher().parse(command, source);
            java.util.Map<String, org.domi.furnace.server.chat.PlayerChatMessage> signed;
            try {
                signed = collectSignedArguments(command, sigs, org.domi.furnace.server.command.SignableCommand.of(parse), timeStamp, salt, lastSeen.get());
            } catch (org.domi.furnace.server.chat.SignedMessageChain.DecodeException e) {
                handleMessageDecodeFailure(e);
                return;
            }
            // 서명 체인은 원문으로 진행했다 — 플러그인이 취소했으면 여기서 끝, 바꿨으면 서명 없이 바뀐 명령을 실행한다.
            if (changed == null) {
                detectRateSpam();
                return;
            }
            log.info("{} issued server command: /{}", p.name(), changed);
            if (!changed.equals(command)) {
                core.commands().performPrefixedCommand(source, changed);
            } else {
                // 서명된 인자를 담은 소스로 다시 파싱한다(바닐라 Commands.mapSource 와 같은 효과).
                core.commands().performCommand(core.commands().getDispatcher().parse(command, source.withSignedArguments(signed)), command);
            }
            detectRateSpam();
        });
    }

    private java.util.Map<String, org.domi.furnace.server.chat.PlayerChatMessage> collectSignedArguments(
            String command, java.util.Map<String, org.domi.furnace.server.chat.MessageSignature> sigs,
            org.domi.furnace.server.command.SignableCommand signable, java.time.Instant timeStamp, long salt,
            org.domi.furnace.server.chat.LastSeenMessages lastSeen) throws org.domi.furnace.server.chat.SignedMessageChain.DecodeException {
        var decoder = player.chat().decoder();
        java.util.Map<String, org.domi.furnace.server.chat.PlayerChatMessage> out = new java.util.HashMap<>();
        if (sigs.isEmpty()) {
            for (var a : signable.arguments()) {
                out.put(a.name(), decoder.unpack(null, org.domi.furnace.server.chat.SignedMessageBody.unsigned(a.value())));
            }
            return out;
        }
        for (var e : sigs.entrySet()) {
            var arg = signable.get(e.getKey());
            if (arg == null) {
                decoder.setChainBroken();
                throw signedCommandMismatch(command, sigs, signable);
            }
            out.put(arg.name(), decoder.unpack(e.getValue(),
                    new org.domi.furnace.server.chat.SignedMessageBody(arg.value(), timeStamp, salt, lastSeen)));
        }
        for (var a : signable.arguments()) if (!out.containsKey(a.name())) throw signedCommandMismatch(command, sigs, signable);
        return out;
    }

    private static org.domi.furnace.server.chat.SignedMessageChain.DecodeException signedCommandMismatch(
            String command, java.util.Map<String, ?> sigs, org.domi.furnace.server.command.SignableCommand signable) {
        String got = String.join(", ", sigs.keySet());
        String expected = String.join(", ", signable.arguments().stream().map(org.domi.furnace.server.command.SignableCommand.Argument::name).toList());
        log.error("Signed command mismatch between server and client ('{}'): got [{}] from client, but expected [{}]", command, got, expected);
        return new org.domi.furnace.server.chat.SignedMessageChain.DecodeException(
                Text.translatable("chat.disabled.invalid_command_signature").color(org.domi.furnace.api.text.NamedTextColor.RED));
    }

    /** 바닐라 handleCustomCommandSuggestions — 서버 측 자동완성(ask_server 노드). */
    private void handleCommandSuggestion(int transactionId, String text) {
        Player p = player;
        org.domi.furnace.server.ServerCore core = login.core;
        if (p == null || core == null) return;
        core.scheduler().global().execute(() -> core.commands()
                .suggestions(org.domi.furnace.server.command.CommandSource.player(core, p), text)
                .thenAccept(s -> p.send(new org.domi.furnace.server.net.protocol.play.CommandSuggestionsResponse(transactionId, s))));
    }

    /** Velocity 포워딩 응답: 서명 확인 → 실제 주소·프로필로 로그인 검사 진행. 그 밖의 질의 응답은 바닐라처럼 끊는다. */
    private void handleCustomQueryAnswer(ChannelHandlerContext ctx, ByteBuf frame) {
        int messageId = Types.readVarInt(frame);
        if (login.velocity == null || loginPhase != LoginPhase.KEY || messageId != velocityMessageId) {
            disconnect(ctx, Text.translatable("multiplayer.disconnect.unexpected_query_response"));
            return;
        }
        if (!frame.readBoolean()) {
            disconnect(ctx, Text.text("This server requires you to connect with Velocity."));
            return;
        }
        org.domi.furnace.server.net.auth.VelocityForwarding.Forwarded fwd;
        try {
            fwd = login.velocity.read(frame);
        } catch (Exception e) {
            protocolError(ctx, e.getMessage());
            return;
        }
        if (fwd == null) {
            disconnect(ctx, Text.text("Unable to verify player details"));
            return;
        }
        int port = ctx.channel().remoteAddress() instanceof java.net.InetSocketAddress isa ? isa.getPort() : 0;
        forwardedAddress = new java.net.InetSocketAddress(fwd.address(), port);
        log.info("UUID of player {} is {}", fwd.profile().name(), fwd.profile().id());
        startVerifying(ctx, fwd.profile());
    }

    /** 실제 클라이언트 주소(Velocity 포워딩이면 프록시가 알려 준 주소). */
    private java.net.SocketAddress clientAddress(ChannelHandlerContext ctx) {
        return forwardedAddress != null ? forwardedAddress : ctx.channel().remoteAddress();
    }

    /** 바닐라 handleKey: 토큰 확인 → 공유 비밀 복호화 → 스트림 암호 켜기 → 세션 서버에 hasJoined(비동기). */
    private void handleEncryptionResponse(ChannelHandlerContext ctx, ByteBuf frame) {
        if (loginPhase != LoginPhase.KEY) { protocolError(ctx, "Unexpected key packet"); return; }
        String serverIdHash;
        try {
            EncryptionResponse resp = EncryptionResponse.read(frame);
            java.security.PrivateKey priv = login.keyPair.getPrivate();
            byte[] token = org.domi.furnace.server.net.auth.Crypt.decryptUsingKey(priv, resp.verifyToken());
            if (!java.util.Arrays.equals(verifyToken, token)) throw new IllegalStateException("Protocol error");
            javax.crypto.SecretKey secret = org.domi.furnace.server.net.auth.Crypt.decryptByteToSecretKey(priv, resp.sharedSecret());
            javax.crypto.Cipher decrypt = org.domi.furnace.server.net.auth.Crypt.getCipher(javax.crypto.Cipher.DECRYPT_MODE, secret);
            javax.crypto.Cipher encrypt = org.domi.furnace.server.net.auth.Crypt.getCipher(javax.crypto.Cipher.ENCRYPT_MODE, secret);
            serverIdHash = org.domi.furnace.server.net.auth.Crypt.serverIdHash("", login.keyPair.getPublic(), secret);
            loginPhase = LoginPhase.AUTHENTICATING;
            // 암호는 가장 바깥: 수신은 프레임 분리 전에 풀고, 송신은 프레임(·압축) 뒤에 감싼다.
            ctx.pipeline().addBefore("frameDecoder", "decrypt", new org.domi.furnace.server.net.auth.CipherDecoder(decrypt));
            ctx.pipeline().addBefore("frameDecoder", "encrypt", new org.domi.furnace.server.net.auth.CipherEncoder(encrypt));
        } catch (java.security.GeneralSecurityException | RuntimeException e) {
            protocolError(ctx, "Protocol error");
            return;
        }
        String name = playerName;
        java.net.InetAddress ip = login.preventProxyConnections && ctx.channel().remoteAddress() instanceof java.net.InetSocketAddress isa
                ? isa.getAddress() : null;
        login.sessions.hasJoinedServer(name, serverIdHash, ip).whenComplete((profile, err) -> ctx.executor().execute(() -> {
            if (!ctx.channel().isActive()) return;
            if (err != null) {
                disconnect(ctx, Text.translatable("multiplayer.disconnect.authservers_down"));
                log.error("Couldn't verify username because servers are unavailable");
            } else if (profile.isEmpty()) {
                disconnect(ctx, Text.translatable("multiplayer.disconnect.unverified_username"));
                log.error("Username '{}' tried to join with an invalid session", name);
            } else {
                log.info("UUID of player {} is {}", profile.get().name(), profile.get().id());
                startVerifying(ctx, profile.get());
            }
        }));
    }

    /** 바닐라 verifyLoginAndFinishConnectionSetup: 로그인 검사 → 압축 → 중복 접속 정리 → Login Success. */
    private void startVerifying(ChannelHandlerContext ctx, org.domi.furnace.server.players.GameProfile profile) {
        loginPhase = LoginPhase.VERIFYING;
        this.playerName = profile.name();
        this.playerUuid = profile.id();
        this.profileProperties = profile.properties();
        org.domi.furnace.server.ServerCore core = login.core;
        if (core != null && core.events().hasSubscribers(org.domi.furnace.api.event.player.AsyncPlayerPreLoginEvent.class)) {
            // 플러그인이 블로킹 작업(DB 등)을 해도 되도록 가상 스레드에서 부르고, 결과를 들고 연결 스레드로 돌아온다.
            java.net.InetAddress addr = clientAddress(ctx) instanceof java.net.InetSocketAddress isa ? isa.getAddress() : null;
            core.plugins().asyncExecutor().execute(() -> {
                var ev = core.events().call(new org.domi.furnace.api.event.player.AsyncPlayerPreLoginEvent(profile.name(), profile.id(), addr));
                ctx.channel().eventLoop().execute(() -> {
                    if (!ctx.channel().isActive()) return;
                    if (ev.result() != org.domi.furnace.api.event.player.AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                        disconnect(ctx, ev.kickMessage());
                        return;
                    }
                    finishVerifying(ctx, profile);
                });
            });
            return;
        }
        finishVerifying(ctx, profile);
    }

    /** 바닐라 접속 검사(+PlayerLoginEvent) → 압축 → 로그인 성공. */
    private void finishVerifying(ChannelHandlerContext ctx, org.domi.furnace.server.players.GameProfile profile) {
        org.domi.furnace.server.ServerCore core = login.core;
        if (login.lists != null) {
            int online = 0;
            for (Player p : world.onlinePlayers()) if (!p.isDisconnected()) online++;
            Text reason = login.lists.canPlayerLogin(clientAddress(ctx), profile, online, login.maxPlayers);
            if (core != null && core.events().hasSubscribers(org.domi.furnace.api.event.player.PlayerLoginEvent.class)) {
                java.net.InetAddress addr = clientAddress(ctx) instanceof java.net.InetSocketAddress isa ? isa.getAddress() : null;
                var ev = core.events().call(new org.domi.furnace.api.event.player.PlayerLoginEvent(profile.name(), profile.id(), addr,
                        loginResult(reason), reason));
                reason = ev.result() == org.domi.furnace.api.event.player.PlayerLoginEvent.Result.ALLOWED ? null : ev.kickMessage();
            }
            if (reason != null) { disconnect(ctx, reason); return; }
        }
        if (compressionThreshold >= 0) {
            send(ctx, new SetCompression(compressionThreshold)); // 비압축으로 나감
            ctx.pipeline().addAfter("frameDecoder", "decompress", new CompressionDecoder(compressionThreshold));
            ctx.pipeline().addAfter("frameEncoder", "compress", new CompressionEncoder(compressionThreshold));
        }
        kickDuplicateSession(playerUuid);
        if (login.profileCache != null) {
            // 바닐라 placeNewPlayer: 캐시에 있던 이름과 다르면 "joined.renamed" 로 알린다.
            previousName = login.profileCache.get(profile.id()).map(org.domi.furnace.server.players.GameProfile::name).orElse(profile.name());
            login.profileCache.add(profile);
        }
        loginPhase = LoginPhase.PROTOCOL_SWITCHING;
        send(ctx, new LoginSuccess(playerUuid, playerName, profileProperties, true));
        loginSuccessSent = true;
    }

    /** 바닐라 Connection.exceptionCaught 의 사유: disconnect.genericReason("Internal Exception: " + 예외). */
    private void protocolError(ChannelHandlerContext ctx, String message) {
        disconnect(ctx, Text.translatable("disconnect.genericReason",
                "Internal Exception: java.lang.IllegalStateException: " + message));
    }

    /** 로그에 쓰는 접속 주소(log-ips=false 면 가린다, 바닐라 Connection.getLoggableAddress). */
    private String loggableAddress(ChannelHandlerContext ctx) {
        if (!login.logIps) return "<ip address withheld>";
        java.net.SocketAddress a = clientAddress(ctx);
        return a == null ? "local" : a.toString();
    }

    /** 사유를 로그에 남기고(로그인 단계는 바닐라 "Disconnecting {}: {}") 단계에 맞는 Disconnect 로 끊는다. */
    void disconnect(ChannelHandlerContext ctx, Text reason) {
        if (state == ConnectionState.LOGIN) {
            String who = playerName != null ? playerName + " (" + loggableAddress(ctx) + ")" : loggableAddress(ctx);
            log.info("Disconnecting {}: {}", who, reason.getString());
        }
        kick(ctx, reason);
    }

    private void onEnterConfiguration(ChannelHandlerContext ctx) {
        send(ctx, new BrandPayload(org.domi.furnace.server.FurnaceMain.SERVER_BRAND));         // 바닐라 순서: brand → 기능 플래그 → Known Packs
        send(ctx, FeatureFlags.vanilla());
        send(ctx, new KnownPacksClientbound());
    }

    private void handleConfiguration(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (packetId) {
            case 0x00 -> readClientInformation(frame); // Client Information: view distance 읽음
            case 0x02 -> { /* Plugin Message(brand): 무시 */ }
            case 0x07 -> { // Serverbound Known Packs → 레지스트리 전송 후 종료
                if (knownPacksHandled) return;                           // 반복 요청은 무시(증폭 방지)
                knownPacksHandled = true;
                for (String registry : REGISTRIES.registryKeys()) {
                    send(ctx, new RegistryData(registry, REGISTRIES.entries(registry)));
                }
                // 레지스트리 뒤, Finish 앞(바닐라 순서). 데이터 팩이 있으면 거기서 읽은 태그, 없으면 미리 만든 태그 파일.
                var dp = login.core != null ? login.core.dataPacks() : null;
                send(ctx, new UpdateTags(dp != null ? dp.tags() : TAGS));
                send(ctx, new FinishConfiguration());
                finishConfigSent = true;
            }
            case 0x03 -> { // Acknowledge Finish Configuration
                if (!finishConfigSent) { ctx.close(); return; }          // Finish를 보내기 전의 ack는 프로토콜 위반
                state = ConnectionState.PLAY;
                onEnterPlay(ctx); // Task 9에서 구현
            }
            default -> log.debug("미처리 config 패킷 id=0x{}", Integer.toHexString(packetId));
        }
    }

    /** ClientInformation 파싱 — locale 건너뛰고 view distance(byte) 읽음. config·play 양쪽서 호출. */
    private void readClientInformation(ByteBuf frame) {
        try {
            this.clientLocale = Types.readString(frame, 16);   // locale(en_us 등)
            int vd = frame.readByte();                   // view distance (chunks)
            this.clientViewDistance = Math.max(Config.MIN_VIEW_DISTANCE, Math.min(Config.MAX_VIEW_DISTANCE, vd));
            Player p = this.player;
            if (p != null) p.locale(clientLocale);
            if (p != null) p.viewDistance(effectiveViewDistance()); // 게임 중 렌더거리 변경 반영
            this.chatVisibility = Types.readVarInt(frame); // chat mode: 0 전부, 1 시스템만, 2 숨김
            if (p != null) p.chat().visibility(chatVisibility);
            frame.readBoolean();                         // chat colors
            frame.readUnsignedByte();                    // displayed skin parts
            Types.readVarInt(frame);                     // main hand
            frame.readBoolean();                         // text filtering
            this.clientAllowsListing = frame.readBoolean(); // 서버 목록(상태 응답 sample)에 이름 공개 허용
            if (p != null) p.allowsListing(clientAllowsListing);
        } catch (Exception e) {
            log.debug("ClientInformation 파싱 실패", e);
        }
    }

    /** 서버 view-distance와 클라 요청의 최솟값 — 과전송·"not in view range" 방지. */
    private int effectiveViewDistance() {
        return Math.max(Config.MIN_VIEW_DISTANCE, Math.min(world.viewDistance(), clientViewDistance));
    }

    private void onEnterPlay(ChannelHandlerContext ctx) {
        int eid = idAllocator.next();
        int dimIndex = Math.max(0,
                REGISTRIES.indexOf("minecraft:dimension_type", "minecraft:overworld"));
        int effVd = effectiveViewDistance();
        org.domi.furnace.server.ServerCore coreJ = login.core;
        // 저장 데이터를 먼저 읽는다 — 게임 모드·체력 등이 LoginPlay 부터 들어간다(바닐라 placeNewPlayer: load → loadGameTypes).
        org.domi.furnace.server.entity.PlayerData.Snapshot saved =
                org.domi.furnace.server.entity.PlayerData.load(world.worldDir(), playerUuid);
        Player p = new Player(eid, playerUuid, playerName, ctx.channel());
        p.inventory(inv);
        p.inventoryHost(new Player.InventoryHost() {
            public ItemStack slot(int windowSlot) { return inv.get(windowSlot); }

            public int selected() { return inv.selected(); }

            public void replace(int windowSlot, ItemStack expect, ItemStack now) {
                // 그 사이 인벤토리가 바뀌었으면 손대지 않는다. 클라이언트·inventory_changed 는 다음 틱 broadcastChanges 가.
                inv.replace(windowSlot, expect, now);
            }

            public void inventoryChanged(int windowSlot) { ctx.channel().eventLoop().execute(() -> fireInventoryChanged(windowSlot)); }

            public void closeContainer(org.domi.furnace.server.menu.AbstractContainerMenu expect) {
                synchronized (p.menuLock()) {
                    if (p.containerMenu() == expect && p.hasOpenContainer()) ConnectionHandler.this.closeContainer(p);
                }
            }

            public boolean menuStillValid(org.domi.furnace.server.menu.AbstractContainerMenu menu) { return menu.stillValid(menus); }

            public void validateBlockMenu(org.domi.furnace.server.world.Region region, org.domi.furnace.server.menu.AbstractContainerMenu menu) {
                int[] bp = menu.containerBlockPos();
                synchronized (p.menuLock()) {
                    if (p.containerMenu() != menu) return;
                    org.domi.furnace.server.world.MenuBlockContext.runIn(region, () -> {
                        if (bp == null || !region.chunkLoaded(ChunkPos.ofBlock(bp[0], bp[2])) || !menu.stillValid(menus)) ConnectionHandler.this.closeContainer(p);
                    });
                }
            }

            public int permissionLevel() {
                org.domi.furnace.server.ServerCore c = login.core;
                return c == null ? 0 : c.profilePermissions(org.domi.furnace.server.ServerCore.profileOf(p));
            }

            public boolean mayInteract(int x, int z) {
                org.domi.furnace.server.ServerCore c = login.core;
                return c == null || c.mayInteract(p, x, z);
            }

            public boolean allowBreak(int x, int y, int z, int state) {
                org.domi.furnace.server.ServerCore c = login.core;
                if (c == null || !c.events().hasSubscribers(org.domi.furnace.api.event.block.BlockBreakEvent.class)) return true;
                var ev = c.events().call(new org.domi.furnace.api.event.block.BlockBreakEvent("minecraft:overworld",
                        new org.domi.furnace.api.world.BlockPos(x, y, z), world.blockRegistry().nameOf(state), c.plugins().player(p)));
                return !ev.isCancelled();
            }
        });
        var vitals = p.vitals();
        if (coreJ != null) vitals.env(coreJ.vitalsEnv());
        vitals.deathListener(keepInventory -> ctx.channel().eventLoop().execute(() -> onPlayerDied(keepInventory)));
        if (saved != null && saved.root() != null) vitals.load(saved.root());
        org.domi.furnace.server.world.GameMode forced = coreJ != null ? coreJ.forcedGameMode() : null;
        org.domi.furnace.server.world.GameMode defaultMode = coreJ != null ? coreJ.defaultGameMode() : org.domi.furnace.server.world.GameMode.CREATIVE;
        vitals.loadGameTypes(saved != null ? saved.root() : null, forced != null ? forced : defaultMode, forced != null);
        var prevMode = vitals.previousGameMode();
        // 바닐라처럼 서버의 view distance를 보낸다(클라 렌더 거리는 min(자기 설정, 이 값)). 스트리밍은 effVd(= min(서버, 클라 요청))로 한다.
        if (coreJ != null) {
            var props = coreJ.settings().get();
            var rules = coreJ.gameRules();
            var level = coreJ.levelData();
            send(ctx, new LoginPlay(eid, level.hardcore, java.util.List.of("minecraft:overworld", "minecraft:the_end", "minecraft:the_nether"),
                    props.maxPlayers, world.viewDistance(), props.simulationDistance, rules.getBoolean("reducedDebugInfo"),
                    !rules.getBoolean("doImmediateRespawn"), rules.getBoolean("doLimitedCrafting"), dimIndex, "minecraft:overworld",
                    LoginPlay.obfuscateSeed(level.seed), vitals.gameMode().id(), prevMode == null ? -1 : prevMode.id(), false,
                    vitals.lastDeathDimension(), vitals.lastDeathPos(), coreJ.enforceSecureProfile()));
            send(ctx, new ChangeDifficulty(level.difficulty.id(), level.difficultyLocked));
        } else {
            send(ctx, new LoginPlay(eid, "minecraft:overworld", dimIndex, world.viewDistance()));
            send(ctx, new ChangeDifficulty(1, false));          // 바닐라 기본 easy
        }
        send(ctx, vitals.abilitiesPacket());
        // 바닐라 placeNewPlayer: 능력 → 손에 든 슬롯 → 레시피 목록 → 권한·명령 → 레시피 북 INIT
        if (saved != null && saved.root() != null) {
            int saved0 = saved.root().getIntOr0("SelectedItemSlot");
            if (saved0 >= 0 && saved0 < 9) inv.setSelected(saved0);
        }
        send(ctx, new SetHeldSlot(inv.selected()));
        org.domi.furnace.server.ServerCore core = login.core;
        var recipes = core != null ? core.recipes() : null;
        if (recipes != null) send(ctx, new org.domi.furnace.server.net.protocol.play.UpdateRecipes(recipes.networkBody()));
        if (core != null) {
            // 바닐라 placeNewPlayer → sendPlayerPermissionLevel: 권한 레벨 이벤트 + 쓸 수 있는 명령 트리
            int level = core.profilePermissions(new org.domi.furnace.server.players.GameProfile(playerUuid, playerName));
            send(ctx, org.domi.furnace.server.net.protocol.play.EntityEvent.permissionLevel(eid, level));
            send(ctx, core.commands().treeFor(org.domi.furnace.server.command.CommandSource.forProfile(core, playerName, level)));
        }
        if (saved != null && saved.root() != null && saved.root().tags().get("recipeBook") instanceof org.domi.furnace.server.net.io.nbt.NbtCompound book) {
            p.recipeBook().load(book, recipes);
        }
        send(ctx, p.recipeBook().initPacket());
        // 저장된 위치 있으면 그 자리, 없으면 seed 기반 월드 기본 스폰.
        SpawnPoint defaultSpawn = world.defaultSpawn();
        double sx = saved != null ? saved.x() : defaultSpawn.playerX();
        double sy = saved != null ? saved.y() : defaultSpawn.playerY();
        double sz = saved != null ? saved.z() : defaultSpawn.playerZ();
        float syaw = saved != null ? saved.yaw() : defaultSpawn.yaw();
        float spitch = saved != null ? saved.pitch() : 0f;
        if (saved != null) inv.load(saved.slots());
        menus = new ServerMenus(p);
        p.initMenus(new org.domi.furnace.server.menu.InventoryMenu(inv, menus));
        p.inventoryMenu().addSlotListener(menus);   // 바닐라 initInventoryMenu: 리스너(inventory_changed) 먼저, 동기기는 첫 패킷 때
        sy += 0.05; // 지면 살짝 위서 스폰 — 동기 청크 위로 살짝 떨어져 안착(clip/immovable 없이 자연 착지).
        spawnX = sx; spawnY = sy; spawnZ = sz; spawnYaw = syaw; spawnPitch = spitch;
        ix = sx; iy = sy; iz = sz; iyaw = syaw; ipitch = spitch; ig = true;
        // 바닐라 placeNewPlayer 처럼 레벨 정보(보더·시간·인벤토리)를 보내기 전에 접속자 목록에 올린다 — 명령·방송이 곧바로 이 플레이어를 본다.
        p.profileProperties(profileProperties);
        p.allowsListing(clientAllowsListing);
        if (login.core != null) {
            org.domi.furnace.server.ServerCore core0 = login.core;
            p.chat(new org.domi.furnace.server.chat.PlayerChatState(playerUuid, core0::enforceSecureProfile));
        }
        p.chat().visibility(chatVisibility);
        p.locale(clientLocale);
        p.remoteAddress(clientAddress(ctx));
        log.info("{}[{}] logged in with entity id {} at ({}, {}, {})", playerName, loggableAddress(ctx), eid, sx, sy, sz);
        p.publishPosition(sx, sy, sz, syaw, spitch, true);
        p.pushTeleport(sx, sy, sz);
        p.viewDistance(effVd);
        this.player = p;
        // 바닐라 placeNewPlayer: 위치 → server_data → 탭 목록(기존 접속자, 그다음 본인) → sendLevelInfo → 청크 중심 → 인벤토리
        send(ctx, new SyncPlayerPosition(sx, sy, sz, syaw, spitch, 1));
        pendingTeleportId = 1; // 스폰 청크 도착(타이머)까지 클라 이동 무시
        if (coreJ != null) send(ctx, new org.domi.furnace.server.net.protocol.play.ServerData(
                org.domi.furnace.api.text.Text.text(login.motd), login.faviconBytes()));
        // 기존 접속자 목록(없으면 빈 패킷 — 바닐라와 같다). 아직 adopt 전이라 p.listed()는 이 스레드 전용.
        java.util.List<org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate.Entry> existing = new java.util.ArrayList<>();
        for (Player b : world.onlinePlayers()) {
            if (b == p || b.isDisconnected()) continue;
            existing.add(tabEntry(b));
            p.listed().put(b.entityId(), b.uuid());
        }
        send(ctx, new org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate(existing));
        world.playerOnline(p);   // 탭 목록 대상(해제는 소유 region이 disconnect 처리 때). 다른 접속자에게는 그쪽 region 틱이 올린다.
        send(ctx, new org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate(java.util.List.of(tabEntry(p))));
        p.listed().put(p.entityId(), p.uuid());
        // 바닐라 sendLevelInfo: 보더 → 시간 → 월드 공유 스폰(나침반) → 비 → 청크 대기 → 틱 상태
        send(ctx, coreJ != null ? InitializeBorder.of(coreJ.border()) : InitializeBorder.vanillaDefault());
        if (coreJ != null) {
            send(ctx, coreJ.clock().timePacket(coreJ.gameRules()));
        } else {
            send(ctx, new SetTime(world.gameTime(), world.dayTime()));
        }
        send(ctx, new SetDefaultSpawnPosition(defaultSpawn.x(), defaultSpawn.y(), defaultSpawn.z(), defaultSpawn.yaw()));
        if (coreJ != null && coreJ.clock().isRaining()) {
            send(ctx, new GameEvent(GameEvent.START_RAINING, 0f));
            send(ctx, new GameEvent(GameEvent.RAIN_LEVEL_CHANGE, coreJ.clock().rainLevel()));
            send(ctx, new GameEvent(GameEvent.THUNDER_LEVEL_CHANGE, coreJ.clock().thunderLevel()));
        }
        send(ctx, new GameEvent(GameEvent.START_WAITING_FOR_CHUNKS, 0f));
        send(ctx, new org.domi.furnace.server.net.protocol.play.TickingState(20.0f, false));
        send(ctx, new org.domi.furnace.server.net.protocol.play.TickingStep(0));
        ChunkPos centerCp = ChunkPos.ofBlock(sx, sz);
        // SetCenterChunk 먼저 — 클라 viewCenter를 스폰으로 맞춰 스폰 청크가 "not in view range"로 안 버려지게.
        send(ctx, new SetCenterChunk(centerCp.x(), centerCp.z()));
        // 서버가 아는 인벤토리를 클라이언트에도 맞춘다(바닐라 initInventoryMenu).
        synchronized (p.menuLock()) { p.inventoryMenu().setSynchronizer(menus); }   // 첫 container_set_content(상태 번호 1)
        // 바닐라 첫 틱: 엔티티 데이터 → (ServerPlayer.tick 끝) 발전 과제 tick 트리거·flushDirty → (doTick) 체력·허기·경험치.
        // 발전 과제 쪽 패킷(레시피 ADD 등)은 Player.send 로 이벤트 루프 대기열에 들어가므로 뒤 패킷도 같은 대기열로 보낸다.
        var advancements = coreJ != null ? coreJ.attachAdvancements(p) : null;
        java.util.List<Packet> firstTick = vitals.joinPackets();
        int ft = 0;
        while (ft < firstTick.size() && firstTick.get(ft) instanceof org.domi.furnace.server.net.protocol.play.SetEntityData) send(ctx, firstTick.get(ft++));
        if (advancements != null) {
            advancements.trigger("minecraft:tick", null);
            advancements.flushDirty();
        }
        for (; ft < firstTick.size(); ft++) p.send(firstTick.get(ft));

        // 스폰 청크를 join 버스트에 동기 전송 — 클라가 발밑 청크 있는 채로 스폰(낙하 방지, M2 방식).
        // adopt 전이라 p.loadedChunks는 이 스레드 전용(레이스 없음). region streamLoads는 이미 로드된 중앙을 건너뜀.
        // spawn-chunk-radius 반경을 nearest-first로 동기 전송. gen은 genPool 병렬, 여기선 순서대로 수거.
        int r = world.spawnChunkRadius();
        java.util.List<ChunkPos> spawnChunks = new java.util.ArrayList<>();
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                spawnChunks.add(new ChunkPos(centerCp.x() + dx, centerCp.z() + dz));
        spawnChunks.sort(java.util.Comparator.comparingInt(
                c -> Math.max(Math.abs(c.x() - centerCp.x()), Math.abs(c.z() - centerCp.z()))));
        // 스냅샷을 뜨기 전에 먼저 구독한다 — 구독이 청크 전달 전까지 블록 변경을 보류했다가 청크 뒤에 보낸다.
        // (예전엔 청크를 보낸 뒤 구독해, 스냅샷과 구독 사이의 편집이 아예 전달되지 않았다.)
        java.util.Map<ChunkPos, World.Subscription> subs = new java.util.HashMap<>();
        for (ChunkPos cp : spawnChunks) subs.put(cp, world.subscribeChunk(cp, p));
        java.util.List<java.util.concurrent.CompletableFuture<byte[]>> futs =
                spawnChunks.stream().map(world::serializedChunkBody).toList();
        sendSpawnChunkBatchAsync(ctx, p, spawnChunks, subs, futs, sx, sz);
        scheduleKeepAlive();
        scheduleAutosave(ctx);
        if (login.core == null) scheduleTimeUpdate(ctx); // 서버 핸들이 있으면 전역 틱이 20틱마다 동기화한다
        scheduleSpawnGateRelease();
        if (login.core != null) announceJoin(login.core, p);
    }

    /** 스폰 청크를 비동기로 수거 후 event loop에서 batch 전송 — Netty 스레드 블로킹(future.get) 제거.
     *  재접속처럼 gen이 2초를 넘겨도 TimeoutException 없이, 완료되면 전송. 완료 후 region에 adopt. */
    /** 서버 아이템 → 전리품 스택(조건 비교용) — 컴포넌트 패치까지. */
    private org.domi.furnace.server.loot.LootStack lootStack(ItemStack s) {
        var items = world.itemRegistry();
        if (s == null || s.isEmpty() || items == null) return org.domi.furnace.server.loot.LootStack.empty();
        String name = items.nameOf(s.itemId());
        return name == null ? org.domi.furnace.server.loot.LootStack.empty()
                : org.domi.furnace.server.loot.LootStack.of(name, s.count(), s.patch().entries());
    }

    /** 바닐라 Inventory 칸(0..8 핫바, 9..35 주 인벤토리, 36..39 발·다리·가슴·머리, 40 왼손) → 창 칸. */
    private static int inventoryToWindowSlot(int i) {
        if (i <= 8) return i + 36;
        if (i <= 35) return i;
        if (i <= 39) return 8 - (i - 36);
        return 45;
    }

    /** 바닐라 inventory_changed — 플레이어 인벤토리 칸이 바뀔 때마다(제작 칸 제외). */
    private void fireInventoryChanged(int windowSlot) {
        Player p = player;
        var adv = p == null ? null : p.advancements();
        if (adv == null || windowSlot < 5 || windowSlot > 45) return;
        ItemStack[] now = this.inv.snapshot();
        org.domi.furnace.server.loot.LootStack[] inv = new org.domi.furnace.server.loot.LootStack[41];
        for (int i = 0; i < inv.length; i++) inv[i] = lootStack(now[inventoryToWindowSlot(i)]);
        adv.trigger("minecraft:inventory_changed",
                new org.domi.furnace.server.advancement.Criterion.InventoryEvent(inv, lootStack(now[windowSlot])));
    }


    /** 탭 목록 항목(바닐라 ClientboundPlayerInfoUpdatePacket.createPlayerInitializing 의 한 줄). */
    private static org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate.Entry tabEntry(Player b) {
        var session = b.chat().session();
        return new org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate.Entry(b.uuid(), b.name(), b.profileProperties(),
                session == null ? null : session.asData(), b.gameMode().id(), b.latencyMs());
    }

    private void sendSpawnChunkBatchAsync(ChannelHandlerContext ctx, Player p,
            java.util.List<ChunkPos> spawnChunks,
            java.util.Map<ChunkPos, World.Subscription> subs,
            java.util.List<java.util.concurrent.CompletableFuture<byte[]>> futs,
            double sx, double sz) {
        java.util.concurrent.CompletableFuture
                .allOf(futs.toArray(new java.util.concurrent.CompletableFuture[0]))
                .whenComplete((v, err) -> {
                    if (!ctx.channel().isActive()) {
                        // adopt 전이라 region이 구독을 정리해 주지 않는다 — 직접 해제해 누수를 막는다.
                        for (ChunkPos cp : spawnChunks) world.unsubscribeChunk(cp, p);
                        return;
                    }
                    ctx.channel().eventLoop().execute(() -> {
                        send(ctx, new ChunkBatchStart());
                        int sent = 0;
                        for (int i = 0; i < spawnChunks.size(); i++) {
                            ChunkPos cp = spawnChunks.get(i);
                            try {
                                byte[] bodyBytes = futs.get(i).getNow(null);
                                if (bodyBytes == null) { world.unsubscribeChunk(cp, p); continue; }
                                send(ctx, new LevelChunkWithLight(Unpooled.wrappedBuffer(bodyBytes)));
                                p.loadedChunks().add(cp);
                                world.chunkDelivered(cp, p, subs.get(cp));   // 청크를 쓴 뒤 보류된 변경을 흘려보낸다
                                sent++;
                            } catch (Exception e) {
                                world.unsubscribeChunk(cp, p);
                                log.warn("스폰 청크 전송 실패 {}", cp, e);
                            }
                        }
                        send(ctx, new ChunkBatchFinished(sent));
                        world.runAt(sx, sz, r -> r.adopt(p));
                        spawnChunksDelivered = true;
                        maybeReleaseSpawnGate();
                    });
                });
    }

    /** confirm과 스폰 청크 전송 완료 둘 다 충족했을 때만 게이트 해제. 채널 이벤트루프에서만 호출됨. */
    private void maybeReleaseSpawnGate() {
        if (shouldReleaseSpawnGate(spawnConfirmReceived, spawnChunksDelivered)) {
            releaseSpawnGate();
        }
    }

    /** 순수 게이트 판정 — confirm 왕복과 스폰 청크 배치 전송이 둘 다 끝났을 때만 해제. */
    static boolean shouldReleaseSpawnGate(boolean confirmReceived, boolean chunksDelivered) {
        return confirmReceived && chunksDelivered;
    }

    /** 게이트 해제 fallback(6틱) — confirm+청크완료가 그전에 둘 다 안 갖춰지면 무조건 해제(위치 재확정 포함). */
    private void scheduleSpawnGateRelease() {
        scheduler.global().executeDelayed(() -> {
            if (ctx == null || !ctx.channel().isActive()) return;
            ctx.channel().eventLoop().execute(this::releaseSpawnGate);
        }, 6);
    }

    private void releaseSpawnGate() {
        if (pendingTeleportId == -1) return;
        // 스폰 좌표로 위치 재확정(클라가 갭 중 떨어졌으면 끌어올림) 후 클라 이동 수용 시작.
        send(ctx, new SyncPlayerPosition(spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, 2));
        pendingTeleportId = -1;
        ix = spawnX; iy = spawnY; iz = spawnZ; iyaw = spawnYaw; ipitch = spawnPitch; ig = true;   // 클라가 스폰으로 되돌아간다
        Player p = player;
        if (p != null) {
            p.pushTeleport(ix, iy, iz);
            publishInput(p);
        }
    }

    private void handlePlay(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (packetId) {
            case 0x00 -> { // Confirm Teleportation: 스폰 텔포 confirm이면 spawn 좌표로 재고정+게이트 해제.
                // bare 해제(pendingTeleportId=-1)만 하면 갭 중 클라 추락이 보정 안 됨 → 땅 끼임.
                // Pumpkin식: confirm 시 위치를 텔포 목표로 되돌림(releaseSpawnGate가 SyncPlayerPosition+publish).
                // 스폰 청크 전송은 비동기(sendSpawnChunkBatchAsync)라 confirm이 먼저 올 수 있음 —
                // confirm만으로는 해제 안 하고 스폰 청크 배치 전송 완료(spawnChunksDelivered)까지 기다림(maybeReleaseSpawnGate).
                int tpId = Types.readVarInt(frame);
                if (tpId == pendingTeleportId) {
                    spawnConfirmReceived = true;
                    maybeReleaseSpawnGate();
                } else if (tpId == correctionTpId) {
                    correctionTpId = -1;   // 보정 완료 — 이후 위치 패킷 수락 재개
                }
            }
            case 0x0A -> readClientInformation(frame); // Client Information(play): 게임중 렌더거리 변경
            case 0x03 -> handleChatAck(Types.readVarInt(frame));                    // chat_ack
            case 0x04 -> handleChatCommand(Types.readString(frame, 256));          // chat_command
            case 0x05 -> handleSignedChatCommand(frame);                            // chat_command_signed
            case 0x06 -> handleChat(frame);                                         // chat
            case 0x07 -> handleChatSessionUpdate(org.domi.furnace.server.chat.RemoteChatSession.Data.read(frame)); // chat_session_update
            case 0x0B -> handleCommandSuggestion(Types.readVarInt(frame), Types.readString(frame, 32500));
            case 0x18 -> { // Keep Alive (serverbound)
                long echoed = frame.readLong();
                if (echoed == lastKeepAliveId) {
                    awaitingKeepAlive.set(false);
                    Player lp = player;
                    if (lp != null) lp.recordLatency((int) Math.max(0, (System.nanoTime() - echoed) / 1_000_000L));
                }
            }
            case 0x1A -> { // move_player_pos: Double x,y,z, Boolean onGround
                double x = frame.readDouble(), y = frame.readDouble(), z = frame.readDouble();
                boolean g = frame.readBoolean();
                Player p = player;
                if (p != null && pendingTeleportId == -1 && acceptPosition(ctx, x, y, z)) {
                    ig = g;
                    publishInput(p);
                    p.pushMove(ix, iy, iz, ig);
                }
            }
            case 0x1B -> { // move_player_pos_rot: Double x,y,z, Float yaw,pitch, Boolean onGround
                double x = frame.readDouble(), y = frame.readDouble(), z = frame.readDouble();
                float yaw = frame.readFloat(), pitch = frame.readFloat();
                boolean g = frame.readBoolean();
                Player p = player;
                if (p != null && pendingTeleportId == -1 && acceptPosition(ctx, x, y, z)) {
                    iyaw = finiteOr0(yaw);
                    ipitch = finiteOr0(pitch);
                    ig = g;
                    publishInput(p);
                    p.pushMove(ix, iy, iz, ig);
                }
            }
            case 0x1C -> { // move_player_rot: Float yaw,pitch, Boolean onGround
                float yaw = frame.readFloat(), pitch = frame.readFloat();
                boolean g = frame.readBoolean();
                Player p = player;
                if (p != null && pendingTeleportId == -1 && correctionTpId == -1) {
                    iyaw = finiteOr0(yaw);
                    ipitch = finiteOr0(pitch);
                    ig = g;
                    publishInput(p);
                    p.pushMove(ix, iy, iz, ig);
                }
            }
            case 0x09 -> { // client_command: 0 = 리스폰, 1 = 통계 요청
                int action = Types.readVarInt(frame);
                if (action == 0) requestRespawn(ctx);
            }
            case 0x23 -> { // player_abilities(serverbound): 비행 시작/끝 — 비행 가능할 때만 반영
                byte flags = frame.readByte();
                Player p = player;
                if (p != null) p.vitals().setFlying((flags & 0x02) != 0);
            }
            case 0x25 -> { // player_command: eid, action, data
                Types.readVarInt(frame);
                int action = Types.readVarInt(frame);
                Types.readVarInt(frame);
                Player p = player;
                if (p != null) {
                    switch (action) {
                        case 0 -> p.sneaking(true);      // PRESS_SHIFT_KEY
                        case 1 -> p.sneaking(false);     // RELEASE_SHIFT_KEY
                        case 3 -> p.sprinting(true);     // START_SPRINTING
                        case 4 -> p.sprinting(false);    // STOP_SPRINTING
                        default -> { }
                    }
                }
            }
            case 0x24 -> { // player_action: 0 시작, 1 취소, 2 완료(파괴), 3·4 버리기, 5 사용 끝, 6 손 바꾸기
                int status = Types.readVarInt(frame);
                int[] bp = Types.readBlockPos(frame);
                frame.readByte();        // face
                int seq = Types.readVarInt(frame); // 예측 sequence — block_update 후 ack로 회신
                Player actor = player;
                if (actor != null && status >= 0 && status <= 2) handleBlockBreak(actor, status, bp[0], bp[1], bp[2], seq);
                if (actor != null && (status == 3 || status == 4) && actor.gameMode() != org.domi.furnace.server.world.GameMode.SPECTATOR) {
                    dropFromSelected(status == 3);   // DROP_ALL_ITEMS · DROP_ITEM
                }
            }
            case 0x28 -> { // recipe_book_change_settings: 책 종류, 열림, 필터
                int type = Types.readVarInt(frame);
                boolean open = frame.readBoolean(), filtering = frame.readBoolean();
                Player p = player;
                if (p != null) p.recipeBook().setBookSetting(type, open, filtering);
            }
            case 0x29 -> { // recipe_book_seen_recipe: 강조 해제
                var id = org.domi.furnace.server.data.ResourceId.tryParse(Types.readString(frame, 32767));
                Player p = player;
                if (p != null && id != null) p.recipeBook().removeHighlight(id);
            }
            case 0x2C -> { // seen_advancements: 0 탭 열기(+ id), 1 화면 닫기(바닐라는 닫기에 아무것도 하지 않는다)
                int action = Types.readVarInt(frame);
                if (action == 0) {
                    var tab = org.domi.furnace.server.data.ResourceId.tryParse(Types.readString(frame, 32767));
                    Player sp = player;
                    if (tab != null && sp != null && sp.advancements() != null && sp.advancements().manager().get(tab) != null) {
                        sp.advancements().setSelectedTab(tab);
                    }
                }
            }
            case 0x2F -> { // Set Held Item(serverbound): Short slot(0~8)
                int slot = frame.readShort();
                if (slot >= 0 && slot < 9) inv.setSelected(slot);
            }
            case 0x32 -> { // Set Creative Mode Slot: Short slot + Slot
                int slot = frame.readShort();
                ItemStack stack;
                try {
                    stack = readSlot(frame);
                    // 바닐라 ItemStack.validatedStreamCodec: 저장 코덱(개수 1..99)으로 다시 쓸 수 없으면 디코드 실패
                    if (!stack.isEmpty() && (stack.count() < 1 || stack.count() > 99)) {
                        throw new org.domi.furnace.server.item.CodecException("Value must be within range [1;99]: " + stack.count());
                    }
                } catch (org.domi.furnace.server.item.CodecException | IndexOutOfBoundsException e) {
                    protocolError(ctx, "Set Creative Mode Slot", e);   // 바닐라: 디코드 실패는 연결 종료
                    break;
                }
                // 바닐라: 슬롯 1..45만, 개수·아이템 유효. 비정상 값은 다른 플레이어(공유 상자 경유)까지 클라이언트를 깨뜨린다.
                Player cp = player;
                boolean creative = cp != null && cp.gameMode() == org.domi.furnace.server.world.GameMode.CREATIVE;
                if (creative && slot >= 1 && slot < org.domi.furnace.server.entity.PlayerInventory.SIZE && validStack(stack)) {
                    // 바닐라 handleSetCreativeModeSlot: 인벤토리 창의 칸에 놓고, 클라이언트가 이미 아는 값으로 받아들이고, 변경을 방송한다.
                    synchronized (cp.menuLock()) {
                        var im = cp.inventoryMenu();
                        im.getSlot(slot).setByPlayer(stack);
                        im.setRemoteSlot(slot, stack);
                        im.broadcastChanges();
                    }
                } else if (creative && slot < 0 && validStack(stack) && !stack.isEmpty() && cp.tryCreativeDrop()) {
                    spawnDrop(stack, false, true);   // 크리에이티브 창 밖으로 버리기 — player.drop(stack, true)
                }
            }
            case 0x38 -> { // Use Item On: hand, pos, face, cursorXYZ, insideBlock, sequence
                int hand = Types.readVarInt(frame);
                int[] bp = Types.readBlockPos(frame);
                int face = Types.readVarInt(frame);
                float cx = frame.readFloat(), cy = frame.readFloat(), cz = frame.readFloat();
                boolean inside = frame.readBoolean();
                int seq = Types.readVarInt(frame);
                if (hand < 0 || hand > 1 || face < 0 || face > 5) {
                    protocolError(ctx, "Use Item On", new IllegalArgumentException("bad enum"));
                    break;
                }
                if (seq < 0) {   // 바닐라 ackBlockChangesUpTo: 음수면 예외 → 연결 종료
                    protocolError(ctx, "Use Item On", new IllegalArgumentException("Expected packet sequence nr >= 0"));
                    break;
                }
                Player actor = player;
                if (actor != null) handleUseItemOn(ctx, actor, hand, bp, face, cx, cy, cz, inside, seq);
            }
            case 0x39 -> { // Use Item: hand, sequence, yRot, xRot
                int hand = Types.readVarInt(frame);
                int seq = Types.readVarInt(frame);
                float yRot = frame.readFloat(), xRot = frame.readFloat();
                if (hand < 0 || hand > 1) {
                    protocolError(ctx, "Use Item", new IllegalArgumentException("bad enum"));
                    break;
                }
                if (seq < 0) {
                    protocolError(ctx, "Use Item", new IllegalArgumentException("Expected packet sequence nr >= 0"));
                    break;
                }
                Player actor = player;
                if (actor != null) world.runAt(actor.x(), actor.z(), region -> region.handleUseItem(actor, hand, seq, yRot, xRot));
            }
            case 0x0E -> { // Container Click: containerId, stateId, slotNum, button, clickType, changedSlots, carried
                int containerId = Types.readVarInt(frame);
                int stateId = Types.readVarInt(frame);
                int slotNum = frame.readShort();
                int button = frame.readByte();
                int clickTypeId = Types.readVarInt(frame);
                int numChanged = Types.readVarInt(frame);
                if (numChanged < 0 || numChanged > MAX_CLICK_CHANGED_SLOTS) {   // 바닐라는 128 초과를 거부한다
                    ctx.close();
                    break;
                }
                java.util.Map<Integer, ItemStack> changed = new java.util.HashMap<>();
                ItemStack newCarried;
                try {
                    for (int i = 0; i < numChanged; i++) {
                        int changedSlot = frame.readShort();
                        changed.put(changedSlot, readSlot(frame));
                    }
                    newCarried = readSlot(frame);
                } catch (org.domi.furnace.server.item.CodecException | IndexOutOfBoundsException e) {
                    protocolError(ctx, "Container Click", e);   // 바닐라: 디코드 실패는 연결 종료
                    break;
                }
                var clickType = org.domi.furnace.server.menu.ClickType.byId(clickTypeId);
                if (clickType == null) {
                    protocolError(ctx, "Container Click", new IllegalArgumentException("clickType " + clickTypeId));
                    break;
                }
                boolean validClick = validStack(newCarried);
                for (ItemStack c : changed.values()) validClick &= validStack(c);
                if (!validClick) break; // 음수 개수·알 수 없는 아이템 등 비정상 — 패킷 폐기
                Player p = player;
                if (p != null) handleContainerClick(p, containerId, stateId, slotNum, button, clickType, changed, newCarried);
            }
            case 0x0F -> { // Container Close(serverbound): VarInt containerId — 바닐라는 번호를 보지 않고 열린 창을 닫는다(doCloseContainer)
                Types.readVarInt(frame);
                Player p = player;
                if (p != null && p.containerMenu() != null) handleContainerClose(p);
            }
            default -> { /* 그 외 무시 */ }
        }
    }

    /**
     * 블록 파괴 요청(player_action 0 시작·1 취소·2 완료 — 바닐라 handlePlayerAction → ServerPlayerGameMode.handleBlockBreakAction → ack).
     * 거리·높이·스폰 보호·게임 모드 판정과 서바이벌 진행도 검증·드롭은 그 플레이어의 region 에서(BlockBreaking).
     */
    private void handleBlockBreak(Player actor, int status, int bx, int by, int bz, int seq) {
        org.domi.furnace.server.ServerCore core = login.core;
        var pos = new org.domi.furnace.server.block.Pos(bx, by, bz);
        world.runAtRequested(bx, bz, actor.x(), actor.z(), region -> {
            // 이미 로드된 청크만 — 임의 좌표 요청이 청크를 새로 생성(수백 ms)시키지 못하게 한다.
            if (by >= ChunkColumn.MIN_Y && by <= ChunkColumn.MAX_Y && region.chunkLoaded(ChunkPos.ofBlock(bx, bz))) {
                boolean mayInteract = core == null || core.mayInteract(actor, bx, bz);
                region.handleBlockBreakAction(actor, pos, status, mayInteract);
            }
            region.level().ackBlockChangesUpTo(actor, seq);   // 바닐라: 처리 뒤 ack(실제 전송은 틱 끝 변경 방송 뒤)
        });
    }

    // ------------------------------------------------------------------ 컨테이너 메뉴(4-2)

    private ServerMenus menus;

    /**
     * 바닐라 ServerGamePacketListenerImpl.handleContainerClick: 서버가 클릭을 재현하고(AbstractContainerMenu.clicked), 클라이언트가 보고한
     * 바뀐 칸·커서는 "클라이언트가 이미 아는 값"으로만 받아들여 서버 결과와 같으면 추가 패킷이 나가지 않게 한다. stateId 가 다르면 전체를 다시 보낸다.
     * 블록에 매인 창(상자)은 그 블록을 가진 region 스레드에서 처리한다 — 같은 상자에 대한 클릭·닫기가 그 region 큐에서 차례로 돈다.
     */
    private void handleContainerClick(Player p, int containerId, int stateId, int slotNum, int button,
            org.domi.furnace.server.menu.ClickType type, java.util.Map<Integer, ItemStack> changed, ItemStack carried) {
        var menu = p.containerMenu();
        if (menu == null || menu.containerId != containerId) return;   // 바닐라: 열린 창과 다르면 무시
        Runnable body = () -> applyContainerClick(p, menu, stateId, slotNum, button, type, changed, carried);
        int[] bp = menu.containerBlockPos();
        if (bp == null) {
            synchronized (p.menuLock()) { body.run(); }
        } else {
            world.runAt(bp[0], bp[2], region -> runOnBlockRegion(region, p, menu, bp, body));
        }
    }

    private void applyContainerClick(Player p, org.domi.furnace.server.menu.AbstractContainerMenu menu, int stateId, int slotNum, int button,
            org.domi.furnace.server.menu.ClickType type, java.util.Map<Integer, ItemStack> changed, ItemStack carried) {
        if (p.containerMenu() != menu) return;   // 그 사이 닫혔다
        try {
            if (p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) {
                menu.sendAllDataToRemote();
            } else if (!menu.stillValid(menus)) {
                log.debug("Player {} interacted with invalid menu {}", p.name(), menu);
            } else if (!menu.isValidSlotIndex(slotNum)) {
                log.debug("Player {} clicked invalid slot index: {}, available slots: {}", p.name(), slotNum, menu.slots.size());
            } else {
                boolean stateMismatch = stateId != menu.getStateId();
                menu.suppressRemoteUpdates();
                menu.clicked(slotNum, button, type, menus);
                for (var e : changed.entrySet()) menu.setRemoteSlotNoCopy(e.getKey(), e.getValue());
                menu.setRemoteCarried(carried);
                menu.resumeRemoteUpdates();
                if (stateMismatch) menu.broadcastFullState();
                else menu.broadcastChanges();
            }
        } catch (RuntimeException e) {
            menu.resumeRemoteUpdates();
            log.warn("컨테이너 클릭 처리 중 오류({}), 연결 종료: {}", menu, e.toString());
            ctx.close();
        }
    }

    /** 블록에 매인 창의 작업을 그 블록의 region 스레드에서 돌린다 — 청크가 내려갔거나 창이 바뀌었으면 body 를 돌리지 않는다. */
    private void runOnBlockRegion(org.domi.furnace.server.world.Region region, Player p,
            org.domi.furnace.server.menu.AbstractContainerMenu menu, int[] bp, Runnable body) {
        synchronized (p.menuLock()) {
            if (p.containerMenu() != menu) return;
            if (!region.chunkLoaded(ChunkPos.ofBlock(bp[0], bp[2]))) {   // 청크 없이 상자를 읽거나 쓰지 않는다
                closeContainer(p);
                return;
            }
            org.domi.furnace.server.world.MenuBlockContext.runIn(region, body);
        }
    }

    /** 제작대 화면 열기(CraftingTableBlock.useWithoutItem → openMenu): 3×3 격자 + 결과 칸. */
    private void openCraftingTable(int x, int y, int z) {
        Player p = player;
        if (p == null) return;
        synchronized (p.menuLock()) {
            if (p.hasOpenContainer()) closeContainer(p);
            int containerId = p.nextContainerCounter();
            var menu = new org.domi.furnace.server.menu.CraftingMenu(containerId, p.inventoryMenu().inventoryContainer(), menus, new int[]{x, y, z}, () -> {
                // 제작대 블록이 아직 그 자리에 있는가 — 블록을 가진 region 스레드(MenuBlockContext 안)에서만 부른다
                var region = org.domi.furnace.server.world.MenuBlockContext.current();
                return region == null || "minecraft:crafting_table".equals(world.blockRegistry().nameOf(region.getBlock(x, y, z)));
            });
            send(ctx, new OpenScreen(containerId, menuTypeId(menu), Text.translatable("container.crafting")));
            menu.addSlotListener(menus);
            p.setContainerMenu(menu);
            menu.setSynchronizer(menus);
        }
    }

    /** minecraft:menu 레지스트리 번호(generic_9x1.. 이 앞에서부터 0..5). */
    private static int menuTypeId(org.domi.furnace.server.menu.AbstractContainerMenu menu) {
        String type = menu.menuType();
        Integer id = org.domi.furnace.server.item.ItemContext.installed() ? org.domi.furnace.server.item.ItemContext.id("minecraft:menu", type) : null;
        if (id != null) return id;
        if (type.startsWith("minecraft:generic_9x")) return type.charAt(type.length() - 1) - '1';
        throw new IllegalStateException("menu type id unknown: " + type);
    }

    /** 바닐라 handleContainerClose → ServerPlayer.doCloseContainer. */
    private void handleContainerClose(Player p) {
        var menu = p.containerMenu();
        int[] bp = menu.containerBlockPos();
        Runnable close = () -> {
            if (p.containerMenu() == menu) doCloseContainer(p);
        };
        if (bp == null) {
            synchronized (p.menuLock()) { close.run(); }
        } else {
            world.runAt(bp[0], bp[2], region -> runOnBlockRegion(region, p, menu, bp, close));
        }
    }

    /** ServerPlayer.doCloseContainer: 창의 removed(커서·제작 격자 돌려주기) → 동기 상태를 인벤토리 창에 넘기고 인벤토리 창으로 돌아간다. */
    private void doCloseContainer(Player p) {
        var menu = p.containerMenu();
        menu.removed(menus);
        p.inventoryMenu().transferState(menu);
        p.setContainerMenu(p.inventoryMenu());
    }

    /** ServerPlayer.closeContainer: 클라이언트에 닫으라고 알리고 닫는다. */
    private void closeContainer(Player p) {
        var menu = p.containerMenu();
        menus.out(new ContainerClose(menu.containerId));
        doCloseContainer(p);
    }

    /** 메뉴 ↔ 플레이어·연결 어댑터: MenuPlayer(메뉴가 플레이어에게 바라는 것) + MenuSynchronizer(패킷) + MenuListener(inventory_changed). */
    private final class ServerMenus implements org.domi.furnace.server.menu.MenuPlayer, org.domi.furnace.server.menu.MenuSynchronizer,
            org.domi.furnace.server.menu.MenuListener {
        private final Player p;

        ServerMenus(Player p) { this.p = p; }

        /** 이벤트 루프 안이면 바로, 아니면(region 스레드) 대기열로 — 접속 순서의 패킷은 바로 나가야 순서가 맞다. */
        void out(Packet pkt) {
            if (ctx.executor().inEventLoop()) send(ctx, pkt);
            else p.send(pkt);
        }

        // --- MenuPlayer
        @Override public boolean isCreative() { return p.gameMode() == org.domi.furnace.server.world.GameMode.CREATIVE; }
        @Override public boolean isSpectator() { return p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR; }
        @Override public boolean isDeadOrDying() { return p.vitals().isDeadOrDying(); }
        @Override public boolean hasDisconnected() { return !ctx.channel().isActive(); }

        @Override public void drop(ItemStack stack, boolean includeThrower) {
            if (!stack.isEmpty()) spawnDrop(stack, false, includeThrower);
        }

        @Override public void placeItemBackInInventory(ItemStack stack) {
            if (stack.isEmpty()) return;
            ItemStack left = inv.add(stack, false, org.domi.furnace.server.item.ItemStackCodecs.maxStackSize(stack),
                    org.domi.furnace.server.item.ItemTools.damage(stack) > 0);
            if (!left.isEmpty()) spawnDrop(left, false, false);
        }

        @Override public ItemStack addToInventory(ItemStack stack) {
            return inv.add(stack, false, org.domi.furnace.server.item.ItemStackCodecs.maxStackSize(stack),
                    org.domi.furnace.server.item.ItemTools.damage(stack) > 0);
        }

        @Override public ItemStack inventoryItem(int i) { return inv.get(org.domi.furnace.server.entity.PlayerInventory.windowSlot(i)); }

        @Override public void setInventoryItem(int i, ItemStack s) { inv.set(org.domi.furnace.server.entity.PlayerInventory.windowSlot(i), s); }

        @Override public org.domi.furnace.server.menu.EquipSlot equipmentSlotFor(ItemStack s) {
            return org.domi.furnace.server.menu.EquipSlots.forItem(
                    org.domi.furnace.server.item.ItemContext.installed() ? org.domi.furnace.server.item.ItemContext.name("minecraft:item", s.itemId()) : null);
        }

        @Override public boolean hasBindingCurse(ItemStack s) {
            return org.domi.furnace.server.item.ItemTools.enchantmentLevel(s, "minecraft:binding_curse") > 0;
        }

        @Override public org.domi.furnace.server.recipe.RecipeManager recipes() {
            var c = login.core;
            return c == null ? null : c.recipes();
        }

        @Override public boolean doLimitedCrafting() {
            var c = login.core;
            return c != null && c.gameRules().getBoolean("doLimitedCrafting");
        }

        @Override public boolean hasRecipe(org.domi.furnace.server.data.ResourceId id) { return p.recipeBook().contains(id); }

        @Override public void awardRecipes(java.util.List<org.domi.furnace.server.data.ResourceId> ids) {
            var rm = recipes();
            if (rm != null) p.awardRecipes(ids, rm);
        }

        @Override public void triggerRecipeCrafted(org.domi.furnace.server.data.ResourceId recipe, java.util.List<ItemStack> grid) {
            var adv = p.advancements();
            if (adv == null) return;
            java.util.List<org.domi.furnace.server.loot.LootStack> items = new java.util.ArrayList<>(grid.size());
            for (ItemStack s : grid) items.add(lootStack(s));
            adv.trigger("minecraft:recipe_crafted", new org.domi.furnace.server.advancement.Criterion.RecipeCraftedEvent(recipe, items));
        }

        @Override public void onItemCrafted(ItemStack stack, int count) {
            // TODO(통계): crafted:<item> 통계를 count 만큼 올린다. TODO(아이템 제작 훅): Item.onCraftedBy(stack, level, player).
        }

        @Override public double distanceToSqr(double x, double y, double z) {
            double dx = p.x() - x, dy = p.y() - y, dz = p.z() - z;
            return dx * dx + dy * dy + dz * dz;
        }

        // --- MenuSynchronizer (바닐라 ServerPlayer.containerSynchronizer — 패킷마다 상태 번호를 올린다)
        @Override public void sendInitialData(org.domi.furnace.server.menu.AbstractContainerMenu m, java.util.List<ItemStack> items, ItemStack carried, int[] data) {
            out(new ContainerSetContent(m.containerId, m.incrementStateId(), items.toArray(new ItemStack[0]), carried));
            // TODO(메뉴 데이터): container_set_data — 화로·마법 부여 등 DataSlot 이 생기면 data[] 를 칸마다 보낸다.
        }

        @Override public void sendSlotChange(org.domi.furnace.server.menu.AbstractContainerMenu m, int slot, ItemStack stack) {
            out(new ContainerSetSlot(m.containerId, m.incrementStateId(), slot, stack));
        }

        @Override public void sendCarriedChange(org.domi.furnace.server.menu.AbstractContainerMenu m, ItemStack stack) {
            out(new ContainerSetSlot(-1, m.incrementStateId(), -1, stack));
        }

        @Override public void sendDataChange(org.domi.furnace.server.menu.AbstractContainerMenu m, int id, int value) {
            // TODO(메뉴 데이터): container_set_data(containerId, id, value)
        }

        // --- MenuListener (ServerPlayer.slotListener: 플레이어 인벤토리 칸이 바뀌면 inventory_changed)
        @Override public void slotChanged(org.domi.furnace.server.menu.AbstractContainerMenu m, int slotIndex, ItemStack stack) {
            var slot = m.getSlot(slotIndex);
            if (!(slot.container instanceof org.domi.furnace.server.menu.PlayerInventoryContainer)) return;
            int ws = org.domi.furnace.server.entity.PlayerInventory.windowSlot(slot.containerIndex());
            if (ctx.executor().inEventLoop()) fireInventoryChanged(ws);
            else ctx.channel().eventLoop().execute(() -> fireInventoryChanged(ws));
        }
    }

    private String previousName;
    private Text kickReason;   // 우리가 끊은 사유(퇴장 로그 "lost connection: 사유"용)

    /** 바닐라 접속 거부 사유 → PlayerLoginEvent 결과 종류. */
    private static org.domi.furnace.api.event.player.PlayerLoginEvent.Result loginResult(Text reason) {
        if (reason == null) return org.domi.furnace.api.event.player.PlayerLoginEvent.Result.ALLOWED;
        String key = reason.translationKey();
        if (key == null) return org.domi.furnace.api.event.player.PlayerLoginEvent.Result.KICK_OTHER;
        if (key.startsWith("multiplayer.disconnect.banned")) return org.domi.furnace.api.event.player.PlayerLoginEvent.Result.KICK_BANNED;
        if (key.equals("multiplayer.disconnect.not_whitelisted")) return org.domi.furnace.api.event.player.PlayerLoginEvent.Result.KICK_WHITELIST;
        if (key.equals("multiplayer.disconnect.server_full")) return org.domi.furnace.api.event.player.PlayerLoginEvent.Result.KICK_FULL;
        return org.domi.furnace.api.event.player.PlayerLoginEvent.Result.KICK_OTHER;
    }

    /** 바닐라 placeNewPlayer 의 접속 알림(PlayerJoinEvent 로 바꿀 수 있다). 접속자 본인도 받는다(Paper 와 같다). */
    private void announceJoin(org.domi.furnace.server.ServerCore core, Player p) {
        Text msg = previousName == null || previousName.equalsIgnoreCase(p.name())
                ? Text.translatable("multiplayer.player.joined", p.displayName())
                : Text.translatable("multiplayer.player.joined.renamed", p.displayName(), previousName);
        msg = msg.color(org.domi.furnace.api.text.NamedTextColor.YELLOW);
        if (core.events().hasSubscribers(org.domi.furnace.api.event.player.PlayerJoinEvent.class)) {
            msg = core.events().call(new org.domi.furnace.api.event.player.PlayerJoinEvent(core.plugins().player(p), msg)).joinMessage();
        }
        if (msg != null) core.broadcastSystemMessage(msg, false);
    }

    /** 죽었을 때(소유 연결 이벤트 루프): keepInventory 가 아니면 인벤토리를 비운다(바닐라 dropAll — 아이템 엔티티는 3단계). */
    private void onPlayerDied(boolean keepInventory) {
        Player p = player;
        if (p == null) return;
        synchronized (p.menuLock()) {
            if (p.hasOpenContainer()) closeContainer(p);
        }
        if (!keepInventory) {
            // Inventory.dropAll(소멸 저주는 먼저 지운다): 주 인벤토리 → 갑옷(발·다리·가슴·머리) → 왼손 순으로 아무 쪽으로 던진다
            java.util.List<ItemStack> drops = new java.util.ArrayList<>();
            for (int i = 0; i <= 40; i++) {
                int ws = org.domi.furnace.server.entity.PlayerInventory.windowSlot(i);
                ItemStack s = inv.get(ws);
                if (s.isEmpty()) continue;
                inv.set(ws, ItemStack.EMPTY);
                if (org.domi.furnace.server.item.ItemTools.enchantmentLevel(s, "minecraft:vanishing_curse") > 0) continue;
                drops.add(s);
            }
            if (!drops.isEmpty()) world.runAt(p.x(), p.z(), region -> { for (ItemStack s : drops) region.playerDrop(p, s, true, false); });
        }
    }

    /** ServerPlayer.drop(all): 든 칸에서 하나(또는 다) 꺼내 던진다. 열린 창에서 그 칸을 찾아, 클라이언트가 이미 비운 값을 아는 것으로 맞춘다(setRemoteSlot). */
    private void dropFromSelected(boolean all) {
        Player p = player;
        int ws = inv.heldWindowSlot();
        ItemStack held = inv.get(ws);
        ItemStack left = held;
        if (!held.isEmpty()) {
            int n = all ? held.count() : 1;
            left = held.withCount(held.count() - n);
            if (!inv.replace(ws, held, left)) return;
            fireInventoryChanged(ws);
            spawnDrop(held.withCount(n), false, true);
        }
        if (p != null && p.containerMenu() != null) {
            synchronized (p.menuLock()) {
                var menu = p.containerMenu();
                int idx = menu.findSlot(p.inventoryMenu().inventoryContainer(), inv.selected());
                if (idx >= 0) menu.setRemoteSlot(idx, left);
            }
        }
    }

    /** 플레이어가 서 있는 칸의 region 에서 아이템을 던진다. */
    private void spawnDrop(ItemStack stack, boolean dropAround, boolean includeThrower) {
        Player p = player;
        if (p == null || stack.isEmpty()) return;
        world.runAt(p.x(), p.z(), region -> region.playerDrop(p, stack, dropAround, includeThrower));
    }

    /** client_command 0: 죽어 있으면 리스폰 지점을 정해(강제 스폰 지점은 그 블록을 소유 region 에서 확인) 리스폰한다. */
    private void requestRespawn(ChannelHandlerContext ctx) {
        Player p = player;
        if (p == null || !p.vitals().isDeadOrDying() || respawnPending) return;
        respawnPending = true;
        int[] rp = p.vitals().respawnPos();
        SpawnPoint spawn = world.defaultSpawn();
        if (rp == null || !p.vitals().respawnForced()) {
            // 침대·리스폰 정박기는 3단계 — 강제가 아닌 스폰 지점은 블록이 없으므로 "없음" 처리(바닐라 missingRespawnBlock)
            finishRespawn(ctx, spawn.playerX(), spawn.playerY(), spawn.playerZ(), spawn.yaw(), rp != null, true);
            return;
        }
        float angle = p.vitals().respawnAngle();
        world.runAt(rp[0], rp[2], region -> {
            var attrs = org.domi.furnace.server.world.BlockAttrs.get();
            int a = region.getBlock(rp[0], rp[1], rp[2]), b = region.getBlock(rp[0], rp[1] + 1, rp[2]);
            // Block.isPossibleToRespawnInThis: 단단하지 않고 액체가 아님(발·머리 두 칸)
            boolean ok = !attrs.isSolid(a) && !attrs.isLiquid(a) && !attrs.isSolid(b) && !attrs.isLiquid(b);
            ctx.channel().eventLoop().execute(() -> {
                if (ok) finishRespawn(ctx, rp[0] + 0.5, rp[1] + 0.1, rp[2] + 0.5, angle, false, false);
                else finishRespawn(ctx, spawn.playerX(), spawn.playerY(), spawn.playerZ(), spawn.yaw(), true, true);
            });
        });
    }

    private boolean respawnPending;

    /** 바닐라 PlayerList.respawn 이 보내는 순서대로 — 능력, (스폰 블록 없음), Respawn, 위치, 스폰 지점, 난이도, 경험치, 레벨 정보, 권한, 인벤토리. */
    private void finishRespawn(ChannelHandlerContext ctx, double x, double y, double z, float yaw, boolean missingRespawnBlock, boolean worldSpawn) {
        respawnPending = false;
        Player p = player;
        if (p == null || !ctx.channel().isActive() || !p.vitals().isDeadOrDying()) return;
        org.domi.furnace.server.ServerCore core = login.core;
        float pitch = 0f;
        if (core != null && core.events().hasSubscribers(org.domi.furnace.api.event.player.PlayerRespawnEvent.class)) {
            var ev = core.events().call(new org.domi.furnace.api.event.player.PlayerRespawnEvent(core.plugins().player(p),
                    new org.domi.furnace.api.world.Location("minecraft:overworld", x, y, z, yaw, 0f), worldSpawn));
            var l = ev.respawnLocation();
            x = l.x(); y = l.y(); z = l.z(); yaw = l.yaw(); pitch = l.pitch();
        }
        boolean keepInventory = core != null && core.gameRules().getBoolean("keepInventory");
        var vitals = p.vitals();
        vitals.respawn();
        p.bumpSpawnGeneration();
        send(ctx, vitals.abilitiesPacket());
        if (missingRespawnBlock) send(ctx, new GameEvent(GameEvent.NO_RESPAWN_BLOCK_AVAILABLE, 0f));
        var prev = vitals.previousGameMode();
        long hashedSeed = core != null ? LoginPlay.obfuscateSeed(core.levelData().seed) : 0L;
        int dimIndex = Math.max(0, REGISTRIES.indexOf("minecraft:dimension_type", "minecraft:overworld"));
        send(ctx, new org.domi.furnace.server.net.protocol.play.Respawn(dimIndex, "minecraft:overworld", hashedSeed,
                vitals.gameMode().id(), prev == null ? -1 : prev.id(), false, vitals.lastDeathDimension(), vitals.lastDeathPos(), 0, (byte) 0));
        // 서버가 정한 위치로 텔레포트 — 확인 전까지 클라 이동은 받지 않는다.
        p.clearMoves();
        ix = x; iy = y; iz = z; iyaw = yaw; ipitch = pitch; ig = false;
        correctionTpId = ++teleportId;
        send(ctx, new SyncPlayerPosition(x, y, z, yaw, pitch, correctionTpId));
        p.pushTeleport(x, y, z);
        publishInput(p);
        SpawnPoint spawn = world.defaultSpawn();
        send(ctx, new SetDefaultSpawnPosition(spawn.x(), spawn.y(), spawn.z(), spawn.yaw()));
        if (core != null) {
            send(ctx, new ChangeDifficulty(core.levelData().difficulty.id(), core.levelData().difficultyLocked));
        }
        send(ctx, vitals.experiencePacket());
        // sendLevelInfo
        send(ctx, core != null ? InitializeBorder.of(core.border()) : InitializeBorder.vanillaDefault());
        if (core != null) {
            send(ctx, core.clock().timePacket(core.gameRules()));
        } else {
            send(ctx, new SetTime(world.gameTime(), world.dayTime()));
        }
        send(ctx, new SetDefaultSpawnPosition(spawn.x(), spawn.y(), spawn.z(), spawn.yaw()));
        if (core != null && core.clock().isRaining()) {
            send(ctx, new GameEvent(GameEvent.START_RAINING, 0f));
            send(ctx, new GameEvent(GameEvent.RAIN_LEVEL_CHANGE, core.clock().rainLevel()));
            send(ctx, new GameEvent(GameEvent.THUNDER_LEVEL_CHANGE, core.clock().thunderLevel()));
        }
        send(ctx, new GameEvent(GameEvent.START_WAITING_FOR_CHUNKS, 0f));
        if (core != null) core.sendPlayerPermissionLevel(p);
        synchronized (p.menuLock()) { p.inventoryMenu().sendAllDataToRemote(); }
        send(ctx, new SetHeldSlot(inv.selected()));
    }

    /** 마지막으로 수락한 위치·회전·onGround의 합을 발행한다(region 스레드가 다음 틱에 drain). */
    private void publishInput(Player p) {
        p.publishPosition(ix, iy, iz, iyaw, ipitch, ig);
    }

    private static float finiteOr0(float v) {
        return Float.isFinite(v) ? v : 0f;
    }

    /**
     * 이동 수용 검사. 통과하면 수락 위치로 기록하고 true. 비유한·월드 경계 밖·패킷당 {@link #MAX_STEP} 초과 이동이면
     * 마지막 수락 위치로 되돌리는 보정 텔레포트를 보내고 false(서버 권위). 보정 확인 전엔 위치 패킷을 조용히 버린다.
     */
    private boolean acceptPosition(ChannelHandlerContext ctx, double x, double y, double z) {
        if (correctionTpId != -1) return false;   // 보정 응답 대기 중 — 클라가 옛 위치로 보낸 패킷은 무시
        boolean ok = Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(x) <= MAX_COORD && Math.abs(y) <= MAX_COORD && Math.abs(z) <= MAX_COORD;
        if (ok) {
            double dx = x - ix, dy = y - iy, dz = z - iz;
            double d2 = dx * dx + dy * dy + dz * dz;
            ok = d2 <= MAX_STEP * MAX_STEP && moveBucket.tryAcquire(Math.sqrt(d2));
        }
        if (!ok) {
            correctionTpId = ++teleportId;
            // 회전도 IO 전용 iyaw/ipitch로 — Player.yaw()/pitch()는 region이 아직 drain 안 한 옛 값(비volatile, 다른 스레드 소유)이다.
            send(ctx, new SyncPlayerPosition(ix, iy, iz, iyaw, ipitch, correctionTpId));
            return false;
        }
        ix = x; iy = y; iz = z;
        return true;
    }

    /** 블록 요청(파괴/설치) 허용 여부 — 월드 높이 안이고, 마지막 수락 위치(눈 높이)에서 도달 거리 안인가. */
    private boolean blockRequestAllowed(int bx, int by, int bz) {
        if (by < ChunkColumn.MIN_Y || by > ChunkColumn.MAX_Y) return false;
        double dx = bx + 0.5 - ix, dy = by + 0.5 - (iy + 1.62), dz = bz + 0.5 - iz;
        return dx * dx + dy * dy + dz * dz <= REACH_SQ;
    }

    /**
     * 바닐라 ServerGamePacketListenerImpl.handleUseItemOn. 클릭한 칸의 region 스레드에서 거리·위치 검사 → useItemOn →
     * 클릭 칸과 면 너머 칸의 블록 갱신을 바로 보내고, ack 는 그 region 의 틱 끝(변경 방송 뒤)에 보낸다.
     * 손의 스택은 지금(연결 스레드) 잡은 사본으로 쓰고, 바뀐 손은 돌아와서 그 칸이 그대로일 때만 반영한다.
     */
    private void handleUseItemOn(ChannelHandlerContext ctx, Player actor, int hand, int[] bp, int face,
                                 float cx, float cy, float cz, boolean inside, int seq) {
        final int heldSlot = 36 + inv.selected();
        final int handSlot = hand == 0 ? heldSlot : 45;
        final ItemStack main = inv.get(heldSlot), off = inv.get(45);
        final ItemStack before = hand == 0 ? main : off;
        org.domi.furnace.server.ServerCore core = login.core;
        int permission = core == null ? 0 : core.profilePermissions(org.domi.furnace.server.ServerCore.profileOf(actor));
        var pos = new org.domi.furnace.server.block.Pos(bp[0], bp[1], bp[2]);
        var hit = new org.domi.furnace.server.block.Hit(pos, face, bp[0] + (double) cx, bp[1] + (double) cy, bp[2] + (double) cz, inside);
        world.runAtRequested(bp[0], bp[2], actor.x(), actor.z(), region -> {
            var level = region.level();
            level.ackBlockChangesUpTo(actor, seq);
            ItemStack head = inv.get(5);
            var pa = new org.domi.furnace.server.block.BreakerActor(actor, main, off, head, permission, false, heldSlot) {
                @Override
                protected void brokenEvent(int h, ItemStack broken) { region.broadcastEntityEvent(actor, (byte) (47 + h)); }

                @Override
                public void trigger(String criterion, org.domi.furnace.server.block.Pos p, ItemStack stack) {
                    fireBlockTrigger(actor, criterion, p.x(), p.y(), p.z(), level.getBlockState(p), stack);
                }

                @Override
                public void openChest(org.domi.furnace.server.block.Pos p) {
                    ctx.channel().eventLoop().execute(() -> ConnectionHandler.this.openChest(p.x(), p.y(), p.z()));
                }

                @Override
                public void openCraftingTable(org.domi.furnace.server.block.Pos p) {
                    ctx.channel().eventLoop().execute(() -> ConnectionHandler.this.openCraftingTable(p.x(), p.y(), p.z()));
                }

                @Override
                public boolean allowPlace(org.domi.furnace.server.block.Pos p, int state, org.domi.furnace.server.block.Pos against) {
                    org.domi.furnace.server.ServerCore pc = login.core;
                    if (pc == null || !pc.events().hasSubscribers(org.domi.furnace.api.event.block.BlockPlaceEvent.class)) return true;
                    var ev = pc.events().call(new org.domi.furnace.api.event.block.BlockPlaceEvent("minecraft:overworld",
                            new org.domi.furnace.api.world.BlockPos(p.x(), p.y(), p.z()), world.blockRegistry().nameOf(state),
                            new org.domi.furnace.api.world.BlockPos(against.x(), against.y(), against.z()), pc.plugins().player(actor)));
                    return !ev.isCancelled();
                }
            };
            pa.withInventory(actor.inventoryHost());
            if (!pa.canInteractWithBlock(pos, 1.0)) return;   // 바닐라: 조용히 무시(패킷 없음, ack 만)
            double dx = hit.x() - (bp[0] + 0.5), dy = hit.y() - (bp[1] + 0.5), dz = hit.z() - (bp[2] + 0.5);
            if (!(Math.abs(dx) < 1.0000001 && Math.abs(dy) < 1.0000001 && Math.abs(dz) < 1.0000001)) {
                log.warn("Rejecting UseItemOnPacket from {}: Location ({}, {}, {}) too far away from hit block {}.",
                        actor.name(), hit.x(), hit.y(), hit.z(), pos.toShortString());
                return;
            }
            int maxY = level.maxBuildHeight();
            if (bp[1] < maxY) {
                org.domi.furnace.server.ServerCore c = login.core;
                if (c == null || c.mayInteract(actor, bp[0], bp[2])) {
                    var r = org.domi.furnace.server.block.BlockPlacement.useItemOn(level, pa, hand, hit);
                    if (r.consumesAction()) pa.trigger("minecraft:any_block_use", pos, before);
                    if (face == org.domi.furnace.server.block.Dir.UP && !r.consumesAction() && bp[1] >= maxY - 1
                            && org.domi.furnace.server.block.BlockPlacement.wasBlockPlacementAttempt(pa, before)) {
                        actor.sendSystemMessage(org.domi.furnace.server.block.BlockPlacement.tooHigh(maxY - 1), true);
                    }
                    // shouldSwing 이면 바닐라는 다른 플레이어에게 팔 휘두르기를 보낸다 — 서버 쪽 설치는 CONSUME 이라 해당 없음
                }
            } else {
                actor.sendSystemMessage(org.domi.furnace.server.block.BlockPlacement.tooHigh(maxY - 1), true);
            }
            sendBlockState(actor, region, pos);
            sendBlockState(actor, region, pos.relative(face));
            for (int h = 0; h < 2; h++) {
                if (!pa.handChanged(h)) continue;
                int slot = h == 0 ? heldSlot : 45;
                ItemStack expect = h == 0 ? main : off, now = pa.itemInHand(h);
                inv.replace(slot, expect, now);
            }
            for (int ws = 5; ws <= 8; ws++) {
                if (!pa.armorChanged(ws)) continue;
                int slot = ws;
                ItemStack expect = pa.originalArmor(ws), now = pa.equipment(ws);
                inv.replace(slot, expect, now);
            }
        });
    }

    /** 그 칸의 지금 상태를 행위자에게(ClientboundBlockUpdatePacket(level, pos)). 다른 region 의 칸은 그 region 에서 읽어 보낸다. */
    private void sendBlockState(Player actor, Region here, org.domi.furnace.server.block.Pos p) {
        if (p.y() < ChunkColumn.MIN_Y || p.y() > ChunkColumn.MAX_Y) {
            actor.send(new org.domi.furnace.server.net.protocol.play.BlockUpdate(p.x(), p.y(), p.z(), here.level().getBlockState(p)));
            return;
        }
        if (here.level().hasChunkAt(p)) {
            actor.send(new org.domi.furnace.server.net.protocol.play.BlockUpdate(p.x(), p.y(), p.z(), here.level().getBlockState(p)));
            return;
        }
        // 다른 region 의 칸(합쳐지기 전). 주인이 없는 칸이면 클라도 그 청크가 없다 — 보낼 것 없음.
        if (world.regionizer().ownerOf(p.x() >> 4, p.z() >> 4) != null) world.runAt(p.x(), p.z(), r -> r.resendBlock(actor, p.x(), p.y(), p.z()));
    }

    /** 위치 기준 발전 과제(placed_block·item_used_on_block·default_block_use·any_block_use). */
    private void fireBlockTrigger(Player actor, String criterion, int x, int y, int z, int state, ItemStack stack) {
        var adv = actor.advancements();
        org.domi.furnace.server.ServerCore core = login.core;
        if (adv == null || core == null || core.dataPacks() == null || core.dataPacks().loot() == null) return;
        adv.trigger(criterion, new org.domi.furnace.server.advancement.Criterion.LocationEvent(
                core.locationContext(actor, x, y, z, world.blockRegistry().canonicalKeyOf(state), lootStack(stack))));
    }

    /** 거부한 블록 요청 — 클라 예측이 어긋나지 않게 (로드된 청크면) 실제 블록 상태를 되돌려 보내고 ack. */
    private void rejectBlockRequest(Player actor, int seq, int bx, int by, int bz) {
        if (by < ChunkColumn.MIN_Y || by > ChunkColumn.MAX_Y) {
            ackLater(actor, bx, bz, seq);
            return;
        }
        world.runAtRequested(bx, bz, actor.x(), actor.z(), r -> {
            r.resendBlock(actor, bx, by, bz);
            r.level().ackBlockChangesUpTo(actor, seq);
        });
    }

    /** 블록 요청 ack 를 그 칸 region 의 틱 끝으로(바닐라: 연결 틱 첫머리 — 그 틱의 블록 변경 방송 뒤). */
    private void ackLater(Player actor, int bx, int bz, int seq) {
        if (seq < 0) return;
        world.runAtRequested(bx, bz, actor.x(), actor.z(), r -> r.level().ackBlockChangesUpTo(actor, seq));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Player p = player;
        if (p != null) {
            org.domi.furnace.server.ServerCore core = login.core;
            if (core != null) {
                // 바닐라 onDisconnect → removePlayerFromWorld: "이름 lost connection: 사유" 로그, 노란 퇴장 알림.
                Text why = kickReason != null ? kickReason : p.kickReason() != null ? p.kickReason() : Text.translatable("disconnect.disconnected");
                log.info("{} lost connection: {}", p.name(), why.getString());
                Text msg = Text.translatable("multiplayer.player.left", p.displayName()).color(org.domi.furnace.api.text.NamedTextColor.YELLOW);
                if (core.events().hasSubscribers(org.domi.furnace.api.event.player.PlayerQuitEvent.class)) {
                    msg = core.events().call(new org.domi.furnace.api.event.player.PlayerQuitEvent(core.plugins().player(p), msg)).quitMessage();
                }
                world.playerOffline(p.entityId());   // 방송 대상에서 먼저 뺀다(바닐라: 퇴장자는 자기 퇴장 메시지를 받지 않는다)
                p.markDisconnected();
                if (msg != null) core.broadcastSystemMessage(msg, false);
            }
            // 바닐라 Player.remove: 인벤토리 창의 removed(커서·제작 격자는 끊긴 플레이어라 월드에 떨어진다)와 열린 창 닫기 — 제작 격자가 저장과 겹치지 않게 저장 전에.
            if (menus != null && p.inventoryMenu() != null) {
                synchronized (p.menuLock()) {
                    p.inventoryMenu().removed(menus);
                    if (p.hasOpenContainer()) doCloseContainer(p);
                }
            }
            savePlayerSnapshot();
            p.markDisconnected(); // 소유 region이 다음 틱에 정리(라우팅 불필요)
            world.playerOffline(p.entityId());   // adopt 전에 끊긴 경우에도 접속자 집합에서 즉시 제거(누수 방지, 관찰자는 다음 틱에 탭 제거)
        }
    }

    /**
     * 이벤트 루프가 가진 위치·인벤토리를 스냅샷 떠서 IO 풀로 저장한다(퇴장·자동저장 공용).
     * 시퀀스 번호로 풀에서 순서가 뒤바뀌어도 옛 스냅샷이 최신을 덮어쓰지 못한다. IO 풀은 종료 훅이 대기하므로 종료 시 저장도 마무리된다.
     */
    private void savePlayerSnapshot() {
        Player p = player;
        if (p == null) return;
        final ItemStack[] snapshot = inv.snapshot();
        final double sx = ix, sy = iy, sz = iz;     // IO 전용 최신 위치(Player.x()는 region drain 전이면 0,0,0)
        final float syaw = iyaw, spitch = ipitch;
        final boolean sg = ig;
        final var extra = p.vitals().saveData();
        extra.putInt("SelectedItemSlot", inv.selected());
        extra.putCompound("recipeBook", p.recipeBook().save());
        final long seq = org.domi.furnace.server.entity.PlayerData.nextSeq();
        final var advancements = p.advancements();
        try {
            world.ioPool().execute(() -> {
                try {
                    org.domi.furnace.server.entity.PlayerData.saveSequenced(seq, world.worldDir(), p.uuid(),
                            sx, sy, sz, syaw, spitch, sg, snapshot, extra);
                    if (advancements != null) advancements.save();   // 바닐라 savePlayerData: 플레이어 NBT·통계·발전 과제
                } catch (RuntimeException e) {
                    log.error("플레이어 저장 실패 {}", p.name(), e);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.error("플레이어 저장 거부(종료 중): {}", p.name());
        }
    }

    /** 바닐라처럼 20틱(1초)마다 월드 시간을 보낸다 — 모든 플레이어가 같은 시계를 본다. */
    private void scheduleTimeUpdate(ChannelHandlerContext c) {
        c.executor().schedule(() -> {
            if (!c.channel().isActive()) return;
            send(c, new SetTime(world.gameTime(), world.dayTime()));
            scheduleTimeUpdate(c);
        }, 1000, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /** 자동저장 주기(autosave-ticks)마다 접속 중인 플레이어를 저장한다. 연결이 닫히면 멈춘다. */
    private void scheduleAutosave(ChannelHandlerContext c) {
        long periodMs = Math.max(50L, (long) Math.min(world.autosaveTicks(), Integer.MAX_VALUE / 100) * 50L);
        if (world.autosaveTicks() == Integer.MAX_VALUE) return;   // 비활성(테스트 기본)
        c.executor().schedule(() -> {
            if (!c.channel().isActive()) return;
            savePlayerSnapshot();
            scheduleAutosave(c);
        }, periodMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private void scheduleKeepAlive() {
        // 타이밍은 global 스케줄러로(설계 결정), 실제 전송/검사는 채널 eventLoop로 되돌림.
        scheduler.global().executeDelayed(() -> {
            if (ctx == null || !ctx.channel().isActive()) return;
            ctx.channel().eventLoop().execute(this::sendKeepAlive);
        }, 300); // 15초(300틱)
    }

    private void sendKeepAlive() {
        if (awaitingKeepAlive.get()) {
            log.info("KeepAlive 응답 없음, 종료: {}", playerName);
            kick(ctx, Text.translatable("disconnect.timeout"));
            return;
        }
        lastKeepAliveId = System.nanoTime();
        awaitingKeepAlive.set(true);
        send(ctx, new KeepAliveClientbound(lastKeepAliveId));
        scheduleKeepAlive(); // 다음 주기 재예약
    }

    /** 응답 패킷 전송: [VarInt id][body] 프레임으로 인코딩 → FrameEncoder가 길이 prefix. */
    void send(ChannelHandlerContext ctx, Packet packet) {
        ByteBuf buf = ctx.alloc().buffer();
        Types.writeVarInt(buf, packet.id());
        packet.write(buf);
        ctx.writeAndFlush(buf);
    }

    /** 서버 종료: 이벤트 루프에서 현재 단계에 맞는 Disconnect로 사유를 알리고 닫는다. */
    void kickForShutdown() {
        ChannelHandlerContext c = this.ctx;
        if (c == null) return;
        c.channel().eventLoop().execute(() -> kick(c, Text.translatable("multiplayer.disconnect.server_shutdown")));
    }

    /** 바닐라 StringUtil.isValidPlayerName: 길이 1..16, 문자 코드 33..126만. */
    static boolean isValidPlayerName(String name) {
        if (name == null || name.isEmpty() || name.length() > 16) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c <= 32 || c >= 127) return false;
        }
        return true;
    }

    /** 현재 단계에 맞는 Disconnect 패킷으로 사유를 알리고 연결을 닫는다. 사유를 보낼 수 없는 단계면 그냥 닫는다. */
    void kick(ChannelHandlerContext ctx, String reason) {
        kick(ctx, Text.text(reason));
    }

    void kick(ChannelHandlerContext ctx, Text reason) {
        kickReason = reason;
        Packet pkt = switch (state) {
            case LOGIN -> new LoginDisconnect(reason);
            case CONFIGURATION -> new ConfigDisconnect(reason);
            case PLAY -> new Disconnect(reason);
            default -> null;
        };
        if (pkt == null) { ctx.close(); return; }
        ByteBuf buf = ctx.alloc().buffer();
        Types.writeVarInt(buf, pkt.id());
        pkt.write(buf);
        ctx.writeAndFlush(buf).addListener(io.netty.channel.ChannelFutureListener.CLOSE);
    }

    /** 같은 UUID로 이미 접속 중인 세션이 있으면 바닐라처럼 기존 세션을 끊는다(같은 playerdata 공유 방지). */
    private void kickDuplicateSession(java.util.UUID uuid) {
        for (Player other : world.onlinePlayers()) {
            if (other != player && !other.isDisconnected() && uuid.equals(other.uuid())) {
                other.kick(new Disconnect(Text.translatable("multiplayer.disconnect.duplicate_login")));
            }
        }
    }

    void state(ConnectionState newState) {
        this.state = newState;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
            log.debug("읽기 타임아웃, 종료: {}", playerName);   // 정상적인 유휴 정리 — 경고 로그 대상 아님
            kick(ctx, Text.translatable("disconnect.timeout"));
            return;
        }
        log.warn("커넥션 오류, 종료: {}", cause.toString());
        ctx.close();
    }

    private static final int MAX_CLICK_CHANGED_SLOTS = 128;
    private static final int MAX_STACK_COUNT = 99;

    /** 클라이언트가 보낸 아이템이 서버가 저장·재전송해도 안전한가(빈 스택은 허용). */
    private boolean validStack(ItemStack s) {
        if (s == null || s.isEmpty()) return s == null || s.count() == 0 || s.itemId() == 0;
        if (s.count() < 1 || s.count() > MAX_STACK_COUNT) return false;
        if (!world.isValidItem(s.itemId())) return false;
        // 바닐라 handleSetCreativeModeSlot: 최대 스택 크기를 넘는 아이템은 조용히 무시
        if (org.domi.furnace.server.item.ItemContext.installed() && s.count() > org.domi.furnace.server.item.ItemStackCodecs.maxStackSize(s)) return false;
        return true;
    }

    /** 아이템 스택(바닐라 ItemStack.OPTIONAL_STREAM_CODEC) — 컴포넌트 패치까지 정확히 읽는다. */
    private static ItemStack readSlot(ByteBuf frame) {
        return org.domi.furnace.server.item.ItemStackCodecs.readStack(frame);
    }

    /** 바닐라처럼 패킷 디코드 실패는 연결을 끊는다(클라이언트 화면: 네트워크 프로토콜 오류). */
    private void protocolError(ChannelHandlerContext ctx, String packet, RuntimeException e) {
        log.warn("{} 패킷 디코드 실패, 연결 종료: {}", packet, e.toString());
        ctx.close();
    }

    /**
     * Use Item On 이 상자를 클릭했을 때 화면 열기(ServerPlayer.openMenu): 열려 있던 창을 닫고, 번호를 올리고, ChestMenu 를 만들어
     * open_screen → 첫 container_set_content 를 보낸다. 싱글체스트만 — 더블체스트 병합은 TODO(더블 상자).
     */
    private void openChest(int x, int y, int z) {
        Player p = player;
        if (p == null) return;
        world.submitAtChunk(ChunkPos.ofBlock(x, z), region -> {
            ChestBlockEntity chest = region.chestAt(x, y, z);
            if (chest != null && chest.lootTable != null && login.core != null) {
                login.core.unpackLoot(chest, p);   // 바닐라 startOpen → unpackLootTable(player)
                region.markChestChanged(x, y, z);
            }
            return chest;
        }).thenAccept(chest -> ctx.channel().eventLoop().execute(() -> {
            if (chest == null) return;
            synchronized (p.menuLock()) {
                if (p.hasOpenContainer()) closeContainer(p);
                int containerId = p.nextContainerCounter();
                var menu = org.domi.furnace.server.menu.ChestMenu.threeRows(containerId, p.inventoryMenu().inventoryContainer(),
                        new org.domi.furnace.server.world.ChestContainer(chest), new int[]{x, y, z}, menus);
                send(ctx, new OpenScreen(containerId, menuTypeId(menu), Text.translatable("container.chest")));
                menu.addSlotListener(menus);
                p.setContainerMenu(menu);
                menu.setSynchronizer(menus);   // 첫 container_set_content
            }
        }));
    }

}
