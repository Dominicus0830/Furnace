// handshake→status 핑 경로를 실제 소켓으로 검증(JSON에 protocol 767 포함)
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class StatusPingTest {

    @Test
    void handshakeThenStatusRequest_returnsJsonWithProtocol767() throws Exception {
        NetworkServer server = new NetworkServer(0, -1, "Furnace Server");
        int port = server.start();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();

            ByteBuf hs = Unpooled.buffer();
            Types.writeVarInt(hs, 0x00);
            Types.writeVarInt(hs, 767);
            Types.writeString(hs, "127.0.0.1");
            hs.writeShort(port);
            Types.writeVarInt(hs, 1);
            writeFrame(out, hs);

            ByteBuf req = Unpooled.buffer();
            Types.writeVarInt(req, 0x00);
            writeFrame(out, req);

            int frameLen = readVarInt(in);
            byte[] body = in.readNBytes(frameLen);
            ByteBuf resp = Unpooled.wrappedBuffer(body);
            int id = Types.readVarInt(resp);
            assertEquals(0x00, id);
            String json = Types.readString(resp, 32767);
            assertTrue(json.contains("\"protocol\":767"), json);
            assertTrue(json.contains("Furnace Server"), json);
        } finally {
            server.stop();
        }
    }

    private static void writeFrame(OutputStream out, ByteBuf body) throws Exception {
        ByteBuf framed = Unpooled.buffer();
        Types.writeVarInt(framed, body.readableBytes());
        framed.writeBytes(body);
        byte[] arr = new byte[framed.readableBytes()];
        framed.readBytes(arr);
        out.write(arr);
        out.flush();
    }

    private static int readVarInt(InputStream in) throws Exception {
        int value = 0, position = 0, b;
        do {
            b = in.read();
            if (b == -1) throw new IllegalStateException("EOF");
            value |= (b & 0x7F) << position;
            position += 7;
        } while ((b & 0x80) != 0);
        return value;
    }
}
