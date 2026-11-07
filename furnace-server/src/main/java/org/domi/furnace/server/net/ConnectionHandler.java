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
import org.domi.furnace.server.net.protocol.configuration.ConfigTransfer;
import org.domi.furnace.server.net.protocol.configuration.CookieRequest;
import org.domi.furnace.server.net.protocol.configuration.ResourcePackPop;
import org.domi.furnace.server.net.protocol.configuration.StoreCookie;
import org.domi.furnace.server.net.protocol.configuration.FeatureFlags;
import org.domi.furnace.server.net.protocol.configuration.UpdateTags;
import org.domi.furnace.server.net.protocol.configuration.FinishConfiguration;
import org.domi.furnace.server.net.protocol.configuration.KnownPacksClientbound;
import org.domi.furnace.server.net.protocol.configuration.RegistryData;
import org.domi.furnace.server.net.protocol.handshake.Handshake;
import org.domi.furnace.server.net.protocol.play.BlockChangedAck;
import org.domi.furnace.server.net.protocol.play.ContainerClose;
import org.domi.furnace.server.net.protocol.play.ContainerSetContent;
import org.domi.furnace.server.net.protocol.play.ContainerSetData;
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
    /**
     * 클라이언트에 보내는 동기 레지스트리 — 서버가 시작 때 만든 세트(플러그인 바이옴·차원 타입, 데이터 팩·플러그인 변종·마법부여 포함).
     * 서버 없이 돌면 지금 걸린 세트(없으면 내장 1.21.1 목록).
     */
    private RegistrySet registries() {
        var core = login != null ? login.core : null;
        RegistrySet r = core != null ? core.registries() : null;
        return r != null ? r : RegistrySet.current();
    }
    private static final org.domi.furnace.server.net.registry.TagSet TAGS =
            org.domi.furnace.server.net.registry.TagSet.load("registries/tags-1.21.1.txt");

    private final ServerLoginContext login;
    private final String serverName;
    private final int compressionThreshold;
    private final FurnaceScheduler scheduler;
    // 플레이어가 지금 있는 차원의 월드(차원 이동 때 연결 스레드가 바꾼다). 처음은 서버가 넘긴 오버월드.
    private volatile World world;
    private final EntityIdAllocator idAllocator;

    private volatile ChannelHandlerContext ctx;
    private final AtomicBoolean awaitingKeepAlive = new AtomicBoolean(false);
    private volatile long lastKeepAliveId;

    private volatile ConnectionState state = ConnectionState.HANDSHAKE;   // volatile: 플러그인 PlayerConnection.phase() 가 다른 스레드에서 읽는다
    /** 플러그인이 보는 이 연결(접속 단계 이벤트·Player.connection() 이 같은 객체). */
    private final ConnectionInfo info = new ConnectionInfo(this);
    private boolean configureEventFired;    // AsyncPlayerConfigureEvent 는 처음 구성 때 한 번
    private volatile boolean awaitingConfigure;   // 플러그인 구성 이벤트가 끝나기를 기다리는 중(구성 keep-alive 를 보낸다)
    private static final int PROTOCOL_VERSION = 767;                 // 1.21.1
    private static final String VERSION_NAME = "1.21.1";
    // 패킷 속도 제한(Paper packet-limiter 대응): 단계마다 토큰 버킷(기본 버스트 400개, 초당 200개 회복, ConnectionLimits). 초과 시 킥.
    // 단계가 바뀔 때 그 단계의 버킷으로 바꾼다. null 이면 이 단계는 제한 없음.
    private TokenBucket packetBucket;
    // server.properties rate-limit(바닐라 RateKickingConnection): 초당 평균 수신 패킷 수. 0 이면 없음.
    private ReceiveRateWatch rateWatch;
    private io.netty.util.concurrent.ScheduledFuture<?> rateTick;
    // 지금 단계에 허용된 시간(connection.<단계>.timeout-seconds). 단계가 바뀌면 다시 건다.
    private io.netty.util.concurrent.ScheduledFuture<?> stateDeadline;
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
    private volatile int clientSkinParts, clientMainArm = 1;   // ClientInformation 의 스킨 부위·주 손(플레이어 생성 전에도 받는다)
    private int chatVisibility = org.domi.furnace.server.chat.PlayerChatState.VISIBILITY_FULL;
    private int velocityMessageId;
    private volatile java.net.InetSocketAddress forwardedAddress;   // Velocity·BungeeCord 포워딩으로 받은 실제 주소
    private org.domi.furnace.server.net.auth.BungeeForwarding.Forwarded bungeeForwarded;   // BungeeCord 핸드셰이크에서 꺼낸 UUID·속성          // 상태 요청은 연결당 1회(바닐라 ServerStatusPacketListenerImpl)
    private boolean transferred;            // 전송(intent 3)으로 들어온 연결
    private java.util.List<org.domi.furnace.server.players.GameProfile.Property> profileProperties = java.util.List.of();
    private boolean knownPacksHandled;      // Known Packs 응답은 연결당 1회(레지스트리 재전송 증폭 방지)
    private boolean finishConfigSent;       // FinishConfiguration을 보낸 뒤에만 PLAY 진입 수락
    // D-14: ack 뒤 PlayerData.load 를 플레이어 데이터 실행기(world.playerIo)에서 읽는 동안 도착한 play 패킷(id, 남은 본문). 이벤트루프 전용.
    // 읽기가 끝나면 onEnterPlay 직후 도착 순서대로 처리한다 — 동기 읽기 때와 같은 처리 순서.
    private java.util.ArrayDeque<Object[]> pendingPlayFrames;
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
        // 속도 제한은 모든 단계에 건다 — 로그인 전 단계에서 무제한으로 패킷을 처리하지 않게(바닐라 클라는 이 단계에 몇 개만 보낸다)
        if (rateWatch != null) rateWatch.onPacket();
        if (packetBucket != null && !packetBucket.tryAcquire(1)) {
            log.warn("{} 패킷 속도 초과({} 단계), 연결 종료", loggableAddress(ctx), state);
            kick(ctx, Text.text("Too many packets"), org.domi.furnace.api.net.QuitReason.ERRONEOUS_STATE);
            return;
        }
        if (pendingPlayFrames != null) {   // 플레이어 데이터 읽는 중 — 들어온 순서대로 보관
            pendingPlayFrames.add(new Object[]{packetId, frame.retain()});
            return;
        }
        dispatch(ctx, packetId, frame);
    }

    private void dispatch(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (state) {
            case HANDSHAKE -> handleHandshake(ctx, packetId, frame);
            case STATUS -> handleStatus(ctx, packetId, frame);
            case LOGIN -> handleLogin(ctx, packetId, frame);
            case CONFIGURATION -> handleConfiguration(ctx, packetId, frame);
            case PLAY -> dispatchPlay(ctx, packetId, frame, false);
            default -> log.debug("미처리 패킷: state={}, id=0x{}", state, Integer.toHexString(packetId));
        }
        if (state != frameLimitState) applyStateLimits(ctx);
    }

    /** play 패킷: 플러그인 패킷 리스너(붙어 있으면)를 거친 뒤 처리한다 — 취소면 버리고, 바뀌었으면 바뀐 프레임을 처리한다. */
    private void dispatchPlay(ChannelHandlerContext ctx, int packetId, ByteBuf frame, boolean fromPlugin) {
        PacketTap tap = ctx.channel().attr(PacketTap.KEY).get();
        if (tap == null) {
            handlePlay(ctx, packetId, frame);
            return;
        }
        ByteBuf r = tap.inbound(packetId, frame, fromPlugin);
        if (r == null) return;
        if (r == frame) {
            handlePlay(ctx, packetId, frame);
            return;
        }
        try {
            handlePlay(ctx, Types.readVarInt(r), r);
        } finally {
            r.release();
        }
    }

    /**
     * 플러그인이 넣은 클라이언트→서버 패킷(PacketService.receive). 이벤트 루프에서 진짜 패킷과 똑같이 처리한다(검사 포함).
     * frame 은 [VarInt id][본문]이고 이 메서드가 release 한다. play 단계가 아니면 버린다. 처리 중 예외는 연결을 끊지 않고 기록만 한다.
     */
    public void injectPlay(ByteBuf frame, boolean silent) {
        ChannelHandlerContext c = this.ctx;
        if (c == null) {
            frame.release();
            return;
        }
        try {
            c.executor().execute(() -> {
                try {
                    if (state != ConnectionState.PLAY || player == null || pendingPlayFrames != null || !c.channel().isActive()) return;
                    int id = Types.readVarInt(frame);
                    if (silent) handlePlay(c, id, frame);
                    else dispatchPlay(c, id, frame, true);
                } catch (RuntimeException e) {
                    log.warn("플러그인이 넣은 패킷 처리 실패: {}", e.toString());
                } finally {
                    frame.release();
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException closed) {
            frame.release();
        }
    }

    // 단계별 프레임 상한(1.21.1 기준). 핸드셰이크: id(1) + 프로토콜 VarInt(5) + 주소 문자열(255자 → 최대 765B + 길이 3)
    // + 포트(2) + 다음 단계 VarInt(5) = 781B. 상태: 요청(id 만)·핑(id + long) = 9B. 로그인·구성은 바닐라가 받는 가장 큰 패킷,
    // 플레이는 바닐라 21비트 프레임(2 MiB). 값은 ConnectionLimits(connection.<단계>.max-frame-bytes)에서 바꿀 수 있다.
    static final int HANDSHAKE_FRAME_MAX = 1024;
    // BungeeCord 레거시 포워딩을 켜면 주소 칸이 Short.MAX_VALUE 자(최대 3B/자 + 길이 3)까지 늘어난다: 1 + 5 + 98304 + 2 + 5.
    static final int FORWARDED_HANDSHAKE_FRAME_MAX = 1 + 5 + (3 + Short.MAX_VALUE * 3) + 2 + 5;
    static final int STATUS_FRAME_MAX = 64;
    private ConnectionState frameLimitState;

    /** 기본 설정에서 이 단계가 받는 프레임 최대 길이. */
    static int frameLimit(ConnectionState s) {
        return ConnectionLimits.DEFAULT.of(s).maxFrameBytes();
    }

    private ConnectionLimits limits() { return login.limits; }

    /** 단계가 바뀔 때마다 부른다: 프레임 상한·패킷 속도 버킷·단계 시간 제한을 새 단계 것으로 바꾼다. */
    private void applyStateLimits(ChannelHandlerContext ctx) {
        frameLimitState = state;
        ConnectionLimits.StateLimits sl = limits().of(state);
        org.domi.furnace.server.net.io.FrameDecoder fd = ctx.pipeline().get(org.domi.furnace.server.net.io.FrameDecoder.class);
        if (fd != null) {
            int max = sl.maxFrameBytes();
            if (state == ConnectionState.HANDSHAKE && login.bungeecord) max = Math.max(max, FORWARDED_HANDSHAKE_FRAME_MAX);
            fd.maxLength(max);
        }
        packetBucket = sl.packetsPerSecond() > 0 ? new TokenBucket(sl.packetsBurst(), sl.packetsPerSecond()) : null;
        if (stateDeadline != null) { stateDeadline.cancel(false); stateDeadline = null; }
        if (sl.timeoutSeconds() > 0 && ctx.channel().isActive()) {
            ConnectionState armed = state;
            stateDeadline = ctx.executor().schedule(() -> {
                // 이미 끊긴 연결(밴·포워딩 거절 등)에 사유를 한 번 더 찍지 않는다
                if (!ctx.channel().isActive() || state != armed) return;
                if (armed == ConnectionState.LOGIN) {
                    // 바닐라: 로그인 단계 600틱(30초) 안에 끝나지 않으면 끊는다.
                    markQuit(org.domi.furnace.api.net.QuitReason.TIMED_OUT);
                    disconnect(ctx, Text.translatable("multiplayer.disconnect.slow_login"));
                } else {
                    log.debug("{} 단계 시간 초과({}초), 연결 종료: {}", armed, sl.timeoutSeconds(), loggableAddress(ctx));
                    kick(ctx, Text.translatable("disconnect.timeout"), org.domi.furnace.api.net.QuitReason.TIMED_OUT);
                }
            }, sl.timeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    private void startRateWatch(ChannelHandlerContext ctx) {
        int limit = limits().vanillaRateLimit();
        if (limit <= 0 || rateWatch != null) return;
        ReceiveRateWatch watch = new ReceiveRateWatch(limit);
        rateWatch = watch;
        rateTick = ctx.executor().scheduleAtFixedRate(() -> {
            if (watch.tickSecond()) {
                log.warn("Player exceeded rate-limit (sent {} packets per second)", watch.average());
                rateTick.cancel(false);
                kick(ctx, Text.translatable("disconnect.exceeded_packet_rate"));
            }
        }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        applyStateLimits(ctx);
        startRateWatch(ctx);
    }

    private void handleHandshake(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        if (packetId != Handshake.ID) {
            closeFlushed(ctx);
            return;
        }
        Handshake hs = Handshake.read(frame, login.bungeecord ? Handshake.MAX_FORWARDED_ADDRESS : Handshake.MAX_ADDRESS);
        info.hostname = ConnectionInfo.hostnameOf(hs.serverAddress());
        info.hostPort = hs.serverPort();
        info.protocolVersion = hs.protocolVersion();
        // 바닐라 ServerHandshakePacketListenerImpl
        switch (hs.nextState()) {
            case 1 -> {
                state = ConnectionState.STATUS;
                // enable-status=false 면 응답하지 않고 끊는다(Ignoring status request). 상태 조회는 버전과 무관하게 허용.
                if (!login.enableStatus) closeFlushed(ctx);
            }
            case 2 -> beginLogin(ctx, hs, false);
            case 3 -> {
                state = ConnectionState.LOGIN;
                if (!login.acceptsTransfers) {
                    disconnect(ctx, Text.translatable("multiplayer.disconnect.transfers_disabled"));
                } else {
                    beginLogin(ctx, hs, true);
                }
            }
            default -> closeFlushed(ctx);
        }
    }

    private void beginLogin(ChannelHandlerContext ctx, Handshake hs, boolean transfer) {
        state = ConnectionState.LOGIN;
        transferred = transfer;
        info.transferred = transfer;
        int protocolVersion = hs.protocolVersion();
        if (protocolVersion != PROTOCOL_VERSION) {
            String key = protocolVersion < 754 ? "multiplayer.disconnect.outdated_client" : "multiplayer.disconnect.incompatible";
            disconnect(ctx, Text.translatable(key, VERSION_NAME));
            return;
        }
        // Spigot ServerHandshakePacketListenerImpl.beginLogin: 버전 검사 뒤 주소 칸의 레거시 포워딩을 읽는다.
        if (login.bungeecord) {
            var fwd = org.domi.furnace.server.net.auth.BungeeForwarding.parse(hs.serverAddress());
            if (fwd == null) {
                disconnect(ctx, Text.text(org.domi.furnace.server.net.auth.BungeeForwarding.NOT_FORWARDED));
                return;
            }
            int port = ctx.channel().remoteAddress() instanceof java.net.InetSocketAddress isa ? isa.getPort() : 0;
            forwardedAddress = new java.net.InetSocketAddress(fwd.address(), port);
            bungeeForwarded = fwd;
        } else if (org.domi.furnace.server.net.auth.BungeeForwarding.looksForwarded(hs.serverAddress())) {
            disconnect(ctx, Text.text(org.domi.furnace.server.net.auth.BungeeForwarding.UNEXPECTED_FORWARD));
            return;
        }
        // 플러그인 PlayerHandshakeEvent: 버전·포워딩 검사를 통과한 로그인 연결만(상태 조회는 ServerListPingEvent). 구독자 없으면 객체도 안 만든다.
        org.domi.furnace.server.ServerCore core = login.core;
        if (core != null && core.events().hasSubscribers(org.domi.furnace.api.event.player.PlayerHandshakeEvent.class)) {
            var ev = core.events().call(new org.domi.furnace.api.event.player.PlayerHandshakeEvent(info));
            if (ev.isCancelled()) {
                disconnect(ctx, ev.kickMessage());
                return;
            }
        }
    }

    private void handleStatus(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (packetId) {
            case 0x00 -> { // Status Request — 연결당 한 번(두 번째는 "Status request has been handled" 로 종료)
                if (statusHandled) { closeFlushed(ctx); return; }
                statusHandled = true;
                java.util.List<org.domi.furnace.server.players.GameProfile> online = new java.util.ArrayList<>();
                java.util.List<Boolean> listing = new java.util.ArrayList<>();
                for (Player p : world.onlinePlayers()) {
                    if (p.isDisconnected()) continue;
                    online.add(new org.domi.furnace.server.players.GameProfile(p.uuid(), p.name()));
                    listing.add(p.allowsListing());
                }
                java.net.InetAddress addr = clientAddress(ctx) instanceof java.net.InetSocketAddress isa ? isa.getAddress() : null;
                send(ctx, new StatusResponse(login.statusJson(online, listing, pluginEvents(), addr)));   // 플러그인 ServerListPingEvent
            }
            case 0x01 -> { // Ping Request → Pong 후 종료(바닐라)
                ByteBuf buf = ctx.alloc().buffer();
                Types.writeVarInt(buf, PongResponse.ID);
                new PongResponse(frame.readLong()).write(buf);
                ctx.writeAndFlush(buf).addListener(io.netty.channel.ChannelFutureListener.CLOSE);
            }
            default -> closeFlushed(ctx);
        }
    }

    private void handleLogin(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (packetId) {
            case 0x00 -> { // Login Start(Hello)
                if (loginPhase != LoginPhase.HELLO) { protocolError(ctx, "Unexpected hello packet"); return; }
                LoginStart start = LoginStart.read(frame);
                if (!isValidPlayerName(start.name())) { protocolError(ctx, "Invalid characters in username"); return; }
                this.playerName = start.name();
                info.name = start.name();
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
                } else if (bungeeForwarded != null) {
                    // Spigot initUUID: 이름은 클라이언트가 보낸 것, UUID·속성은 프록시가 알려 준 것.
                    startVerifying(ctx, new org.domi.furnace.server.players.GameProfile(bungeeForwarded.uuid(), playerName,
                            bungeeForwarded.properties()));
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
                state = ConnectionState.CONFIGURATION;
                onEnterConfiguration(ctx);
            }
            default -> closeFlushed(ctx);
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
            p.resetLastActionTime();   // 바닐라 resetLastActionTime — 채팅·명령도 "행동"
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

    /** chat.strict-order=false 로 체인에 이어 붙인 순서 어긋난 메시지·명령은 여기서 버린다(알림만 보낸다). */
    private boolean dropIfOutOfOrder() {
        if (!player.chat().decoder().consumeOutOfOrder()) return false;
        handleMessageDecodeFailure(new org.domi.furnace.server.chat.SignedMessageChain.DecodeException(
                org.domi.furnace.server.chat.SignedMessageChain.DecodeException.OUT_OF_ORDER_CHAT));
        return true;
    }

    private void handleChat(ByteBuf frame) {
        Player p = player;
        org.domi.furnace.server.ServerCore core = login.core;
        if (p == null || core == null) return;
        String message = Types.readString(frame, limits().chatLength());
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
            if (dropIfOutOfOrder()) return;
            msg = msg.withUnsignedContent(msg.decoratedContent()); // 채팅 꾸밈(decorator)은 없다
            var events = core.events();
            if (events.hasSubscribers(org.domi.furnace.api.event.player.AsyncChatEvent.class)) {
                Text original = msg.decoratedContent();
                var ev = events.call(new org.domi.furnace.api.event.player.AsyncChatEvent(core.plugins().player(p), message, original,
                        msg.signature() == null ? null : msg.signature().bytes()));
                if (ev.isCancelled()) {
                    detectRateSpam();
                    return;
                }
                if (!ev.message().equals(original)) msg = msg.withUnsignedContent(ev.message());
            }
            var bound = org.domi.furnace.server.chat.ChatType.CHAT.bind(
                    org.domi.furnace.server.command.CommandSource.player(core, p).getDisplayName());
            if (org.domi.furnace.server.config.OperatorOptions.current().unsignedChatAsSystem()) core.broadcastChatAsSystem(msg, bound);
            else core.broadcastChatMessage(msg, p, bound);
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
        String command = Types.readString(frame, limits().commandLength());
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
            if (dropIfOutOfOrder()) return;
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

    /** 실제 클라이언트 주소(Velocity·BungeeCord 포워딩이면 프록시가 알려 준 주소). */
    java.net.SocketAddress clientAddress(ChannelHandlerContext ctx) {
        if (forwardedAddress != null) return forwardedAddress;
        java.net.InetSocketAddress proxied = ProxyProtocolHandler.sourceOf(ctx.channel());   // PROXY 프로토콜 헤더로 받은 실제 주소
        return proxied != null ? proxied : ctx.channel().remoteAddress();
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
        java.net.InetAddress ip = login.preventProxyConnections && clientAddress(ctx) instanceof java.net.InetSocketAddress isa
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
        info.name = profile.name();
        info.uniqueId = profile.id();
        this.profileProperties = profile.properties();
        org.domi.furnace.server.ServerCore core = login.core;
        if (core != null && core.events().hasSubscribers(org.domi.furnace.api.event.player.AsyncPlayerPreLoginEvent.class)) {
            // 플러그인이 블로킹 작업(DB 등)을 해도 되도록 가상 스레드에서 부르고, 결과를 들고 연결 스레드로 돌아온다.
            java.net.InetAddress addr = clientAddress(ctx) instanceof java.net.InetSocketAddress isa ? isa.getAddress() : null;
            core.plugins().asyncExecutor().execute(() -> {
                var ev = core.events().call(new org.domi.furnace.api.event.player.AsyncPlayerPreLoginEvent(profile.name(), profile.id(), addr, info));
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
                        loginResult(reason), reason, info));
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
        send(ctx, new BrandPayload(org.domi.furnace.server.config.OperatorOptions.current().serverBrand()));         // 바닐라 순서: brand → 기능 플래그 → Known Packs
        var links = serverLinks();
        if (!links.isEmpty()) send(ctx, new org.domi.furnace.server.net.protocol.play.ServerLinksPacket(links, false));   // 바닐라 순서: brand → 서버 링크 → 기능 플래그
        send(ctx, FeatureFlags.vanilla());
        send(ctx, new KnownPacksClientbound());
    }

    /** 바닐라 DedicatedServer.serverLinks: bug-report-link 가 유효한 URI 면 report_bug 링크 하나(파싱 실패는 경고 후 없음). */
    private java.util.List<org.domi.furnace.server.net.protocol.play.ServerLinksPacket.Entry> serverLinks() {
        String link = login.core != null ? login.core.settings().get().bugReportLink : "";
        if (link == null || link.isEmpty()) return java.util.List.of();
        try {
            return java.util.List.of(new org.domi.furnace.server.net.protocol.play.ServerLinksPacket.Entry(
                    org.domi.furnace.server.net.protocol.play.ServerLinksPacket.KNOWN_REPORT_BUG, null, new java.net.URI(link).toString()));
        } catch (Exception e) {
            log.warn("Failed to parse bug link {}", link, e);
            return java.util.List.of();
        }
    }

    private void handleConfiguration(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (packetId) {
            case 0x00 -> readClientInformation(frame); // Client Information: view distance 읽음
            case 0x01 -> handleCookieResponse(ctx, frame); // cookie_response: 플러그인이 requestCookie 로 요청한 것만 받는다
            case 0x02 -> handleCustomPayload(frame, false); // custom_payload: minecraft:brand 만 읽는다(바닐라는 나머지를 버린다)
            case 0x07 -> { // Serverbound Known Packs → 레지스트리 전송 후 종료
                if (knownPacksHandled) return;                           // 반복 요청은 무시(증폭 방지)
                knownPacksHandled = true;
                for (String registry : registries().registryKeys()) {
                    send(ctx, new RegistryData(registry, registries().entries(registry), registries()));
                }
                // 레지스트리 뒤, Finish 앞(바닐라 순서). 데이터 팩이 있으면 거기서 읽은 태그, 없으면 미리 만든 태그 파일.
                var dp = login.core != null ? login.core.dataPacks() : null;
                send(ctx, new UpdateTags(dp != null ? dp.tags() : TAGS));
                // 바닐라 구성 작업 순서: 레지스트리·태그 → server_resource_pack(응답이 끝 상태일 때까지 기다림) → 구성 끝
                var pack = serverPack();
                if (pack != null) {
                    send(ctx, packPush(pack, false));
                    awaitingPack = true;
                    scheduleConfigKeepAlive(ctx);   // 큰 팩을 내려받는 동안 읽기 타임아웃(30초)에 걸리지 않게 바닐라처럼 15초마다 핑
                } else {
                    finishConfiguration(ctx);
                }
            }
            case 0x06 -> { // resource_pack(구성): UUID, VarInt 상태
                int action = readPackResponse(ctx, frame);
                if (action < 0) return;
                if (awaitingPack && !lastPackResponseWasPlugin && ResourcePackResponses.isTerminal(action)) {
                    awaitingPack = false;
                    finishConfiguration(ctx);
                }
            }
            case 0x03 -> { // Acknowledge Finish Configuration
                if (!finishConfigSent) { closeFlushed(ctx); return; }          // Finish를 보내기 전의 ack는 프로토콜 위반
                state = ConnectionState.PLAY;
                beginEnterPlay(ctx);
            }
            default -> log.debug("미처리 config 패킷 id=0x{}", Integer.toHexString(packetId));
        }
    }

    /**
     * 구성 작업이 다 끝났다 → 구성 끝(FinishConfiguration). 플러그인 AsyncPlayerConfigureEvent 구독자가 있으면 처음 한 번은
     * 가상 스레드에서 이벤트를 부르고(블로킹 허용) 돌아와서 보낸다 — 기다리는 동안 구성 keep-alive 로 클라이언트 시간 초과를 막는다.
     * 구독자가 없으면 바닐라와 똑같이 바로 보낸다.
     */
    private void finishConfiguration(ChannelHandlerContext ctx) {
        org.domi.furnace.server.ServerCore core = login.core;
        if (!configureEventFired && core != null
                && core.events().hasSubscribers(org.domi.furnace.api.event.player.AsyncPlayerConfigureEvent.class)) {
            configureEventFired = true;
            awaitingConfigure = true;
            try {
                core.plugins().asyncExecutor().execute(() -> {
                    org.domi.furnace.api.event.player.AsyncPlayerConfigureEvent ev = null;
                    boolean run;
                    synchronized (configureLock) {   // 연결이 이미 끊겼으면 돌리지 않고, 돌리는 동안 끊기면 channelInactive 가 인터럽트로 깨운다
                        run = !configureWaitersClosed;
                        if (run) configureThread = Thread.currentThread();
                    }
                    if (run) {
                        try {
                            ev = core.events().call(new org.domi.furnace.api.event.player.AsyncPlayerConfigureEvent(info));
                        } catch (Throwable t) {
                            if (ctx.channel().isActive()) log.error("AsyncPlayerConfigureEvent 처리 실패: {}", playerName, t);
                        } finally {
                            synchronized (configureLock) {
                                configureThread = null;
                                Thread.interrupted();   // 끊김 인터럽트가 실행기 스레드에 남지 않게
                            }
                        }
                    }
                    final var done = ev;
                    try {
                        ctx.channel().eventLoop().execute(() -> {
                            awaitingConfigure = false;
                            // 끊겼거나(플러그인이 PlayerConnection.disconnect 를 불렀을 때 포함) 단계가 바뀌었으면 구성 끝을 보내지 않는다
                            if (!ctx.channel().isActive() || kickReason != null || state != ConnectionState.CONFIGURATION) return;
                            if (done != null && !done.isAllowed()) {
                                disconnect(ctx, done.kickMessage());
                                return;
                            }
                            sendFinishConfiguration(ctx);
                        });
                    } catch (java.util.concurrent.RejectedExecutionException closed) {
                        awaitingConfigure = false;   // 이벤트루프가 끝났다(서버 종료)
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException closed) {
                awaitingConfigure = false;   // 플러그인 실행기가 닫혔다(종료 중) — 이벤트 없이 진행
                sendFinishConfiguration(ctx);
                return;
            }
            scheduleConfigKeepAlive(ctx);
            return;
        }
        sendFinishConfiguration(ctx);
    }

    private void sendFinishConfiguration(ChannelHandlerContext ctx) {
        send(ctx, new FinishConfiguration());
        finishConfigSent = true;
    }

    /** ClientInformation 파싱 — locale 건너뛰고 view distance(byte) 읽음. config·play 양쪽서 호출. */
    private void readClientInformation(ByteBuf frame) {
        try {
            this.clientLocale = Types.readString(frame, 16);   // locale(en_us 등)
            int vd = frame.readByte();                   // view distance (chunks)
            this.clientViewDistance = Math.max(Config.MIN_VIEW_DISTANCE, Math.min(Config.MAX_VIEW_DISTANCE, vd));
            Player p = this.player;
            if (p != null) p.locale(clientLocale);
            if (p != null) p.clientViewDistance(clientViewDistance);
            if (p != null) p.viewDistance(effectiveViewDistance()); // 게임 중 렌더거리 변경 반영
            this.chatVisibility = Types.readVarInt(frame); // chat mode: 0 전부, 1 시스템만, 2 숨김
            if (p != null) p.chat().visibility(chatVisibility);
            frame.readBoolean();                         // chat colors
            this.clientSkinParts = frame.readUnsignedByte(); // displayed skin parts(겉옷·겉머리 등 비트)
            this.clientMainArm = Types.readVarInt(frame);    // main hand: 0 왼손, 1 오른손
            if (p != null) p.clientOptions(clientSkinParts, clientMainArm);
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

    /**
     * D-14: 저장 데이터를 플레이어 데이터 전용 직렬 실행기(world.playerIo)에서 읽고(이벤트루프 블로킹 제거) 이벤트루프로 돌아와
     * onEnterPlay 를 부른다. 그 사이 play 패킷은 보관했다가 onEnterPlay 직후 순서대로 처리한다. 실행기가 거부하면 동기로 읽는다.
     * 퇴장 저장(savePlayerSnapshot)도 같은 단일 스레드 실행기라, 먼저 제출된 저장이 끝난 뒤에 읽는다(청크 IO 대기열과도 분리).
     */
    private void beginEnterPlay(ChannelHandlerContext ctx) {
        java.util.concurrent.Executor io = world.playerIo();
        java.nio.file.Path dir = world.worldDir();
        java.util.UUID uuid = playerUuid;
        if (io != null) {
            pendingPlayFrames = new java.util.ArrayDeque<>();
            try {
                io.execute(() -> {
                    org.domi.furnace.server.entity.PlayerData.Snapshot saved = null;
                    Throwable fail = null;
                    try {
                        saved = org.domi.furnace.server.entity.PlayerData.load(dir, uuid);
                    } catch (Throwable t) {
                        fail = t;
                    }
                    final var s = saved;
                    final Throwable f = fail;
                    if (s == null && f == null && login.core != null) {
                        // 처음 들어오는 플레이어: ServerPlayer 생성자의 adjustSpawnLocation(오버월드 공유 스폰) — 오버월드 region 에서 계산
                        // 계산이 늦으면(청크 생성 대기) FIRST_SPAWN_TIMEOUT_MS 뒤 흩뿌리지 않은 월드 스폰으로 들어간다 — 클라이언트 읽기 시간 초과(30초) 방지
                        World over = login.core.world();
                        java.util.concurrent.atomic.AtomicBoolean once = new java.util.concurrent.atomic.AtomicBoolean();
                        SpawnPoint ws = over.defaultSpawn();
                        var plain = new org.domi.furnace.server.world.RespawnFinder.Spot(ws.playerX(), ws.playerY(), ws.playerZ(), 0.0F);
                        try {
                            ctx.executor().schedule(() -> {
                                if (!once.compareAndSet(false, true)) return;
                                log.warn("첫 스폰 자리 계산이 {}ms 안에 끝나지 않아 월드 스폰으로 들어간다: {}", FIRST_SPAWN_TIMEOUT_MS, playerName);
                                firstSpawn = plain;
                                finishEnterPlay(ctx, null, null);
                            }, FIRST_SPAWN_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
                        } catch (java.util.concurrent.RejectedExecutionException closed) {
                            java.util.ArrayDeque<Object[]> q = pendingPlayFrames;
                            pendingPlayFrames = null;
                            releasePendingFrames(q);
                            return;
                        }
                        org.domi.furnace.server.world.RespawnFinder.worldSpawn(over, over.levelServices().gameRuleInt("spawnRadius"),
                                over.levelServices().worldAdventure(), spot -> {
                                    try {
                                        ctx.executor().execute(() -> {
                                            if (!once.compareAndSet(false, true)) return;
                                            firstSpawn = spot;
                                            finishEnterPlay(ctx, null, null);
                                        });
                                    } catch (java.util.concurrent.RejectedExecutionException closed) {
                                        java.util.ArrayDeque<Object[]> q = pendingPlayFrames;
                                        pendingPlayFrames = null;
                                        releasePendingFrames(q);
                                    }
                                });
                        return;
                    }
                    try {
                        ctx.executor().execute(() -> finishEnterPlay(ctx, s, f));
                    } catch (java.util.concurrent.RejectedExecutionException closed) {
                        // 이벤트루프가 이미 끝났다(서버 종료) — 보관 패킷을 만질 스레드가 없으니 여기서 해제한다
                        java.util.ArrayDeque<Object[]> q = pendingPlayFrames;
                        pendingPlayFrames = null;
                        releasePendingFrames(q);
                    }
                });
                return;
            } catch (java.util.concurrent.RejectedExecutionException e) {
                pendingPlayFrames = null;   // 종료 중 — 아래 동기 경로
            }
        }
        onEnterPlay(ctx, org.domi.furnace.server.entity.PlayerData.load(dir, uuid));
    }

    /** 이벤트루프에서: 읽은 저장 데이터로 진입을 마치고 보관한 패킷을 도착 순서대로 처리한다. */
    private void finishEnterPlay(ChannelHandlerContext ctx, org.domi.furnace.server.entity.PlayerData.Snapshot saved, Throwable fail) {
        java.util.ArrayDeque<Object[]> queued = pendingPlayFrames;
        pendingPlayFrames = null;
        try {
            if (!ctx.channel().isActive()) return;   // 읽는 동안 끊겼다 — 플레이어를 만들지 않는다(저장할 것도 없다)
            if (fail != null) { exceptionCaught(ctx, fail); return; }   // 예전엔 channelRead0 예외 → exceptionCaught
            try {
                onEnterPlay(ctx, saved);
                while (queued != null && !queued.isEmpty() && ctx.channel().isActive()) {
                    Object[] q = queued.poll();
                    ByteBuf frame = (ByteBuf) q[1];
                    try {
                        dispatch(ctx, (Integer) q[0], frame);
                    } finally {
                        frame.release();
                    }
                    if (pendingPlayFrames != null) {
                        // 재생 중 재구성(play→config→ack)으로 새 진입이 시작됐다 — 남은 패킷은 새 진입 뒤로 넘겨 순서를 지킨다
                        while (!queued.isEmpty()) pendingPlayFrames.add(queued.poll());
                        break;
                    }
                }
            } catch (Throwable t) {
                exceptionCaught(ctx, t);
            }
        } finally {
            releasePendingFrames(queued);
        }
    }

    private static void releasePendingFrames(java.util.ArrayDeque<Object[]> queued) {
        if (queued == null) return;
        Object[] q;
        while ((q = queued.poll()) != null) ((ByteBuf) q[1]).release();
    }

    private void onEnterPlay(ChannelHandlerContext ctx, org.domi.furnace.server.entity.PlayerData.Snapshot saved) {
        int eid = idAllocator.next();
        int dimIndex = Math.max(0,
                registries().indexOf("minecraft:dimension_type", "minecraft:overworld"));
        int effVd = effectiveViewDistance();
        org.domi.furnace.server.ServerCore coreJ = login.core;
        // 저장 데이터는 beginEnterPlay 가 먼저 읽었다 — 게임 모드·체력 등이 LoginPlay 부터 들어간다(바닐라 placeNewPlayer: load → loadGameTypes).
        // 바닐라 placeNewPlayer: 저장된 Dimension 의 레벨(없는 차원이면 경고 후 오버월드 — 위치는 저장된 그대로)
        if (saved != null && coreJ != null && saved.root() != null && saved.root().tags().get("Dimension") instanceof String savedDim) {
            World w = coreJ.world(savedDim);
            if (w != null) world = w;
            else log.warn("Unknown respawn dimension {}, defaulting to overworld", savedDim);
        }
        dimIndex = Math.max(0, registries().indexOf("minecraft:dimension_type", world.dimensionType().typeKey()));
        Player p = new Player(eid, playerUuid, playerName, ctx.channel());
        if (login.core != null) login.core.plugins().packetsImpl().attach(p);   // 플러그인 패킷 리스너 — 첫 play 패킷(login)부터
        p.clientBrand(clientBrand);   // 바닐라 클라이언트는 brand 를 구성 단계에서 보낸다 — Player 가 생기기 전이라 여기서 넘긴다
        p.pluginChannels(pluginChannels);
        p.dimension(world.dimension());
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

            public void openHorseInventory(org.domi.furnace.server.entity.AbstractHorse horse, org.domi.furnace.server.menu.Container container) {
                int columns = horse.inventoryColumns();   // 말 region 에서 읽어 둔다
                ctx.channel().eventLoop().execute(() -> ConnectionHandler.this.openHorseInventory(p, horse, container, columns));
            }

            public void openPluginMenu(org.domi.furnace.server.plugin.MenuSession session) {
                ctx.channel().eventLoop().execute(() -> ConnectionHandler.this.openPluginMenu(p, session));
            }

            public void execute(Runnable task) { ctx.channel().eventLoop().execute(task); }

            public void openEntityContainer(org.domi.furnace.server.entity.ContainerEntity entity) {
                // createMenu: 전리품 표가 있으면 관전자는 못 열고, 아니면 연 플레이어로 표를 푼다(엔티티 region 에서)
                boolean blocked = entity.lootTable() != null && p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR;
                org.domi.furnace.server.ServerCore c = login.core;
                if (!blocked && c != null) c.unpackLoot(entity, p);
                ctx.channel().eventLoop().execute(() -> ConnectionHandler.this.openEntityContainer(p, entity, blocked));
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
                var ev = c.events().call(new org.domi.furnace.api.event.block.BlockBreakEvent(p.dimension(),
                        new org.domi.furnace.api.world.BlockPos(x, y, z), world.blockRegistry().nameOf(state), c.plugins().player(p)));
                return !ev.isCancelled();
            }
        });
        var vitals = p.vitals();
        if (coreJ != null) vitals.env(coreJ.vitalsEnv());
        vitals.deathListener(keepInventory -> ctx.channel().eventLoop().execute(() -> onPlayerDied(keepInventory)));
        if (saved != null && saved.root() != null) {
            p.loadAttributes(saved.root());   // 바닐라 순서: attributes → active_effects → Health
            vitals.load(saved.root());
        }
        org.domi.furnace.server.world.GameMode forced = coreJ != null ? coreJ.forcedGameMode() : null;
        org.domi.furnace.server.world.GameMode defaultMode = coreJ != null ? coreJ.defaultGameMode() : org.domi.furnace.server.world.GameMode.CREATIVE;
        vitals.loadGameTypes(saved != null ? saved.root() : null, forced != null ? forced : defaultMode, forced != null);
        var prevMode = vitals.previousGameMode();
        p.connection(info);
        p.remoteAddress(clientAddress(ctx));
        // 플러그인 PlayerCreateEvent: Player 가 생긴 직후·플레이 패킷(login·권한·명령 트리) 전 — 여기서 붙인 권한이 첫 명령 트리부터 반영된다.
        if (coreJ != null && coreJ.events().hasSubscribers(org.domi.furnace.api.event.player.PlayerCreateEvent.class)) {
            coreJ.events().call(new org.domi.furnace.api.event.player.PlayerCreateEvent(coreJ.plugins().player(p), info));
        }
        // 첫 위치: 저장된 자리, 없으면 흩뿌린 월드 스폰(새 플레이어 — 회전 0, ServerPlayer 생성자), 그것도 없으면 월드 기본 스폰.
        SpawnPoint fallbackSpawn = world.defaultSpawn();
        var first = firstSpawn;
        firstSpawn = null;
        double sx = saved != null ? saved.x() : first != null ? first.x() : fallbackSpawn.playerX();
        double sy = saved != null ? saved.y() : first != null ? first.y() : fallbackSpawn.playerY();
        double sz = saved != null ? saved.z() : first != null ? first.z() : fallbackSpawn.playerZ();
        float syaw = saved != null ? saved.yaw() : first != null ? first.yaw() : 0.0F;
        float spitch = saved != null ? saved.pitch() : 0f;
        // 플러그인 PlayerSpawnLocationEvent: 차원을 알리기(LoginPlay) 전에 첫 위치·차원을 바꿀 수 있다. 없는 차원이면 무시.
        if (coreJ != null && coreJ.events().hasSubscribers(org.domi.furnace.api.event.player.PlayerSpawnLocationEvent.class)) {
            var ev = coreJ.events().call(new org.domi.furnace.api.event.player.PlayerSpawnLocationEvent(coreJ.plugins().player(p),
                    new org.domi.furnace.api.world.Location(world.dimension(), sx, sy, sz, syaw, spitch), saved == null));
            org.domi.furnace.api.world.Location l = ev.spawnLocation();
            World target = coreJ.world(l.world());
            if (target == null) {
                log.warn("PlayerSpawnLocationEvent: 없는 차원 {} — 기본 위치로 들어간다: {}", l.world(), playerName);
            } else if (Double.isFinite(l.x()) && Double.isFinite(l.y()) && Double.isFinite(l.z())) {
                if (target != world) {
                    world = target;
                    p.dimension(world.dimension());
                    dimIndex = Math.max(0, registries().indexOf("minecraft:dimension_type", world.dimensionType().typeKey()));
                }
                sx = l.x(); sy = l.y(); sz = l.z(); syaw = l.yaw(); spitch = l.pitch();
            }
        }
        // 바닐라처럼 서버의 view distance를 보낸다(클라 렌더 거리는 min(자기 설정, 이 값)). 스트리밍은 effVd(= min(서버, 클라 요청))로 한다.
        if (coreJ != null) {
            var props = coreJ.settings().get();
            var rules = coreJ.gameRules();
            var level = coreJ.levelData();
            send(ctx, new LoginPlay(eid, level.hardcore, loginLevelKeys(coreJ),
                    props.maxPlayers, world.viewDistance(), coreJ.simulationDistance(), rules.getBoolean("reducedDebugInfo"),
                    !rules.getBoolean("doImmediateRespawn"), rules.getBoolean("doLimitedCrafting"), dimIndex, world.dimension(),
                    coreJ.biomeZoomSeed(world), vitals.gameMode().id(), prevMode == null ? -1 : prevMode.id(), false,
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
            // 바닐라 sendCommands 처럼 플레이어 소스로 거른다 — 플러그인 명령의 권한(PlayerCreateEvent 에서 붙인 첨부 포함)이 첫 트리부터 맞다.
            send(ctx, core.commands().treeFor(org.domi.furnace.server.command.CommandSource.player(core, p)));
        }
        if (saved != null && saved.root() != null && saved.root().tags().get("recipeBook") instanceof org.domi.furnace.server.net.io.nbt.NbtCompound book) {
            p.recipeBook().load(book, recipes);
        }
        send(ctx, p.recipeBook().initPacket());
        // 첫 위치(sx..spitch)는 위에서 정했다(PlayerSpawnLocationEvent). 나침반 스폰은 들어갈 차원의 것.
        SpawnPoint defaultSpawn = world.defaultSpawn();
        if (saved != null) inv.load(saved.slots());
        menus = new ServerMenus(p);
        p.initMenus(new org.domi.furnace.server.menu.InventoryMenu(inv, menus));
        p.enderChest().load(org.domi.furnace.server.entity.PlayerData.enderItemsFromNbt(saved != null ? saved.root() : null,
                org.domi.furnace.server.entity.EnderChestInventory.SIZE));
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
        p.clientOptions(clientSkinParts, clientMainArm);
        log.info("{}[{}] logged in with entity id {} at ({}, {}, {})", playerName, loggableAddress(ctx), eid, sx, sy, sz);
        p.publishPosition(sx, sy, sz, syaw, spitch, true);
        p.pushTeleport(sx, sy, sz);
        p.clientViewDistance(clientViewDistance);
        p.viewDistance(effVd);
        this.player = p;
        // 서버가 플레이어를 옮긴다(엔더 진주 등): 어느 스레드에서 불러도 IO 스레드에서 한다.
        p.teleporter(t -> ctx.executor().execute(() -> {
            if (t.length == 8) { teleportPlayerRelative(ctx, t[0], t[1], t[2], t[5], t[6], t[7]); return; }   // Player.teleportRelative
            if (t.length >= 5) { iyaw = (float) t[3]; ipitch = (float) t[4]; }
            teleportPlayer(ctx, t[0], t[1], t[2]);
        }));
        // 차원 이동 도착(목적 region 스레드에서 부른다): 연결 스레드에서 리스폰 패킷부터 보내고 목적 region 에 넣는다.
        p.dimensionChanger((target, t, cooldown) -> ctx.executor().execute(() -> applyDimensionChange(ctx, target, t, cooldown)));
        joinSavedRoot = saved != null ? saved.root() : null;   // RootVehicle 은 region 에 들어간 뒤 태운다
        if (saved != null && saved.root() != null) {
            p.seenCredits = saved.root().getBoolean("seenCredits");
            p.loadTags(saved.root());
            p.loadPersistentData(saved.root());
            p.loadShoulders(saved.root());
        }
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
        world.updateSleepingPlayerList();   // ServerLevel EntityCallbacks.onTrackingStart
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
        if (coreJ != null && coreJ.clock().isRaining() && world.dimensionType().hasSkyLight()) {
            send(ctx, new GameEvent(GameEvent.START_RAINING, 0f));
            send(ctx, new GameEvent(GameEvent.RAIN_LEVEL_CHANGE, coreJ.clock().rainLevel()));
            send(ctx, new GameEvent(GameEvent.THUNDER_LEVEL_CHANGE, coreJ.clock().thunderLevel()));
        }
        send(ctx, new GameEvent(GameEvent.START_WAITING_FOR_CHUNKS, 0f));
        send(ctx, new org.domi.furnace.server.net.protocol.play.TickingState(world.tickState().rate(), world.tickState().frozen()));
        send(ctx, new org.domi.furnace.server.net.protocol.play.TickingStep(world.tickState().globalStepsLeft()));
        for (Packet effectPacket : p.vitals().effectPackets()) send(ctx, effectPacket);   // 바닐라: sendLevelInfo 다음 활성 효과
        ChunkPos centerCp = ChunkPos.ofBlock(sx, sz);
        // SetCenterChunk 먼저 — 클라 viewCenter를 스폰으로 맞춰 스폰 청크가 "not in view range"로 안 버려지게.
        send(ctx, new SetCenterChunk(centerCp.x(), centerCp.z()));
        // 서버가 아는 인벤토리를 클라이언트에도 맞춘다(바닐라 initInventoryMenu).
        synchronized (p.menuLock()) { p.inventoryMenu().setSynchronizer(menus); }   // 첫 container_set_content(상태 번호 1)
        // 바닐라 첫 틱: 엔티티 데이터 → (ServerPlayer.tick 끝) 발전 과제 tick 트리거·flushDirty → (doTick) 체력·허기·경험치.
        // 발전 과제 쪽 패킷(레시피 ADD 등)은 Player.send 로 이벤트 루프 대기열에 들어가므로 뒤 패킷도 같은 대기열로 보낸다.
        var advancements = coreJ != null ? coreJ.attachAdvancements(p) : null;
        if (p.stats() == null && org.domi.furnace.server.item.ItemContext.installed()) {   // 통계(world/stats/<uuid>.json) — 바닐라 placeNewPlayer 가 markAllDirty
            var st = new org.domi.furnace.server.stats.PlayerStats(world.worldDir(), p.uuid());
            st.markAllDirty();
            p.stats(st);
        }
        if (coreJ != null) p.scoreboard(coreJ.scoreboard());
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
        if (login.core != null) login.core.scoreboard().sendTo(p);   // 바닐라 placeNewPlayer: updateEntireScoreboard 뒤 접속 알림
        if (login.core != null) login.core.plugins().sendRegisteredChannels(p);   // CraftBukkit sendSupportedChannels: 서버 플러그인이 받는 채널을 알린다
        if (login.core != null) announceJoin(login.core, p);
        if (login.core != null) login.core.customBossEvents().onPlayerConnect(p);
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
                        p.chunkSendRate().batchSent(spawnChunks.size());   // 이 배치의 chunk_batch_received 도 센다
                        send(ctx, new ChunkBatchStart());
                        int sent = 0;
                        for (int i = 0; i < spawnChunks.size(); i++) {
                            ChunkPos cp = spawnChunks.get(i);
                            try {
                                byte[] bodyBytes = futs.get(i).getNow(null);
                                if (bodyBytes == null) { world.unsubscribeChunk(cp, p); continue; }
                                send(ctx, LevelChunkWithLight.ofShared(bodyBytes));
                                p.loadedChunks().add(cp);
                                world.chunkDelivered(cp, p, subs.get(cp));   // 청크를 쓴 뒤 보류된 변경을 흘려보낸다
                                sent++;
                            } catch (Exception e) {
                                world.unsubscribeChunk(cp, p);
                                log.warn("스폰 청크 전송 실패 {}", cp, e);
                            }
                        }
                        send(ctx, new ChunkBatchFinished(sent));
                        final var savedRoot = joinSavedRoot;
                        joinSavedRoot = null;
                        world.runAt(sx, sz, r -> {
                            r.adopt(p);
                            org.domi.furnace.server.entity.PlayerVehicleSave.restore(p, r.level(), savedRoot);   // placeNewPlayer 의 RootVehicle
                        });
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
            case 0x04 -> handleChatCommand(Types.readString(frame, limits().commandLength()));          // chat_command
            case 0x05 -> handleSignedChatCommand(frame);                            // chat_command_signed
            case 0x06 -> handleChat(frame);                                         // chat
            case 0x07 -> handleChatSessionUpdate(org.domi.furnace.server.chat.RemoteChatSession.Data.read(frame)); // chat_session_update
            case 0x0B -> handleCommandSuggestion(Types.readVarInt(frame), Types.readString(frame, 32500));
            case 0x14 -> { // edit_book: 칸(VarInt), 쪽 목록(최대 200, 각 8192자), 제목(Optional, 128자) — 바닐라는 쪽을 앞의 100개만 쓴다
                int slot = Types.readVarInt(frame);
                int count = Types.readVarInt(frame);
                if (count < 0 || count > 200) throw new org.domi.furnace.server.net.protocol.ProtocolException("Edit book pages out of bounds: " + count);
                java.util.List<String> pages = new java.util.ArrayList<>(count);
                int pageMax = limits().bookPageLength() > 0 ? Math.min(8192, limits().bookPageLength()) : 8192;   // limits.item.book-page
                for (int i = 0; i < count; i++) pages.add(Types.readString(frame, pageMax));
                String title = frame.readBoolean() ? Types.readString(frame, 128) : null;
                Player actor = player;
                if (actor != null) handleEditBook(actor, slot, pages, title);
            }
            case 0x16 -> { // use_entity(interact): 엔티티 번호, 0 상호작용·1 공격·2 위치 상호작용, [x,y,z], [손], 웅크리기
                touchLastAction();
                int entityId = Types.readVarInt(frame);
                int action = Types.readVarInt(frame);
                int hand = 0;
                float hitY = 0.0F;
                if (action == 2) { frame.readFloat(); hitY = frame.readFloat(); frame.readFloat(); }
                if (action != 1) hand = Types.readVarInt(frame);
                frame.readBoolean();
                Player actor = player;
                int h = hand;
                double hy = hitY;
                if (actor != null && actor.gameMode() != org.domi.furnace.server.world.GameMode.SPECTATOR) {
                    world.runAt(actor.x(), actor.z(), region -> handleInteractEntity(region, actor, entityId, action, h, hy));
                }
            }
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
                if (invalidMovement(ctx, x, y, z, 0f, 0f)) return;
                Player p = player;
                if (p != null && pendingTeleportId == -1 && p.isSleeping()) {
                    acceptSleeping(ctx, p, x, y, z, iyaw, ipitch);
                } else if (p != null && pendingTeleportId == -1 && p.vehicle() != null) {
                    acceptRiding(p, iyaw, ipitch, g);
                } else if (p != null && pendingTeleportId == -1 && acceptPosition(ctx, x, y, z)) {
                    ig = g;
                    acceptMove(p, true, false);
                }
            }
            case 0x1B -> { // move_player_pos_rot: Double x,y,z, Float yaw,pitch, Boolean onGround
                double x = frame.readDouble(), y = frame.readDouble(), z = frame.readDouble();
                float yaw = frame.readFloat(), pitch = frame.readFloat();
                boolean g = frame.readBoolean();
                if (invalidMovement(ctx, x, y, z, yaw, pitch)) return;
                Player p = player;
                if (p != null && pendingTeleportId == -1 && p.isSleeping()) {
                    acceptSleeping(ctx, p, x, y, z, yaw, pitch);
                } else if (p != null && pendingTeleportId == -1 && p.vehicle() != null) {
                    acceptRiding(p, yaw, pitch, g);
                } else if (p != null && pendingTeleportId == -1 && acceptPosition(ctx, x, y, z)) {
                    iyaw = yaw;
                    ipitch = pitch;
                    ig = g;
                    acceptMove(p, true, true);
                }
            }
            case 0x1C -> { // move_player_rot: Float yaw,pitch, Boolean onGround
                float yaw = frame.readFloat(), pitch = frame.readFloat();
                boolean g = frame.readBoolean();
                if (invalidMovement(ctx, 0.0, 0.0, 0.0, yaw, pitch)) return;
                Player p = player;
                if (p != null && pendingTeleportId == -1 && p.isSleeping()) {
                    acceptSleeping(ctx, p, p.x(), p.y(), p.z(), yaw, pitch);
                } else if (p != null && pendingTeleportId == -1 && p.vehicle() != null) {
                    acceptRiding(p, yaw, pitch, g);
                } else if (p != null && pendingTeleportId == -1 && correctionTpId == -1) {
                    iyaw = yaw;
                    ipitch = pitch;
                    ig = g;
                    acceptMove(p, false, true);
                }
            }
            case 0x1D -> { // move_player_status_only: Boolean onGround — 위치·회전 없이 땅 위 여부만(바닐라 StatusOnly)
                boolean g = frame.readBoolean();
                Player p = player;
                if (p != null && pendingTeleportId == -1 && p.isSleeping()) {
                    acceptSleeping(ctx, p, p.x(), p.y(), p.z(), iyaw, ipitch);
                } else if (p != null && pendingTeleportId == -1 && p.vehicle() != null) {
                    acceptRiding(p, iyaw, ipitch, g);
                } else if (p != null && pendingTeleportId == -1 && correctionTpId == -1) {
                    ig = g;
                    acceptMove(p, false, false);
                }
            }
            case 0x1E -> { // move_vehicle: Double x,y,z, Float yRot,xRot — 조종 중인 탈것의 위치(클라이언트 권위)
                double x = frame.readDouble(), y = frame.readDouble(), z = frame.readDouble();
                float yRot = frame.readFloat(), xRot = frame.readFloat();
                Player p = player;
                if (p == null) return;
                if (org.domi.furnace.server.entity.VehicleMoves.invalid(x, y, z, yRot, xRot)) {
                    disconnect(ctx, Text.translatable("multiplayer.disconnect.invalid_vehicle_movement"));
                } else if (pendingTeleportId == -1 && correctionTpId == -1) {
                    world.runAt(p.x(), p.z(), region -> org.domi.furnace.server.entity.VehicleMoves.handle(p, x, y, z, yRot, xRot));
                }
            }
            case 0x1F -> { // paddle_boat: Boolean 왼쪽, Boolean 오른쪽 — 조종 중인 배의 노 상태
                boolean left = frame.readBoolean(), right = frame.readBoolean();
                Player p = player;
                if (p != null) world.runAt(p.x(), p.z(), region -> {
                    if (p.vehicle() instanceof org.domi.furnace.server.entity.Boat b && b.getControllingPassenger() == p) b.setPaddleState(left, right);
                });
            }
            case 0x26 -> { // player_input: Float 옆, Float 앞, Byte(1 점프, 2 웅크림) — 탄 동안만 반영(ServerPlayer.setPlayerInput)
                float xxa = frame.readFloat(), zza = frame.readFloat();
                byte flags = frame.readByte();
                Player p = player;
                if (p != null) p.setPlayerInput(xxa, zza, (flags & 1) != 0, (flags & 2) != 0);
            }
            case 0x09 -> { // client_command: 0 = 리스폰, 1 = 통계 요청
                touchLastAction();
                int action = Types.readVarInt(frame);
                if (action == 0) requestRespawn(ctx);
                else if (action == 1) {   // REQUEST_STATS: 바뀐 통계를 award_stats 로
                    Player sp = player;
                    if (sp != null && sp.stats() != null) sp.send(sp.stats().takeDirtyPacket());
                }
            }
            case 0x22 -> { // place_recipe: containerId(byte), 레시피 id(String), shift(bool)
                touchLastAction();
                int containerId = frame.readByte();
                String recipe = Types.readString(frame, 32767);
                boolean shift = frame.readBoolean();
                Player p = player;
                if (p != null) handlePlaceRecipe(p, containerId, recipe, shift);
            }
            case 0x23 -> { // player_abilities(serverbound): 비행 시작/끝 — 비행 가능할 때만 반영
                byte flags = frame.readByte();
                Player p = player;
                if (p != null) {
                    boolean fly = (flags & 0x02) != 0;
                    var ge = pluginEvents();
                    // 플러그인 PlayerToggleFlightEvent: 날 수 있고 상태가 바뀔 때만(Bukkit). 취소하면 능력 패킷을 다시 보내 클라이언트 비행을 되돌린다
                    if (ge.has(org.domi.furnace.api.event.player.PlayerToggleFlightEvent.class) && p.vitals().mayfly() && p.vitals().isFlying() != fly) {
                        p.runOnOwner(() -> {
                            if (ge.playerToggle(p, 2, fly)) p.vitals().setFlying(fly);
                            else p.send(p.vitals().abilitiesPacket());
                        });
                    } else {
                        p.vitals().setFlying(fly);
                    }
                }
            }
            case 0x25 -> { // player_command: eid, action, data
                touchLastAction();
                Types.readVarInt(frame);
                int action = Types.readVarInt(frame);
                int jumpData = Types.readVarInt(frame);
                Player p = player;
                if (p != null) {
                    switch (action) {
                        case 0 -> toggle(p, 0, true, () -> p.sneaking(true));      // PRESS_SHIFT_KEY
                        case 1 -> toggle(p, 0, false, () -> p.sneaking(false));    // RELEASE_SHIFT_KEY
                        case 2 -> world.runAt(p.x(), p.z(), region -> {   // STOP_SLEEPING: 침대에서 나가기 버튼
                            if (p.isSleeping()) org.domi.furnace.server.entity.PlayerSleeping.stopSleepInBed(p, region.level(), false, true);
                        });
                        case 3 -> toggle(p, 1, true, () -> p.sprinting(true));     // START_SPRINTING
                        case 4 -> toggle(p, 1, false, () -> p.sprinting(false));   // STOP_SPRINTING
                        case 5, 6, 7 -> { int act = action; int data = jumpData; world.runAt(p.x(), p.z(), region -> handleRidingCommand(p, act, data)); }
                        case 8 -> world.runAt(p.x(), p.z(), region -> {   // START_FALL_FLYING: 안 되면 stopFallFlying(플래그 다시 보냄)
                            if (region.hasPlayer(p)) org.domi.furnace.server.entity.ElytraFlight.handleStartCommand(p);
                        });
                        default -> { }
                    }
                }
            }
            case 0x24 -> { // player_action: 0 시작, 1 취소, 2 완료(파괴), 3·4 버리기, 5 사용 끝, 6 손 바꾸기
                touchLastAction();
                int status = Types.readVarInt(frame);
                int[] bp = Types.readBlockPos(frame);
                int face = frame.readByte();        // face
                int seq = Types.readVarInt(frame); // 예측 sequence — block_update 후 ack로 회신
                Player actor = player;
                if (actor != null && status >= 0 && status <= 2) handleBlockBreak(actor, status, bp[0], bp[1], bp[2], face, seq);
                if (actor != null && status == 5) world.runAt(actor.x(), actor.z(), r -> r.handleReleaseUsing(actor));   // 사용 끝(release)
                if (actor != null && status == 6) actor.runOnOwner(() -> swapHands(actor));   // SWAP_ITEM_WITH_OFFHAND(F 키)
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
            case 0x2A -> { // rename_item: 이름(String) — 모루가 열려 있을 때만
                String name = Types.readString(frame, 32767);
                Player p = player;
                if (p != null) handleRenameItem(p, name);
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
            case 0x2D -> { // select_trade: 거래 번호(VarInt)
                int tradeIndex = Types.readVarInt(frame);
                Player p = player;
                if (p != null) handleSelectTrade(p, tradeIndex);
            }
            case 0x2E -> { // set_beacon: Optional<효과> 둘 (불리언 + VarInt 레지스트리 id)
                String primary = frame.readBoolean() ? org.domi.furnace.server.item.ItemContext.name("minecraft:mob_effect", Types.readVarInt(frame)) : null;
                String secondary = frame.readBoolean() ? org.domi.furnace.server.item.ItemContext.name("minecraft:mob_effect", Types.readVarInt(frame)) : null;
                Player p = player;
                if (p != null) handleSetBeacon(p, primary, secondary);
            }
            case 0x36 -> { // swing: 손(VarInt) — ServerGamePacketListenerImpl.handleAnimate → player.swing(hand): 보는 사람에게 animate(본인 제외)
                touchLastAction();
                int hand = Types.readVarInt(frame);
                if (hand < 0 || hand > 1) {
                    protocolError(ctx, "Swing", new IllegalArgumentException("bad enum"));
                    break;
                }
                Player actor = player;
                if (actor != null) world.runAt(actor.x(), actor.z(), r -> {
                    r.swingLeftClick(actor, hand);   // 플러그인 LEFT_CLICK_AIR(Spigot: 휘두르기 앞, 취소해도 휘두르기는 보인다)
                    actor.swing(hand, false);
                });
            }
            case 0x20 -> { // pick_item: 인벤토리 번호(VarInt) — handlePickItem(서바이벌 휠클릭; 크리에이티브 복제는 클라이언트가 set_creative_mode_slot 으로)
                int slot = Types.readVarInt(frame);
                if (slot < 0 || slot >= 36) {   // 바닐라는 검사 없이 items.get → 예외(처리 오류) — 가정: 끊는다
                    protocolError(ctx, "Pick Item", new IndexOutOfBoundsException(String.valueOf(slot)));
                    break;
                }
                inv.pickSlot(slot);
                int sel = inv.selected();
                send(ctx, new org.domi.furnace.server.net.protocol.play.ContainerSetSlot(-2, 0, sel, inv.get(inv.heldWindowSlot())));
                send(ctx, new org.domi.furnace.server.net.protocol.play.ContainerSetSlot(-2, 0, slot, inv.get(org.domi.furnace.server.entity.PlayerInventory.windowSlot(slot))));
                send(ctx, new SetHeldSlot(sel));
            }
            case 0x2F -> { // Set Held Item(serverbound): Short slot(0~8)
                int slot = frame.readShort();
                if (slot >= 0 && slot < 9) {
                    Player p = player;
                    var ge = pluginEvents();
                    if (p != null && ge.has(org.domi.furnace.api.event.player.PlayerItemHeldEvent.class)) {
                        // 플러그인 PlayerItemHeldEvent: 소유 스레드에서 묻는다. 취소하면 이전 칸을 다시 보낸다
                        p.runOnOwner(() -> {
                            int prev = inv.selected();
                            if (ge.playerItemHeld(p, prev, slot)) inv.setSelected(slot);
                            else p.send(new SetHeldSlot(prev));
                        });
                    } else {
                        inv.setSelected(slot);
                    }
                    touchLastAction();
                }
            }
            case 0x17 -> { // jigsaw_generate: 칸, 단계 수(VarInt), 직소 블록 유지(bool) — handleJigsawGenerate
                int[] bp = Types.readBlockPos(frame);
                int levels = Types.readVarInt(frame);
                boolean keep = frame.readBoolean();
                Player actor = player;
                if (actor != null) handleJigsawGenerate(actor, bp, levels, keep);
            }
            case 0x33 -> { // set_jigsaw_block: 칸, 이름·대상·풀(ResourceLocation 문자열), 최종 상태(String), 연결 종류(String), 선택 우선순위·배치 우선순위(VarInt)
                int[] bp = Types.readBlockPos(frame);
                String name = Types.readString(frame, 32767), target = Types.readString(frame, 32767), pool = Types.readString(frame, 32767);
                String finalState = Types.readString(frame, 32767), joint = Types.readString(frame, 32767);
                int selection = Types.readVarInt(frame), placement = Types.readVarInt(frame);
                org.domi.furnace.server.data.ResourceId n = org.domi.furnace.server.data.ResourceId.tryParse(name), t = org.domi.furnace.server.data.ResourceId.tryParse(target),
                        pl = org.domi.furnace.server.data.ResourceId.tryParse(pool);
                if (n == null || t == null || pl == null) {   // readResourceLocation 이 잘못된 이름이면 디코드 실패 → 연결 종료
                    protocolError(ctx, "Set Jigsaw Block", new IllegalArgumentException("bad resource location"));
                    break;
                }
                Player actor = player;
                if (actor != null) handleSetJigsawBlock(actor, bp, n, t, pl, finalState, joint, selection, placement);
            }
            case 0x34 -> { // set_structure_block: 칸, 동작·모드(VarInt), 이름(String), 오프셋 3 byte(±48), 크기 3 byte(0~48), 미러·회전(VarInt), 메타데이터(String 128), 완전성 float(0~1), 시드 VarLong, 플래그 byte(1 엔티티 무시·2 공기 보기·4 상자 보기)
                int[] bp = Types.readBlockPos(frame);
                int update = Types.readVarInt(frame), mode = Types.readVarInt(frame);
                String name = Types.readString(frame, 32767);
                int[] offset = {Math.max(-48, Math.min(48, frame.readByte())), Math.max(-48, Math.min(48, frame.readByte())), Math.max(-48, Math.min(48, frame.readByte()))};
                int[] size = {Math.max(0, Math.min(48, frame.readByte())), Math.max(0, Math.min(48, frame.readByte())), Math.max(0, Math.min(48, frame.readByte()))};
                int mirror = Types.readVarInt(frame), rotation = Types.readVarInt(frame);
                String metadata = Types.readString(frame, 128);
                float integrity = Math.max(0.0F, Math.min(1.0F, frame.readFloat()));
                long seed = Types.readVarLong(frame);
                int flags = frame.readByte();
                if (update < 0 || update > 3 || mode < 0 || mode > 3 || mirror < 0 || mirror > 2 || rotation < 0 || rotation > 3) {   // readEnum: 범위 밖 번호는 디코드 실패
                    protocolError(ctx, "Set Structure Block", new IllegalArgumentException("bad enum"));
                    break;
                }
                Player actor = player;
                if (actor != null) handleSetStructureBlock(actor, bp, update, mode, name, offset, size, mirror, rotation, metadata, integrity, seed,
                        (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
            }
            case 0x30 -> { // set_command_block: 칸, 명령(String 32767), 모드(VarInt 0 체인·1 반복·2 임펄스), 플래그 byte(1 출력 기록·2 조건부·4 항상 활성)
                int[] bp = Types.readBlockPos(frame);
                String command = Types.readString(frame, 32767);
                int mode = Types.readVarInt(frame);
                int flags = frame.readByte();
                if (mode < 0 || mode > 2) {   // readEnum: 범위 밖 번호는 디코드 실패 → 연결 종료
                    protocolError(ctx, "Set Command Block", new IllegalArgumentException("bad enum"));
                    break;
                }
                Player actor = player;
                if (actor != null) { boolean[] f = org.domi.furnace.server.block.entity.CommandBlockEntity.decodeFlags(flags); handleSetCommandBlock(actor, bp, command, mode, f[0], f[1], f[2]); }
            }
            case 0x31 -> { // set_command_minecart: 엔티티 번호(VarInt), 명령(String 32767), 출력 기록(bool)
                int entityId = Types.readVarInt(frame);
                String command = Types.readString(frame, 32767);
                boolean track = frame.readBoolean();
                Player actor = player;
                if (actor != null) handleSetCommandMinecart(actor, entityId, command, track);
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
                if (actor != null) { actor.resetLastActionTime(); handleUseItemOn(ctx, actor, hand, bp, face, cx, cy, cz, inside, seq); }
            }
            case 0x35 -> { // sign_update: pos, 앞면인가, 줄 4개(각 384자)
                touchLastAction();
                int[] bp = Types.readBlockPos(frame);
                boolean front = frame.readBoolean();
                java.util.List<String> lines = new java.util.ArrayList<>(4);
                for (int i = 0; i < 4; i++) lines.add(stripFormatting(Types.readString(frame, 384)));
                Player actor = player;
                if (actor != null) handleSignUpdate(actor, bp, front, lines);
            }
            case 0x39 -> { // Use Item: hand, sequence, yRot, xRot
                touchLastAction();
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
                touchLastAction();
                int containerId = Types.readVarInt(frame);
                int stateId = Types.readVarInt(frame);
                int slotNum = frame.readShort();
                int button = frame.readByte();
                int clickTypeId = Types.readVarInt(frame);
                int numChanged = Types.readVarInt(frame);
                if (numChanged < 0 || numChanged > MAX_CLICK_CHANGED_SLOTS) {   // 바닐라는 128 초과를 거부한다
                    closeFlushed(ctx);
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
            case 0x0D -> { // Container Button Click: containerId, buttonId (VarInt 둘)
                touchLastAction();
                int containerId = Types.readVarInt(frame);
                int buttonId = Types.readVarInt(frame);
                Player p = player;
                if (p != null) handleContainerButtonClick(p, containerId, buttonId);
            }
            case 0x10 -> { // container_slot_state_changed: VarInt slotId, VarInt containerId, Boolean newState(true = 풀림)
                int slotId = Types.readVarInt(frame);
                int containerId = Types.readVarInt(frame);
                boolean newState = frame.readBoolean();
                Player p = player;
                if (p != null) handleContainerSlotStateChanged(p, slotId, containerId, newState);
            }
            case 0x0F -> { // Container Close(serverbound): VarInt containerId — 바닐라는 번호를 보지 않고 열린 창을 닫는다(doCloseContainer)
                Types.readVarInt(frame);
                Player p = player;
                if (p != null && p.containerMenu() != null) handleContainerClose(p);
            }
            case 0x01 -> { // block_entity_tag_query: VarInt 처리 번호, 위치 — op(2)만
                int tx = Types.readVarInt(frame);
                int[] bp = Types.readBlockPos(frame);
                Player p = player;
                if (p != null && hasPermission(p, 2)) handleBlockEntityTagQuery(p, tx, bp[0], bp[1], bp[2]);
            }
            case 0x15 -> { // entity_tag_query: VarInt 처리 번호, VarInt 엔티티 번호 — op(2)만
                int tx = Types.readVarInt(frame);
                int eid = Types.readVarInt(frame);
                Player p = player;
                if (p != null && hasPermission(p, 2)) handleEntityTagQuery(p, tx, eid);
            }
            case 0x02 -> { // change_difficulty: 난이도 번호(바닐라 WRAP) — op(2)만, 잠금은 무시하지 않는다
                int id = Types.readVarInt(frame);
                Player p = player;
                if (p != null && login.core != null && hasPermission(p, 2)) login.core.setDifficulty(org.domi.furnace.server.world.Difficulty.byId(id), false);
            }
            case 0x19 -> { // lock_difficulty: boolean — op(2)만
                boolean locked = frame.readBoolean();
                Player p = player;
                if (p != null && login.core != null && hasPermission(p, 2)) login.core.setDifficultyLocked(locked);
            }
            case 0x08 -> { // chunk_batch_received: float 틱당 청크 수(바닐라 PlayerChunkSender.onChunkBatchReceivedByClient)
                float cpt = frame.readFloat();
                Player p = player;
                if (p != null) p.chunkSendRate().onBatchReceived(cpt);
            }
            case 0x2B -> readPackResponse(ctx, frame); // resource_pack(play): 필수 팩 거절만 쫓아낸다
            case 0x12 -> handleCustomPayload(frame, true); // custom_payload
            case 0x37 -> { // teleport_to_entity: UUID — 관전자만
                java.util.UUID target = Types.readUuid(frame);
                Player p = player;
                if (p != null && p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) handleTeleportToEntity(p, target);
            }
            default -> { /* 그 외 무시 */ }
        }
    }

    private volatile boolean awaitingPack;

    /**
     * 구성 단계 keep-alive(바닐라는 구성 단계에서도 15초마다 보낸다). 팩 응답을 기다리는 동안에만 돈다 — 팩이 없는 서버의 접속 순서는 그대로다.
     * 클라이언트는 구성 keep_alive(0x04)로 답하고, 그 입력이 읽기 타임아웃을 갱신한다. 응답 검사는 읽기 타임아웃에 맡긴다.
     */
    private void scheduleConfigKeepAlive(ChannelHandlerContext c) {
        c.executor().schedule(() -> {
            if (!(awaitingPack || awaitingConfigure) || !c.channel().isActive() || state != ConnectionState.CONFIGURATION) return;
            send(c, new org.domi.furnace.server.net.protocol.configuration.ConfigKeepAlive(System.nanoTime()));
            scheduleConfigKeepAlive(c);
        }, CONFIG_KEEPALIVE_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** 구성 keep-alive 간격(초). 읽기 타임아웃(30초)의 절반. */
    static final int CONFIG_KEEPALIVE_SECONDS = 15;

    /** server.properties 의 리소스 팩(resource-pack 이 비어 있으면 null). */
    private org.domi.furnace.server.config.ServerProperties.ResourcePackInfo serverPack() {
        return login.resourcePack;
    }

    private static org.domi.furnace.server.net.protocol.play.ResourcePackPush packPush(
            org.domi.furnace.server.config.ServerProperties.ResourcePackInfo i, boolean inPlay) {
        Text prompt = null;
        if (i.prompt() != null) {
            try {
                prompt = org.domi.furnace.api.text.serializer.JsonTextSerializer.json().deserialize(i.prompt());
            } catch (RuntimeException e) {
                log.warn("Failed to parse resource pack prompt '{}'", i.prompt(), e);
            }
        }
        String hash = i.hash().length() > org.domi.furnace.server.net.protocol.play.ResourcePackPush.MAX_HASH
                ? i.hash().substring(0, org.domi.furnace.server.net.protocol.play.ResourcePackPush.MAX_HASH) : i.hash();
        return new org.domi.furnace.server.net.protocol.play.ResourcePackPush(i.id(), i.url(), hash, i.isRequired(), prompt, inPlay);
    }

    /**
     * resource_pack 응답 읽기(구성·play 공통). 상태가 범위 밖이면 연결을 닫는다(-1). 필수 팩을 거절하면 바닐라 메시지로 쫓아낸다(-1).
     * 그 밖에는 상태 번호를 돌려준다.
     */
    private int readPackResponse(ChannelHandlerContext ctx, ByteBuf frame) {
        java.util.UUID id;
        int action;
        try {
            id = Types.readUuid(frame);
            action = Types.readVarInt(frame);
        } catch (RuntimeException e) {
            closeFlushed(ctx);
            return -1;
        }
        if (action < 0 || action >= ResourcePackResponses.ACTION_COUNT) { closeFlushed(ctx); return -1; }
        // 플러그인이 sendResourcePack 으로 보낸 팩의 응답: 끝 상태면 Future 를 끝내고, 필수 팩 거절이면 쫓아낸다
        PendingPack mine = pluginPacks.get(id);
        lastPackResponseWasPlugin = mine != null;
        if (mine != null) {
            var status = org.domi.furnace.api.net.ResourcePackStatus.values()[action];
            if (status.isTerminal() && pluginPacks.remove(id, mine)) completeAsync(mine.future(), status, null);
            if (ResourcePackResponses.kicksRequired(action, mine.required())) {
                log.info("Disconnecting {} due to resource pack {} rejection", playerName, id);
                kick(ctx, Text.translatable("multiplayer.requiredTexturePrompt.disconnect"));
                return -1;
            }
            return action;
        }
        var pack = serverPack();
        if (pack != null && ResourcePackResponses.kicksRequired(action, pack.isRequired())) {
            log.info("Disconnecting {} due to resource pack {} rejection", playerName, id);
            kick(ctx, Text.translatable("multiplayer.requiredTexturePrompt.disconnect"));
            return -1;
        }
        return action;
    }

    /** 바닐라 ServerPlayer.resetLastActionTime — player-idle-timeout 의 기준. 이동 패킷은 갱신하지 않는다. */
    private void touchLastAction() {
        Player p = player;
        if (p != null) p.resetLastActionTime();
    }

    private boolean hasPermission(Player p, int level) {
        org.domi.furnace.server.ServerCore c = login.core;
        return c != null && c.profilePermissions(org.domi.furnace.server.ServerCore.profileOf(p)) >= level;
    }

    /**
     * custom_payload(채널 이름 문자열 + 나머지 바이트, 상한 32767바이트). minecraft:brand 는 문자열 하나로 읽어 기억하고,
     * play 단계의 나머지 채널은 PluginMessageEvent 로 넘긴다. 잘못된 입력은 조용히 버린다.
     */
    private void handleCustomPayload(ByteBuf frame, boolean inPlay) {
        try {
            String channel = Types.readString(frame, 32767);
            int n = frame.readableBytes();
            if (n > org.domi.furnace.server.net.protocol.play.CustomPayloadPacket.MAX_SERVERBOUND) {
                log.debug("custom_payload 가 너무 크다({}바이트): {}", n, channel);
                return;
            }
            if (channel.equals("minecraft:brand")) {
                String brand = Types.readString(frame, 32767);
                this.clientBrand = brand;
                Player p = player;
                if (p != null) p.clientBrand(brand);
                return;
            }
            // Bukkit 관례: 클라이언트(모드)가 듣는 채널을 알린다. 구성·play 두 단계 모두 받는다(Fabric 은 구성 단계에서 보낸다). 플러그인 메시지 이벤트로는 넘기지 않는다.
            if (channel.equals(PluginChannels.REGISTER) || channel.equals(PluginChannels.UNREGISTER)) {
                byte[] data = new byte[n];
                frame.readBytes(data);
                if (channel.equals(PluginChannels.REGISTER)) pluginChannels.register(data);
                else pluginChannels.unregister(data);
                return;
            }
            if (!inPlay) return;
            Player p = player;
            org.domi.furnace.server.ServerCore c = login.core;
            if (p == null || c == null || !c.events().hasSubscribers(org.domi.furnace.api.event.player.PluginMessageEvent.class)) return;
            byte[] data = new byte[n];
            frame.readBytes(data);
            c.events().call(new org.domi.furnace.api.event.player.PluginMessageEvent(c.plugins().player(p), channel, data));
        } catch (Exception e) {
            log.debug("custom_payload 파싱 실패", e);
        }
    }

    private volatile String clientBrand;
    /** 이 연결의 클라이언트가 등록한 플러그인 채널(구성 단계부터 모으고 play 진입 때 Player 에 넘긴다). */
    private final PluginChannels pluginChannels = new PluginChannels();

    /** 바닐라 handleBlockEntityTagQuery: 그 칸의 블록 엔티티 NBT(saveWithoutMetadata)를 tag_query 로. 로드 안 됐거나 블록 엔티티가 아니면 null 로 답한다(바닐라는 null 태그를 보낸다). */
    private void handleBlockEntityTagQuery(Player p, int tx, int bx, int by, int bz) {
        var pos = new org.domi.furnace.server.block.Pos(bx, by, bz);
        org.domi.furnace.server.command.builtin.DataCommands.blockEntityTag(world, pos)
                .thenAccept(tag -> p.send(new org.domi.furnace.server.net.protocol.play.TagQuery(tx,
                        tag == null ? null : (org.domi.furnace.server.net.io.nbt.NbtCompound) org.domi.furnace.server.net.io.nbt.NbtTags.freeze(tag))));
    }

    /**
     * 바닐라 handleEntityTagQuery: 같은 레벨에서 있는 엔티티면 saveWithoutId 를 tag_query 로(없으면 아무것도 안 보낸다).
     * 질의 플레이어가 추적 중인(tracked) 엔티티만 찾는다. tracked 는 질의 플레이어의 소유 region 이 갱신하는 맵이라 그 region 에서 읽고,
     * 대상 엔티티·플레이어의 NBT 는 그 대상의 소유 region(추적 위치의 region)에서 읽어 응답을 보낸다. 소유 region 이 아니면(이동 중) 버린다.
     */
    private void handleEntityTagQuery(Player p, int tx, int eid) {
        world.runAt(p.x(), p.z(), own -> {
            if (own.players().get(p.entityId()) != p) return;   // 이 틱의 소유 region 이 아님(차원 이동·넘기기 중)
            if (eid == p.entityId()) {
                sendEntityTag(p, tx, p);
                return;
            }
            double[] at = p.tracked().get(eid);
            if (at == null) return;   // 추적하지 않는 엔티티는 클라이언트가 알 수 없다
            world.runAt(at[0], at[2], region -> {
                Player target = region.players().get(eid);
                if (target != null) {
                    if (!target.isDisconnected()) sendEntityTag(p, tx, target);
                    return;
                }
                var lvl = region.level();
                if (lvl == null) return;
                var e = lvl.entities().get(eid);
                if (e == null) return;
                p.send(new org.domi.furnace.server.net.protocol.play.TagQuery(tx,
                        (org.domi.furnace.server.net.io.nbt.NbtCompound) org.domi.furnace.server.net.io.nbt.NbtTags.freeze(e.saveWithoutId(new org.domi.furnace.server.net.io.nbt.NbtCompound()))));
            });
        });
    }

    // 응답 NBT 는 배열을 byte[]/int[]/long[] 꼴(freeze)로 보낸다 — /data 경로 연산용 NbtArray(thaw)는 NbtWriter 가 못 쓴다
    // (2026-11-03 봇 시험: UUID int 배열 때문에 엔티티 질의 응답이 쓰기 예외로 사라졌다).
    private void sendEntityTag(Player to, int tx, Player target) {
        org.domi.furnace.server.net.io.nbt.NbtCompound tag;
        synchronized (target.menuLock()) { tag = org.domi.furnace.server.entity.PlayerData.snapshotNbt(target); }
        to.send(new org.domi.furnace.server.net.protocol.play.TagQuery(tx, (org.domi.furnace.server.net.io.nbt.NbtCompound) org.domi.furnace.server.net.io.nbt.NbtTags.freeze(tag)));
    }

    /**
     * 바닐라 handleTeleportToEntityPacket(관전자만): UUID 의 엔티티가 있는 레벨로 옮긴다. 이 서버는 접속 중인 플레이어만 찾는다
     * (바닐라 관전자 메뉴는 플레이어 목록뿐이다 — 다른 엔티티 UUID 는 무시).
     */
    private void handleTeleportToEntity(Player p, java.util.UUID target) {
        org.domi.furnace.server.ServerCore c = login.core;
        if (c == null) return;
        for (Player o : c.players()) {
            if (o != p && !o.isDisconnected() && target.equals(o.uuid())) {
                org.domi.furnace.server.command.builtin.SpectatorTeleports.toPlayer(c, p, o);
                return;
            }
        }
    }

    /**
     * 블록 파괴 요청(player_action 0 시작·1 취소·2 완료 — 바닐라 handlePlayerAction → ServerPlayerGameMode.handleBlockBreakAction → ack).
     * 거리·높이·스폰 보호·게임 모드 판정과 서바이벌 진행도 검증·드롭은 그 플레이어의 region 에서(BlockBreaking).
     */
    private void handleBlockBreak(Player actor, int status, int bx, int by, int bz, int face, int seq) {
        org.domi.furnace.server.ServerCore core = login.core;
        var pos = new org.domi.furnace.server.block.Pos(bx, by, bz);
        world.runAtRequested(bx, bz, actor.x(), actor.z(), region -> {
            // 이미 로드된 청크만 — 임의 좌표 요청이 청크를 새로 생성(수백 ms)시키지 못하게 한다.
            if (by >= world.dimensionType().minY() && by < world.dimensionType().maxY() && region.chunkLoaded(ChunkPos.ofBlock(bx, bz))) {
                boolean mayInteract = core == null || core.mayInteract(actor, bx, bz);
                region.handleBlockBreakAction(actor, pos, status, face, mayInteract);
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

    /** 바닐라 handlePlaceRecipe: 레시피 북이 열린 창의 격자를 채운다(스펙테이터는 안 된다). */
    private void handlePlaceRecipe(Player p, int containerId, String recipeId, boolean shift) {
        var rm = menus.recipes();
        if (rm == null) return;
        org.domi.furnace.server.data.ResourceId rid = org.domi.furnace.server.data.ResourceId.tryParse(recipeId);
        if (rid == null) return;
        var entry = rm.craftingEntry(rid);
        if (entry == null) return;
        var menu = p.containerMenu();
        if (menu == null || menu.containerId != containerId || !(menu instanceof org.domi.furnace.server.menu.RecipeBookMenu rbm)) return;
        int[] bp = menu.containerBlockPos();
        Runnable body = () -> {
            if (p.containerMenu() != menu) return;
            if (p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) { menu.sendAllDataToRemote(); return; }
            if (!menu.stillValid(menus)) { log.debug("Player {} interacted with invalid menu {}", p.name(), menu); return; }
            rbm.beginPlacingRecipe();
            try {
                new org.domi.furnace.server.menu.ServerPlaceRecipe(rbm, menus, inv, (cid, r) ->
                        menus.out(new org.domi.furnace.server.net.protocol.play.PlaceGhostRecipe(cid, r.id().toString()))).recipeClicked(entry, shift);
            } finally {
                rbm.finishPlacingRecipe(entry);
            }
            menu.broadcastChanges();
        };
        if (bp == null) {
            synchronized (p.menuLock()) { body.run(); }
        } else {
            world.runAt(bp[0], bp[2], region -> runOnBlockRegion(region, p, menu, bp, body));
        }
    }

    /** 바닐라 handleSelectTrade: 열린 창이 상인 거래이면 고른 거래의 재료를 지불 칸으로 옮겨 준다. */
    private void handleSelectTrade(Player p, int index) {
        var menu = p.containerMenu();
        if (!(menu instanceof org.domi.furnace.server.menu.MerchantMenu merchantMenu)) return;
        synchronized (p.menuLock()) {
            if (p.containerMenu() != menu) return;
            if (!merchantMenu.stillValid(menus)) {
                log.debug("Player {} interacted with invalid menu {}", p.name(), menu);
                return;
            }
            merchantMenu.setSelectionHint(index);
            merchantMenu.tryMoveItems(index);
            merchantMenu.broadcastChanges();
        }
    }

    /** ServerPlayer.openHorseInventory: 열린 창을 닫고 → 번호를 올리고 → horse_screen_open → HorseInventoryMenu → 첫 내용. */
    private void openHorseInventory(Player p, org.domi.furnace.server.entity.AbstractHorse horse, org.domi.furnace.server.menu.Container container, int columns) {
        if (player != p) return;
        synchronized (p.menuLock()) {
            if (p.hasOpenContainer()) closeContainer(p);
            int containerId = p.nextContainerCounter();
            send(ctx, new org.domi.furnace.server.net.protocol.play.HorseScreenOpen(containerId, columns, horse.id()));
            var menu = new org.domi.furnace.server.menu.HorseInventoryMenu(containerId, p.inventoryMenu().inventoryContainer(), container, horse, columns, menus);
            menu.addSlotListener(menus);
            p.setContainerMenu(menu);
            menu.setSynchronizer(menus);
        }
    }

    /**
     * ServerPlayer.openMenu(엔티티 용기): 열린 창을 닫고 → 번호를 올리고 → 메뉴(상자 수레·상자 배는 9×3, 깔때기 수레는 깔때기) → open_screen.
     * 관전자가 전리품 표 있는 것을 열려 하면 메뉴가 없다(container.spectatorCantOpen 를 액션 바로).
     */
    private void openEntityContainer(Player p, org.domi.furnace.server.entity.ContainerEntity entity, boolean blocked) {
        if (player != p) return;
        synchronized (p.menuLock()) {
            if (p.hasOpenContainer()) closeContainer(p);
            int containerId = p.nextContainerCounter();
            if (blocked) {
                p.send(new org.domi.furnace.server.net.protocol.play.SystemChat(
                        Text.translatable("container.spectatorCantOpen").color(org.domi.furnace.api.text.NamedTextColor.RED), true));
                return;
            }
            var inventory = p.inventoryMenu().inventoryContainer();
            var c = new org.domi.furnace.server.world.EntityContainer(entity, p, world::runAt);
            org.domi.furnace.server.menu.AbstractContainerMenu menu = entity instanceof org.domi.furnace.server.entity.MinecartHopper
                    ? new org.domi.furnace.server.menu.HopperMenu(containerId, inventory, c, null, menus)
                    : org.domi.furnace.server.menu.ChestMenu.threeRows(containerId, inventory, c, null, menus);
            String type = entity.asEntity().type().name();
            Text title = Text.translatable("entity." + type.replace(':', '.'));   // getDisplayName: 이름표 이름은 TODO(이름표)
            send(ctx, new OpenScreen(containerId, menuTypeId(menu), title));
            menu.addSlotListener(menus);
            p.setContainerMenu(menu);
            menu.setSynchronizer(menus);
        }
    }

    /** 플러그인 Menu 화면 열기: 열린 창을 닫고 → 번호를 올리고 → 메뉴 → InventoryOpenEvent(취소되면 그만) → open_screen. */
    private void openPluginMenu(Player p, org.domi.furnace.server.plugin.MenuSession session) {
        if (player != p || p.isDisconnected()) return;
        synchronized (p.menuLock()) {
            if (p.hasOpenContainer()) closeContainer(p);
            int containerId = p.nextContainerCounter();
            var menu = session.createMenu(containerId, p.inventoryMenu().inventoryContainer(), menus);
            if (login.core != null && !login.core.gameEvents().inventoryOpen(p, menu)) return;
            send(ctx, new OpenScreen(containerId, menuTypeId(menu), p.localize(session.title())));
            menu.addSlotListener(menus);
            p.setContainerMenu(menu);
            menu.setSynchronizer(menus);
            session.opened();
        }
    }

    /**
     * 바닐라 Merchant.openTradingScreen(player, title, villagerLevel): 거래 화면을 열고 거래 목록을 보낸다.
     * 주민·떠돌이 상인 엔티티가 생기면 그쪽에서 부른다(이벤트 루프 밖에서 불러도 된다).
     */
    public void openMerchantScreen(org.domi.furnace.server.trade.Merchant merchant, Text title, int villagerLevel) {
        ctx.channel().eventLoop().execute(() -> {
            Player p = player;
            if (p == null) return;
            synchronized (p.menuLock()) {
                if (p.hasOpenContainer()) closeContainer(p);
                int containerId = p.nextContainerCounter();
                merchant.setTradingPlayer(menus);
                var menu = new org.domi.furnace.server.menu.MerchantMenu(containerId, p.inventoryMenu().inventoryContainer(), menus, merchant);
                send(ctx, new OpenScreen(containerId, menuTypeId(menu), title));
                menu.addSlotListener(menus);
                p.setContainerMenu(menu);
                menu.setSynchronizer(menus);
                var offers = merchant.offers();
                if (!offers.isEmpty()) {
                    send(ctx, new org.domi.furnace.server.net.protocol.play.MerchantOffersPacket(containerId, offers, villagerLevel,
                            merchant.villagerXp(), merchant.showProgressBar(), merchant.canRestock()));
                }
            }
        });
    }

    /**
     * 바닐라 handleEditBook: 핫바(0~8)·보조 손(40) 칸의 깃펜 책을 저장(제목 없음)하거나 서명(제목 있음)한다. 창 칸으로 바꿔 넣고 바뀐 칸을 방송한다.
     * 글자 걸러내기는 없다. 값이 코덱 한도(쪽 1024자·제목 32자)를 넘으면 무시한다(바닐라는 일단 담고 이후 저장·전송에서 실패한다).
     */
    private void handleEditBook(Player p, int slot, java.util.List<String> pages, String title) {
        if (!org.domi.furnace.server.entity.BookEditing.validSlot(slot)) return;
        int ws = org.domi.furnace.server.entity.BookEditing.windowSlot(slot);
        synchronized (p.menuLock()) {
            ItemStack cur = p.inventory().get(ws);
            ItemStack out;
            var core = login.core;
            if (core != null && org.domi.furnace.server.entity.BookEditing.write(cur, java.util.List.of()) != null) {   // 깃펜 책일 때만 플러그인 PlayerEditBookEvent
                var edit = core.gameEvents().editBook(p, slot, org.domi.furnace.server.entity.BookEditing.pagesOf(cur), pages, title);
                if (edit == null) return;
                pages = edit.pages();
                title = edit.title();
            }
            try {
                out = title == null ? org.domi.furnace.server.entity.BookEditing.write(cur, pages)
                        : org.domi.furnace.server.entity.BookEditing.sign(cur, title, pages, p.name());
            } catch (RuntimeException e) {   // 코덱 한도 초과 등: 연결을 끊지 않고 무시
                log.warn("edit_book 무시 ({}): {}", p.name(), e.toString());
                return;
            }
            if (out == null) return;
            var im = p.inventoryMenu();
            im.getSlot(ws).setByPlayer(out);
            im.broadcastChanges();
        }
    }

    /** 바닐라 handleInteract: 6칸(제곱 36) 안의 엔티티만. 공격(1)은 때리고, 상호작용(0·2)은 주민이면 거래 화면을 연다. */
    private void handleInteractEntity(org.domi.furnace.server.world.Region region, Player p, int entityId, int action, int hand, double hitY) {
        Player tp = region.players().get(entityId);
        if (tp != null) {   // 다른 플레이어: 공격만(상호작용은 플레이어에게 할 것이 없다). 관전자는 공격 대신 시점이라 넘어간다
            double dx = p.x() - tp.x(), dy = p.y() + 1.62 - tp.y(), dz = p.z() - tp.z();
            if (action == 1 && dx * dx + dy * dy + dz * dz < 36.0) {
                // ServerPlayer.attack: 관전자는 때리지 않고 그 대상의 시점으로
                if (p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) { if (tp != p) region.setCamera(p, tp); }
                else region.attackPlayer(p, tp);
            }
            return;
        }
        var e = region.level().entities().get(entityId);
        final ItemStack heldBefore = p.inventory().get(hand == 0 ? p.inventory().heldWindowSlot() : 45);   // 바닐라 handleInteract: 상호작용 전 손에 든 것의 사본
        if (e == null) {   // 엔더 드래곤 파트(본체 id+1..+8): 바닐라 getEntityOrPart
            for (int k = 1; k <= 8 && e == null; k++) {
                if (region.level().entities().get(entityId - k) instanceof org.domi.furnace.server.entity.EnderDragon dragon) {
                    var part = dragon.partById(entityId);
                    // 바닐라 handleInteract: canInteractWithEntity(대상 상자, 1.0) — 상자와 눈 위치 거리² < (3 + 1)²
                    var bb = part.boundingBox();
                    double ex = p.x(), ey = p.y() + 1.62, ez = p.z();
                    double dx = Math.max(Math.max(bb.minX() - ex, ex - bb.maxX()), 0.0);
                    double dy = Math.max(Math.max(bb.minY() - ey, ey - bb.maxY()), 0.0);
                    double dz = Math.max(Math.max(bb.minZ() - ez, ez - bb.maxZ()), 0.0);
                    if (action == 1 && dragon.isAlive() && dx * dx + dy * dy + dz * dz < 16.0) region.attackDragonPart(p, part);
                    return;
                }
            }
        }
        if (e == null || e.isRemoved()) return;
        double dx = p.x() - e.x(), dy = p.y() + 1.62 - e.y(), dz = p.z() - e.z();
        if (dx * dx + dy * dy + dz * dz >= 36.0) return;
        if (action == 0 && !region.level().gameEvents().playerInteractEntity(p, e, hand, heldBefore)) {
            // 플러그인 PlayerInteractEntityEvent 취소: 아무 상호작용도 하지 않고, 클라이언트가 예측한 손 아이템 변화를 되돌린다
            org.domi.furnace.server.plugin.ClientResync.hand(p, hand);
            return;
        }
        if (action == 1 && p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) {
            // ServerPlayer.attack: 관전자의 왼쪽 클릭은 공격 대신 그 엔티티의 시점으로
            if (e instanceof org.domi.furnace.server.entity.LivingEntity || e.isPickable()) region.setCamera(p, e);
        } else if (action == 1) {
            if (e instanceof org.domi.furnace.server.entity.LivingEntity le) region.attack(p, le);
            else if (e.isPickable()) region.attackEntity(p, e);
        } else if (action == 0 && e instanceof org.domi.furnace.server.entity.Mob nameMob
                && org.domi.furnace.server.entity.ItemEffects.nameTag(p, hand, p.inventory().get(hand == 0 ? p.inventory().heldWindowSlot() : 45), nameMob)) {
            // Mob.checkAndHandleImportantInteractions: 이름표가 끈·mobInteract 보다 먼저
        } else if (action == 0 && e instanceof org.domi.furnace.server.entity.Leashable leashMob
                && leashMob.interactLeash(p, hand, heldBefore)) {
            // Entity.interact 의 끈 맺기·풀기(mobInteract 보다 먼저)
            interactedWith(p, heldBefore, e);
        } else if (e instanceof org.domi.furnace.server.entity.LeashFenceKnotEntity knot) {
            if (action == 0) knot.interact(p);
        } else if (e instanceof org.domi.furnace.server.entity.Boat boat) {
            if (action == 0 && boat.interact(p, hand)) interactedWith(p, heldBefore, e);
        } else if (e instanceof org.domi.furnace.server.entity.AbstractMinecart cart) {
            if (action == 0) {
                boolean used = false;
                if (cart instanceof org.domi.furnace.server.entity.Minecart m) used = m.interact(p, hand);
                else if (cart instanceof org.domi.furnace.server.entity.MinecartFurnace f) used = f.interact(p, hand);
                else if (cart instanceof org.domi.furnace.server.entity.AbstractMinecartContainer c) used = c.interact(p, hand);
                if (used) interactedWith(p, heldBefore, e);
            }
        } else if (e instanceof org.domi.furnace.server.entity.HangingEntity hanging) {
            if (action == 0) hanging.interact(p, hand);
        } else if (e instanceof org.domi.furnace.server.entity.ArmorStand stand) {
            if (action == 2) stand.interactAt(p, hitY, hand);
            else if (action == 0) {   // Player.interactOn: Entity.interact 가 PASS 면 아이템의 interactLivingEntity(이름표)
                org.domi.furnace.server.entity.ItemEffects.nameTag(p, hand, p.inventory().get(hand == 0 ? p.inventory().heldWindowSlot() : 45), stand);
            }
        } else if (e instanceof org.domi.furnace.server.entity.WanderingTrader wt && action == 0 && hand == 0
                && !org.domi.furnace.server.item.ItemTools.itemName(p.inventory().get(p.inventory().heldWindowSlot())).endsWith("_spawn_egg")) {
            // 바닐라 WanderingTrader.mobInteract: 거래 중이 아니고 거래가 있으면 거래 화면(레벨 1). 없으면 아무 일도 없다.
            if (wt.canTrade()) {
                openMerchantScreen(wt, wt.tradeTitle(), 1);
                interactedWith(p, heldBefore, e);
            }
        } else if (e instanceof org.domi.furnace.server.entity.Mob mob && !(e instanceof org.domi.furnace.server.entity.Villager) && action == 0) {
            var inv = p.inventory();
            if (mob.interact(p, hand, inv.get(hand == 0 ? inv.heldWindowSlot() : 45), region)) interactedWith(p, heldBefore, e);
        } else if (e instanceof org.domi.furnace.server.entity.Villager vv && !p.sneaking()) {
            if (vv.canTrade()) {
                vv.updateSpecialPrices(p);   // 바닐라 startTrading: 평판·영웅 특별 가격 → 거래 화면
                openMerchantScreen(vv, vv.tradeTitle(), vv.villagerLevel());
            }
            else vv.playSound("entity.villager.no", 1.0F, 1.0F);
            interactedWith(p, heldBefore, e);   // 바닐라 Villager.mobInteract 는 두 경우 모두 sidedSuccess
        }
    }

    /**
     * 바닐라 handleInteract: 상호작용이 행동을 소비했으면 player_interacted_with_entity(상호작용 전 손 아이템 사본, 대상).
     * 가정: 소비한 경우 늘 SUCCESS(통계 대상)로 보고 아이템을 넘긴다(CONSUME_PARTIAL 은 빈 손을 넘기는 차이가 있다).
     */
    private void interactedWith(Player p, ItemStack held, org.domi.furnace.server.entity.Entity target) {
        var item = org.domi.furnace.server.ServerCore.lootStackOf(held);
        var ent = org.domi.furnace.server.entity.KillCredit.lootOf(target);
        p.triggerAdvancement(a -> a.triggerItemEntity("minecraft:player_interacted_with_entity", item, ent));
    }

    /** 바닐라 handleSetBeaconPacket. */
    private void handleSetBeacon(Player p, String primary, String secondary) {
        var menu = p.containerMenu();
        if (!(menu instanceof org.domi.furnace.server.menu.BeaconMenu beacon)) return;
        Runnable body = () -> {
            if (p.containerMenu() != menu) return;
            if (!beacon.stillValid(menus)) log.debug("Player {} interacted with invalid menu {}", p.name(), menu);
            else {
                beacon.updateEffects(primary, secondary);
                beacon.broadcastChanges();
            }
        };
        int[] bp = menu.containerBlockPos();
        world.runAt(bp[0], bp[2], region -> runOnBlockRegion(region, p, menu, bp, body));
    }

    /** 바닐라 handleRenameItem: 열린 창이 모루이면 유효성을 보고 이름을 바꾼다. */
    private void handleRenameItem(Player p, String name) {
        var menu = p.containerMenu();
        if (!(menu instanceof org.domi.furnace.server.menu.AnvilMenu anvil)) return;
        Runnable body = () -> {
            if (p.containerMenu() != menu) return;
            if (!anvil.stillValid(menus)) log.debug("Player {} interacted with invalid menu {}", p.name(), menu);
            else {
                anvil.setItemName(name);
                anvil.broadcastChanges();
            }
        };
        int[] bp = menu.containerBlockPos();
        world.runAt(bp[0], bp[2], region -> runOnBlockRegion(region, p, menu, bp, body));
    }

    /**
     * 바닐라 handleContainerSlotStateChanged: 관전자가 아니고 열린 창 번호가 같고 제작기 창이면 setSlotState(빈 칸만 바뀐다).
     * 바닐라는 stillValid 를 보지 않는다. 데이터 칸 변경은 다음 틱 broadcastChanges 가 보낸다.
     */
    private void handleContainerSlotStateChanged(Player p, int slotId, int containerId, boolean newState) {
        if (!(p.containerMenu() instanceof org.domi.furnace.server.menu.CrafterMenu menu) || menu.containerId != containerId) return;
        int[] bp = menu.containerBlockPos();
        world.runAt(bp[0], bp[2], region -> runOnBlockRegion(region, p, menu, bp, () -> {
            if (p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) return;
            menu.crafter().setSlotState(slotId, newState, region.level());
        }));
    }

    /** 바닐라 handleContainerButtonClick: 열린 창이 맞고 관전자가 아니며 유효하면 clickMenuButton, 바뀌었으면 broadcastChanges. */
    private void handleContainerButtonClick(Player p, int containerId, int buttonId) {
        var menu = p.containerMenu();
        if (menu == null || menu.containerId != containerId) return;
        Runnable body = () -> {
            if (p.containerMenu() != menu) return;
            if (p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) return;
            if (!menu.stillValid(menus)) {
                log.debug("Player {} interacted with invalid menu {}", p.name(), menu);
            } else if (menu.clickMenuButton(menus, buttonId)) {
                menu.broadcastChanges();
            }
        };
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
            } else if (login.core != null && !login.core.gameEvents().inventoryClick(p, menu, slotNum, button, type)) {   // 플러그인 InventoryClickEvent 취소: 적용하지 않고 전체 재전송
                menu.sendAllDataToRemote();
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
            closeFlushed(ctx);
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
            if (login.core != null && !login.core.gameEvents().inventoryOpen(p, menu)) return;   // 플러그인 InventoryOpenEvent 취소
            send(ctx, new OpenScreen(containerId, menuTypeId(menu), Text.translatable("container.crafting")));
            menu.addSlotListener(menus);
            p.setContainerMenu(menu);
            menu.setSynchronizer(menus);
        }
    }

    private interface BlockMenuFactory {
        org.domi.furnace.server.menu.AbstractContainerMenu create(int containerId, org.domi.furnace.server.menu.Container playerInventory,
                int[] pos, org.domi.furnace.server.menu.BlockAccess access);
    }

    /** 블록에 매인 화면(석재 절단기·숫돌 등) 열기 — 그 좌표가 아직 block 인 동안만 유효하다. */
    private void openBlockMenu(int x, int y, int z, java.util.Set<String> blocks, String titleKey, BlockMenuFactory factory) {
        Player p = player;
        if (p == null) return;
        synchronized (p.menuLock()) {
            if (p.hasOpenContainer()) closeContainer(p);
            int containerId = p.nextContainerCounter();
            var menu = factory.create(containerId, p.inventoryMenu().inventoryContainer(), new int[]{x, y, z},
                    new org.domi.furnace.server.menu.BlockAccess() {
                        // 아래는 블록을 가진 region 스레드(MenuBlockContext 안)에서만 불린다
                        @Override public boolean stillBlock() {
                            var region = org.domi.furnace.server.world.MenuBlockContext.current();
                            return region == null || blocks.contains(world.blockRegistry().nameOf(region.getBlock(x, y, z)));
                        }

                        @Override public long gameTime() {
                            var region = org.domi.furnace.server.world.MenuBlockContext.current();
                            return region == null ? 0L : region.level().gameTime();
                        }

                        @Override public void playSound(String sound) {
                            var region = org.domi.furnace.server.world.MenuBlockContext.current();
                            if (region != null) region.level().playSound(null, x + 0.5, y + 0.5, z + 0.5, sound, "blocks", 1.0F, 1.0F);
                        }

                        @Override public void levelEvent(int type) {
                            var region = org.domi.furnace.server.world.MenuBlockContext.current();
                            if (region != null) region.level().levelEvent(null, type, new org.domi.furnace.server.block.Pos(x, y, z), 0);
                        }

                        @Override public void anvilUsed(boolean infiniteMaterials) {
                            var region = org.domi.furnace.server.world.MenuBlockContext.current();
                            if (region == null) return;
                            var pos = new org.domi.furnace.server.block.Pos(x, y, z);
                            int state = region.getBlock(x, y, z);
                            String name = world.blockRegistry().nameOf(state);
                            boolean anvil = "minecraft:anvil".equals(name) || "minecraft:chipped_anvil".equals(name) || "minecraft:damaged_anvil".equals(name);
                            if (!infiniteMaterials && anvil && p.random().nextFloat() < 0.12F) {
                                int damaged = org.domi.furnace.server.block.types.AnvilBlock.damage(state);
                                if (damaged < 0) {
                                    region.level().setBlock(pos, 0, 3);
                                    region.level().levelEvent(null, 1029, pos, 0);
                                } else {
                                    region.level().setBlock(pos, damaged, 2);
                                    region.level().levelEvent(null, 1030, pos, 0);
                                }
                            } else {
                                region.level().levelEvent(null, 1030, pos, 0);
                            }
                        }

                        @Override public MapInfo mapInfo(ItemStack map) {
                            var m = org.domi.furnace.server.map.MapStore.of(map);
                            return m == null ? null : new MapInfo(m.locked(), m.scale());
                        }

                        @Override public void playSound(String sound, float volume, float pitch) {
                            var region = org.domi.furnace.server.world.MenuBlockContext.current();
                            if (region != null) region.level().playSound(null, x + 0.5, y + 0.5, z + 0.5, sound, "blocks", volume, pitch);
                        }

                        @Override public float nextFloat() {
                            var region = org.domi.furnace.server.world.MenuBlockContext.current();
                            return region == null ? 0.5F : region.level().random().nextFloat();
                        }

                        /** EnchantingTableBlock.isValidBookShelf 를 세는 값: 둘레 5×5 의 가장자리(높이 0·1)에 책장이 있고 사이가 비어 있다. */
                        @Override public int enchantingPower() {
                            var region = org.domi.furnace.server.world.MenuBlockContext.current();
                            if (region == null) return 0;
                            int n = 0;
                            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                                if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;
                                for (int dy = 0; dy <= 1; dy++) {
                                    if (org.domi.furnace.server.block.B.isTag(region.getBlock(x + dx, y + dy, z + dz), "minecraft:enchantment_power_provider")
                                            && org.domi.furnace.server.block.B.isTag(region.getBlock(x + dx / 2, y + dy, z + dz / 2), "minecraft:enchantment_power_transmitter")) n++;
                                }
                            }
                            return n;
                        }

                        /** 숫돌 등: 블록 가운데에 경험치 구슬. */
                        @Override public void awardExperience(int amount) {
                            var region = org.domi.furnace.server.world.MenuBlockContext.current();
                            if (region != null) region.level().awardExperience(x + 0.5, y + 0.5, z + 0.5, amount);
                        }

                        @Override public int nextInt(int bound) {
                            var region = org.domi.furnace.server.world.MenuBlockContext.current();
                            return region == null ? 0 : region.level().random().nextInt(bound);
                        }
                    });
            if (login.core != null && !login.core.gameEvents().inventoryOpen(p, menu)) return;   // 플러그인 InventoryOpenEvent 취소
            send(ctx, new OpenScreen(containerId, menuTypeId(menu), Text.translatable(titleKey)));
            menu.addSlotListener(menus);
            p.setContainerMenu(menu);
            menu.setSynchronizer(menus);
        }
    }

    private void openStonecutter(int x, int y, int z) {
        openBlockMenu(x, y, z, java.util.Set.of("minecraft:stonecutter"), "container.stonecutter",
                (id, inv, pos, access) -> new org.domi.furnace.server.menu.StonecutterMenu(id, inv, menus, pos, access));
    }

    private void openSmithing(int x, int y, int z) {
        openBlockMenu(x, y, z, java.util.Set.of("minecraft:smithing_table"), "container.upgrade",
                (id, inv, pos, access) -> new org.domi.furnace.server.menu.SmithingMenu(id, inv, menus, pos, access));
    }

    private void openLoom(int x, int y, int z) {
        openBlockMenu(x, y, z, java.util.Set.of("minecraft:loom"), "container.loom",
                (id, inv, pos, access) -> new org.domi.furnace.server.menu.LoomMenu(id, inv, menus, pos, access));
    }

    private void openCartography(int x, int y, int z) {
        openBlockMenu(x, y, z, java.util.Set.of("minecraft:cartography_table"), "container.cartography_table",
                (id, inv, pos, access) -> new org.domi.furnace.server.menu.CartographyMenu(id, inv, menus, pos, access));
    }

    private void openAnvil(int x, int y, int z) {
        openBlockMenu(x, y, z, java.util.Set.of("minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil"), "container.repair",
                (id, inv, pos, access) -> new org.domi.furnace.server.menu.AnvilMenu(id, inv, menus, pos, access));
    }

    private void openEnchanting(int x, int y, int z) {
        openBlockMenu(x, y, z, java.util.Set.of("minecraft:enchanting_table"), "container.enchant",
                (id, inv, pos, access) -> new org.domi.furnace.server.menu.EnchantmentMenu(id, inv, menus, pos, access));
    }

    private void openGrindstone(int x, int y, int z) {
        openBlockMenu(x, y, z, java.util.Set.of("minecraft:grindstone"), "container.grindstone_title",
                (id, inv, pos, access) -> new org.domi.furnace.server.menu.GrindstoneMenu(id, inv, menus, pos, access));
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
        if (login.core != null) login.core.gameEvents().inventoryClose(p, menu);   // 플러그인 InventoryCloseEvent
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

        @Override public Player entityPlayer() { return p; }

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

        @Override public ItemStack prepareCraft(org.domi.furnace.server.menu.AbstractContainerMenu menu, String recipe, ItemStack result) {
            var c = login.core;
            return c == null ? result : c.gameEvents().prepareCraft(p, menu, recipe, result);   // 플러그인 PrepareItemCraftEvent
        }

        @Override public org.domi.furnace.server.plugin.GameEvents.EnchantOffer[] prepareEnchant(org.domi.furnace.server.menu.AbstractContainerMenu menu, int[] table,
                ItemStack item, int bookshelves, org.domi.furnace.server.plugin.GameEvents.EnchantOffer[] offers) {
            var c = login.core;
            return c == null ? offers : c.gameEvents().prepareEnchant(p, menu, table, item, bookshelves, offers);   // 플러그인 PrepareItemEnchantEvent
        }

        @Override public org.domi.furnace.server.plugin.GameEvents.EnchantApply enchantItem(org.domi.furnace.server.menu.AbstractContainerMenu menu, int[] table,
                ItemStack item, int button, int cost, java.util.Map<String, Integer> enchants) {
            var c = login.core;
            return c == null ? new org.domi.furnace.server.plugin.GameEvents.EnchantApply(cost, enchants) : c.gameEvents().enchantItem(p, menu, table, item, button, cost, enchants);   // 플러그인 EnchantItemEvent
        }

        @Override public org.domi.furnace.server.plugin.GameEvents.AnvilPrepared prepareAnvil(org.domi.furnace.server.menu.AbstractContainerMenu menu, ItemStack result,
                int repairCost, String renameText) {
            var c = login.core;
            return c == null ? null : c.gameEvents().prepareAnvil(p, menu, result, repairCost, renameText);   // 플러그인 PrepareAnvilEvent
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
            // 바닐라 ItemStack.onCraftedBy → awardStat(crafted:<item>, count). TODO(아이템 제작 훅): Item.onCraftedBy(stack, level, player).
            if (!stack.isEmpty() && count > 0) p.awardStat("minecraft:crafted:" + org.domi.furnace.server.item.ItemTools.itemName(stack), count);
        }

        @Override public void awardStat(String stat, int amount) { p.awardStat(stat, amount); }

        @Override
        public void onEquipItem(org.domi.furnace.server.menu.EquipSlot slot, ItemStack oldItem, ItemStack newItem) {
            if (p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) return;
            if (oldItem.isEmpty() && newItem.isEmpty()) return;
            if (oldItem.itemId() == newItem.itemId() && oldItem.patch().equals(newItem.patch())) return;
            if (!newItem.isEmpty() && equipmentSlotFor(newItem) == slot) {   // Equipable.getEquipSound — 슬롯에 맞는 장비일 때만
                String sound = org.domi.furnace.server.block.ItemUse.equipSound(org.domi.furnace.server.item.ItemTools.itemName(newItem));
                p.random().nextLong();   // playSeededSound 의 씨앗(엔티티 난수)
                world.runAt(p.x(), p.z(), region -> region.level().playSound(null, p.x(), p.y(), p.z(), sound, "players", 1.0F, 1.0F));
            }
            // Player.doesEmitEquipEvent: 방어구 칸만 equip/unequip 게임 이벤트
            if (slot.isArmor()) {
                double ex = p.x(), ey = p.y(), ez = p.z();
                world.runAt(ex, ez, region -> org.domi.furnace.server.entity.DispenserEquip.equipEvent(region.level(), ex, ey, ez, p, newItem));
            }
        }

        @Override public boolean mayBuild() {
            var gm = p.gameMode();
            return gm != org.domi.furnace.server.world.GameMode.ADVENTURE && gm != org.domi.furnace.server.world.GameMode.SPECTATOR;
        }

        @Override public int enchantmentSeed() { return p.vitals().enchantmentSeed(); }

        @Override public void onEnchantmentPerformed(int levels) { p.vitals().onEnchantmentPerformed(levels); }

        @Override public org.domi.furnace.server.loot.LootRegistries lootRegistries() {
            var core = login.core;
            return core == null ? null : core.dataPacks().loot().registries();
        }

        /** 경험치 구슬을 플레이어 자리에 낸다(화로 꺼내기 — 바닐라 createExperience(player.position())). */
        @Override public void awardExperience(int amount) {
            double px = p.x(), py = p.y(), pz = p.z();
            world.runAt(px, pz, region -> region.level().awardExperience(px, py, pz, amount));
        }

        @Override public int experienceLevel() { return p.vitals().xpLevel(); }

        @Override public void giveExperienceLevels(int levels) { p.vitals().giveExperienceLevels(levels); }

        @Override public org.domi.furnace.server.loot.Enchantment enchantment(String id) {
            var core = login.core;
            return core == null ? null : core.dataPacks().loot().registries().enchantment(id);
        }

        @Override public boolean enchantmentsCompatible(String a, String b) {
            var core = login.core;
            var ea = enchantment(a);
            var eb = enchantment(b);
            if (core == null || ea == null || eb == null) return true;
            return org.domi.furnace.server.loot.Enchantment.areCompatible(ea, eb, core.dataPacks().loot().registries());
        }

        @Override public boolean enchantmentSupports(String enchantment, ItemStack stack) {
            var core = login.core;
            var e = enchantment(enchantment);
            return core == null || e == null || e.supports(org.domi.furnace.server.item.ItemTools.itemName(stack), core.dataPacks().loot().registries());
        }

        @Override public int enchantmentMinCost(String enchantment, int level) {
            var core = login.core;
            var e = core == null ? null : core.dataPacks().loot().registries().enchantment(enchantment);
            return e == null ? 0 : e.minCost(level);
        }

        /** canInteractWithEntity(box, buffer): 눈 위치에서 상자까지 거리² < (엔티티 상호작용 거리 3 + buffer)². */
        @Override public boolean canInteractWithEntity(org.domi.furnace.server.entity.AABB box, double buffer) {
            double ex = p.x(), ey = p.eyeY(), ez = p.z();
            double dx = Math.max(Math.max(box.minX() - ex, ex - box.maxX()), 0.0);
            double dy = Math.max(Math.max(box.minY() - ey, ey - box.maxY()), 0.0);
            double dz = Math.max(Math.max(box.minZ() - ez, ez - box.maxZ()), 0.0);
            double r = 3.0 + buffer;
            return dx * dx + dy * dy + dz * dz < r * r;
        }

        @Override public boolean isRiding(Object entity) { return p.vehicle() == entity; }

        @Override public double distanceToSqr(double x, double y, double z) {
            double dx = p.x() - x, dy = p.y() - y, dz = p.z() - z;
            return dx * dx + dy * dy + dz * dz;
        }

        // --- MenuSynchronizer (바닐라 ServerPlayer.containerSynchronizer — 패킷마다 상태 번호를 올린다)
        @Override public void sendInitialData(org.domi.furnace.server.menu.AbstractContainerMenu m, java.util.List<ItemStack> items, ItemStack carried, int[] data) {
            out(new ContainerSetContent(m.containerId, m.incrementStateId(), items.toArray(new ItemStack[0]), carried));
            for (int i = 0; i < data.length; i++) out(new ContainerSetData(m.containerId, i, data[i]));
        }

        @Override public void sendSlotChange(org.domi.furnace.server.menu.AbstractContainerMenu m, int slot, ItemStack stack) {
            out(new ContainerSetSlot(m.containerId, m.incrementStateId(), slot, stack));
        }

        @Override public void sendCarriedChange(org.domi.furnace.server.menu.AbstractContainerMenu m, ItemStack stack) {
            out(new ContainerSetSlot(-1, m.incrementStateId(), -1, stack));
        }

        @Override public void sendDataChange(org.domi.furnace.server.menu.AbstractContainerMenu m, int id, int value) {
            out(new ContainerSetData(m.containerId, id, value));
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

    /** 플러그인 이벤트 입구(코어가 없는 시험 연결은 NONE). */
    private org.domi.furnace.server.plugin.GameEvents pluginEvents() {
        var c = login.core;
        return c == null ? org.domi.furnace.server.plugin.GameEvents.NONE : c.gameEvents();
    }

    /** 웅크림(0)·달리기(1) 전환: 플러그인이 들으면 소유 스레드에서 물은 뒤 적용, 아니면 지금처럼 바로 적용. */
    private void toggle(Player p, int kind, boolean on, Runnable apply) {
        var ge = pluginEvents();
        var type = kind == 0 ? org.domi.furnace.api.event.player.PlayerToggleSneakEvent.class : org.domi.furnace.api.event.player.PlayerToggleSprintEvent.class;
        if (!ge.has(type)) { apply.run(); return; }
        p.runOnOwner(() -> { if (ge.playerToggle(p, kind, on)) apply.run(); });
    }

    /**
     * 바닐라 handlePlayerAction(SWAP_ITEM_WITH_OFFHAND), 플레이어 소유 스레드: 관전자가 아니면 보조 손 ← 주 손, 주 손 ← 보조 손, 쓰던 아이템을 멈춘다.
     * 클라이언트는 예측하지 않으므로 바뀐 칸은 평소 인벤토리 동기화로 간다. 플러그인 PlayerSwapHandItemsEvent 로 취소하거나 결과를 바꿀 수 있다.
     */
    private void swapHands(Player p) {
        if (p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) return;
        int ws = inv.heldWindowSlot();
        ItemStack main = inv.get(ws), off = inv.get(45);
        ItemStack[] now = pluginEvents().playerSwapHands(p, off, main);
        if (now == null) return;
        synchronized (inv) {   // 이벤트 사이에 창 클릭(연결 스레드)이 칸을 바꿨으면 버린다 — 두 칸을 한 번에
            if (inv.get(ws) != main || inv.get(45) != off) return;
            inv.set(45, now[1]);
            inv.set(ws, now[0]);
        }
        p.stopUsingItem();
    }

    /** 플레이어가 서 있는 칸의 region 에서 아이템을 던진다. */
    private void spawnDrop(ItemStack stack, boolean dropAround, boolean includeThrower) {
        Player p = player;
        if (p == null || stack.isEmpty()) return;
        world.runAt(p.x(), p.z(), region -> region.playerDrop(p, stack, dropAround, includeThrower));
    }

    /**
     * client_command 0: 죽어 있으면 리스폰 위치를 정해(RespawnFinder — 리스폰 차원 region 이 침대·정박기·강제 지점을 본다) 리스폰한다.
     * 리스폰 차원이 지금 차원과 다르면 지금 차원 region 에서 떼어 낸 뒤 보낸다(바닐라 PlayerList.respawn — 리스폰 차원 레벨에 새로).
     */
    private void requestRespawn(ChannelHandlerContext ctx) {
        Player p = player;
        if (p != null && p.wonGame && !respawnPending) {   // 크레딧 끝: PlayerList.respawn(player, true, CHANGED_DIMENSION)
            respawnPending = true;
            p.wonGame = false;
            World over = login.core != null ? login.core.world() : org.domi.furnace.server.world.RespawnFinder.overworld(world);
            World end = world;
            org.domi.furnace.server.world.RespawnFinder.travel(end, p, true, (level, t) -> ctx.executor().execute(() -> {
                respawnPending = false;
                if (t == null || !(level instanceof org.domi.furnace.server.world.RegionLevel rl)) {
                    SpawnPoint spawn = over.defaultSpawn();
                    finishWinRespawn(ctx, over, new org.domi.furnace.server.entity.DimensionTransition(null, spawn.playerX(), spawn.playerY(),
                            spawn.playerZ(), 0, 0, 0, 0f, 0f));
                } else {
                    finishWinRespawn(ctx, rl.world(), t);
                }
            }));
            return;
        }
        if (p == null || !p.vitals().isDeadOrDying() || respawnPending) return;
        respawnPending = true;
        World from = world;
        Runnable find = () -> org.domi.furnace.server.world.RespawnFinder.find(from, p, false, o -> ctx.executor().execute(() -> respawnResolved(ctx, from, o)));
        if (!p.vitals().hasAnyEffect()) {
            find.run();
            return;
        }
        // PlayerList.respawn: removePlayerImmediately(KILLED) 가 먼저 — 죽은 자리 region 에서 효과 발동(돌풍·거미줄·슬라임) 뒤 리스폰 위치를 찾는다
        from.runAt(p.x(), p.z(), r -> {
            try {
                org.domi.furnace.server.effect.MobEffectTriggers.triggerOnDeath(r.level(), p, org.domi.furnace.server.entity.Entity.RemovalReason.KILLED);
            } finally {
                ctx.executor().execute(find);
            }
        });
    }

    /** 리스폰 위치가 정해졌다(연결 스레드): 다른 차원이면 지금 차원에서 떼어 낸 뒤, 리스폰을 보낸다. */
    private void respawnResolved(ChannelHandlerContext ctx, World from, org.domi.furnace.server.world.RespawnFinder.Outcome o) {
        Player p = player;
        if (p == null || !ctx.channel().isActive() || world != from) {
            respawnPending = false;
            return;
        }
        if (o.transition() != null) {
            respawnAt(ctx, from, o, o.world(), o.transition().x(), o.transition().y(), o.transition().z(), o.transition().yRot());
            return;
        }
        // 리스폰 블록 없음: 오버월드 월드 스폰에서 흩뿌린 자리(adjustSpawnLocation — 블록을 읽으므로 오버월드 region 에서)
        World over = login.core != null ? login.core.world() : org.domi.furnace.server.world.RespawnFinder.overworld(world);
        org.domi.furnace.server.world.RespawnFinder.worldSpawn(over, over.levelServices().gameRuleInt("spawnRadius"), over.levelServices().worldAdventure(),
                s -> ctx.executor().execute(() -> {
                    if (player != p || !ctx.channel().isActive() || world != from) {
                        respawnPending = false;
                        return;
                    }
                    respawnAt(ctx, from, o, over, s.x(), s.y(), s.z(), s.yaw());
                }));
    }

    private void respawnAt(ChannelHandlerContext ctx, World from, org.domi.furnace.server.world.RespawnFinder.Outcome o, World target,
                           double x, double y, double z, float yaw) {
        Player p = player;
        boolean worldSpawn = o.transition() == null, missing = o.missing();
        Long seed = o.depleteSoundSeed();
        if (target == from) {
            finishRespawn(ctx, x, y, z, yaw, missing, worldSpawn, seed);
            return;
        }
        java.util.concurrent.CompletableFuture.allOf(from.forEachRegion(r -> r.removeForDimensionChange(p))
                .toArray(new java.util.concurrent.CompletableFuture[0])).whenComplete((v, e) -> ctx.executor().execute(() -> {
                    respawnFrom = from.dimension();
                    world = target;
                    p.dimension(target.dimension());
                    finishRespawn(ctx, x, y, z, yaw, missing, worldSpawn, seed);
                }));
    }

    private boolean respawnPending;
    /** 첫 접속 스폰 흩뿌리기를 기다리는 최대 시간(바닐라는 기다림 없이 메인 스레드에서 청크를 불러온다). */
    static final long FIRST_SPAWN_TIMEOUT_MS = 5000;
    /** 처음 들어오는 플레이어의 스폰 자리(beginEnterPlay 가 정하고 onEnterPlay 가 한 번 쓴다). 이벤트루프 전용. */
    private org.domi.furnace.server.world.RespawnFinder.Spot firstSpawn;
    /** 다른 차원에서 리스폰해 오는 중이면 원래 차원(리스폰을 다 보낸 뒤 오버월드 region 에 넣는다). */
    private String respawnFrom;

    /** 바닐라 sendLevelInfo(그 레벨): 보더(오버월드 것) → 시간 → 공유 스폰 → [비] → 청크 대기 → 틱 상태. */
    private void sendLevelInfo(ChannelHandlerContext ctx, World level) {
        org.domi.furnace.server.ServerCore core = login.core;
        send(ctx, core != null ? InitializeBorder.of(core.border()) : InitializeBorder.vanillaDefault());
        if (core != null) {
            send(ctx, core.clock().timePacket(core.gameRules()));
        } else {
            send(ctx, new SetTime(level.gameTime(), level.dayTime()));
        }
        SpawnPoint spawn = level.defaultSpawn();
        send(ctx, new SetDefaultSpawnPosition(spawn.x(), spawn.y(), spawn.z(), spawn.yaw()));
        if (core != null && core.clock().isRaining() && level.dimensionType().hasSkyLight()) {
            send(ctx, new GameEvent(GameEvent.START_RAINING, 0f));
            send(ctx, new GameEvent(GameEvent.RAIN_LEVEL_CHANGE, core.clock().rainLevel()));
            send(ctx, new GameEvent(GameEvent.THUNDER_LEVEL_CHANGE, core.clock().thunderLevel()));
        }
        send(ctx, new GameEvent(GameEvent.START_WAITING_FOR_CHUNKS, 0f));
        send(ctx, new org.domi.furnace.server.net.protocol.play.TickingState(world.tickState().rate(), world.tickState().frozen()));
        send(ctx, new org.domi.furnace.server.net.protocol.play.TickingStep(world.tickState().globalStepsLeft()));
    }

    /**
     * LoginPlay 의 차원 이름 목록(바닐라 levelKeys — 클라이언트가 차원 인자 제안에 쓴다). 기본 세 차원은 예전 순서 그대로, 추가 월드는 올린 순서로 뒤에.
     * 접속 뒤 올린 월드는 다시 접속해야 제안에 나온다(Paper 와 같다).
     */
    private static java.util.List<String> loginLevelKeys(org.domi.furnace.server.ServerCore core) {
        java.util.List<String> out = new java.util.ArrayList<>(java.util.List.of("minecraft:overworld", "minecraft:the_end", "minecraft:the_nether"));
        var dims = core.dimensions();
        if (dims != null) for (String k : dims.levelKeys()) if (!out.contains(k)) out.add(k);
        return out;
    }

    private int dimensionTypeIndex(World w) {
        return Math.max(0, registries().indexOf("minecraft:dimension_type", w.dimensionType().typeKey()));
    }

    /**
     * 바닐라 ServerPlayer.changeDimension(다른 차원): [리스폰 블록 없음] → respawn(데이터 모두 보존) → 난이도 → 권한 → 위치 →
     * 청크 중심(addDuringTeleport) → 능력 → sendLevelInfo → sendAllPlayerInfo(인벤토리·든 칸) → 효과 → 포털 소리. 그 뒤 목적 region 이
     * 편입하고(다음 틱부터 청크·체력·경험치) 포털 티켓을 건다. 플레이어는 원천 region 에서 이미 떼어졌다.
     */
    private void applyDimensionChange(ChannelHandlerContext ctx, World target, org.domi.furnace.server.entity.DimensionTransition t, int portalCooldown) {
        Player p = player;
        if (p == null || p.isDisconnected() || !ctx.channel().isActive()) return;
        org.domi.furnace.server.ServerCore core = login.core;
        if (worldGone(core, target)) {
            // 오는 사이 그 추가 월드를 내렸다(다중 월드) — 없는 차원으로 접속할 때처럼 오버월드로, 위치는 월드 스폰
            log.warn("{} 의 목적 월드 {} 를 내렸다 — 오버월드 스폰으로 보낸다", p.name(), target.dimension());
            target = core.world();
            t = spawnTransition(target);
        }
        var vitals = p.vitals();
        String from = world.dimension();
        if ("minecraft:overworld".equals(from) && "minecraft:the_nether".equals(target.dimension())) {
            p.enteredNetherPosition = new double[] {p.x(), p.y(), p.z()};   // 바닐라: 오버월드 → 네더 이동 때 떠나는 위치 기록
        }
        if (t.missingRespawnBlock()) send(ctx, new GameEvent(GameEvent.NO_RESPAWN_BLOCK_AVAILABLE, 0f));
        var prev = vitals.previousGameMode();
        long hashedSeed = core != null ? core.biomeZoomSeed(target) : 0L;
        send(ctx, new org.domi.furnace.server.net.protocol.play.Respawn(dimensionTypeIndex(target), target.dimension(), hashedSeed,
                vitals.gameMode().id(), prev == null ? -1 : prev.id(), false, vitals.lastDeathDimension(), vitals.lastDeathPos(), portalCooldown,
                org.domi.furnace.server.net.protocol.play.Respawn.KEEP_ALL));
        if (core != null) {
            send(ctx, new ChangeDifficulty(core.levelData().difficulty.id(), core.levelData().difficultyLocked));
            core.sendPlayerPermissionLevel(p);
        } else {
            send(ctx, new ChangeDifficulty(1, false));   // 서버 핸들 없이(시험): 접속 때와 같은 바닐라 기본 easy
        }
        world = target;
        p.dimension(target.dimension());
        if (worldGone(core, target)) {   // 차원을 바꾸는 바로 그 순간 내렸다(내리기는 플레이어를 이미 봤으므로 여기서만 걸린다)
            applyDimensionChange(ctx, core.world(), spawnTransition(core.world()), 0);
            return;
        }
        placeAfterDimensionChange(ctx, p, target, t.x(), t.y(), t.z(), t.yRot(), t.xRot());
        send(ctx, vitals.abilitiesPacket());
        sendLevelInfo(ctx, target);
        synchronized (p.menuLock()) { p.inventoryMenu().sendAllDataToRemote(); }
        send(ctx, new SetHeldSlot(inv.selected()));
        for (Packet effectPacket : vitals.effectPackets()) send(ctx, effectPacket);
        if (t.portalSound()) send(ctx, new org.domi.furnace.server.net.protocol.play.LevelEventPacket(1032, 0, 0, 0, 0, false));
        final var arrived = t;
        target.runAt(t.x(), t.z(), r -> r.adoptAfterDimensionChange(p, arrived, from));
    }

    /** 내린 추가 월드인가(목록에서 떼었거나 닫혔다). */
    private static boolean worldGone(org.domi.furnace.server.ServerCore core, World w) {
        return core != null && w.isExtraWorld() && (w.isUnloaded() || core.world(w.dimension()) != w);
    }

    /** 그 월드 스폰 칸 가운데로 가는 이동(도착 뒤 처리 없음). */
    private static org.domi.furnace.server.entity.DimensionTransition spawnTransition(World w) {
        var sp = w.defaultSpawn();
        return new org.domi.furnace.server.entity.DimensionTransition(null, sp.x() + 0.5, sp.y(), sp.z() + 0.5, 0, 0, 0, sp.yaw(), 0f);
    }

    /** connection.teleport + addDuringTeleport 의 청크 중심: 이동 대기열을 비우고 위치를 보낸 뒤 새 청크 중심을 알린다. */
    private void placeAfterDimensionChange(ChannelHandlerContext ctx, Player p, World target, double x, double y, double z, float yaw, float pitch) {
        p.clearMoves();
        ix = x; iy = y; iz = z; iyaw = yaw; ipitch = pitch; ig = false;
        correctionTpId = ++teleportId;
        send(ctx, new SyncPlayerPosition(x, y, z, yaw, pitch, correctionTpId));
        p.pushTeleport(x, y, z);
        publishInput(p);
        ChunkPos c = ChunkPos.ofBlock(x, z);
        send(ctx, new SetCenterChunk(c.x(), c.z()));
        p.lastCenter(c);   // 어느 region 에도 없는 동안이라 연결 스레드가 써도 된다 — 편입 뒤 region 이 이 중심으로 청크를 보낸다
    }

    /** 바닐라 PlayerList.respawn 이 보내는 순서대로 — 능력, (스폰 블록 없음), Respawn, 위치, 스폰 지점, 난이도, 경험치, 레벨 정보, 권한, 인벤토리. */
    private void finishRespawn(ChannelHandlerContext ctx, double x, double y, double z, float yaw, boolean missingRespawnBlock, boolean worldSpawn,
                               Long depleteSoundSeed) {
        respawnPending = false;
        Player p = player;
        if (p == null || !ctx.channel().isActive() || !p.vitals().isDeadOrDying()) return;
        org.domi.furnace.server.ServerCore core = login.core;
        float pitch = 0f;
        if (core != null && core.events().hasSubscribers(org.domi.furnace.api.event.player.PlayerRespawnEvent.class)) {
            var ev = core.events().call(new org.domi.furnace.api.event.player.PlayerRespawnEvent(core.plugins().player(p),
                    new org.domi.furnace.api.world.Location(world.dimension(), x, y, z, yaw, 0f), worldSpawn));
            var l = ev.respawnLocation();
            x = l.x(); y = l.y(); z = l.z(); yaw = l.yaw(); pitch = l.pitch();
        }
        boolean keepInventory = core != null && core.gameRules().getBoolean("keepInventory");
        var vitals = p.vitals();
        vitals.respawn();
        p.markEntityDataResend();   // 새 ServerPlayer: 클라가 기본값 엔티티를 만든다
        // PlayerList.respawn: 리스폰 블록이 없었으면 새 플레이어는 리스폰 지점을 이어받지 않는다
        if (missingRespawnBlock) vitals.setRespawnPosition("minecraft:overworld", null, 0.0F, false);
        p.bumpSpawnGeneration();
        send(ctx, vitals.abilitiesPacket());
        if (missingRespawnBlock) send(ctx, new GameEvent(GameEvent.NO_RESPAWN_BLOCK_AVAILABLE, 0f));
        var prev = vitals.previousGameMode();
        long hashedSeed = core != null ? core.biomeZoomSeed(world) : 0L;
        send(ctx, new org.domi.furnace.server.net.protocol.play.Respawn(dimensionTypeIndex(world), world.dimension(), hashedSeed,
                vitals.gameMode().id(), prev == null ? -1 : prev.id(), false, vitals.lastDeathDimension(), vitals.lastDeathPos(), 0, (byte) 0));
        // 서버가 정한 위치로 텔레포트 — 확인 전까지 클라 이동은 받지 않는다.
        p.clearMoves();
        ix = x; iy = y; iz = z; iyaw = yaw; ipitch = pitch; ig = false;
        correctionTpId = ++teleportId;
        send(ctx, new SyncPlayerPosition(x, y, z, yaw, pitch, correctionTpId));
        p.pushTeleport(x, y, z);
        publishInput(p);
        SpawnPoint spawn = (core != null ? core.world() : world).defaultSpawn();   // 공유 스폰(DerivedLevelData — 오버월드 것)
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
        if (core != null && core.clock().isRaining() && world.dimensionType().hasSkyLight()) {
            send(ctx, new GameEvent(GameEvent.START_RAINING, 0f));
            send(ctx, new GameEvent(GameEvent.RAIN_LEVEL_CHANGE, core.clock().rainLevel()));
            send(ctx, new GameEvent(GameEvent.THUNDER_LEVEL_CHANGE, core.clock().thunderLevel()));
        }
        send(ctx, new GameEvent(GameEvent.START_WAITING_FOR_CHUNKS, 0f));
        if (core != null) core.sendPlayerPermissionLevel(p);
        synchronized (p.menuLock()) { p.inventoryMenu().sendAllDataToRemote(); }
        send(ctx, new SetHeldSlot(inv.selected()));
        if (depleteSoundSeed != null) {   // 리스폰 자리 칸이 정박기: 그 칸 좌표에서 deplete 소리(본인에게만)
            send(ctx, new org.domi.furnace.server.net.protocol.play.SoundPacket(
                    org.domi.furnace.server.item.ItemContext.id("minecraft:sound_event", "minecraft:block.respawn_anchor.deplete"), null,
                    org.domi.furnace.server.net.protocol.play.SoundPacket.source("blocks"), Math.floor(x), Math.floor(y), Math.floor(z), 1.0F, 1.0F,
                    depleteSoundSeed));
        }
        if (respawnFrom != null) {   // 다른 차원에서 왔다 — 이제 이 차원 region 에 넣는다
            String changedFrom = respawnFrom;
            respawnFrom = null;
            var t = new org.domi.furnace.server.entity.DimensionTransition(null, x, y, z, 0, 0, 0, yaw, pitch);
            world.runAt(x, z, r -> r.adoptAfterDimensionChange(p, t, null, changedFrom));
        }
    }

    /**
     * 크레딧 뒤 리스폰(PlayerList.respawn(keepAll = true)): 저장 데이터를 모두 유지한 채 리스폰 차원으로. [리스폰 블록 없음] → respawn(속성
     * 보존 1) → 위치 → 공유 스폰 → 난이도 → 경험치 → sendLevelInfo → 권한 → 인벤토리 → 효과 → 체력. 그 뒤 목적 region 이 편입한다.
     */
    private void finishWinRespawn(ChannelHandlerContext ctx, World target, org.domi.furnace.server.entity.DimensionTransition t) {
        Player p = player;
        if (p == null || p.isDisconnected() || !ctx.channel().isActive()) return;
        org.domi.furnace.server.ServerCore core = login.core;
        var vitals = p.vitals();
        String from = world.dimension();
        p.bumpSpawnGeneration();   // 바닐라: 새 ServerPlayer — 관찰자가 지웠다 다시 스폰한다
        p.markEntityDataResend();
        if (t.missingRespawnBlock()) {
            send(ctx, new GameEvent(GameEvent.NO_RESPAWN_BLOCK_AVAILABLE, 0f));
            vitals.setRespawnPosition("minecraft:overworld", null, 0.0F, false);   // 리스폰 지점을 이어받지 않는다
        }
        var prev = vitals.previousGameMode();
        long hashedSeed = core != null ? core.biomeZoomSeed(target) : 0L;
        send(ctx, new org.domi.furnace.server.net.protocol.play.Respawn(dimensionTypeIndex(target), target.dimension(), hashedSeed,
                vitals.gameMode().id(), prev == null ? -1 : prev.id(), false, vitals.lastDeathDimension(), vitals.lastDeathPos(), 0,
                org.domi.furnace.server.net.protocol.play.Respawn.KEEP_ATTRIBUTES));
        world = target;
        p.dimension(target.dimension());
        placeAfterDimensionChange(ctx, p, target, t.x(), t.y(), t.z(), t.yRot(), t.xRot());
        SpawnPoint spawn = target.defaultSpawn();
        send(ctx, new SetDefaultSpawnPosition(spawn.x(), spawn.y(), spawn.z(), spawn.yaw()));
        if (core != null) send(ctx, new ChangeDifficulty(core.levelData().difficulty.id(), core.levelData().difficultyLocked));
        send(ctx, vitals.experiencePacket());
        sendLevelInfo(ctx, target);
        if (core != null) core.sendPlayerPermissionLevel(p);
        synchronized (p.menuLock()) { p.inventoryMenu().sendAllDataToRemote(); }
        send(ctx, new SetHeldSlot(inv.selected()));
        for (Packet effectPacket : vitals.effectPackets()) send(ctx, effectPacket);
        target.runAt(t.x(), t.z(), r -> r.adoptAfterDimensionChange(p, t, from));
    }

    /** 서버가 정한 위치로 플레이어를 옮긴다(IO 스레드): 이동 대기열을 비우고, 클라이언트가 확인하기 전까지 옛 위치 패킷은 버린다. */
    /**
     * ServerGamePacketListenerImpl.teleport(…, RelativeMovement.ALL): 수락 위치는 절대 (x,y,z), 패킷에는 차이(dx,dy,dz)와 회전 0 을
     * 상대 깃발 다섯 개와 함께 보낸다.
     */
    private void teleportPlayerRelative(ChannelHandlerContext ctx, double x, double y, double z, double dx, double dy, double dz) {
        Player p = this.player;
        if (p == null || p.isDisconnected()) return;
        p.clearMoves();
        ix = x;
        iy = y;
        iz = z;
        ig = false;
        correctionTpId = ++teleportId;
        send(ctx, new SyncPlayerPosition(dx, dy, dz, 0.0F, 0.0F, SyncPlayerPosition.RELATIVE_ALL, correctionTpId));
        p.pushTeleport(x, y, z);
        publishInput(p);
    }

    private void teleportPlayer(ChannelHandlerContext ctx, double x, double y, double z) {
        Player p = this.player;
        if (p == null || p.isDisconnected()) return;
        p.clearMoves();
        ix = x;
        iy = y;
        iz = z;
        ig = false;
        correctionTpId = ++teleportId;
        send(ctx, new SyncPlayerPosition(x, y, z, iyaw, ipitch, correctionTpId));
        p.pushTeleport(x, y, z);
        publishInput(p);
    }

    /**
     * 탄 동안의 이동 패킷(바닐라 handleMovePlayer 의 isPassenger 가지): 위치는 버리고 회전만 받는다. 수락 위치는 서버가 정한
     * 지금 위치(탈것이 옮긴 것)로 맞춰 둔다 — 도달 거리 검사와 내린 뒤 이동 검증의 기준점.
     */
    /**
     * 자는 동안의 이동 패킷(바닐라 handleMovePlayer 의 isSleeping 가지): 받아들이지 않는다. 서버 위치(침대 위)와 제곱 거리가 1 을 넘으면
     * 서버 위치로 되돌리는 텔레포트(패킷의 회전). 수락 위치는 서버 위치로 맞춘다.
     */
    private void acceptSleeping(ChannelHandlerContext ctx, Player p, double x, double y, double z, float yaw, float pitch) {
        double sx = p.x(), sy = p.y(), sz = p.z();
        ix = sx;
        iy = sy;
        iz = sz;
        double dx = x - sx, dy = y - sy, dz = z - sz;
        if (dx * dx + dy * dy + dz * dz > 1.0) {
            iyaw = yaw;
            ipitch = pitch;
            teleportPlayer(ctx, sx, sy, sz);
        }
    }

    private void acceptRiding(Player p, float yaw, float pitch, boolean g) {
        ix = p.x();
        iy = p.y();
        iz = p.z();
        iyaw = yaw;
        ipitch = pitch;
        ig = g;
        publishInput(p);
    }

    /**
     * player_command 의 탈것 쪽(바닐라 handlePlayerCommand): 5 START_RIDING_JUMP(조종 중인 말이 점프할 수 있고 세기 > 0),
     * 6 STOP_RIDING_JUMP, 7 OPEN_INVENTORY(탄 것이 HasCustomInventoryScreen 이면 그 창 — 말·상자 배).
     */
    private static void handleRidingCommand(Player p, int action, int data) {
        var v = p.vehicle();
        if (action == 7) {
            if (v instanceof org.domi.furnace.server.entity.AbstractHorse h) h.openCustomInventoryScreen(p);
            else if (v instanceof org.domi.furnace.server.entity.ChestBoat b) b.openCustomInventoryScreen(p);
            return;
        }
        if (!(v instanceof org.domi.furnace.server.entity.AbstractHorse h) || v.getControllingPassenger() != p) return;
        if (action == 5) {
            if (h.canJump() && data > 0) h.handleStartJump(data);
        } else {
            h.handleStopJump();
        }
    }

    /** 마지막으로 수락한 위치·회전·onGround의 합을 발행한다(region 스레드가 다음 틱에 drain). */
    private void publishInput(Player p) {
        p.publishPosition(ix, iy, iz, iyaw, ipitch, ig);
    }

    /**
     * 수락한 이동 패킷 하나를 넘긴다. region 이 검증 중이면(validatesMoves) 위치 발행 없이 검증 대기열에만 넣고(받아들이면 region 이 위치를 정한다),
     * 아니면(region 이 아직 이 플레이어를 돌리지 않음) 이전처럼 바로 발행한다.
     */
    private void acceptMove(Player p, boolean hasPos, boolean hasRot) {
        if (p.validatesMoves) {
            p.pushCheckedMove(new org.domi.furnace.server.entity.PlayerMoveValidator.Check(hasPos, hasRot, ix, iy, iz, iyaw, ipitch), ig);
        } else {
            publishInput(p);
            p.pushMove(ix, iy, iz, ig);
        }
    }

    /** containsInvalidValues: 좌표 NaN 이거나 각도가 유한하지 않으면 바닐라는 연결을 끊는다(multiplayer.disconnect.invalid_player_movement). */
    private boolean invalidMovement(ChannelHandlerContext ctx, double x, double y, double z, float yaw, float pitch) {
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            disconnect(ctx, Text.translatable("multiplayer.disconnect.invalid_player_movement"));
            return true;
        }
        return false;
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

    private static final java.util.regex.Pattern FORMATTING_CODE = java.util.regex.Pattern.compile("(?i)\u00A7[0-9A-FK-OR]");

    /** ChatFormatting.stripFormatting. */
    static String stripFormatting(String s) { return FORMATTING_CODE.matcher(s).replaceAll(""); }

    /** canUseGameMasterBlocks = 크리에이티브 + 권한 2 이상. */
    private boolean canUseGameMasterBlocks(Player actor) {
        var core = login.core;
        return core != null && actor.vitals().instabuild() && core.profilePermissions(org.domi.furnace.server.ServerCore.profileOf(actor)) >= 2;
    }

    /**
     * 바닐라 handleSetStructureBlock: 권한이 없으면 아무 일도 없다. 블록 엔티티에 필드를 쓰고, 이름이 있으면 동작(저장·불러오기·크기 감지)을 해
     * 결과를 본인에게 채팅 메시지로 알린다. 이름이 올바르지 않으면 invalid_structure_name. 끝에 setChanged 와 블록 갱신 방송.
     */
    private void handleSetStructureBlock(Player actor, int[] bp, int update, int mode, String name, int[] offset, int[] size, int mirror, int rotation,
                                         String metadata, float integrity, long seed, boolean ignoreEntities, boolean showAir, boolean showBoundingBox) {
        if (!canUseGameMasterBlocks(actor)) return;
        var pos = new org.domi.furnace.server.block.Pos(bp[0], bp[1], bp[2]);
        world.runAtRequested(bp[0], bp[2], actor.x(), actor.z(), region -> {
            var level = region.level();
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.StructureBlockEntity be)) return;
            be.applyEdit(org.domi.furnace.server.block.entity.StructureBlockEntity.Mode.values()[mode], name, offset, size, mirror, rotation, metadata,
                    ignoreEntities, showAir, showBoundingBox, integrity, seed, level);
            var type = org.domi.furnace.server.block.entity.StructureBlockEntity.UpdateType.values()[update];
            if (be.hasStructureName()) {
                String s = be.getStructureName();
                switch (type) {
                    case SAVE_AREA -> actor.sendSystemMessage(org.domi.furnace.api.text.Text.translatable(
                            be.saveStructure(level) ? "structure_block.save_success" : "structure_block.save_failure", s), false);
                    case LOAD_AREA -> {
                        if (!be.isStructureLoadable(level)) actor.sendSystemMessage(org.domi.furnace.api.text.Text.translatable("structure_block.load_not_found", s), false);
                        else if (be.placeStructureIfSameSize(level)) actor.sendSystemMessage(org.domi.furnace.api.text.Text.translatable("structure_block.load_success", s), false);
                        else actor.sendSystemMessage(org.domi.furnace.api.text.Text.translatable("structure_block.load_prepare", s), false);
                    }
                    case SCAN_AREA -> actor.sendSystemMessage(be.detectSize(level)
                            ? org.domi.furnace.api.text.Text.translatable("structure_block.size_success", s)
                            : org.domi.furnace.api.text.Text.translatable("structure_block.size_failure"), false);
                    default -> { }
                }
            } else {
                actor.sendSystemMessage(org.domi.furnace.api.text.Text.translatable("structure_block.invalid_structure_name", name), false);
            }
            be.setChanged(level);
            level.sendBlockUpdated(pos);
        });
    }

    /** 바닐라 handleSetJigsawBlock: 권한이 없으면 아무 일도 없다. 블록 엔티티가 직소 블록이면 필드를 쓰고 setChanged + 블록 갱신 방송. */
    private void handleSetJigsawBlock(Player actor, int[] bp, org.domi.furnace.server.data.ResourceId name, org.domi.furnace.server.data.ResourceId target,
                                      org.domi.furnace.server.data.ResourceId pool, String finalState, String joint, int selection, int placement) {
        if (!canUseGameMasterBlocks(actor)) return;
        var pos = new org.domi.furnace.server.block.Pos(bp[0], bp[1], bp[2]);
        world.runAtRequested(bp[0], bp[2], actor.x(), actor.z(), region -> {
            var level = region.level();
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.JigsawBlockEntity be)) return;
            be.setName(name);
            be.setTarget(target);
            be.setPool(pool);
            be.setFinalState(finalState);
            var j = org.domi.furnace.server.block.entity.JigsawBlockEntity.JointType.byName(joint);
            be.setJoint(j != null ? j : org.domi.furnace.server.block.entity.JigsawBlockEntity.JointType.ALIGNED);   // 패킷 디코드의 orElse(ALIGNED)
            be.setPlacementPriority(placement);
            be.setSelectionPriority(selection);
            be.setChanged(level);
            level.sendBlockUpdated(pos);
        });
    }

    /** 바닐라 handleJigsawGenerate: 권한이 없으면 아무 일도 없다. 직소 블록이면 앞 칸에서 구조물을 펼친다. */
    private void handleJigsawGenerate(Player actor, int[] bp, int levels, boolean keepJigsaws) {
        if (!canUseGameMasterBlocks(actor)) return;
        var pos = new org.domi.furnace.server.block.Pos(bp[0], bp[1], bp[2]);
        world.runAtRequested(bp[0], bp[2], actor.x(), actor.z(), region -> {
            var level = region.level();
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.JigsawBlockEntity be)) return;
            be.generate(level, levels, keepJigsaws);
        });
    }

    /**
     * 바닐라 handleSetCommandBlock: 명령 블록이 꺼져 있으면 advMode.notEnabled, 크리에이티브 + 권한 2 가 아니면 advMode.notAllowed(둘 다 본인에게),
     * 아니면 그 칸 region 스레드에서 블록 종류·조건부 상태·명령·출력 기록·항상 활성을 바꾼다. 명령이 비지 않았으면 advMode.setCommand.success.
     */
    private void handleSetCommandBlock(Player actor, int[] bp, String command, int mode, boolean trackOutput, boolean conditional, boolean automatic) {
        var core = login.core;
        if (core == null) return;
        int permission = core.profilePermissions(org.domi.furnace.server.ServerCore.profileOf(actor));
        var pos = new org.domi.furnace.server.block.Pos(bp[0], bp[1], bp[2]);
        world.runAtRequested(bp[0], bp[2], actor.x(), actor.z(), region -> {
            var level = region.level();
            String deny = org.domi.furnace.server.block.entity.CommandBlockEntity.denyReason(level.commandBlockEnabled(), actor.vitals().instabuild(), permission);
            if (deny != null) {
                actor.sendSystemMessage(org.domi.furnace.api.text.Text.translatable(deny), false);
            } else if (level.hasChunkAt(pos) && org.domi.furnace.server.block.entity.CommandBlockEntity.applyEdit(
                    level, pos, command, org.domi.furnace.server.block.entity.CommandBlockEntity.Mode.values()[mode], trackOutput, conditional, automatic)) {
                if (!command.isEmpty()) actor.sendSystemMessage(org.domi.furnace.api.text.Text.translatable("advMode.setCommand.success", command), false);
            }
        });
    }

    /**
     * 바닐라 handleSetCommandMinecart: 같은 검사. 수레는 플레이어가 있는 region 에서만 찾는다(바닐라는 레벨 전체) — 다른 region 의 수레는 무시.
     * 찾으면 명령·출력 기록을 바꾸고 advMode.setCommand.success 를 늘 보낸다.
     */
    private void handleSetCommandMinecart(Player actor, int entityId, String command, boolean trackOutput) {
        var core = login.core;
        if (core == null) return;
        int permission = core.profilePermissions(org.domi.furnace.server.ServerCore.profileOf(actor));
        world.runAt(actor.x(), actor.z(), region -> {
            var level = region.level();
            String deny = org.domi.furnace.server.block.entity.CommandBlockEntity.denyReason(level.commandBlockEnabled(), actor.vitals().instabuild(), permission);
            if (deny != null) {
                actor.sendSystemMessage(org.domi.furnace.api.text.Text.translatable(deny), false);
            } else if (level.entities().get(entityId) instanceof org.domi.furnace.server.entity.MinecartCommandBlock cart) {
                cart.applyEdit(command, trackOutput);
                actor.sendSystemMessage(org.domi.furnace.api.text.Text.translatable("advMode.setCommand.success", command), false);
            }
        });
    }

    /** 바닐라 updateSignText: 그 칸 region 스레드에서, 청크가 있고 표지판이면 글을 바꾼다(거르기 없음). */
    private void handleSignUpdate(Player actor, int[] bp, boolean front, java.util.List<String> lines) {
        var pos = new org.domi.furnace.server.block.Pos(bp[0], bp[1], bp[2]);
        world.runAtRequested(bp[0], bp[2], actor.x(), actor.z(), region -> {
            var level = region.level();
            if (!level.hasChunkAt(pos)) return;
            if (level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.SignBlockEntity sign) {
                sign.updateSignText(actor.uuid(), false, front, lines, level);
            }
        });
    }

    /** 블록 요청(파괴/설치) 허용 여부 — 월드 높이 안이고, 마지막 수락 위치(눈 높이)에서 도달 거리 안인가. */
    private boolean blockRequestAllowed(int bx, int by, int bz) {
        if (by < world.dimensionType().minY() || by >= world.dimensionType().maxY()) return false;
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
                public void openContainer(org.domi.furnace.server.block.Pos p) {
                    ctx.channel().eventLoop().execute(() -> ConnectionHandler.this.openContainer(p.x(), p.y(), p.z()));
                }

                @Override
                public void openSmithing(org.domi.furnace.server.block.Pos p) {
                    ctx.channel().eventLoop().execute(() -> ConnectionHandler.this.openSmithing(p.x(), p.y(), p.z()));
                }

                @Override
                public void openLoom(org.domi.furnace.server.block.Pos p) {
                    ctx.channel().eventLoop().execute(() -> ConnectionHandler.this.openLoom(p.x(), p.y(), p.z()));
                }

                @Override
                public void openCartography(org.domi.furnace.server.block.Pos p) {
                    ctx.channel().eventLoop().execute(() -> ConnectionHandler.this.openCartography(p.x(), p.y(), p.z()));
                }

                @Override
                public void openAnvil(org.domi.furnace.server.block.Pos p) {
                    ctx.channel().eventLoop().execute(() -> ConnectionHandler.this.openAnvil(p.x(), p.y(), p.z()));
                }

                @Override
                public void openEnchanting(org.domi.furnace.server.block.Pos p) {
                    ctx.channel().eventLoop().execute(() -> ConnectionHandler.this.openEnchanting(p.x(), p.y(), p.z()));
                }

                @Override
                public void openGrindstone(org.domi.furnace.server.block.Pos p) {
                    ctx.channel().eventLoop().execute(() -> ConnectionHandler.this.openGrindstone(p.x(), p.y(), p.z()));
                }

                @Override
                public void openStonecutter(org.domi.furnace.server.block.Pos p) {
                    ctx.channel().eventLoop().execute(() -> ConnectionHandler.this.openStonecutter(p.x(), p.y(), p.z()));
                }

                @Override
                public void openCraftingTable(org.domi.furnace.server.block.Pos p) {
                    ctx.channel().eventLoop().execute(() -> ConnectionHandler.this.openCraftingTable(p.x(), p.y(), p.z()));
                }

                @Override
                public boolean allowPlace(org.domi.furnace.server.block.Pos p, int state, org.domi.furnace.server.block.Pos against) {
                    org.domi.furnace.server.ServerCore pc = login.core;
                    if (pc == null || !pc.events().hasSubscribers(org.domi.furnace.api.event.block.BlockPlaceEvent.class)) return true;
                    var ev = pc.events().call(new org.domi.furnace.api.event.block.BlockPlaceEvent(actor.dimension(),
                            new org.domi.furnace.api.world.BlockPos(p.x(), p.y(), p.z()), world.blockRegistry().nameOf(state),
                            new org.domi.furnace.api.world.BlockPos(against.x(), against.y(), against.z()), pc.plugins().player(actor)));
                    if (ev.isCancelled()) org.domi.furnace.server.plugin.ClientResync.hands(actor);   // 클라이언트가 먼저 줄인 손 아이템을 되돌린다(칸은 호출 뒤 sendBlockState)
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
                if ((c == null || c.mayInteract(actor, bp[0], bp[2]))
                        && interactAllowed(c, actor, hand, bp, face, before, handSlot, level, pos)) {   // 플러그인 PlayerInteractEvent
                    var r = org.domi.furnace.server.block.BlockPlacement.useItemOn(level, pa, hand, hit);
                    if (r.consumesAction()) pa.trigger("minecraft:any_block_use", pos, before);
                    if (face == org.domi.furnace.server.block.Dir.UP && !r.consumesAction() && bp[1] >= maxY - 1
                            && org.domi.furnace.server.block.BlockPlacement.wasBlockPlacementAttempt(pa, before)) {
                        actor.sendSystemMessage(org.domi.furnace.server.block.BlockPlacement.tooHigh(maxY - 1), true);
                    }
                    else if (r.shouldSwing()) actor.swing(hand, true);   // handleUseItemOn: 결과가 SUCCESS 류면 본인 포함 animate
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

    /**
     * 플러그인 PlayerInteractEvent(블록 우클릭) 허가. 구독자가 없으면 아무것도 평가하지 않는다. 취소하면 손 아이템을 다시 보낸다 —
     * 블록 상태는 호출 뒤의 sendBlockState 가 이미 다시 보낸다(클라이언트가 예측한 설치·소모를 되돌린다).
     */
    private boolean interactAllowed(org.domi.furnace.server.ServerCore c, Player actor, int hand, int[] bp, int face, ItemStack held, int handSlot,
            org.domi.furnace.server.block.ChunkLevel level, org.domi.furnace.server.block.Pos pos) {
        if (c == null || !c.events().hasSubscribers(org.domi.furnace.api.event.player.PlayerInteractEvent.class)) return true;
        if (c.gameEvents().playerInteract(actor, hand, bp, level.getBlockState(pos), face, held)) return true;
        org.domi.furnace.server.plugin.ClientResync.slot(actor, handSlot);
        return false;
    }

    /** 그 칸의 지금 상태를 행위자에게(ClientboundBlockUpdatePacket(level, pos)). 다른 region 의 칸은 그 region 에서 읽어 보낸다. */
    private void sendBlockState(Player actor, Region here, org.domi.furnace.server.block.Pos p) {
        if (p.y() < world.dimensionType().minY() || p.y() >= world.dimensionType().maxY()) {
            org.domi.furnace.server.plugin.ClientResync.block(actor, here.level(), p);
            return;
        }
        if (here.level().hasChunkAt(p)) {
            org.domi.furnace.server.plugin.ClientResync.block(actor, here.level(), p);
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
        if (criterion.equals("minecraft:filled_bucket")) {   // 바닐라 FilledBucketTrigger.trigger(player, stack) — 위치 문맥이 아니라 채운 양동이
            adv.trigger(criterion, lootStack(stack));
            return;
        }
        adv.trigger(criterion, new org.domi.furnace.server.advancement.Criterion.LocationEvent(
                core.locationContext(actor, x, y, z, world.blockRegistry().canonicalKeyOf(state), lootStack(stack))));
    }

    /** 거부한 블록 요청 — 클라 예측이 어긋나지 않게 (로드된 청크면) 실제 블록 상태를 되돌려 보내고 ack. */
    private void rejectBlockRequest(Player actor, int seq, int bx, int by, int bz) {
        if (by < world.dimensionType().minY() || by >= world.dimensionType().maxY()) {
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
        org.domi.furnace.api.net.QuitReason quit = finalQuitReason();
        releaseWaiters();   // 기다리던 리소스 팩·쿠키 Future 와 구성 이벤트 처리기를 풀어 준다(스레드가 매달리지 않게)
        if (stateDeadline != null) { stateDeadline.cancel(false); stateDeadline = null; }
        if (rateTick != null) { rateTick.cancel(false); rateTick = null; }
        java.util.ArrayDeque<Object[]> queued = pendingPlayFrames;
        if (queued != null) {   // 저장 데이터 읽는 중에 끊김 — 보관 패킷 해제(finishEnterPlay 는 비활성 채널이라 진입하지 않는다)
            pendingPlayFrames = null;
            releasePendingFrames(queued);
        }
        Player p = player;
        if (p != null) {
            org.domi.furnace.server.ServerCore core = login.core;
            if (core != null) {
                // 바닐라 onDisconnect → removePlayerFromWorld: "이름 lost connection: 사유" 로그, 노란 퇴장 알림.
                Text why = kickReason != null ? kickReason : p.kickReason() != null ? p.kickReason() : Text.translatable("disconnect.disconnected");
                log.info("{} lost connection: {}", p.name(), why.getString());
                Text msg = Text.translatable("multiplayer.player.left", p.displayName()).color(org.domi.furnace.api.text.NamedTextColor.YELLOW);
                if (core.events().hasSubscribers(org.domi.furnace.api.event.player.PlayerQuitEvent.class)) {
                    msg = core.events().call(new org.domi.furnace.api.event.player.PlayerQuitEvent(core.plugins().player(p), msg, quit)).quitMessage();
                }
                core.customBossEvents().onPlayerDisconnect(p);
                if (p.apiHandle() instanceof org.domi.furnace.server.plugin.PlayerHandle ph) ph.dispose();   // 플러그인 보스바·사이드바 구독 정리
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
            p.awardStat("minecraft:leave_game", 1);   // 바닐라 PlayerList.remove
            savePlayerSnapshot();
            p.markDisconnected(); // 소유 region이 다음 틱에 정리(라우팅 불필요)
            world.playerOffline(p.entityId());   // adopt 전에 끊긴 경우에도 접속자 집합에서 즉시 제거(누수 방지, 관찰자는 다음 틱에 탭 제거)
        }
    }

    /**
     * 이벤트 루프가 가진 위치·인벤토리를 스냅샷 떠서 플레이어 데이터 직렬 실행기(world.playerIo)로 저장한다(퇴장·자동저장 공용).
     * 시퀀스 번호로 풀에서 순서가 뒤바뀌어도 옛 스냅샷이 최신을 덮어쓰지 못한다. 종료 훅이 PlayerData.awaitIo 로 대기하므로 종료 시 저장도 마무리된다.
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
        if (org.domi.furnace.server.entity.PlayerData.hasItemRegistry()) {   // 엔더 상자(바닐라 EnderItems)
            extra.putList("EnderItems", org.domi.furnace.server.entity.PlayerData.enderItemsToNbt(p.enderChest().snapshot()));
        }
        extra.putCompound("recipeBook", p.recipeBook().save());
        extra.putString("Dimension", p.dimension());   // 바닐라 ServerPlayer.addAdditionalSaveData — 지금 레벨
        extra.putBoolean("seenCredits", p.seenCredits);
        p.saveTags(extra);
        p.savePersistentData(extra);
        p.saveShoulders(extra);
        p.saveAttributes(extra);
        final long seq = org.domi.furnace.server.entity.PlayerData.nextSeq();
        final var advancements = p.advancements();
        final var statsToSave = p.stats();
        try {
            world.playerIo().execute(() -> {   // 읽기와 같은 직렬 실행기(UUID 별 저장→재접속 읽기 순서)
                try {
                    org.domi.furnace.server.entity.PlayerData.saveSequenced(seq, world.worldDir(), p.uuid(),
                            sx, sy, sz, syaw, spitch, sg, snapshot, extra);
                    if (statsToSave != null) statsToSave.save();
                    if (advancements != null) advancements.save();   // 바닐라 savePlayerData: 플레이어 NBT·통계·발전 과제
                } catch (RuntimeException e) {
                    log.error("플레이어 저장 실패 {}", p.name(), e);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.error("플레이어 저장 거부(종료 중): {}", p.name());
        }
    }

    /** 접속 때 읽은 플레이어 데이터(RootVehicle 을 region 편입 때 태운다). 이벤트 루프 전용. */
    private org.domi.furnace.server.net.io.nbt.NbtCompound joinSavedRoot;

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
            Player p = player;   // 탈것(RootVehicle)은 주인 region 이 떠서 덧쓴다
            if (p != null) {
                var io = world.playerIo();
                var dir = world.worldDir();
                p.runOnOwner(() -> org.domi.furnace.server.entity.PlayerVehicleSave.autosave(p, io, dir));
            }
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
            kick(ctx, Text.translatable("disconnect.timeout"), org.domi.furnace.api.net.QuitReason.TIMED_OUT);
            return;
        }
        lastKeepAliveId = System.nanoTime();
        awaitingKeepAlive.set(true);
        send(ctx, new KeepAliveClientbound(lastKeepAliveId));
        scheduleKeepAlive(); // 다음 주기 재예약
    }

    /** 응답 패킷 전송: [VarInt id][body] 프레임으로 인코딩 → FrameEncoder가 길이 prefix. */
    void send(ChannelHandlerContext ctx, Packet packet) {
        Object msg = org.domi.furnace.server.net.io.SharedChunkFrames.encode(ctx.channel(), packet);
        if (msg == null) return;   // 플러그인 패킷 리스너가 취소
        ctx.write(msg);
        scheduleFlush(ctx);
    }

    // 같은 이벤트 루프 순간에 쌓인 패킷(접속 직후 스폰 청크 수백 개 등)은 시스템 호출 한 번으로 내보낸다.
    // 쓰기 순서는 그대로고, 플래그는 이벤트 루프 스레드에서만 만진다(Player.send 와 같은 방식).
    private boolean flushPending;

    private void scheduleFlush(ChannelHandlerContext ctx) {
        if (flushPending) return;
        flushPending = true;
        ctx.executor().execute(() -> {
            flushPending = false;
            ctx.flush();
        });
    }

    /** 밀린 쓰기를 먼저 내보낸 뒤 닫는다 — 닫으면 flush 안 된 쓰기는 버려지기 때문이다. */
    private void closeFlushed(ChannelHandlerContext ctx) {
        markQuit(org.domi.furnace.api.net.QuitReason.ERRONEOUS_STATE);   // 사유 없이 닫는 곳은 잘못된 패킷·상태뿐이다
        ctx.flush();
        ctx.close();
    }

    /** 서버 종료: 이벤트 루프에서 현재 단계에 맞는 Disconnect로 사유를 알리고 닫는다. */
    void kickForShutdown() {
        ChannelHandlerContext c = this.ctx;
        if (c == null) return;
        c.channel().eventLoop().execute(() -> kick(c, Text.translatable("multiplayer.disconnect.server_shutdown"),
                org.domi.furnace.api.net.QuitReason.SERVER_SHUTDOWN));
    }

    // ------------------------------------------------------------------ 끊긴 이유(QuitReason)

    private final java.util.concurrent.atomic.AtomicReference<org.domi.furnace.api.net.QuitReason> quitReason = new java.util.concurrent.atomic.AtomicReference<>();

    /** 끊는 이유를 남긴다. 처음 남긴 것만 남는다(킥 패킷을 보낸 뒤 닫는 흐름이 그 이유를 덮어쓰지 않게). */
    void markQuit(org.domi.furnace.api.net.QuitReason r) { quitReason.compareAndSet(null, r); }

    /** 끊긴 뒤의 최종 이유: 서버가 남긴 이유가 있으면 그것, 없으면 사유 있는 킥이었는지(Player.kick) 보고, 아니면 클라이언트가 스스로 나간 것. */
    org.domi.furnace.api.net.QuitReason finalQuitReason() {
        Player p = player;
        quitReason.compareAndSet(null, p != null && p.kickReason() != null
                ? org.domi.furnace.api.net.QuitReason.KICKED : org.domi.furnace.api.net.QuitReason.DISCONNECTED);
        return quitReason.get();
    }

    /** 플러그인 PlayerConnection.quitReason: 연결이 끊기기 전에는 null. */
    org.domi.furnace.api.net.QuitReason quitReasonOrNull() { return isChannelOpen() ? null : finalQuitReason(); }

    // ------------------------------------------------------------------ 구성·플레이 중 플러그인이 주고받기(PlayerConnection 의 sendResourcePack·쿠키·transfer)

    /** 플러그인이 보낸 리소스 팩 한 개를 기다리는 중. */
    private record PendingPack(java.util.concurrent.CompletableFuture<org.domi.furnace.api.net.ResourcePackStatus> future, boolean required) { }

    private final java.util.concurrent.ConcurrentHashMap<UUID, PendingPack> pluginPacks = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<byte[]>> pendingCookies = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean lastPackResponseWasPlugin;     // 방금 읽은 팩 응답이 플러그인 팩의 것(서버 리소스 팩 대기에 쓰지 않는다) — 이벤트 루프 전용
    private final Object configureLock = new Object();
    private Thread configureThread;                // AsyncPlayerConfigureEvent 를 돌리는 중인 스레드(끊기면 깨운다)
    private boolean configureWaitersClosed;        // 연결이 끊겼다 — 더 돌리지 않는다

    private static final java.util.regex.Pattern COOKIE_KEY = java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** 응답이 netty 스레드가 아니라 플러그인 실행기에서 완료되게 한다(thenApply 같은 후속 처리가 이벤트 루프를 막지 않게). 실행기가 닫혔으면 직접. */
    private <T> void completeAsync(java.util.concurrent.CompletableFuture<T> f, T value, Throwable error) {
        Runnable r = () -> { if (error != null) f.completeExceptionally(error); else f.complete(value); };
        var core = login.core;
        if (core != null) {
            try {
                core.plugins().asyncExecutor().execute(r);
                return;
            } catch (java.util.concurrent.RejectedExecutionException closed) {
                // 종료 중 — 아래에서 직접
            }
        }
        r.run();
    }

    /** 연결이 끊겼다: 기다리던 Future 를 ConnectionClosedException 으로 끝내고, 구성 이벤트 처리기가 블로킹 중이면 인터럽트로 깨운다. */
    private void releaseWaiters() {
        var closed = new org.domi.furnace.api.net.ConnectionClosedException("연결이 끊겼다");
        for (var e : pluginPacks.entrySet()) {
            if (pluginPacks.remove(e.getKey(), e.getValue())) completeAsync(e.getValue().future(), null, closed);
        }
        for (var e : pendingCookies.entrySet()) {
            if (pendingCookies.remove(e.getKey(), e.getValue())) completeAsync(e.getValue(), null, closed);
        }
        Thread running;
        synchronized (configureLock) {
            configureWaitersClosed = true;
            running = configureThread;
        }
        if (running != null) {
            // 위에서 끝낸 Future 를 기다리던 처리기는 스스로 돌아온다. 그래도 남아 있는(다른 일로 블로킹 중인) 처리기만 유예 뒤에 인터럽트로 깨운다
            io.netty.util.concurrent.GlobalEventExecutor.INSTANCE.schedule(() -> {
                synchronized (configureLock) {
                    if (configureThread == running) running.interrupt();
                }
            }, CONFIGURE_INTERRUPT_GRACE_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    /** 연결이 끊긴 뒤 구성 이벤트 처리기가 안 돌아오면 인터럽트를 거는 유예(밀리초). */
    static final long CONFIGURE_INTERRUPT_GRACE_MS = 1000;

    /** 구성·플레이 중 패킷을 보낼 수 있는가: 구성 단계는 구성 끝(Finish)을 보내기 전까지만. 이벤트 루프에서 확인한다. */
    private boolean canSendDuringConfigurationOrPlay() {
        return state == ConnectionState.PLAY || (state == ConnectionState.CONFIGURATION && !finishConfigSent);
    }

    private static <T> java.util.concurrent.CompletableFuture<T> notAllowed(String what) {
        return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException(what));
    }

    private static boolean eventLoopRun(ChannelHandlerContext c, Runnable r) {
        try {
            c.channel().eventLoop().execute(r);
            return true;
        } catch (java.util.concurrent.RejectedExecutionException closed) {
            return false;
        }
    }

    java.util.concurrent.CompletableFuture<org.domi.furnace.api.net.ResourcePackStatus> apiSendResourcePack(org.domi.furnace.api.net.ResourcePack pack) {
        java.util.Objects.requireNonNull(pack, "pack");
        ChannelHandlerContext c = this.ctx;
        if (c == null || !c.channel().isActive()) return java.util.concurrent.CompletableFuture.failedFuture(new org.domi.furnace.api.net.ConnectionClosedException("연결이 끊겼다"));
        ConnectionState st = state;
        if (st != ConnectionState.CONFIGURATION && st != ConnectionState.PLAY) return notAllowed("리소스 팩은 구성·플레이 단계에서만 보낼 수 있다: " + st);
        var fresh = new PendingPack(new java.util.concurrent.CompletableFuture<>(), pack.required());
        PendingPack prev = pluginPacks.putIfAbsent(pack.id(), fresh);
        if (prev != null) return prev.future();
        Runnable send = () -> {
            if (!c.channel().isActive() || !canSendDuringConfigurationOrPlay()) {
                pluginPacks.remove(pack.id(), fresh);
                fresh.future().completeExceptionally(c.channel().isActive()
                        ? new IllegalStateException("리소스 팩을 보낼 수 없는 단계다: " + state)
                        : new org.domi.furnace.api.net.ConnectionClosedException("연결이 끊겼다"));
                return;
            }
            send(c, new org.domi.furnace.server.net.protocol.play.ResourcePackPush(pack.id(), pack.url(), pack.hash(), pack.required(), pack.prompt(), state == ConnectionState.PLAY));
        };
        if (!eventLoopRun(c, send)) {
            pluginPacks.remove(pack.id(), fresh);
            fresh.future().completeExceptionally(new org.domi.furnace.api.net.ConnectionClosedException("연결이 끊겼다"));
        }
        return fresh.future();
    }

    void apiRemoveResourcePack(UUID id) {
        ChannelHandlerContext c = this.ctx;
        if (c == null || !c.channel().isActive()) return;
        requireConfigurationOrPlay();
        eventLoopRun(c, () -> {
            if (!c.channel().isActive() || !canSendDuringConfigurationOrPlay()) return;
            if (id != null) {
                PendingPack pp = pluginPacks.remove(id);
                if (pp != null) completeAsync(pp.future(), org.domi.furnace.api.net.ResourcePackStatus.DISCARDED, null);   // 바닐라 클라이언트는 뺀 팩에 DISCARDED 로 답한다
            } else {
                for (var e : pluginPacks.entrySet()) {
                    if (pluginPacks.remove(e.getKey(), e.getValue())) completeAsync(e.getValue().future(), org.domi.furnace.api.net.ResourcePackStatus.DISCARDED, null);
                }
            }
            send(c, new ResourcePackPop(id, state == ConnectionState.PLAY));
        });
    }

    private void requireConfigurationOrPlay() {
        ConnectionState st = state;
        if (st != ConnectionState.CONFIGURATION && st != ConnectionState.PLAY) throw new IllegalStateException("구성·플레이 단계에서만 쓸 수 있다: " + st);
    }

    private void requireConfiguration(String what) {
        if (state != ConnectionState.CONFIGURATION) throw new IllegalStateException(what + " 은(는) 구성 단계에서만 쓸 수 있다: " + state);
    }

    private static String checkCookieKey(String key) {
        java.util.Objects.requireNonNull(key, "key");
        if (key.length() > 32767 || !COOKIE_KEY.matcher(key).matches()) throw new IllegalArgumentException("쿠키 키는 namespace:path 꼴이어야 한다: " + key);
        return key;
    }

    java.util.concurrent.CompletableFuture<byte[]> apiRequestCookie(String key) {
        checkCookieKey(key);
        ChannelHandlerContext c = this.ctx;
        if (c == null || !c.channel().isActive()) return java.util.concurrent.CompletableFuture.failedFuture(new org.domi.furnace.api.net.ConnectionClosedException("연결이 끊겼다"));
        if (state != ConnectionState.CONFIGURATION) return notAllowed("쿠키 요청은 구성 단계에서만 쓸 수 있다: " + state);
        var fresh = new java.util.concurrent.CompletableFuture<byte[]>();
        var prev = pendingCookies.putIfAbsent(key, fresh);
        if (prev != null) return prev;
        if (!eventLoopRun(c, () -> {
            if (!c.channel().isActive() || state != ConnectionState.CONFIGURATION || finishConfigSent) {
                pendingCookies.remove(key, fresh);
                fresh.completeExceptionally(c.channel().isActive() ? new IllegalStateException("쿠키를 요청할 수 없는 단계다: " + state)
                        : new org.domi.furnace.api.net.ConnectionClosedException("연결이 끊겼다"));
                return;
            }
            send(c, new CookieRequest(key));
        })) {
            pendingCookies.remove(key, fresh);
            fresh.completeExceptionally(new org.domi.furnace.api.net.ConnectionClosedException("연결이 끊겼다"));
        }
        return fresh;
    }

    void apiStoreCookie(String key, byte[] value) {
        checkCookieKey(key);
        var pkt = new StoreCookie(key, java.util.Objects.requireNonNull(value, "value").clone());
        ChannelHandlerContext c = this.ctx;
        if (c == null || !c.channel().isActive()) return;
        requireConfiguration("쿠키 저장");
        eventLoopRun(c, () -> {
            if (c.channel().isActive() && state == ConnectionState.CONFIGURATION && !finishConfigSent) send(c, pkt);
        });
    }

    void apiTransfer(String host, int port) {
        java.util.Objects.requireNonNull(host, "host");
        if (port < 0 || port > 65535) throw new IllegalArgumentException("port: " + port);
        ChannelHandlerContext c = this.ctx;
        if (c == null || !c.channel().isActive()) return;
        requireConfigurationOrPlay();
        eventLoopRun(c, () -> {
            if (!c.channel().isActive() || !canSendDuringConfigurationOrPlay()) return;
            send(c, state == ConnectionState.PLAY ? new org.domi.furnace.server.net.protocol.play.Transfer(host, port) : new ConfigTransfer(host, port));
        });
    }

    /** 구성 cookie_response(0x01): 키 + Optional<바이트 배열>(바닐라 상한 5120). 요청한 적 없는 응답은 버린다. */
    private void handleCookieResponse(ChannelHandlerContext ctx, ByteBuf frame) {
        String key = Types.readString(frame, 32767);
        byte[] payload = null;
        if (frame.readBoolean()) {
            int n = Types.readVarInt(frame);
            if (n < 0 || n > StoreCookie.MAX_PAYLOAD || n > frame.readableBytes()) { closeFlushed(ctx); return; }
            payload = new byte[n];
            frame.readBytes(payload);
        }
        var f = pendingCookies.remove(key);
        if (f == null) {
            log.debug("요청하지 않은 쿠키 응답 무시: {} ({})", key, playerName);
            return;
        }
        completeAsync(f, payload, null);
    }

    // ------------------------------------------------------------------ ConnectionInfo(플러그인 PlayerConnection) 용

    boolean isChannelOpen() {
        ChannelHandlerContext c = this.ctx;
        return c != null && c.channel().isActive();
    }

    ConnectionState connectionState() { return state; }

    java.net.InetSocketAddress apiAddress() {
        java.net.InetSocketAddress f = forwardedAddress;
        if (f != null) return f;
        ChannelHandlerContext c = this.ctx;
        return c != null && c.channel().remoteAddress() instanceof java.net.InetSocketAddress isa ? isa : null;
    }

    String clientBrandValue() { return clientBrand; }

    String clientLocaleValue() { return clientLocale; }

    /** 플러그인 PlayerConnection.disconnect: 플레이 중이면 Player.kick(PlayerKickEvent), 그 전이면 단계에 맞는 끊기 패킷. */
    void disconnectFromApi(Text reason) {
        ChannelHandlerContext c = this.ctx;
        if (c == null || !c.channel().isActive()) return;
        Player p = player;
        if (p != null) {
            p.kick(new Disconnect(reason));
            return;
        }
        try {
            c.channel().eventLoop().execute(() -> {
                if (!c.channel().isActive()) return;
                if (player != null) { player.kick(new Disconnect(reason)); return; }
                disconnect(c, reason);
            });
        } catch (java.util.concurrent.RejectedExecutionException closed) {
            // 이벤트루프가 끝났다 — 이미 닫히는 중
        }
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
        kick(ctx, reason, org.domi.furnace.api.net.QuitReason.KICKED);
    }

    /** 끊는 이유(QuitReason)를 함께 남기는 kick — 처음 남긴 이유가 이긴다(markQuit). */
    void kick(ChannelHandlerContext ctx, Text reason, org.domi.furnace.api.net.QuitReason why) {
        markQuit(why);
        kickReason = reason;
        Packet pkt = switch (state) {
            case LOGIN -> new LoginDisconnect(reason);
            case CONFIGURATION -> new ConfigDisconnect(reason);
            case PLAY -> new Disconnect(reason);
            default -> null;
        };
        if (pkt == null) { closeFlushed(ctx); return; }
        ByteBuf buf = ctx.alloc().buffer();
        Types.writeVarInt(buf, pkt.id());
        pkt.write(buf);
        ctx.writeAndFlush(buf).addListener(io.netty.channel.ChannelFutureListener.CLOSE);
    }

    /** 같은 UUID로 이미 접속 중인 세션이 있으면 바닐라처럼 기존 세션을 끊는다(같은 playerdata 공유 방지). */
    private void kickDuplicateSession(java.util.UUID uuid) {
        for (Player other : world.onlinePlayers()) {
            if (other != player && !other.isDisconnected() && uuid.equals(other.uuid())) {
                other.kickForced(new Disconnect(Text.translatable("multiplayer.disconnect.duplicate_login")));
            }
        }
    }

    void state(ConnectionState newState) {
        this.state = newState;
        ChannelHandlerContext c = this.ctx;
        if (c != null && newState != frameLimitState) applyStateLimits(c);   // 시험용: 단계 제한도 새 단계 것으로
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
            log.debug("읽기 타임아웃, 종료: {}", playerName);   // 정상적인 유휴 정리 — 경고 로그 대상 아님
            kick(ctx, Text.translatable("disconnect.timeout"), org.domi.furnace.api.net.QuitReason.TIMED_OUT);
            return;
        }
        log.warn("커넥션 오류, 종료: {}", cause.toString());
        closeFlushed(ctx);
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
    private ItemStack readSlot(ByteBuf frame) {
        ItemStack s = org.domi.furnace.server.item.ItemStackCodecs.readStack(frame);
        ItemValidation.check(s, limits());   // item-validation 상한(기본 꺼짐 = 바닐라)
        return s;
    }

    /** 바닐라처럼 패킷 디코드 실패는 연결을 끊는다(클라이언트 화면: 네트워크 프로토콜 오류). */
    private void protocolError(ChannelHandlerContext ctx, String packet, RuntimeException e) {
        log.warn("{} 패킷 디코드 실패, 연결 종료: {}", packet, e.toString());
        closeFlushed(ctx);
    }

    /** 용기를 열 때 region 스레드에서 정한 것: 주 블록 엔티티·(더블 상자의) 둘째·제목. */
    private record OpenSpec(org.domi.furnace.server.block.entity.ContainerBlockEntity first,
            org.domi.furnace.server.block.entity.ContainerBlockEntity second,
            org.domi.furnace.server.block.entity.EnderChestBlockEntity ender, Text title,
            org.domi.furnace.server.block.entity.LecternBlockEntity lectern,
            org.domi.furnace.server.block.entity.BeaconBlockEntity beacon) { }

    /**
     * 용기를 클릭했을 때 화면 열기(ServerPlayer.openMenu(getMenuProvider)): 그 블록의 region 스레드에서 블록 엔티티(와 더블 상자의 짝)를 찾고
     * 열지 않은 전리품을 채운 뒤, 같은 region 작업 안에서 열려 있던 창을 닫고 → 번호를 올리고 → 메뉴를 만들어 open_screen → 첫 container_set_content.
     * 메뉴 생성(제작기 결과 미리보기)과 첫 내용은 블록 엔티티를 읽으므로 이벤트 루프가 아니라 소유 region 에서 한다(틱 도중의 반쯤 바뀐 칸을 보내지 않게).
     */
    private void openContainer(int x, int y, int z) {
        Player p = player;
        if (p == null) return;
        world.submitAtChunk(ChunkPos.ofBlock(x, z), region -> {
            OpenSpec spec = resolveOpenSpec(region, p, new org.domi.furnace.server.block.Pos(x, y, z));
            if (spec != null) org.domi.furnace.server.world.MenuBlockContext.runIn(region, () -> openResolved(p, spec, x, y, z));
            return null;
        });
    }

    /** openContainer 의 뒷부분 — 블록의 region 스레드, MenuBlockContext 안. 패킷은 menus.out(p.send) 으로 이 순서대로 나간다. */
    private void openResolved(Player p, OpenSpec spec, int x, int y, int z) {
        synchronized (p.menuLock()) {
            if (p.hasOpenContainer()) closeContainer(p);
            int containerId = p.nextContainerCounter();
            int[] bp = {x, y, z};
            var inventory = p.inventoryMenu().inventoryContainer();
            org.domi.furnace.server.menu.AbstractContainerMenu menu;
            var f = spec.first();
            if (spec.beacon() != null) {
                menu = new org.domi.furnace.server.menu.BeaconMenu(containerId, inventory, spec.beacon(), bp, new org.domi.furnace.server.menu.BlockAccess() {
                    @Override public boolean stillBlock() {
                        var region = org.domi.furnace.server.world.MenuBlockContext.current();
                        return region == null || "minecraft:beacon".equals(world.blockRegistry().nameOf(region.getBlock(bp[0], bp[1], bp[2])));
                    }

                    @Override public long gameTime() { return 0L; }

                    @Override public void playSound(String sound) { }
                });
            } else if (spec.lectern() != null) {
                menu = new org.domi.furnace.server.menu.LecternMenu(containerId, new org.domi.furnace.server.world.LecternContainer(spec.lectern()), bp);
            } else if (spec.ender() != null) {   // 엔더 상자: 이 플레이어의 엔더 인벤토리(엔더 상자 블록이 여는 수·소리를 센다)
                var c = new org.domi.furnace.server.world.EnderChestContainer(p.enderChest(), spec.ender(), world::runAt);
                menu = org.domi.furnace.server.menu.ChestMenu.threeRows(containerId, inventory, c, bp, menus);
            } else if (spec.second() != null) {
                var c = new org.domi.furnace.server.menu.CompoundContainer(new org.domi.furnace.server.world.BlockEntityContainer(f, world::runAt),
                        new org.domi.furnace.server.world.BlockEntityContainer(spec.second(), world::runAt));
                menu = org.domi.furnace.server.menu.ChestMenu.sixRows(containerId, inventory, c, bp, menus);
            } else {
                var c = new org.domi.furnace.server.world.BlockEntityContainer(f, world::runAt);
                menu = switch (f.type()) {
                    case "minecraft:shulker_box" -> new org.domi.furnace.server.menu.ShulkerBoxMenu(containerId, inventory, c, bp, menus);
                    case "minecraft:hopper" -> new org.domi.furnace.server.menu.HopperMenu(containerId, inventory, c, bp, menus);
                    case "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker" ->
                        new org.domi.furnace.server.menu.FurnaceMenu(f.type(), containerId, inventory, c, bp, menus);
                    case "minecraft:brewing_stand" -> new org.domi.furnace.server.menu.BrewingStandMenu(containerId, inventory, c, bp);
                    case "minecraft:dispenser", "minecraft:dropper" -> new org.domi.furnace.server.menu.DispenserMenu(containerId, inventory, c, bp, menus);
                    case "minecraft:crafter" -> new org.domi.furnace.server.menu.CrafterMenu(containerId, inventory, c, bp, menus);
                    default -> org.domi.furnace.server.menu.ChestMenu.threeRows(containerId, inventory, c, bp, menus);   // 상자·통
                };
            }
            menus.out(new OpenScreen(containerId, menuTypeId(menu), spec.title()));
            menu.addSlotListener(menus);
            p.setContainerMenu(menu);
            menu.setSynchronizer(menus);   // 첫 container_set_content
        }
    }

    /** region 스레드: 그 칸 용기와 짝(더블 상자)을 찾고 전리품을 풀어 OpenSpec 을 만든다(막혔거나 용기가 없으면 null). */
    private OpenSpec resolveOpenSpec(org.domi.furnace.server.world.Region region, Player p, org.domi.furnace.server.block.Pos pos) {
        var level = region.level();
        if (level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.EnderChestBlockEntity ender) {
            if (org.domi.furnace.server.block.types.ChestBlock.isChestBlockedAt(level, pos)) return null;
            return new OpenSpec(null, null, ender, Text.translatable("container.enderchest"), null, null);
        }
        if (level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.BeaconBlockEntity beacon) {
            Text title;
            try {
                title = beacon.customName != null ? org.domi.furnace.api.text.serializer.JsonTextSerializer.json().deserialize(beacon.customName) : Text.translatable("container.beacon");
            } catch (RuntimeException e) {
                title = Text.translatable("container.beacon");
            }
            return new OpenSpec(null, null, null, title, null, beacon);
        }
        if (level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.LecternBlockEntity lectern) {
            return new OpenSpec(null, null, null, Text.translatable("container.lectern"), lectern, null);
        }
        if (!(level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.ContainerBlockEntity be)) return null;
        int state = level.getBlockState(pos);
        org.domi.furnace.server.block.entity.ContainerBlockEntity first = be, second = null;
        if (org.domi.furnace.server.block.B.behavior(state) instanceof org.domi.furnace.server.block.types.ChestBlock chestBlock) {
            if (org.domi.furnace.server.block.types.ChestBlock.isChestBlockedAt(level, pos)) return null;
            var partnerPos = chestBlock.doubleChestPartner(level, pos, state);
            if (partnerPos != null) {
                if (org.domi.furnace.server.block.types.ChestBlock.isChestBlockedAt(level, partnerPos)) return null;
                if (level.getBlockEntity(partnerPos) instanceof org.domi.furnace.server.block.entity.ContainerBlockEntity other) {
                    // DoubleBlockCombiner: 오른쪽 상자가 첫째(칸 0..26), 왼쪽이 둘째
                    boolean selfFirst = chestBlock.isRightChest(state);
                    first = selfFirst ? be : other;
                    second = selfFirst ? other : be;
                }
            }
        }
        var core = login.core;
        for (var c : second == null ? java.util.List.of(first) : java.util.List.of(first, second)) {
            if (c.lootTable != null && core != null) {
                core.unpackLoot(c, p);   // 바닐라 startOpen → unpackLootTable(player)
                region.markBlockEntityChanged(c);
            }
        }
        return new OpenSpec(first, second, null, containerTitle(first, second), null, null);
    }

    /** 제목: 첫째의 이름 → 둘째의 이름 → 더블이면 container.chestDouble, 아니면 그 종류의 기본 번역 키. */
    private static Text containerTitle(org.domi.furnace.server.block.entity.ContainerBlockEntity first,
            org.domi.furnace.server.block.entity.ContainerBlockEntity second) {
        var json = org.domi.furnace.api.text.serializer.JsonTextSerializer.json();
        try {
            if (first.customName != null) return json.deserialize(first.customName);
            if (second != null && second.customName != null) return json.deserialize(second.customName);
        } catch (RuntimeException e) {
            // 이상한 이름은 기본 제목으로
        }
        return Text.translatable(second != null ? "container.chestDouble" : first.defaultTitleKey());
    }

}
