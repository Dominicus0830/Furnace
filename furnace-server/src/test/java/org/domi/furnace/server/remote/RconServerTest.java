package org.domi.furnace.server.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 실제 루프백 소켓으로 RCON 프로토콜 왕복을 확인한다(명령 실행기는 스텁). */
class RconServerTest {

    private RconServer server;
    private final List<String> ran = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        ServerSocket s = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        server = RconServer.open(s, "secret", cmd -> {
            ran.add(cmd);
            if (cmd.equals("boom")) throw new IllegalStateException("bad");
            if (cmd.equals("long")) return "z".repeat(5000);
            if (cmd.equals("empty")) return "";
            return "ran:" + cmd;
        });
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private record Reply(int id, int type, String body) {}

    private static void send(OutputStream out, int id, int type, String payload) throws IOException {
        out.write(RconProtocol.encode(id, type, payload));
        out.flush();
    }

    private static Reply read(DataInputStream in) throws IOException {
        int len = Integer.reverseBytes(in.readInt());
        byte[] b = new byte[len];
        in.readFully(b);
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        return new Reply(bb.getInt(0), bb.getInt(4), new String(b, 8, len - 10, StandardCharsets.UTF_8));
    }

    private Socket connect() throws IOException {
        Socket s = new Socket(InetAddress.getLoopbackAddress(), server.port());
        s.setSoTimeout(5000);
        return s;
    }

    @Test
    void loginThenCommand() throws Exception {
        try (Socket s = connect()) {
            DataInputStream in = new DataInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            send(out, 11, 3, "secret");
            assertEquals(new Reply(11, 2, ""), read(in));
            send(out, 12, 2, "say hi");
            assertEquals(new Reply(12, 0, "ran:say hi"), read(in));
            assertEquals(List.of("say hi"), ran);
        }
    }

    @Test
    void wrongPasswordRepliesMinusOneAndCommandsStayLocked() throws Exception {
        try (Socket s = connect()) {
            DataInputStream in = new DataInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            send(out, 1, 3, "nope");
            assertEquals(new Reply(-1, 2, ""), read(in));
            send(out, 2, 2, "stop");
            assertEquals(new Reply(-1, 2, ""), read(in));   // 인증 전 명령도 -1, 연결은 유지
            send(out, 3, 3, "");   // 빈 비밀번호는 실패
            assertEquals(new Reply(-1, 2, ""), read(in));
            assertTrue(ran.isEmpty());
        }
    }

    @Test
    void commandBeforeLoginIsRejected() throws Exception {
        try (Socket s = connect()) {
            DataInputStream in = new DataInputStream(s.getInputStream());
            send(s.getOutputStream(), 5, 2, "list");
            assertEquals(new Reply(-1, 2, ""), read(in));
            assertTrue(ran.isEmpty());
        }
    }

    @Test
    void longResponseIsSplitIntoSeveralPackets() throws Exception {
        try (Socket s = connect()) {
            DataInputStream in = new DataInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            send(out, 1, 3, "secret");
            read(in);
            send(out, 9, 2, "long");
            Reply a = read(in), b = read(in);
            assertEquals(9, a.id());
            assertEquals(4096, a.body().length());
            assertEquals(9, b.id());
            assertEquals(904, b.body().length());
        }
    }

    @Test
    void emptyResponseStillSendsOnePacket() throws Exception {
        try (Socket s = connect()) {
            DataInputStream in = new DataInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            send(out, 1, 3, "secret");
            read(in);
            send(out, 4, 2, "empty");
            assertEquals(new Reply(4, 0, ""), read(in));
        }
    }

    @Test
    void executorExceptionAndUnknownType() throws Exception {
        try (Socket s = connect()) {
            DataInputStream in = new DataInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            send(out, 1, 3, "secret");
            read(in);
            send(out, 6, 2, "boom");
            assertEquals(new Reply(6, 0, "Error executing: boom (bad)"), read(in));
            send(out, 7, 0x2a, "x");
            assertEquals(new Reply(7, 0, "Unknown request 2a"), read(in));
        }
    }

    @Test
    void oversizedOrTinyLengthClosesConnection() throws Exception {
        for (int bad : new int[] {5, 100000}) {
            try (Socket s = connect()) {
                s.getOutputStream().write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bad).array());
                s.getOutputStream().flush();
                assertEquals(-1, s.getInputStream().read());
            }
        }
    }

    @Test
    void authenticationIsPerConnection() throws Exception {
        try (Socket a = connect(); Socket b = connect()) {
            DataInputStream ia = new DataInputStream(a.getInputStream()), ib = new DataInputStream(b.getInputStream());
            send(a.getOutputStream(), 1, 3, "secret");
            read(ia);
            send(b.getOutputStream(), 2, 2, "list");   // b 는 로그인 안 함
            assertEquals(-1, read(ib).id());
            send(a.getOutputStream(), 3, 2, "list");
            assertEquals(new Reply(3, 0, "ran:list"), read(ia));
        }
    }

    @Test
    void createRefusesBadPortAndEmptyPassword() {
        assertNull(RconServer.create("", 0, "pw", c -> ""));
        assertNull(RconServer.create("", 70000, "pw", c -> ""));
        assertNull(RconServer.create("", 25575, "", c -> ""));
    }
}
