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
        p.publishPosition(0, -60, 0, 0f, 0f, true);
        this.player = p;
        RegionPos spawn = RegionGrid.regionPos(0, 0, world.regionSize());
        scheduler.region(spawn).execute(() -> world.regionAt(spawn).adopt(p));
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
                Player p = player;
                if (p != null) p.publishPosition(x, y, z, p.yaw(), p.pitch(), g);
            }
            case 0x1B -> { // move_player_pos_rot: Double x,y,z, Float yaw,pitch, Boolean onGround
                double x = frame.readDouble(), y = frame.readDouble(), z = frame.readDouble();
                float yaw = frame.readFloat(), pitch = frame.readFloat();
                boolean g = frame.readBoolean();
                Player p = player;
                if (p != null) p.publishPosition(x, y, z, yaw, pitch, g);
            }
            case 0x1C -> { // move_player_rot: Float yaw,pitch, Boolean onGround
                float yaw = frame.readFloat(), pitch = frame.readFloat();
                boolean g = frame.readBoolean();
                Player p = player;
                if (p != null) p.publishPosition(p.x(), p.y(), p.z(), yaw, pitch, g);
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
