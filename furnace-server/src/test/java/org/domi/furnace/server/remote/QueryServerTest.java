package org.domi.furnace.server.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** 실제 UDP 루프백으로 핸드셰이크 → basic/full 상태 왕복. */
class QueryServerTest {

    private static final QueryHandler.Info INFO = new QueryHandler.Info() {
        @Override public String motd() { return "Hello"; }
        @Override public String levelName() { return "world"; }
        @Override public String version() { return "1.21.1"; }
        @Override public String plugins() { return ""; }
        @Override public int playerCount() { return 1; }
        @Override public int maxPlayers() { return 8; }
        @Override public int hostPort() { return 25565; }
        @Override public String hostIp() { return "127.0.0.1"; }
        @Override public String[] playerNames() { return new String[] {"Alex"}; }
    };

    private static byte[] exchange(DatagramSocket c, int port, byte[] req) throws Exception {
        c.send(new DatagramPacket(req, req.length, InetAddress.getLoopbackAddress(), port));
        byte[] buf = new byte[1460];
        DatagramPacket in = new DatagramPacket(buf, buf.length);
        c.receive(in);
        return java.util.Arrays.copyOf(buf, in.getLength());
    }

    @Test
    void handshakeThenBasicAndFull() throws Exception {
        DatagramSocket srvSock = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        try (QueryServer q = QueryServer.open(srvSock, INFO); DatagramSocket c = new DatagramSocket()) {
            c.setSoTimeout(5000);
            byte[] hs = exchange(c, q.port(), new byte[] {(byte) 0xFE, (byte) 0xFD, 9, 0, 0, 0, 1});
            assertEquals('\t', hs[0]);
            String text = new String(hs, 1, hs.length - 2, StandardCharsets.ISO_8859_1);   // 세션 id 4글자 + 토큰 숫자
            int token = Integer.parseInt(text.substring(4));
            byte[] req = new byte[15];
            req[0] = (byte) 0xFE;
            req[1] = (byte) 0xFD;
            req[6] = 1;
            req[7] = (byte) (token >>> 24);
            req[8] = (byte) (token >>> 16);
            req[9] = (byte) (token >>> 8);
            req[10] = (byte) token;
            byte[] full = exchange(c, q.port(), req);
            String body = new String(full, 5, full.length - 5, StandardCharsets.ISO_8859_1);
            assertTrue(body.contains("hostname\u0000Hello\u0000"), body);
            assertTrue(body.contains("numplayers\u00001\u0000"), body);
            assertTrue(body.endsWith("player_\u0000\u0000Alex\u0000\u0000"), body);
            byte[] basic = exchange(c, q.port(), java.util.Arrays.copyOf(req, 11));
            assertEquals(0, basic[0]);
            assertTrue(new String(basic, 5, basic.length - 5, StandardCharsets.ISO_8859_1).startsWith("Hello\u0000SMP\u0000world\u0000"));
        }
    }
}
