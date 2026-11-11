// 접속 방어(실제 소켓): 단계 시간 초과, 바닐라 rate-limit, PROXY 프로토콜. 시간 초과는 1초 설정으로 시험한다.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.Consumer;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConnectionDefenseSocketTest {

    private FurnaceSchedulerImpl sched;
    private NetworkServer server;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
        if (sched != null) sched.close();
    }

    private int start(ConnectionLimits limits) {
        sched = new FurnaceSchedulerImpl(50_000_000L);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 2, 2, reg.directBits(), 7, java.util.concurrent.Executors.newSingleThreadExecutor());
        server = new NetworkServer(0, -1, ServerLoginContext.offline("Furnace Server").limits(limits), sched, world, new EntityIdAllocator());
        return server.start();
    }

    private static ConnectionLimits limits(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) p.setProperty(kv[i], kv[i + 1]);
        return ConnectionLimits.fromProperties(p, m -> {});
    }

    private static Socket connect(int port) throws Exception {
        Socket s = new Socket();
        s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
        s.setSoTimeout(8000);
        return s;
    }

    private static void writeFrame(OutputStream out, int id, Consumer<ByteBuf> body) throws Exception {
        ByteBuf b = Unpooled.buffer();
        Types.writeVarInt(b, id);
        if (body != null) body.accept(b);
        ByteBuf f = Unpooled.buffer();
        Types.writeVarInt(f, b.readableBytes());
        f.writeBytes(b);
        byte[] bytes = new byte[f.readableBytes()];
        f.readBytes(bytes);
        out.write(bytes);
        out.flush();
    }

    private static void handshake(OutputStream out, int next) throws Exception {
        writeFrame(out, 0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost"); b.writeShort(25565); Types.writeVarInt(b, next); });
    }

    /** 로그인 시작 → LoginSuccess 를 받고 Login Acknowledged 까지 보내 구성 단계로 들어간다. */
    private static void toConfiguration(Socket s) throws Exception {
        OutputStream out = s.getOutputStream();
        handshake(out, 2);
        writeFrame(out, 0x00, b -> { Types.writeString(b, "Tester"); Types.writeUuid(b, new java.util.UUID(0, 0)); });
        InputStream in = s.getInputStream();
        // LoginSuccess(0x02) 프레임이 올 때까지
        while (true) {
            int len = readVarInt(in);
            byte[] data = in.readNBytes(len);
            if (data.length > 0 && data[0] == 0x02) break;
        }
        writeFrame(out, 0x03, null);
    }

    private static int readVarInt(InputStream in) throws Exception {
        int value = 0, shift = 0, b;
        do {
            b = in.read();
            if (b < 0) throw new java.io.EOFException();
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }

    private static String drain(Socket s) throws Exception {
        return new String(s.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
    }

    @Test
    void handshakeTimeout_closesSilentConnection() throws Exception {
        int port = start(limits("connection.handshake.timeout-seconds", "1"));
        try (Socket s = connect(port)) {
            long t0 = System.nanoTime();
            assertEquals(-1, s.getInputStream().read(), "핸드셰이크를 안 보내면 1초 뒤 닫힌다");
            assertTrue((System.nanoTime() - t0) < 6_000_000_000L, "읽기 타임아웃(30초)이 아니라 단계 시간 초과로 닫혀야 한다");
        }
    }

    @Test
    void loginTimeout_usesSlowLoginReason() throws Exception {
        int port = start(limits("connection.login.timeout-seconds", "1"));
        try (Socket s = connect(port)) {
            handshake(s.getOutputStream(), 2);   // 로그인 단계로 들어가 아무것도 안 보낸다
            assertTrue(drain(s).contains("multiplayer.disconnect.slow_login"), "로그인 단계 시간 초과는 바닐라 slow_login 사유");
        }
    }

    @Test
    void configurationTimeout_kicksStalledConfiguration() throws Exception {
        int port = start(limits("connection.configuration.timeout-seconds", "1"));
        try (Socket s = connect(port)) {
            toConfiguration(s);
            assertTrue(drain(s).contains("disconnect.timeout"), "구성 단계에서 멈춘 연결은 시간 초과로 끊긴다");
        }
    }

    @Test
    void timeoutsDefaultOff_configurationStaysOpen() throws Exception {
        int port = start(ConnectionLimits.DEFAULT);
        try (Socket s = connect(port)) {
            toConfiguration(s);
            s.setSoTimeout(2500);
            // 기본값에서는 구성 단계 시간 제한이 없다(바닐라와 같다): 2.5초 동안 닫히지 않는다.
            assertThrows(java.net.SocketTimeoutException.class, () -> { while (s.getInputStream().read() >= 0) { /* 구성 패킷 소비 */ } });
        }
    }

    /** 잘못된 패킷: 단계 상한을 넘는 프레임은 본문을 기다리지 않고 닫힌다(로그인 1 MiB+, 구성 약 128 KiB 초과). */
    @Test
    void oversizedFrames_areClosedPerStateBeforeBuffering() throws Exception {
        int port = start(ConnectionLimits.DEFAULT);
        try (Socket s = connect(port)) {
            toConfiguration(s);
            ByteBuf len = Unpooled.buffer();
            Types.writeVarInt(len, 200_000);   // 구성 단계 상한(약 131 KiB) 초과 — 본문은 보내지 않는다
            byte[] b = new byte[len.readableBytes()];
            len.readBytes(b);
            s.getOutputStream().write(b);
            s.getOutputStream().flush();
            drain(s);   // 서버가 닫으면 EOF 로 끝난다(본문을 기다리다 읽기 타임아웃까지 가면 SocketTimeoutException)
        }
        try (Socket s = connect(port)) {
            handshake(s.getOutputStream(), 2);
            ByteBuf len = Unpooled.buffer();
            Types.writeVarInt(len, 1_500_000);   // 로그인 단계 상한(1 MiB + 7) 초과
            byte[] b = new byte[len.readableBytes()];
            len.readBytes(b);
            s.getOutputStream().write(b);
            s.getOutputStream().flush();
            drain(s);
        }
    }

    /** 잘못된 패킷: 알 수 없는 id 와 쓰레기 바이트로 서버가 죽거나 멈추지 않고, 그 연결만 정리된다. */
    @Test
    void garbageBytes_closeOnlyThatConnection() throws Exception {
        int port = start(ConnectionLimits.DEFAULT);
        try (Socket s = connect(port)) {
            s.getOutputStream().write(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F, 1, 2, 3});
            s.getOutputStream().flush();
            drain(s);
        }
        try (Socket s = connect(port)) {   // 서버는 계속 정상: 다음 연결의 상태 조회가 된다
            handshake(s.getOutputStream(), 1);
            writeFrame(s.getOutputStream(), 0x00, null);
            int len = readVarInt(s.getInputStream());
            assertTrue(len > 0);
        }
    }

    @Test
    void vanillaRateLimit_kicksFlooderWithVanillaReason() throws Exception {
        int port = start(ConnectionLimits.DEFAULT.withVanillaRateLimit(5));
        try (Socket s = connect(port)) {
            toConfiguration(s);
            OutputStream out = s.getOutputStream();
            for (int i = 0; i < 60; i++) writeFrame(out, 0x00, ConnectionDefenseTest::clientInfoBody);   // 한 초에 60개 > rate-limit 5
            assertTrue(drain(s).contains("disconnect.exceeded_packet_rate"), "바닐라 사유로 끊긴다");
        }
    }

    @Test
    void vanillaRateLimit_off_floodWithinBucketIsFine() throws Exception {
        int port = start(ConnectionLimits.DEFAULT);
        try (Socket s = connect(port)) {
            toConfiguration(s);
            OutputStream out = s.getOutputStream();
            for (int i = 0; i < 60; i++) writeFrame(out, 0x00, ConnectionDefenseTest::clientInfoBody);
            s.setSoTimeout(2500);
            assertThrows(java.net.SocketTimeoutException.class, () -> { while (s.getInputStream().read() >= 0) { /* 소비 */ } },
                    "rate-limit=0(바닐라 기본)이면 버스트 안의 60개로는 끊기지 않는다");
        }
    }

    @Test
    void proxyProtocol_statusPingThroughHeader() throws Exception {
        int port = start(limits("proxy-protocol", "true"));
        try (Socket s = connect(port)) {
            OutputStream out = s.getOutputStream();
            ByteBuf header = ConnectionDefenseTest.v2Tcp4(new int[]{203, 0, 113, 9}, 51234);
            byte[] hb = new byte[header.readableBytes()];
            header.readBytes(hb);
            out.write(hb);
            handshake(out, 1);
            writeFrame(out, 0x00, null);   // Status Request
            int len = readVarInt(s.getInputStream());
            byte[] data = s.getInputStream().readNBytes(len);
            assertTrue(new String(data, StandardCharsets.UTF_8).contains("1.21.1"), "PROXY 헤더 뒤의 상태 요청에 정상 응답");
        }
    }

    @Test
    void proxyProtocol_directConnectionWithoutHeaderIsRejectedAtOnce() throws Exception {
        int port = start(limits("proxy-protocol", "true"));
        try (Socket s = connect(port)) {
            handshake(s.getOutputStream(), 1);
            writeFrame(s.getOutputStream(), 0x00, null);
            long t0 = System.nanoTime();
            assertEquals(-1, s.getInputStream().read(), "헤더 없는 직접 접속은 거부(응답 없이 닫힘)");
            assertTrue((System.nanoTime() - t0) < 3_000_000_000L, "읽기 타임아웃을 기다리지 않고 바로 닫힌다");
        }
    }

    @Test
    void proxyProtocol_off_headerBytesAreJustGarbage() throws Exception {
        int port = start(ConnectionLimits.DEFAULT);
        try (Socket s = connect(port)) {
            ByteBuf header = ConnectionDefenseTest.v2Tcp4(new int[]{203, 0, 113, 9}, 51234);
            byte[] hb = new byte[header.readableBytes()];
            header.readBytes(hb);
            s.getOutputStream().write(hb);
            s.getOutputStream().flush();
            assertEquals(-1, s.getInputStream().read(), "꺼져 있으면 PROXY 헤더는 잘못된 프레임이라 닫힌다(바닐라와 같다)");
        }
    }
}
