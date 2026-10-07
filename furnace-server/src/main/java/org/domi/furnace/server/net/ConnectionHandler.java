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
import org.domi.furnace.server.net.protocol.configuration.FinishConfiguration;
import org.domi.furnace.server.net.protocol.configuration.KnownPacksClientbound;
import org.domi.furnace.server.net.protocol.configuration.RegistryData;
import org.domi.furnace.server.net.protocol.handshake.Handshake;
import org.domi.furnace.server.net.protocol.play.BlockChangedAck;
import org.domi.furnace.server.net.protocol.play.ContainerClose;
import org.domi.furnace.server.net.protocol.play.ContainerSetContent;
import org.domi.furnace.server.net.protocol.play.OpenScreen;
import org.domi.furnace.server.world.ChestBlockEntity;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.server.net.protocol.login.LoginStart;
import org.domi.furnace.server.net.protocol.login.LoginSuccess;
import org.domi.furnace.server.net.protocol.login.SetCompression;
import org.domi.furnace.server.net.protocol.play.ChunkBatchFinished;
import org.domi.furnace.server.net.protocol.play.ChunkBatchStart;
import org.domi.furnace.server.net.protocol.play.GameEvent;
import org.domi.furnace.server.net.protocol.play.KeepAliveClientbound;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.protocol.play.SetCenterChunk;
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

    private final String serverName;
    private final int compressionThreshold;
    private final FurnaceScheduler scheduler;
    private final World world;
    private final EntityIdAllocator idAllocator;

    private volatile ChannelHandlerContext ctx;
    private final AtomicBoolean awaitingKeepAlive = new AtomicBoolean(false);
    private volatile long lastKeepAliveId;

    private ConnectionState state = ConnectionState.HANDSHAKE;
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
        this.serverName = serverName;
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
            case PLAY -> handlePlay(ctx, packetId, frame);
            default -> log.debug("미처리 패킷: state={}, id=0x{}", state, Integer.toHexString(packetId));
        }
    }

    private void handleHandshake(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        if (packetId != Handshake.ID) {
            ctx.close();
            return;
        }
        Handshake hs = Handshake.read(frame);
        state = switch (hs.nextState()) {
            case 1 -> ConnectionState.STATUS;
            case 2 -> ConnectionState.LOGIN;
            default -> { ctx.close(); yield ConnectionState.HANDSHAKE; }
        };
    }

    private void handleStatus(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (packetId) {
            case 0x00 -> send(ctx, StatusResponse.of(serverName));        // Status Request
            case 0x01 -> send(ctx, new PongResponse(frame.readLong()));   // Ping Request
            default -> ctx.close();
        }
    }

    private void handleLogin(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (packetId) {
            case 0x00 -> { // Login Start
                LoginStart start = LoginStart.read(frame);
                this.playerName = start.name();
                this.playerUuid = LoginStart.offlineUuid(start.name());
                if (compressionThreshold >= 0) {
                    send(ctx, new SetCompression(compressionThreshold)); // 비압축으로 나감
                    ctx.pipeline().addAfter("frameDecoder", "decompress",
                            new CompressionDecoder(compressionThreshold));
                    ctx.pipeline().addAfter("frameEncoder", "compress",
                            new CompressionEncoder(compressionThreshold));
                }
                send(ctx, new LoginSuccess(playerUuid, playerName));
            }
            case 0x03 -> { // Login Acknowledged
                state = ConnectionState.CONFIGURATION;
                onEnterConfiguration(ctx); // Task 8에서 구현
            }
            default -> ctx.close();
        }
    }

    private void onEnterConfiguration(ChannelHandlerContext ctx) {
        send(ctx, new KnownPacksClientbound());
    }

    private void handleConfiguration(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (packetId) {
            case 0x00 -> readClientInformation(frame); // Client Information: view distance 읽음
            case 0x02 -> { /* Plugin Message(brand): 무시 */ }
            case 0x07 -> { // Serverbound Known Packs → 레지스트리 전송 후 종료
                for (String registry : REGISTRIES.registryKeys()) {
                    send(ctx, new RegistryData(registry, REGISTRIES.entries(registry)));
                }
                send(ctx, new FinishConfiguration());
            }
            case 0x03 -> { // Acknowledge Finish Configuration
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
        send(ctx, new LoginPlay(eid, "minecraft:overworld", dimIndex, effVd));
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

        // 플레이어를 스폰 region에 편입 — 첫 틱부터 청크/가시성 처리.
        Player p = new Player(eid, playerUuid, playerName, ctx.channel());
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
            case 0x18 -> { // Keep Alive (serverbound)
                long echoed = frame.readLong();
                if (echoed == lastKeepAliveId) {
                    awaitingKeepAlive.set(false);
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
                if (slot >= 0 && slot < slots.length) slots[slot] = stack;
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
                ItemStack newCarried = parseOk ? readSlotStrict(frame) : null;
                if (!parseOk) break; // 경계 유실 — 이 패킷 전체 폐기(범위 밖 케이스)
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
            ok = dx * dx + dy * dy + dz * dz <= MAX_STEP * MAX_STEP;
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
            ItemStack[] snapshot = copySlots();
            // IO 전용 위치(ix..)를 저장 — Player.x()는 region이 drainPending 전이면 0,0,0이다(초기 접속 직후 끊김).
            final double sx = ix, sy = iy, sz = iz;
            final float syaw = iyaw, spitch = ipitch;
            final boolean sg = ig;
            scheduler.global().execute(() -> org.domi.furnace.server.entity.PlayerData.save(
                    world.worldDir(), p.uuid(), sx, sy, sz, syaw, spitch, sg, snapshot));
            p.markDisconnected(); // 소유 region이 다음 틱에 정리(라우팅 불필요)
        }
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
            ctx.close();
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

    void state(ConnectionState newState) {
        this.state = newState;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("커넥션 오류, 종료: {}", cause.toString());
        ctx.close();
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
