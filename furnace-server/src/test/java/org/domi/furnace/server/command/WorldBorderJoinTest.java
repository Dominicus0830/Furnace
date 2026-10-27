// 실제 소켓 접속: 접속 시 initialize_border 가 현재 보더 상태를 싣고, /worldborder 변경이 바닐라 리스너처럼 접속자에게 방송된다.
package org.domi.furnace.server.command;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.net.NetworkServer;
import org.domi.furnace.server.net.ServerLoginContext;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldBorderJoinTest {

    @TempDir Path tmp;

    @Test
    void joinSendsLiveBorderAndChangesBroadcast() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        NetworkServer server = null;
        try {
            ServerCore core = VanillaCommandTreeTest.core(tmp, sched);
            core.border().setCenter(100.0, -50.0);
            core.border().setSize(1000.0);
            core.border().setWarningBlocks(9);
            core.border().setWarningTime(20);
            ServerLoginContext ctx = ServerLoginContext.offline("t").core(core);
            server = new NetworkServer(0, -1, ctx, sched, core.world(), new EntityIdAllocator());
            int port = server.start();
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
                s.setSoTimeout(15000);
                OutputStream out = s.getOutputStream();
                InputStream in = s.getInputStream();
                write(out, 0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost"); b.writeShort(25565); Types.writeVarInt(b, 2); });
                write(out, 0x00, b -> { Types.writeString(b, "Tester"); Types.writeUuid(b, new UUID(0, 0)); });
                assertEquals(0x02, Types.readVarInt(read(in)), "Login Success");
                write(out, 0x03, null);
                write(out, 0x07, b -> Types.writeVarInt(b, 0));
                int id;
                do { id = Types.readVarInt(read(in)); } while (id != 0x03);
                write(out, 0x03, null);

                ByteBuf init = awaitPacket(in, 0x25);
                assertEquals(100.0, init.readDouble());
                assertEquals(-50.0, init.readDouble());
                assertEquals(1000.0, init.readDouble());
                assertEquals(1000.0, init.readDouble());
                assertEquals(0, init.readByte());
                assertEquals(29999984, Types.readVarInt(init));
                assertEquals(9, Types.readVarInt(init));
                assertEquals(20, Types.readVarInt(init));

                CommandSource console = CommandSource.console(core);
                core.commands().performPrefixedCommandAsync(console, "worldborder damage amount 3").join();   // 아무것도 보내지 않는다
                core.commands().performPrefixedCommandAsync(console, "worldborder damage buffer 1").join();
                core.commands().performPrefixedCommandAsync(console, "worldborder set 500").join();
                ByteBuf size = awaitPacket(in, 0x4F);
                assertEquals(500.0, size.readDouble());
                core.commands().performPrefixedCommandAsync(console, "worldborder set 100 30").join();
                ByteBuf lerp = awaitPacket(in, 0x4E);
                // 바닐라처럼 패킷은 보내는 순간의 보간 크기·남은 시간을 담는다: 명령과 전송 사이 몇 ms 가 흐르면 조금 줄어 있다.
                double from = lerp.readDouble();
                assertTrue(from <= 500.0 && from > 480.0, "보간 시작 크기: " + from);
                assertEquals(100.0, lerp.readDouble());
                long remaining = readVarLong(lerp);
                assertTrue(remaining <= 30_000L && remaining > 28_000L, "남은 보간 시간: " + remaining);
                core.commands().performPrefixedCommandAsync(console, "worldborder center 1.0 2.0").join();
                ByteBuf center = awaitPacket(in, 0x4D);
                assertEquals(1.0, center.readDouble());
                assertEquals(2.0, center.readDouble());
                core.commands().performPrefixedCommandAsync(console, "worldborder warning time 7").join();
                assertEquals(7, Types.readVarInt(awaitPacket(in, 0x50)));
                core.commands().performPrefixedCommandAsync(console, "worldborder warning distance 4").join();
                assertEquals(4, Types.readVarInt(awaitPacket(in, 0x51)));
            }
        } finally {
            if (server != null) server.stop();
            sched.close();
        }
    }

    /** 원하는 id 의 패킷이 올 때까지 읽는다(그 사이 패킷은 버림). 중간에 오는 다른 보더 패킷이 순서를 깨면 안 되므로 id 만 본다. */
    private static ByteBuf awaitPacket(InputStream in, int wanted) throws Exception {
        while (true) {
            ByteBuf f = read(in);
            if (Types.readVarInt(f) == wanted) return f;
        }
    }

    private static long readVarLong(ByteBuf b) {
        long v = 0;
        int shift = 0;
        byte cur;
        do {
            cur = b.readByte();
            v |= (long) (cur & 0x7F) << shift;
            shift += 7;
        } while ((cur & 0x80) != 0);
        return v;
    }

    private static void write(OutputStream out, int id, Consumer<ByteBuf> body) throws Exception {
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

    private static ByteBuf read(InputStream in) throws Exception {
        DataInputStream d = new DataInputStream(in);
        int len = 0, shift = 0, b;
        do {
            b = d.readUnsignedByte();
            len |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        byte[] data = new byte[len];
        d.readFully(data);
        return Unpooled.wrappedBuffer(data);
    }
}
