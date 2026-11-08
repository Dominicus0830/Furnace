// RCON 인증 전 자리 점유 막기(A02-02 회귀 시험): 인증 마감은 수락 시각 기준 절대 시간이고, 인증 전 로그인 아닌 패킷은 실패로 세며,
// 자리가 꽉 차면 가장 오래된 미인증 연결을 밀어내 올바른 비밀번호의 관리자가 들어올 수 있다.
package org.domi.furnace.server.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class RconAuthDeadlineTest {

    private static void send(OutputStream out, int id, int type, String payload) throws IOException {
        out.write(RconProtocol.encode(id, type, payload));
        out.flush();
    }

    /** 응답 하나를 읽어 id 를 돌려준다. */
    private static int readId(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(in);
        int len = Integer.reverseBytes(d.readInt());
        byte[] b = new byte[len];
        d.readFully(b);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt(0);
    }

    private static RconServer server(InetAddress bind) throws IOException {
        RconServer r = RconServer.open(new ServerSocket(0, 0, bind), "secret", cmd -> "ran:" + cmd);
        r.failDelayMillis(0);
        return r;
    }

    /** 인증 없이 마감 전에 계속 말을 걸어도(바이트를 하나씩 흘려도) 수락 후 마감이 지나면 끊긴다. */
    @Test
    void deadlineIsAbsoluteEvenWhileTrickling() throws Exception {
        RconServer server = server(InetAddress.getLoopbackAddress());
        server.authTimeoutMillis(1500);
        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            s.setSoTimeout(5000);
            byte[] pkt = RconProtocol.encode(1, 9, "x".repeat(40));
            long t0 = System.nanoTime();
            boolean closed = false;
            try {
                for (byte b : pkt) {               // 50바이트 남짓을 200ms 마다 1바이트 — 예전 읽기 기준 제한 시간으로는 끝없이 버틴다
                    s.getOutputStream().write(b);
                    s.getOutputStream().flush();
                    Thread.sleep(200);
                    if (System.nanoTime() - t0 > 4_000_000_000L) break;
                }
                closed = s.getInputStream().read() == -1;
            } catch (IOException e) {
                closed = true;                     // 서버가 닫은 뒤 쓰기는 reset 이 될 수 있다
            }
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertTrue(closed, "마감이 지나도 연결이 살아 있다");
            assertTrue(ms < 4_000, "끊기기까지 " + ms + " ms");
        } finally {
            server.close();
        }
    }

    /** 원래 재현: 인증 없이 종류 9 패킷을 주기적으로 보내면 무기한 남았다. 이제는 마감이 지나면 끊긴다. */
    @Test
    void unauthenticatedClientIsClosedAtDeadline() throws Exception {
        RconServer server = server(InetAddress.getLoopbackAddress());
        server.authTimeoutMillis(1000);
        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            s.setSoTimeout(5000);
            send(s.getOutputStream(), 1, 9, "");
            readId(s.getInputStream());            // "Unknown request 9" — 응답은 바닐라와 같다
            Thread.sleep(700);
            send(s.getOutputStream(), 2, 9, "");
            readId(s.getInputStream());
            assertEquals(-1, s.getInputStream().read());   // 수락 후 1초에 닫힘
        } finally {
            server.close();
        }
    }

    /** 인증한 연결에는 마감이 없다(바닐라처럼 제한 없이 유지). */
    @Test
    void authenticatedClientOutlivesDeadline() throws Exception {
        RconServer server = server(InetAddress.getLoopbackAddress());
        server.authTimeoutMillis(500);
        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            s.setSoTimeout(5000);
            send(s.getOutputStream(), 1, 3, "secret");
            assertEquals(1, readId(s.getInputStream()));
            Thread.sleep(1200);
            send(s.getOutputStream(), 2, 2, "list");
            assertEquals(2, readId(s.getInputStream()));
        } finally {
            server.close();
        }
    }

    /** 인증 전 로그인 아닌 패킷은 응답(-1 / Unknown request)은 그대로 하고 실패로 세서, 연결당 한도에서 끊는다. */
    @Test
    void unauthenticatedRequestsCountAsFailures() throws Exception {
        RconServer server = server(InetAddress.getLoopbackAddress());
        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            s.setSoTimeout(5000);
            for (int i = 0; i < RconServer.MAX_FAILS_PER_CONNECTION; i++) {
                send(s.getOutputStream(), i, i % 2 == 0 ? 2 : 9, "list");
                int id = readId(s.getInputStream());
                assertEquals(i % 2 == 0 ? -1 : i, id);
            }
            assertEquals(-1, s.getInputStream().read());
        } finally {
            server.close();
        }
    }

    private static Socket connectFrom(String local, int port) throws IOException {
        Socket s = new Socket();
        s.bind(new InetSocketAddress(InetAddress.getByName(local), 0));
        s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
        s.setSoTimeout(3000);
        return s;
    }

    /** 원래 재현: 주소 4개(127.0.0.2~5)가 4개씩 자리 16개를 채워도 다른 주소의 관리자는 로그인할 수 있다(가장 오래된 미인증 연결이 밀려난다). */
    @Test
    void fourAddressesCannotLockOutAdmin() throws Exception {
        try (Socket probe = new Socket()) {
            probe.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.2"), 0));
        } catch (IOException e) {
            Assumptions.abort("127.0.0.2 에 바인드할 수 없는 환경: " + e);
        }
        RconServer server = server(InetAddress.getByName("0.0.0.0"));
        List<Socket> held = new ArrayList<>();
        try {
            for (int ip = 2; ip <= 5; ip++) {
                for (int k = 0; k < 4; k++) held.add(connectFrom("127.0.0." + ip, server.port()));
            }
            long end = System.nanoTime() + 5_000_000_000L;
            while (server.clientCount() < RconServer.MAX_CLIENTS && System.nanoTime() < end) Thread.sleep(10);
            assertEquals(RconServer.MAX_CLIENTS, server.clientCount());
            try (Socket admin = connectFrom("127.0.0.9", server.port())) {
                send(admin.getOutputStream(), 1, 3, "secret");
                assertEquals(1, readId(admin.getInputStream()));
                send(admin.getOutputStream(), 2, 2, "list");
                assertEquals(2, readId(admin.getInputStream()));
            }
            assertEquals(-1, held.get(0).getInputStream().read());   // 가장 먼저 들어온 미인증 연결이 밀려났다
        } finally {
            for (Socket s : held) s.close();
            server.close();
        }
    }

    /** 자리가 모두 인증된 연결이면 밀어낼 것이 없어 새 연결을 거절한다(예전 동작). */
    @Test
    void fullOfAuthenticatedClientsStillRefuses() throws Exception {
        RconServer server = server(InetAddress.getLoopbackAddress());
        server.maxClientsPerIp(RconServer.MAX_CLIENTS + 1);
        List<Socket> held = new ArrayList<>();
        try {
            for (int i = 0; i < RconServer.MAX_CLIENTS; i++) {
                Socket s = new Socket(InetAddress.getLoopbackAddress(), server.port());
                s.setSoTimeout(3000);
                send(s.getOutputStream(), i, 3, "secret");
                assertEquals(i, readId(s.getInputStream()));
                held.add(s);
            }
            try (Socket extra = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                extra.setSoTimeout(3000);
                assertEquals(-1, extra.getInputStream().read());
            }
        } finally {
            for (Socket s : held) s.close();
            server.close();
        }
    }
}
