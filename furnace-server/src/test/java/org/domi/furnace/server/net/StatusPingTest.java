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
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class StatusPingTest {

    @Test
    void handshakeThenStatusRequest_returnsJsonWithProtocol767() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 2, reg.directBits(), 7);
        NetworkServer server = new NetworkServer(0, -1, "Furnace Server", sched,
                world, new EntityIdAllocator());
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
            resp.release();
        } finally {
            server.stop();
            sched.close();
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
        framed.release();
        body.release(); // 호출부가 소유권을 넘김
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
