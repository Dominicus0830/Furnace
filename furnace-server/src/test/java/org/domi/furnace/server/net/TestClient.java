// 테스트용 최소 클라이언트: 오프라인 로그인 → 구성 → 플레이 진입, 프레임 읽기/쓰기, 특정 패킷 기다리기.
package org.domi.furnace.server.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.domi.furnace.server.net.io.Types;

public final class TestClient implements AutoCloseable {

    private final Socket socket;
    private final OutputStream out;
    private final DataInputStream in;
    public UUID uuid;

    private TestClient(Socket socket) throws Exception {
        this.socket = socket;
        this.out = socket.getOutputStream();
        this.in = new DataInputStream(socket.getInputStream());
    }

    /** 연결만 연다(핸드셰이크부터 시험이 직접 보낸다). */
    public static TestClient open(int port) throws Exception {
        Socket s = new Socket();
        s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
        s.setSoTimeout(15000);
        return new TestClient(s);
    }

    /** 접속해 플레이 단계까지 들어간다(LoginPlay 를 받을 때까지 읽는다). */
    public static TestClient join(int port, String name) throws Exception {
        Socket s = new Socket();
        s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
        s.setSoTimeout(15000);
        TestClient c = new TestClient(s);
        c.write(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost"); b.writeShort(25565); Types.writeVarInt(b, 2); });
        c.write(0x00, b -> { Types.writeString(b, name); Types.writeUuid(b, new UUID(0, 0)); });
        ByteBuf success = c.await(0x02);
        c.uuid = Types.readUuid(success);
        c.write(0x03, null);
        c.write(0x07, b -> Types.writeVarInt(b, 0));
        c.await(0x03);
        c.write(0x03, null);
        c.await(0x2B);
        return c;
    }

    /** 읽기 대기 한도(밀리초)를 바꾼다. 일부러 아무것도 안 오는 것을 확인할 때 짧게. */
    public void socketTimeout(int millis) throws Exception { socket.setSoTimeout(millis == 0 ? 15000 : millis); }

    public void write(int id, Consumer<ByteBuf> body) throws Exception {
        ByteBuf b = Unpooled.buffer();
        Types.writeVarInt(b, id);
        if (body != null) body.accept(b);
        ByteBuf f = Unpooled.buffer();
        Types.writeVarInt(f, b.readableBytes());
        f.writeBytes(b);
        byte[] bytes = new byte[f.readableBytes()];
        f.readBytes(bytes);
        synchronized (out) {
            out.write(bytes);
            out.flush();
        }
    }

    /** 다음 프레임(패킷 id 포함). */
    public ByteBuf read() throws Exception {
        int len = 0, shift = 0, b;
        do {
            b = in.readUnsignedByte();
            len |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        byte[] data = new byte[len];
        in.readFully(data);
        return Unpooled.wrappedBuffer(data);
    }

    /** id 인 패킷을 받을 때까지 읽고, id 를 읽은 뒤의 본문을 돌려준다. */
    public ByteBuf await(int id) throws Exception { return await(id, b -> true); }

    public ByteBuf await(int id, Predicate<ByteBuf> match) throws Exception {
        while (true) {
            ByteBuf f = read();
            if (Types.readVarInt(f) != id) continue;
            ByteBuf view = f.slice();
            if (match.test(view.duplicate())) return view;
        }
    }

    @Override
    public void close() throws Exception { socket.close(); }
}
