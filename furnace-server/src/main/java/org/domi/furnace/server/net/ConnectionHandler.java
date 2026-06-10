// 한 커넥션의 프로토콜 상태머신. 프레임을 받아 단계별로 해석하고 응답 패킷을 보낸다.
package org.domi.furnace.server.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.UUID;
import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.server.net.io.CompressionDecoder;
import org.domi.furnace.server.net.io.CompressionEncoder;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.ConnectionState;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.configuration.FinishConfiguration;
import org.domi.furnace.server.net.protocol.configuration.KnownPacksClientbound;
import org.domi.furnace.server.net.protocol.configuration.RegistryData;
import org.domi.furnace.server.net.protocol.handshake.Handshake;
import org.domi.furnace.server.net.protocol.login.LoginStart;
import org.domi.furnace.server.net.protocol.login.LoginSuccess;
import org.domi.furnace.server.net.protocol.login.SetCompression;
import org.domi.furnace.server.net.protocol.play.ChunkBatchFinished;
import org.domi.furnace.server.net.protocol.play.ChunkBatchStart;
import org.domi.furnace.server.net.protocol.play.ChunkSerializer;
import org.domi.furnace.server.net.protocol.play.GameEvent;
import org.domi.furnace.server.net.protocol.play.KeepAliveClientbound;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.protocol.play.LoginPlay;
import org.domi.furnace.server.net.protocol.play.SetDefaultSpawnPosition;
import org.domi.furnace.server.net.protocol.play.SyncPlayerPosition;
import org.domi.furnace.server.net.protocol.status.PongResponse;
import org.domi.furnace.server.net.protocol.status.StatusResponse;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.ChunkSnapshot;
import org.domi.furnace.server.world.RegionGrid;
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
    private final int[] hotbar = new int[9]; // 핫바 슬롯 아이템 id(0=air). Netty 스레드 전용.
    private int selectedSlot;                // 0~8
    private double spawnX, spawnY, spawnZ;
    private float spawnYaw, spawnPitch;

    public ConnectionHandler(String serverName, int compressionThreshold, FurnaceScheduler scheduler,
                             World world, EntityIdAllocator idAllocator) {
        this.serverName = serverName;
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
            case 0x00, 0x02 -> { /* Client Information / Plugin Message(brand): 읽지 않고 무시 */ }
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

    private void onEnterPlay(ChannelHandlerContext ctx) {
        int eid = idAllocator.next();
        int dimIndex = Math.max(0,
                REGISTRIES.indexOf("minecraft:dimension_type", "minecraft:overworld"));
        send(ctx, new LoginPlay(eid, "minecraft:overworld", dimIndex, world.viewDistance()));
        // 저장된 위치 있으면 그 자리, 없으면 superflat 표면(grass y=−61 위 y=−60).
        double[] saved = org.domi.furnace.server.entity.PlayerData.load(world.worldDir(), playerUuid);
        double sx = saved != null ? saved[0] : 0;
        double sy = saved != null ? saved[1] : -60;
        double sz = saved != null ? saved[2] : 0;
        float syaw = saved != null ? (float) saved[3] : 0f;
        float spitch = saved != null ? (float) saved[4] : 0f;
        sy += 0.05; // 지면 살짝 위서 스폰 — 동기 청크 위로 살짝 떨어져 안착(clip/immovable 없이 자연 착지).
        spawnX = sx; spawnY = sy; spawnZ = sz; spawnYaw = syaw; spawnPitch = spitch;
        // GE13 먼저 — 클라가 "청크 대기" 상태(로딩화면 정지)에 들어간 뒤 위치를 받게 해 위치-유발 중력 억제.
        send(ctx, new GameEvent(GameEvent.START_WAITING_FOR_CHUNKS, 0f));
        send(ctx, new SyncPlayerPosition(sx, sy, sz, syaw, spitch, 1));
        pendingTeleportId = 1; // 스폰 청크 도착(타이머)까지 클라 이동 무시
        send(ctx, new SetDefaultSpawnPosition((int) sx, (int) sy, (int) sz));

        // 플레이어를 스폰 region에 편입 — 첫 틱부터 청크/가시성 처리.
        Player p = new Player(eid, playerUuid, playerName, ctx.channel());
        p.publishPosition(sx, sy, sz, syaw, spitch, true);
        this.player = p;
        // 스폰 청크를 join 버스트에 동기 전송 — 클라가 발밑 청크 있는 채로 스폰(낙하 방지, M2 방식).
        // adopt 전이라 p.loadedChunks는 이 스레드 전용(레이스 없음). region streamLoads는 이미 로드된 중앙을 건너뜀.
        ChunkPos centerCp = ChunkPos.ofBlock(sx, sz);
        try {
            ChunkSnapshot snap = world.snapshotChunk(centerCp).get(2, java.util.concurrent.TimeUnit.SECONDS);
            ByteBuf body = Unpooled.buffer();
            ChunkSerializer.serialize(body, snap, world.blockDirectBits(), world.biomeDirectBits());
            send(ctx, new ChunkBatchStart());
            send(ctx, new LevelChunkWithLight(body));
            send(ctx, new ChunkBatchFinished(1));
            p.loadedChunks().add(centerCp);
            world.subscribeChunk(centerCp, p);
        } catch (Exception e) {
            log.warn("스폰 청크 동기 전송 실패 {}", centerCp, e);
        }
        RegionPos spawn = RegionGrid.regionPos(sx, sz, world.regionSize());
        scheduler.region(spawn).execute(() -> world.regionAt(spawn).adopt(p));
        scheduleKeepAlive();
        scheduleSpawnGateRelease();
    }

    /** 게이트 해제 fallback(6틱) — 보통 confirm(0x00)이 먼저 해제. confirm 유실 시 위치 재확정 후 해제. */
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
        Player p = player;
        if (p != null) p.publishPosition(spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, true);
    }

    private void handlePlay(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (packetId) {
            case 0x00 -> { // Confirm Teleportation: 일치하면 즉시 게이트 해제(스폰 청크 동기 전송됐으므로 안전)
                int tpId = Types.readVarInt(frame);
                if (tpId == pendingTeleportId) pendingTeleportId = -1;
            }
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
                if (p != null && pendingTeleportId == -1) p.publishPosition(x, y, z, p.yaw(), p.pitch(), g);
            }
            case 0x1B -> { // move_player_pos_rot: Double x,y,z, Float yaw,pitch, Boolean onGround
                double x = frame.readDouble(), y = frame.readDouble(), z = frame.readDouble();
                float yaw = frame.readFloat(), pitch = frame.readFloat();
                boolean g = frame.readBoolean();
                Player p = player;
                if (p != null && pendingTeleportId == -1) p.publishPosition(x, y, z, yaw, pitch, g);
            }
            case 0x1C -> { // move_player_rot: Float yaw,pitch, Boolean onGround
                float yaw = frame.readFloat(), pitch = frame.readFloat();
                boolean g = frame.readBoolean();
                Player p = player;
                if (p != null && pendingTeleportId == -1) p.publishPosition(p.x(), p.y(), p.z(), yaw, pitch, g);
            }
            case 0x24 -> { // player_action (digging)
                int status = Types.readVarInt(frame);
                int[] bp = Types.readBlockPos(frame);
                frame.readByte();        // face
                Types.readVarInt(frame); // sequence(예측 ack) — 읽고 무시
                if (status == 0) {       // start digging = 크리에이티브 즉시 파괴
                    int bx = bp[0], by = bp[1], bz = bp[2];
                    RegionPos br = RegionGrid.regionPos(bx, bz, world.regionSize());
                    scheduler.region(br).execute(() -> world.regionAt(br).breakBlock(bx, by, bz));
                }
            }
            case 0x2F -> { // Set Held Item(serverbound): Short slot(0~8)
                int slot = frame.readShort();
                if (slot >= 0 && slot < 9) selectedSlot = slot;
            }
            case 0x32 -> { // Set Creative Mode Slot: Short slot + Slot
                int slot = frame.readShort();
                int count = Types.readVarInt(frame);   // itemCount
                int itemId = count > 0 ? Types.readVarInt(frame) : 0; // >0이면 item id(components는 무시)
                if (slot >= 36 && slot <= 44) hotbar[slot - 36] = itemId;
            }
            case 0x38 -> { // Use Item On: hand, pos, face, cursorXYZ, insideBlock, sequence
                int hand = Types.readVarInt(frame);
                int[] bp = Types.readBlockPos(frame);
                int face = Types.readVarInt(frame);
                frame.readFloat(); frame.readFloat(); frame.readFloat(); // cursor X/Y/Z
                frame.readBoolean(); // insideBlock
                Types.readVarInt(frame); // sequence
                if (hand != 0) break; // 메인핸드만
                int stateId = world.itemToBlockState(hotbar[selectedSlot]);
                if (stateId == 0) break; // 설치 불가 아이템(블록 아님)
                int[] o = org.domi.furnace.server.world.Region.placeOffset(face);
                int px = bp[0] + o[0], py = bp[1] + o[1], pz = bp[2] + o[2];
                RegionPos rp = RegionGrid.regionPos(px, pz, world.regionSize());
                scheduler.region(rp).execute(() -> world.regionAt(rp).placeBlock(px, py, pz, stateId));
            }
            default -> { /* 그 외 무시 */ }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Player p = player;
        if (p != null) p.markDisconnected(); // 소유 region이 다음 틱에 정리(라우팅 불필요)
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
}
