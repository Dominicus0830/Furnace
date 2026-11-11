// A01 점검 수정: 구형 0xFE 서버 목록 핑, play ping_request→pong_response, debug_sample 구독(OP 전용),
// 틀린 keep-alive id 킥, ClientInformation 전체 읽기, 첫 지연 표본 평활화 — 모두 바닐라 결과와 같은지 실제 소켓으로 확인.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectionGapsTest {

    private static final String FULL = "§1" + "\0" + "127" + "\0" + "1.21.1" + "\0" + "A Furnace" + "\0" + "0" + "\0" + "20";

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private NetworkServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.world().startTicking();
        ServerLoginContext login = new ServerLoginContext(false, false, false, true, false, false, true, 20, "A Furnace",
                null, null, core.lists(), null, null).core(core);
        server = new NetworkServer(0, -1, login, sched, core.world(), new EntityIdAllocator());
        port = server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) server.stop();
        core.plugins().close();
        sched.close();
        TestServers.drainIoPools();
    }

    private Player awaitPlayer(String name) throws InterruptedException {
        for (int i = 0; i < 1500 && core.playerByName(name) == null; i++) Thread.sleep(20);
        Player p = core.playerByName(name);
        assertNotNull(p, name);
        return p;
    }

    // ------------------------------------------------------------ A01-01 구형 서버 목록 핑

    /** 바이트를 보내고 서버가 닫을 때까지 읽는다. */
    private byte[] legacy(int... bytes) throws Exception {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            s.setSoTimeout(5000);
            OutputStream out = s.getOutputStream();
            byte[] b = new byte[bytes.length];
            for (int i = 0; i < b.length; i++) b[i] = (byte) bytes[i];
            out.write(b);
            out.flush();
            InputStream in = s.getInputStream();
            return in.readAllBytes();   // 바닐라는 답한 뒤 닫는다 — 닫히지 않으면 SO_TIMEOUT 으로 실패
        }
    }

    private static String legacyText(byte[] reply) {
        assertEquals((byte) 0xFF, reply[0]);
        int chars = ((reply[1] & 0xFF) << 8) | (reply[2] & 0xFF);
        assertEquals(chars * 2, reply.length - 3);
        return new String(reply, 3, reply.length - 3, StandardCharsets.UTF_16BE);
    }

    @Test
    void legacyPing_1_3_answersMotdOnlineMax() throws Exception {
        assertEquals("A Furnace§0§20", legacyText(legacy(0xFE)));
    }

    @Test
    void legacyPing_1_4_and_1_5_answerFullStatus() throws Exception {
        assertEquals(FULL, legacyText(legacy(0xFE, 0x01)));
    }

    @Test
    void legacyPing_1_6_withPingHostPayload() throws Exception {
        ByteBuf b = io.netty.buffer.Unpooled.buffer();
        b.writeByte(0xFE).writeByte(0x01).writeByte(0xFA);
        b.writeShort(11).writeBytes("MC|PingHost".getBytes(StandardCharsets.UTF_16BE));
        String host = "localhost";
        b.writeShort(7 + host.length() * 2);
        b.writeByte(74).writeShort(host.length()).writeBytes(host.getBytes(StandardCharsets.UTF_16BE)).writeInt(25565);
        byte[] raw = new byte[b.readableBytes()];
        b.readBytes(raw);
        int[] ints = new int[raw.length];
        for (int i = 0; i < raw.length; i++) ints[i] = raw[i] & 0xFF;
        assertEquals(FULL, legacyText(legacy(ints)));
    }

    @Test
    void modernStatusStillWorksAfterLegacyHandler() throws Exception {
        try (TestClient c = TestClient.open(port)) {
            c.write(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost"); b.writeShort(25565); Types.writeVarInt(b, 1); });
            c.write(0x00, null);
            assertTrue(Types.readString(c.await(0x00), 32767).contains("\"protocol\":767"));
        }
    }

    // ------------------------------------------------------------ A01-02 play ping

    @Test
    void playPingRequest_isAnsweredWithPong() throws Exception {
        try (TestClient c = TestClient.join(port, "Pinger")) {
            c.write(0x21, b -> b.writeLong(0x1234_5678_9ABCL));
            ByteBuf pong = c.await(0x36);
            assertEquals(0x1234_5678_9ABCL, pong.readLong());
        }
    }

    // ------------------------------------------------------------ A01-03 debug_sample

    @Test
    void debugSample_onlyOpsGetIt_andOnlyWhileOp() throws Exception {
        try (TestClient c = TestClient.join(port, "DebugOp"); TestClient n = TestClient.join(port, "DebugNo")) {
            Player op = awaitPlayer("DebugOp");
            awaitPlayer("DebugNo");
            core.lists().op(ServerCore.profileOf(op), 4);
            c.write(0x13, b -> Types.writeVarInt(b, 0));
            n.write(0x13, b -> Types.writeVarInt(b, 0));
            for (int i = 0; i < 250 && core.debugSamples().size() < 1; i++) Thread.sleep(20);
            Thread.sleep(200);
            assertEquals(1, core.debugSamples().size(), "비 OP 의 요청은 조용히 무시");

            core.debugSamples().tick(50_100_000L, 12_345_678L);
            ByteBuf s = c.await(0x1B);
            assertEquals(4, Types.readVarInt(s));
            assertEquals(50_100_000L, s.readLong());
            assertEquals(12_345_678L, s.readLong());
            assertEquals(0L, s.readLong());
            assertEquals(50_100_000L - 12_345_678L, s.readLong());
            assertEquals(0, Types.readVarInt(s));

            core.lists().deop(ServerCore.profileOf(op));
            core.debugSamples().tick(50_000_000L, 1L);
            c.socketTimeout(600);
            assertThrows(java.net.SocketTimeoutException.class, () -> c.await(0x1B), "OP 가 풀리면 받지 못한다");
            core.lists().op(ServerCore.profileOf(op), 4);   // 바닐라: 새 요청 없이도 다시 구독된다
            core.debugSamples().tick(50_000_000L, 2L);
            c.socketTimeout(0);
            assertEquals(4, Types.readVarInt(c.await(0x1B)));
        }
    }

    // ------------------------------------------------------------ A01-04 keep-alive

    @Test
    void unsolicitedKeepAliveId_kicksWithTimeout() throws Exception {
        try (TestClient c = TestClient.join(port, "BadKa")) {
            c.write(0x18, b -> b.writeLong(42L));
            ByteBuf d = c.await(0x1D);   // 플레이 Disconnect
            assertTrue(new String(ByteBufUtil2.bytes(d), StandardCharsets.UTF_8).contains("disconnect.timeout"));
        }
    }

    // ------------------------------------------------------------ A01-05·06 디코드 실패

    @Test
    void truncatedClientInformation_disconnectsWithInternalException_andAppliesNothing() throws Exception {
        try (TestClient c = TestClient.join(port, "BadInfo")) {
            Player p = awaitPlayer("BadInfo");
            String before = p.locale();
            c.write(0x0A, b -> { Types.writeString(b, "xx_yy"); b.writeByte(5); });   // 렌더 거리까지만
            ByteBuf d = c.await(0x1D);
            assertTrue(new String(ByteBufUtil2.bytes(d), StandardCharsets.UTF_8).contains("Internal Exception"));
            assertEquals(before, p.locale());
        }
    }

    @Test
    void completeClientInformation_isApplied() throws Exception {
        try (TestClient c = TestClient.join(port, "GoodInfo")) {
            Player p = awaitPlayer("GoodInfo");
            c.write(0x0A, b -> {
                Types.writeString(b, "ko_kr"); b.writeByte(6); Types.writeVarInt(b, 1); b.writeBoolean(true);
                b.writeByte(0x7F); Types.writeVarInt(b, 0); b.writeBoolean(false); b.writeBoolean(true);
            });
            for (int i = 0; i < 100 && !"ko_kr".equals(p.locale()); i++) Thread.sleep(20);
            assertEquals("ko_kr", p.locale());
        }
    }

    // ------------------------------------------------------------ A01-07 지연 평활화

    @Test
    void firstLatencySample_isSmoothedFromZero() throws Exception {
        try (TestClient c = TestClient.join(port, "Lat")) {
            Player p = awaitPlayer("Lat");
            p.recordLatency(40);
            assertEquals(10, p.latencyMs());   // 바닐라 (0*3+40)/4
            p.recordLatency(40);
            assertEquals(17, p.latencyMs());   // (10*3+40)/4
        }
    }

    private static final class ByteBufUtil2 {
        static byte[] bytes(ByteBuf b) {
            byte[] r = new byte[b.readableBytes()];
            b.duplicate().readBytes(r);
            return r;
        }
    }
}
