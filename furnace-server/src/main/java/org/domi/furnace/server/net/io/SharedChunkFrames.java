// 같은 청크 본문(같은 청크·같은 modCount 의 캐시 byte[])을 여러 플레이어에게 보낼 때 압축을 한 번만 한다.
// 패킷마다 독립된 zlib 스트림이라 결과 바이트는 연결별 CompressionEncoder 출력과 똑같다.
package org.domi.furnace.server.net.io;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import java.util.Map;
import java.util.WeakHashMap;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;

public final class SharedChunkFrames {

    private SharedChunkFrames() {}

    private record Frame(int threshold, int level, byte[] bytes) {}

    // 키는 본문 배열 자체(배열의 equals/hashCode 는 동일성). 본문 캐시가 비워지고 전송이 끝나면 약참조라 같이 사라진다.
    private static final Map<byte[], Frame> CACHE = new WeakHashMap<>();

    /** 공유 프레임 생성(압축) 횟수 — 테스트·벤치용. */
    public static final java.util.concurrent.atomic.AtomicLong ENCODES = new java.util.concurrent.atomic.AtomicLong();

    /** 채널 이벤트 루프에서 부른다. 보낼 메시지(ByteBuf 또는 Precompressed)를 만든다. */
    public static Object encode(Channel ch, Packet packet) {
        if (packet instanceof LevelChunkWithLight l && l.shared() != null
                && ch.pipeline().get("compress") instanceof CompressionEncoder enc) {
            byte[] frame = frame(enc, l.shared());
            l.body().release();
            return new CompressionEncoder.Precompressed(Unpooled.wrappedBuffer(frame));
        }
        ByteBuf buf = ch.alloc().buffer();
        Types.writeVarInt(buf, packet.id());
        packet.write(buf);
        return buf;
    }

    static byte[] frame(CompressionEncoder enc, byte[] body) {
        Frame f;
        synchronized (CACHE) {
            f = CACHE.get(body);
        }
        if (f != null && f.threshold == enc.threshold() && f.level == enc.level()) return f.bytes;
        byte[] packet = new byte[body.length + 1];
        packet[0] = (byte) LevelChunkWithLight.ID;   // 0x27 < 0x80 이라 VarInt 1바이트
        System.arraycopy(body, 0, packet, 1, body.length);
        byte[] bytes = enc.encodeToBytes(packet);
        ENCODES.incrementAndGet();
        synchronized (CACHE) {
            CACHE.put(body, new Frame(enc.threshold(), enc.level(), bytes));
        }
        return bytes;
    }
}
