// 한 커넥션의 프로토콜 상태머신. 프레임을 받아 단계별로 해석하고 응답 패킷을 보낸다.
package org.domi.furnace.server.net;

import io.netty.buffer.ByteBuf;
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
import org.domi.furnace.server.net.protocol.play.GameEvent;
import org.domi.furnace.server.net.protocol.play.KeepAliveClientbound;
import org.domi.furnace.server.net.protocol.play.LoginPlay;
import org.domi.furnace.server.net.protocol.play.SetDefaultSpawnPosition;
import org.domi.furnace.server.net.protocol.play.SyncPlayerPosition;
import org.domi.furnace.server.net.protocol.status.PongResponse;
import org.domi.furnace.server.net.protocol.status.StatusResponse;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.world.ChunkColumn;
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

    // IO 스레드 전용: 마지막으로 수락한 클라 위치/회전. 부분 패킷(회전만 등)이 이전 값을 덮어쓰지 않게 여기서 합쳐 발행한다.
    private double ix, iy = -60, iz;
    private float iyaw, ipitch;
    private boolean ig = true;
    private int teleportId = 1;
    private static final double MAX_COORD = 3.0e7;   // 월드 경계 수준
    private static final double MAX_STEP = 100;       // 패킷당 허용 이동(블록) — 넘으면 되돌림
    private static final double REACH_SQ = 8 * 8;    // 블록 파괴 도달 거리²(크리 5 + 여유)

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
        // superflat 표면 grass at y=−61 → 플레이어는 그 위 y=−60에 선다.
        send(ctx, new SyncPlayerPosition(0, -60, 0, 1));
        send(ctx, new SetDefaultSpawnPosition(0, -60, 0));
        // 청크 전송 전 "대기 시작" 알림 — 없으면 클라가 로딩 화면서 30초 타임아웃.
        send(ctx, new GameEvent(GameEvent.START_WAITING_FOR_CHUNKS, 0f));

        // 플레이어를 스폰 region에 편입 — 첫 틱부터 청크/가시성 처리.
        Player p = new Player(eid, playerUuid, playerName, ctx.channel());
        p.publishPosition(ix, iy, iz, iyaw, ipitch, ig);
        this.player = p;
        RegionPos spawn = RegionGrid.regionPos(0, 0, world.regionSize());
        world.runOn(spawn, () -> world.regionAt(spawn).adopt(p));
        scheduleKeepAlive();
    }

    private void handlePlay(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (packetId) {
            case 0x00 -> { /* Confirm Teleportation */ }
            case 0x18 -> { // Keep Alive (serverbound)
                long echoed = frame.readLong();
                if (echoed == lastKeepAliveId) {
                    awaitingKeepAlive.set(false);
                }
            }
            case 0x1A -> { // move_player_pos: Double x,y,z, Boolean onGround
                double x = frame.readDouble(), y = frame.readDouble(), z = frame.readDouble();
                boolean g = frame.readBoolean();
                if (player != null && acceptPosition(ctx, x, y, z)) {
                    ig = g;
                    publishInput();
                }
            }
            case 0x1B -> { // move_player_pos_rot: Double x,y,z, Float yaw,pitch, Boolean onGround
                double x = frame.readDouble(), y = frame.readDouble(), z = frame.readDouble();
                float yaw = frame.readFloat(), pitch = frame.readFloat();
                boolean g = frame.readBoolean();
                if (player != null && acceptPosition(ctx, x, y, z)) {
                    iyaw = finiteOr0(yaw);
                    ipitch = finiteOr0(pitch);
                    ig = g;
                    publishInput();
                }
            }
            case 0x1C -> { // move_player_rot: Float yaw,pitch, Boolean onGround
                float yaw = frame.readFloat(), pitch = frame.readFloat();
                boolean g = frame.readBoolean();
                if (player != null) {
                    iyaw = finiteOr0(yaw);
                    ipitch = finiteOr0(pitch);
                    ig = g;
                    publishInput();
                }
            }
            case 0x24 -> { // player_action (digging)
                int status = Types.readVarInt(frame);
                int[] bp = Types.readBlockPos(frame);
                frame.readByte();        // face
                Types.readVarInt(frame); // sequence(예측 ack) — 읽고 무시
                if (status == 0 && player != null) {   // start digging = 크리에이티브 즉시 파괴
                    int bx = bp[0], by = bp[1], bz = bp[2];
                    if (by >= ChunkColumn.MIN_Y && by <= ChunkColumn.MAX_Y && withinReach(bx, by, bz)) {
                        RegionPos br = RegionGrid.regionPos(bx, bz, world.regionSize());
                        world.runOn(br, () -> world.regionAt(br).breakBlock(bx, by, bz));
                    }
                }
            }
            default -> { /* 그 외 무시 */ }
        }
    }

    private void publishInput() {
        player.publishPosition(ix, iy, iz, iyaw, ipitch, ig);
    }

    private static float finiteOr0(float v) {
        return Float.isFinite(v) ? v : 0f;
    }

    /** 이동 수용 검사. 통과하면 수락 위치로 기록, 비유한·범위 밖·급이동이면 마지막 수락 위치로 되돌리고 false(서버 권위 보정). */
    private boolean acceptPosition(ChannelHandlerContext ctx, double x, double y, double z) {
        boolean ok = Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(x) <= MAX_COORD && Math.abs(y) <= MAX_COORD && Math.abs(z) <= MAX_COORD;
        if (ok) {
            double dx = x - ix, dy = y - iy, dz = z - iz;
            ok = dx * dx + dy * dy + dz * dz <= MAX_STEP * MAX_STEP;
        }
        if (!ok) {
            send(ctx, new SyncPlayerPosition(ix, iy, iz, ++teleportId));
            return false;
        }
        ix = x; iy = y; iz = z;
        return true;
    }

    /** 마지막 수락 위치(눈 높이 기준)에서 블록 중심까지 도달 거리 안인가. */
    private boolean withinReach(int bx, int by, int bz) {
        double dx = bx + 0.5 - ix, dy = by + 0.5 - (iy + 1.62), dz = bz + 0.5 - iz;
        return dx * dx + dy * dy + dz * dz <= REACH_SQ;
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
