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
import org.domi.furnace.server.net.protocol.play.Disconnect;
import org.domi.furnace.server.net.protocol.play.ContainerSetContent;
import org.domi.furnace.server.net.protocol.play.OpenScreen;
import org.domi.furnace.server.world.ChestBlockEntity;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.server.net.protocol.login.LoginDisconnect;
import org.domi.furnace.server.net.protocol.login.EncryptionRequest;
import org.domi.furnace.server.net.protocol.login.EncryptionResponse;
import org.domi.furnace.server.net.protocol.Text;
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
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.RegionGrid;
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
    private final ItemStack[] slots = new ItemStack[46];
    private int selectedSlot;                // 0~8
    private int nextContainerId = 1;         // 체스트 세션 id(1..100 순환, 0은 플레이어 자기 인벤 예약)
    private ItemStack carried = ItemStack.EMPTY; // Container Click의 carried(커서에 든 아이템)
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
        Arrays.fill(slots, ItemStack.EMPTY);
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
            p.send(new org.domi.furnace.server.net.protocol.play.SystemChat(Text.translatable("chat.disabled.options").color("red"), false));
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
        player.sendSystemMessage(Text.empty().append(e.component()).color("red"), false);
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
    private void handleChatCommand(String command) {
        Player p = player;
        org.domi.furnace.server.ServerCore core = login.core;
        if (p == null || core == null) return;
        tryHandleChat(command, () -> {
            log.info("{} issued server command: /{}", p.name(), command);
            var source = org.domi.furnace.server.command.CommandSource.player(core, p);
            var parse = core.commands().getDispatcher().parse(command, source);
            if (core.enforceSecureProfile() && org.domi.furnace.server.command.SignableCommand.hasSignableArguments(parse)) {
                log.error("Received unsigned command packet from {}, but the command requires signable arguments: {}", p.name(), command);
                p.sendSystemMessage(Text.translatable("chat.disabled.invalid_command_signature").color("red"), false);
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
            log.info("{} issued server command: /{}", p.name(), command);
            var source = org.domi.furnace.server.command.CommandSource.player(core, p);
            var parse = core.commands().getDispatcher().parse(command, source);
            java.util.Map<String, org.domi.furnace.server.chat.PlayerChatMessage> signed;
            try {
                signed = collectSignedArguments(command, sigs, org.domi.furnace.server.command.SignableCommand.of(parse), timeStamp, salt, lastSeen.get());
            } catch (org.domi.furnace.server.chat.SignedMessageChain.DecodeException e) {
                handleMessageDecodeFailure(e);
                return;
            }
            // 서명된 인자를 담은 소스로 다시 파싱한다(바닐라 Commands.mapSource 와 같은 효과).
            core.commands().performCommand(core.commands().getDispatcher().parse(command, source.withSignedArguments(signed)), command);
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
                Text.translatable("chat.disabled.invalid_command_signature").color("red"));
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
            disconnect(ctx, Text.literal("This server requires you to connect with Velocity."));
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
            disconnect(ctx, Text.literal("Unable to verify player details"));
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
        if (login.lists != null) {
            int online = 0;
            for (Player p : world.onlinePlayers()) if (!p.isDisconnected()) online++;
            Text reason = login.lists.canPlayerLogin(clientAddress(ctx), profile, online, login.maxPlayers);
            if (reason != null) { disconnect(ctx, reason); return; }
        }
        if (compressionThreshold >= 0) {
            send(ctx, new SetCompression(compressionThreshold)); // 비압축으로 나감
            ctx.pipeline().addAfter("frameDecoder", "decompress", new CompressionDecoder(compressionThreshold));
            ctx.pipeline().addAfter("frameEncoder", "compress", new CompressionEncoder(compressionThreshold));
        }
        kickDuplicateSession(playerUuid);
        if (login.profileCache != null) login.profileCache.add(profile);
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
                send(ctx, new UpdateTags(TAGS));   // 레지스트리 뒤, Finish 앞(바닐라 순서)
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
            Types.readString(frame, 16);                 // locale (무시)
            int vd = frame.readByte();                   // view distance (chunks)
            this.clientViewDistance = Math.max(Config.MIN_VIEW_DISTANCE, Math.min(Config.MAX_VIEW_DISTANCE, vd));
            Player p = this.player;
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
        // 바닐라처럼 서버의 view distance를 보낸다(클라 렌더 거리는 min(자기 설정, 이 값)). 스트리밍은 effVd(= min(서버, 클라 요청))로 한다.
        org.domi.furnace.server.ServerCore coreJ = login.core;
        if (coreJ != null) {
            var props = coreJ.settings().get();
            var rules = coreJ.gameRules();
            var level = coreJ.levelData();
            send(ctx, new LoginPlay(eid, level.hardcore, java.util.List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                    props.maxPlayers, world.viewDistance(), props.simulationDistance, rules.getBoolean("reducedDebugInfo"),
                    !rules.getBoolean("doImmediateRespawn"), rules.getBoolean("doLimitedCrafting"), dimIndex, "minecraft:overworld",
                    LoginPlay.obfuscateSeed(level.seed), org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate.DEFAULT_GAME_MODE, -1, false, coreJ.enforceSecureProfile()));
            send(ctx, new ChangeDifficulty(level.difficulty.id(), level.difficultyLocked));
        } else {
            send(ctx, new LoginPlay(eid, "minecraft:overworld", dimIndex, world.viewDistance()));
            send(ctx, new ChangeDifficulty(1, false));          // 바닐라 기본 easy
        }
        send(ctx, PlayerAbilities.creative());
        org.domi.furnace.server.ServerCore core = login.core;
        if (core != null) {
            // 바닐라 placeNewPlayer → sendPlayerPermissionLevel: 권한 레벨 이벤트 + 쓸 수 있는 명령 트리
            int level = core.profilePermissions(new org.domi.furnace.server.players.GameProfile(playerUuid, playerName));
            send(ctx, org.domi.furnace.server.net.protocol.play.EntityEvent.permissionLevel(eid, level));
            send(ctx, core.commands().treeFor(org.domi.furnace.server.command.CommandSource.forProfile(core, playerName, level)));
        }
        // 저장된 위치 있으면 그 자리, 없으면 seed 기반 월드 기본 스폰.
        org.domi.furnace.server.entity.PlayerData.Snapshot saved =
                org.domi.furnace.server.entity.PlayerData.load(world.worldDir(), playerUuid);
        SpawnPoint defaultSpawn = world.defaultSpawn();
        double sx = saved != null ? saved.x() : defaultSpawn.playerX();
        double sy = saved != null ? saved.y() : defaultSpawn.playerY();
        double sz = saved != null ? saved.z() : defaultSpawn.playerZ();
        float syaw = saved != null ? saved.yaw() : defaultSpawn.yaw();
        float spitch = saved != null ? saved.pitch() : 0f;
        if (saved != null) System.arraycopy(saved.slots(), 0, slots, 0, slots.length);
        sy += 0.05; // 지면 살짝 위서 스폰 — 동기 청크 위로 살짝 떨어져 안착(clip/immovable 없이 자연 착지).
        spawnX = sx; spawnY = sy; spawnZ = sz; spawnYaw = syaw; spawnPitch = spitch;
        ix = sx; iy = sy; iz = sz; iyaw = syaw; ipitch = spitch; ig = true;
        // GE13 먼저 — 클라가 "청크 대기" 상태(로딩화면 정지)에 들어간 뒤 위치를 받게 해 위치-유발 중력 억제.
        send(ctx, new GameEvent(GameEvent.START_WAITING_FOR_CHUNKS, 0f));
        send(ctx, new SyncPlayerPosition(sx, sy, sz, syaw, spitch, 1));
        pendingTeleportId = 1; // 스폰 청크 도착(타이머)까지 클라 이동 무시
        send(ctx, new SetDefaultSpawnPosition((int) sx, (int) sy, (int) sz));
        // 바닐라 sendLevelInfo: 현재 보더 상태(코어가 없는 테스트 경로는 기본 보더)
        send(ctx, coreJ != null ? InitializeBorder.of(coreJ.border()) : InitializeBorder.vanillaDefault());
        if (coreJ != null) {
            // 바닐라 sendLevelInfo: 시간, 그리고 비가 오면 비 시작 + 비·천둥 강도
            send(ctx, coreJ.clock().timePacket(coreJ.gameRules()));
            if (coreJ.clock().isRaining()) {
                send(ctx, new GameEvent(GameEvent.START_RAINING, 0f));
                send(ctx, new GameEvent(GameEvent.RAIN_LEVEL_CHANGE, coreJ.clock().rainLevel()));
                send(ctx, new GameEvent(GameEvent.THUNDER_LEVEL_CHANGE, coreJ.clock().thunderLevel()));
            }
        } else {
            send(ctx, new SetTime(world.gameTime(), world.dayTime()));
        }
        // 서버가 아는 인벤토리·선택 슬롯을 클라이언트에도 맞춘다(안 보내면 클라는 빈 손인데 서버는 아이템을 들고 있다).
        send(ctx, new ContainerSetContent(0, 0, copySlots(), ItemStack.EMPTY));
        send(ctx, new SetHeldSlot(selectedSlot));
        send(ctx, new SetHealth(20f, 20, 5f));
        send(ctx, new SetExperience(0f, 0, 0));

        // 플레이어를 스폰 region에 편입 — 첫 틱부터 청크/가시성 처리.
        Player p = new Player(eid, playerUuid, playerName, ctx.channel());
        p.profileProperties(profileProperties);
        p.allowsListing(clientAllowsListing);
        if (login.core != null) {
            org.domi.furnace.server.ServerCore core0 = login.core;
            p.chat(new org.domi.furnace.server.chat.PlayerChatState(playerUuid, core0::enforceSecureProfile));
        }
        p.chat().visibility(chatVisibility);
        p.remoteAddress(clientAddress(ctx));
        log.info("{}[{}] logged in with entity id {} at ({}, {}, {})", playerName, loggableAddress(ctx), eid, sx, sy, sz);
        p.publishPosition(sx, sy, sz, syaw, spitch, true);
        p.viewDistance(effVd);
        this.player = p;
        world.playerOnline(p);   // 탭 목록 대상(해제는 소유 region이 disconnect 처리 때)
        // 스폰 청크를 join 버스트에 동기 전송 — 클라가 발밑 청크 있는 채로 스폰(낙하 방지, M2 방식).
        // adopt 전이라 p.loadedChunks는 이 스레드 전용(레이스 없음). region streamLoads는 이미 로드된 중앙을 건너뜀.
        ChunkPos centerCp = ChunkPos.ofBlock(sx, sz);
        // SetCenterChunk 먼저 — 클라 viewCenter를 스폰으로 맞춰 스폰 청크가 "not in view range"로 안 버려지게.
        send(ctx, new SetCenterChunk(centerCp.x(), centerCp.z()));
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
    }

    /** 스폰 청크를 비동기로 수거 후 event loop에서 batch 전송 — Netty 스레드 블로킹(future.get) 제거.
     *  재접속처럼 gen이 2초를 넘겨도 TimeoutException 없이, 완료되면 전송. 완료 후 region에 adopt. */
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
                        RegionPos spawn = RegionGrid.regionPos(sx, sz, world.regionSize());
                        world.runOn(spawn, () -> world.regionAt(spawn).adopt(p));
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
        if (p != null) publishInput(p);
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
                }
            }
            case 0x1C -> { // move_player_rot: Float yaw,pitch, Boolean onGround
                float yaw = frame.readFloat(), pitch = frame.readFloat();
                boolean g = frame.readBoolean();
                Player p = player;
                if (p != null && pendingTeleportId == -1) {
                    iyaw = finiteOr0(yaw);
                    ipitch = finiteOr0(pitch);
                    ig = g;
                    publishInput(p);
                }
            }
            case 0x24 -> { // player_action (digging)
                int status = Types.readVarInt(frame);
                int[] bp = Types.readBlockPos(frame);
                frame.readByte();        // face
                int seq = Types.readVarInt(frame); // 예측 sequence — block_update 후 ack로 회신
                Player actor = player;
                if (status == 0) {       // start digging = 크리에이티브 즉시 파괴
                    int bx = bp[0], by = bp[1], bz = bp[2];
                    if (!blockRequestAllowed(bx, by, bz)) {
                        rejectBlockRequest(actor, seq, bx, by, bz);
                    } else {
                        RegionPos br = RegionGrid.regionPos(bx, bz, world.regionSize());
                        world.runOn(br, () -> {
                            Region region = world.regionAt(br);
                            // 이미 로드된 청크만 수정 — 임의 좌표 요청이 청크를 새로 생성(수백 ms)시키지 못하게 한다.
                            if (region.chunkLoaded(ChunkPos.ofBlock(bx, bz))) region.breakBlock(bx, by, bz);
                            actor.send(new BlockChangedAck(seq)); // block_update 다음에 ack → 클라 예측 reconcile
                        });
                    }
                } else {
                    actor.send(new BlockChangedAck(seq)); // 블록 변경 없음 — ack만(예측 정리)
                }
            }
            case 0x2F -> { // Set Held Item(serverbound): Short slot(0~8)
                int slot = frame.readShort();
                if (slot >= 0 && slot < 9) selectedSlot = slot;
            }
            case 0x32 -> { // Set Creative Mode Slot: Short slot + Slot
                int slot = frame.readShort();
                ItemStack stack = readSlot(frame);
                // 바닐라: 슬롯 1..45만, 개수·아이템 유효. 비정상 값은 다른 플레이어(공유 상자 경유)까지 클라이언트를 깨뜨린다.
                if (slot >= 1 && slot < slots.length && validStack(stack)) slots[slot] = stack;
            }
            case 0x38 -> { // Use Item On: hand, pos, face, cursorXYZ, insideBlock, sequence
                int hand = Types.readVarInt(frame);
                int[] bp = Types.readBlockPos(frame);
                int face = Types.readVarInt(frame);
                frame.readFloat(); frame.readFloat(); frame.readFloat(); // cursor X/Y/Z
                frame.readBoolean(); // insideBlock
                int seq = Types.readVarInt(frame); // 예측 sequence
                Player actor = player;
                int[] o0 = Region.placeOffset(face);
                if (!blockRequestAllowed(bp[0], bp[1], bp[2])) {
                    // 클릭 지점이 범위/도달 거리 밖 — 클라가 예측으로 놓았을 설치 위치를 되돌리고 ack.
                    rejectBlockRequest(actor, seq, bp[0] + o0[0], bp[1] + o0[1], bp[2] + o0[2]);
                    break;
                }
                // 클릭 블록 조회는 region 스레드서만(소유권) — 조회 후 netty로 복귀해 배치 여부 판단(§3.2).
                RegionPos clickedRp = RegionGrid.regionPos(bp[0], bp[2], world.regionSize());
                world.runOn(clickedRp, () -> {
                    Region clickedRegion = world.regionAt(clickedRp);
                    if (!clickedRegion.chunkLoaded(ChunkPos.ofBlock(bp[0], bp[2]))) { // 미로드 청크는 생성하지 않는다
                        actor.send(new BlockChangedAck(seq));
                        return;
                    }
                    int clickedState = clickedRegion.getBlock(bp[0], bp[1], bp[2]);
                    boolean isChest = "minecraft:chest".equals(world.blockRegistry().nameOf(clickedState));
                    ctx.channel().eventLoop().execute(() -> {
                        if (isChest) { // 배치 대신 열기(§3.2) — 더블체스트·방향 무관, 싱글체스트만
                            openChest(bp[0], bp[1], bp[2]);
                            actor.send(new BlockChangedAck(seq));
                            return;
                        }
                        ItemStack held = hand == 0 ? slots[36 + selectedSlot] : ItemStack.EMPTY;
                        int stateId = held.isEmpty() ? 0 : world.itemToBlockState(held.itemId());
                        if (stateId == 0) { // 오프핸드 / 비블록 아이템 — 변경 없음, ack만(클라 예측 정리)
                            actor.send(new BlockChangedAck(seq));
                            return;
                        }
                        int[] o = Region.placeOffset(face);
                        int px = bp[0] + o[0], py = bp[1] + o[1], pz = bp[2] + o[2];
                        if (py < ChunkColumn.MIN_Y || py > ChunkColumn.MAX_Y) { // 월드 높이 밖(예: 최상단 블록 위에 설치)
                            actor.send(new BlockChangedAck(seq));
                            return;
                        }
                        RegionPos rp = RegionGrid.regionPos(px, pz, world.regionSize());
                        world.runOn(rp, () -> {
                            Region region = world.regionAt(rp);
                            // 설치 대상이 미로드 청크(시야 가장자리 너머)면 생성하지 않고 거부한다.
                            if (region.chunkLoaded(ChunkPos.ofBlock(px, pz))) region.placeBlock(px, py, pz, stateId);
                            actor.send(new BlockChangedAck(seq)); // 설치(또는 충돌거부) 후 ack → 거부 시 고스트 reverts
                        });
                    });
                });
            }
            case 0x0E -> { // Container Click: containerId, stateId, slotNum, button, clickType, changedSlots, carried
                int containerId = Types.readVarInt(frame);
                Types.readVarInt(frame);  // stateId(§ 검증 생략, 항상 클라 예측 수용)
                frame.readShort();        // slotNum(사용 안 함 — changedSlots가 실제 변경 진실)
                frame.readByte();         // buttonNum
                int clickType = Types.readVarInt(frame);
                int numChanged = Types.readVarInt(frame);
                if (numChanged < 0 || numChanged > MAX_CLICK_CHANGED_SLOTS) {   // 바닐라는 128 초과를 거부한다
                    ctx.close();
                    break;
                }
                java.util.Map<Integer, ItemStack> changed = new java.util.HashMap<>();
                boolean parseOk = true;
                for (int i = 0; i < numChanged && parseOk; i++) {
                    int changedSlot = frame.readShort();
                    try {
                        changed.put(changedSlot, readSlotStrict(frame));
                    } catch (UnsupportedComponentException e) {
                        log.debug("Container Click: components 있는 아이템 — 파싱 범위 밖(§2.3), 이후 바이트 폐기");
                        parseOk = false;
                    }
                }
                ItemStack newCarried = null;
                if (parseOk) {
                    try {
                        newCarried = readSlotStrict(frame);
                    } catch (UnsupportedComponentException e) {
                        parseOk = false;   // 커서 아이템에 컴포넌트 — 예외로 연결을 끊지 말고 패킷만 폐기
                    }
                }
                if (!parseOk) break; // 경계 유실 — 이 패킷 전체 폐기(범위 밖 케이스)
                boolean validClick = validStack(newCarried);
                for (ItemStack c : changed.values()) validClick &= validStack(c);
                if (!validClick) break; // 음수 개수·알 수 없는 아이템 등 비정상 — 패킷 폐기
                Player p = player;
                int expectedContainerId = p.openContainerId();
                if (clickType != 0 || expectedContainerId == -1 || containerId != expectedContainerId) {
                    break; // PICKUP만 처리, 컨테이너 불일치·미오픈 무시(§3.3)
                }
                int[] chestPos = p.openChestPos();
                // 열린 체스트가 사거리 밖(텔레포트·보정 등)이면 클릭 전체를 폐기하고 창을 닫는다 — 체스트만 안 바뀌고 인벤토리만 바뀌는 복제 방지.
                if (chestPos != null && !blockRequestAllowed(chestPos[0], chestPos[1], chestPos[2])) {
                    p.closeChest();
                    send(ctx, new ContainerClose(expectedContainerId));
                    break;
                }
                this.carried = newCarried;
                java.util.Map<Integer, ItemStack> chestUpdates = new java.util.HashMap<>();
                for (var e : changed.entrySet()) {
                    int menuSlot = e.getKey();
                    if (menuSlot >= 0 && menuSlot < 27) {
                        chestUpdates.put(menuSlot, e.getValue());
                    } else if (menuSlot >= 27 && menuSlot <= 53) {
                        slots[9 + (menuSlot - 27)] = e.getValue();
                    } else if (menuSlot >= 54 && menuSlot <= 62) {
                        slots[36 + (menuSlot - 54)] = e.getValue();
                    }
                }
                if (!chestUpdates.isEmpty() && chestPos != null) {
                    RegionPos rp = RegionGrid.regionPos(chestPos[0], chestPos[2], world.regionSize());
                    world.runOn(rp, () -> {
                        Region region = world.regionAt(rp);
                        if (!region.chunkLoaded(ChunkPos.ofBlock(chestPos[0], chestPos[2]))) return; // 미로드 청크를 생성/로드하지 않는다
                        for (var e : chestUpdates.entrySet()) {
                            region.setChestItem(chestPos[0], chestPos[1], chestPos[2], e.getKey(), e.getValue());
                        }
                    });
                }
            }
            case 0x0F -> { // Container Close(serverbound): VarInt containerId
                Types.readVarInt(frame);
                Player p = player;
                if (p != null) p.closeChest();
            }
            default -> { /* 그 외 무시 */ }
        }
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

    /** 거부한 블록 요청 — 클라 예측이 어긋나지 않게 (로드된 청크면) 실제 블록 상태를 되돌려 보내고 ack. */
    private void rejectBlockRequest(Player actor, int seq, int bx, int by, int bz) {
        if (by < ChunkColumn.MIN_Y || by > ChunkColumn.MAX_Y) {
            actor.send(new BlockChangedAck(seq));
            return;
        }
        RegionPos rp = RegionGrid.regionPos(bx, bz, world.regionSize());
        world.runOn(rp, () -> {
            world.regionAt(rp).resendBlock(actor, bx, by, bz);
            actor.send(new BlockChangedAck(seq));
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Player p = player;
        if (p != null) {
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
        final ItemStack[] snapshot = copySlots();
        final double sx = ix, sy = iy, sz = iz;     // IO 전용 최신 위치(Player.x()는 region drain 전이면 0,0,0)
        final float syaw = iyaw, spitch = ipitch;
        final boolean sg = ig;
        final long seq = org.domi.furnace.server.entity.PlayerData.nextSeq();
        try {
            world.ioPool().execute(() -> {
                try {
                    org.domi.furnace.server.entity.PlayerData.saveSequenced(seq, world.worldDir(), p.uuid(),
                            sx, sy, sz, syaw, spitch, sg, snapshot);
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
        kick(ctx, Text.literal(reason));
    }

    void kick(ChannelHandlerContext ctx, Text reason) {
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
    private static final int MAX_COMPONENT_BYTES = 8192;

    /** 클라이언트가 보낸 아이템이 서버가 저장·재전송해도 안전한가(빈 스택은 허용). */
    private boolean validStack(ItemStack s) {
        if (s == null || s.isEmpty()) return s == null || s.count() == 0 || s.itemId() == 0;
        if (s.count() < 1 || s.count() > MAX_STACK_COUNT) return false;
        if (!world.isValidItem(s.itemId())) return false;
        byte[] comp = s.components();
        return comp == null || comp.length <= MAX_COMPONENT_BYTES;
    }

    private ItemStack readSlot(ByteBuf frame) {
        int count = Types.readVarInt(frame);
        if (count == 0) return ItemStack.EMPTY;
        int itemId = Types.readVarInt(frame);
        byte[] components = null;
        if (frame.isReadable()) {
            components = new byte[frame.readableBytes()];
            frame.readBytes(components);
        }
        return new ItemStack(itemId, count, components);
    }

    /**
     * 여러 Slot이 한 프레임에 연속으로 오는 패킷(Container Click)용 정밀 파싱(§2.3) —
     * numComponentsToAdd/Remove를 읽어 정확한 경계를 찾는다. 둘 다 0(컴포넌트 없음)인 경우만 지원 범위.
     * 컴포넌트가 실제로 붙은 아이템이면 이후 바이트 경계를 알 수 없어 예외로 신호한다.
     */
    private ItemStack readSlotStrict(ByteBuf frame) {
        int count = Types.readVarInt(frame);
        if (count == 0) return ItemStack.EMPTY;
        int itemId = Types.readVarInt(frame);
        int numAdd = Types.readVarInt(frame);
        int numRemove = Types.readVarInt(frame);
        if (numAdd != 0 || numRemove != 0) throw new UnsupportedComponentException();
        return new ItemStack(itemId, count, null);
    }

    /** readSlotStrict가 components 있는 아이템을 만났을 때 신호용(§2.3 범위 밖). */
    private static final class UnsupportedComponentException extends RuntimeException {}

    /** Use Item On이 체스트를 클릭했을 때 화면 열기(§3.2) — 싱글체스트만, 더블체스트 병합 없음. */
    private void openChest(int x, int y, int z) {
        Player p = player;
        if (p == null) return;
        int containerId = nextContainerId;
        nextContainerId = nextContainerId % 100 + 1; // 1..100 순환(vanilla 관례), 0은 자기 인벤 예약
        p.openChest(containerId, x, y, z);
        RegionPos rp = RegionGrid.regionPos(x, z, world.regionSize());
        scheduler.region(rp).submit(() -> {
            ChestBlockEntity chest = world.regionAt(rp).chestAt(x, y, z);
            ItemStack[] out = new ItemStack[ChestBlockEntity.SLOTS];
            if (chest != null) System.arraycopy(chest.items, 0, out, 0, ChestBlockEntity.SLOTS);
            else Arrays.fill(out, ItemStack.EMPTY);
            return out;
        }).thenAccept(chestItems -> ctx.channel().eventLoop().execute(() -> {
            send(ctx, new OpenScreen(containerId, 2, "Chest")); // menuTypeId=2 GENERIC_9x3(§0.2)
            ItemStack[] contents = new ItemStack[63]; // 0~26 체스트 + 27~53 main + 54~62 hotbar
            System.arraycopy(chestItems, 0, contents, 0, 27);
            System.arraycopy(slots, 9, contents, 27, 27);
            System.arraycopy(slots, 36, contents, 54, 9);
            send(ctx, new ContainerSetContent(containerId, 0, contents, carried));
        }));
    }

    private ItemStack[] copySlots() {
        ItemStack[] copy = new ItemStack[slots.length];
        System.arraycopy(slots, 0, copy, 0, slots.length);
        return copy;
    }
}
