// 서버(바닐라든 Furnace 든)에 오프라인으로 접속해 구성·플레이 단계 패킷을 원본 그대로 파일에 적는다(수동 도구).
// 사용: -Dfurnace.capture.port=<포트> -Dfurnace.capture.out=<파일> [-Dfurnace.capture.seconds=8]
// 파일 형식: 줄마다 "<단계> <id 16진> <본문 길이> <본문 16진>". 압축은 쓰지 않는 서버(network-compression-threshold=-1)를 전제.
package org.domi.furnace.server.net;

import io.netty.buffer.ByteBuf;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class PacketCapture {

    @Test
    void capture() throws Exception {
        String port = System.getProperty("furnace.capture.port");
        Assumptions.assumeTrue(port != null);
        Path out = Path.of(System.getProperty("furnace.capture.out", "capture.txt"));
        int seconds = Integer.getInteger("furnace.capture.seconds", 8);
        java.net.Socket s = new java.net.Socket("127.0.0.1", Integer.parseInt(port));
        s.setSoTimeout(1000);
        var raw = new RawConn(s);
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            raw.write(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost"); b.writeShort(25565); Types.writeVarInt(b, 2); });
            raw.write(0x00, b -> { Types.writeString(b, "Capture"); Types.writeUuid(b, new UUID(0, 0)); });
            String phase = "login";
            long end = Long.MAX_VALUE;
            while (System.nanoTime() < end) {
                ByteBuf f;
                try { f = raw.read(); } catch (java.net.SocketTimeoutException e) { continue; }
                int id = Types.readVarInt(f);
                byte[] body = new byte[f.readableBytes()];
                f.getBytes(f.readerIndex(), body);
                w.println(phase + " " + Integer.toHexString(id) + " " + body.length + " " + HexFormat.of().formatHex(body));
                switch (phase) {
                    case "login" -> {
                        if (id == 0x02) { raw.write(0x03, null); phase = "config"; }
                    }
                    case "config" -> {
                        if (id == 0x0E) raw.write(0x07, b -> Types.writeVarInt(b, 0));          // known packs: 없음
                        else if (id == 0x04) { long v = f.readLong(); raw.write(0x04, b -> b.writeLong(v)); }
                        else if (id == 0x03) { raw.write(0x03, null); phase = "play"; end = System.nanoTime() + seconds * 1_000_000_000L; }
                    }
                    case "play" -> {
                        if (id == 0x26) { long v = f.readLong(); raw.write(0x18, b -> b.writeLong(v)); }
                        else if (id == 0x40) {
                            f.skipBytes(8 * 3 + 4 * 2 + 1);
                            int tid = Types.readVarInt(f);
                            raw.write(0x00, b -> Types.writeVarInt(b, tid));
                        }
                    }
                    default -> { }
                }
            }
        }
        s.close();
    }

    /** 압축·암호화 없는 프레임 읽기/쓰기. */
    static final class RawConn {
        private final java.io.DataInputStream in;
        private final java.io.OutputStream os;

        RawConn(java.net.Socket s) throws java.io.IOException {
            in = new java.io.DataInputStream(new java.io.BufferedInputStream(s.getInputStream()));
            os = s.getOutputStream();
        }

        ByteBuf read() throws java.io.IOException {
            int len = 0, shift = 0, b;
            do { b = in.readUnsignedByte(); len |= (b & 0x7F) << shift; shift += 7; } while ((b & 0x80) != 0);
            byte[] data = new byte[len];
            in.readFully(data);
            return io.netty.buffer.Unpooled.wrappedBuffer(data);
        }

        void write(int id, java.util.function.Consumer<ByteBuf> body) throws java.io.IOException {
            ByteBuf b = io.netty.buffer.Unpooled.buffer();
            Types.writeVarInt(b, id);
            if (body != null) body.accept(b);
            ByteBuf f = io.netty.buffer.Unpooled.buffer();
            Types.writeVarInt(f, b.readableBytes());
            f.writeBytes(b);
            byte[] bytes = new byte[f.readableBytes()];
            f.readBytes(bytes);
            os.write(bytes);
            os.flush();
        }
    }
}
