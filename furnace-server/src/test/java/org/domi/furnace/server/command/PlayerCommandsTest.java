// 실제 소켓 접속: 접속 시 권한 이벤트·명령 트리를 받고, chat_command 실행 결과가 시스템 채팅으로, 자동완성 요청이 응답으로 온다.
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
import org.domi.furnace.server.players.GameProfile;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerCommandsTest {

    @TempDir Path tmp;

    @Test
    void joinReceivesTreeAndCommandsRun() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        NetworkServer server = null;
        try {
            ServerCore core = VanillaCommandTreeTest.core(tmp, sched);
            core.lists().op(GameProfile.offline("Tester"), 4);
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
                write(out, 0x03, null);                                     // Login Acknowledged
                write(out, 0x07, b -> Types.writeVarInt(b, 0));              // Known Packs
                int id;
                do { id = Types.readVarInt(read(in)); } while (id != 0x03); // Finish Configuration
                write(out, 0x03, null);                                     // Ack Finish
                boolean sawEvent = false, sawTree = false;
                while (!(sawEvent && sawTree)) {
                    ByteBuf f = read(in);
                    int pid = Types.readVarInt(f);
                    if (pid == 0x1F) { f.readInt(); assertEquals(28, f.readByte(), "OP 4 → 이벤트 28"); sawEvent = true; }
                    if (pid == 0x11) sawTree = true;
                }
                write(out, 0x04, b -> Types.writeString(b, "list"));
                assertTrue(awaitSystemChat(in).contains("commands.list.players"));
                write(out, 0x04, b -> Types.writeString(b, "nope"));
                assertTrue(awaitSystemChat(in).contains("command.unknown.command"));
                write(out, 0x0B, b -> { Types.writeVarInt(b, 42); Types.writeString(b, "/whitelist add "); });
                ByteBuf resp;
                do { resp = read(in); } while (Types.readVarInt(resp) != 0x10);
                assertEquals(42, Types.readVarInt(resp), "자동완성 transaction id");
            }
        } finally {
            if (server != null) server.stop();
            sched.close();
        }
    }

    /** 다음 시스템 채팅(0x6C)의 NBT 내용을 대충 문자열로(번역 키 확인용). */
    private static String awaitSystemChat(InputStream in) throws Exception {
        while (true) {
            ByteBuf f = read(in);
            if (Types.readVarInt(f) == 0x6C) {
                byte[] rest = new byte[f.readableBytes()];
                f.readBytes(rest);
                return new String(rest, java.nio.charset.StandardCharsets.ISO_8859_1);
            }
        }
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
