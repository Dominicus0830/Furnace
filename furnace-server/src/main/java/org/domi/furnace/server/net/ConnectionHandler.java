// 한 커넥션의 프로토콜 상태머신. 프레임을 받아 단계별로 해석하고 응답 패킷을 보낸다.
package org.domi.furnace.server.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.ConnectionState;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.configuration.FinishConfiguration;
import org.domi.furnace.server.net.protocol.configuration.KnownPacksClientbound;
import org.domi.furnace.server.net.protocol.configuration.RegistryData;
import org.domi.furnace.server.net.protocol.handshake.Handshake;
import org.domi.furnace.server.net.protocol.login.LoginStart;
import org.domi.furnace.server.net.protocol.login.LoginSuccess;
import org.domi.furnace.server.net.protocol.status.PongResponse;
import org.domi.furnace.server.net.protocol.status.StatusResponse;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConnectionHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger log = LoggerFactory.getLogger(ConnectionHandler.class);
    private static final RegistrySet REGISTRIES = RegistrySet.load("registries/1.21.1.txt");

    private final String serverName;
    private ConnectionState state = ConnectionState.HANDSHAKE;
    private String playerName;
    private UUID playerUuid;

    public ConnectionHandler(String serverName) {
        this.serverName = serverName;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
        int packetId = Types.readVarInt(frame);
        switch (state) {
            case HANDSHAKE -> handleHandshake(ctx, packetId, frame);
            case STATUS -> handleStatus(ctx, packetId, frame);
            case LOGIN -> handleLogin(ctx, packetId, frame);
            case CONFIGURATION -> handleConfiguration(ctx, packetId, frame);
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
                // (압축은 Task 10에서: 여기서 Set Compression 전송 후 코덱 삽입)
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
        // Task 9에서 구현.
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
