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

    /**
     * 채널 이벤트 루프에서 부른다. 보낼 메시지(ByteBuf 또는 Precompressed)를 만든다.
     * 플러그인 패킷 리스너가 붙은 연결이면 먼저 거친다 — 취소되면 null(보내지 않는다).
     */
    public static Object encode(Channel ch, Packet packet) {
        org.domi.furnace.server.net.PacketTap tap = ch.attr(org.domi.furnace.server.net.PacketTap.KEY).get();
        if (tap != null) {
            Packet out;
            Nested outer = NESTED.get();
            Nested mine = new Nested(ch);
            NESTED.set(mine);
            try {
                out = tap.outbound(packet);
            } finally {
                NESTED.set(outer);
            }
            mine.writeAll();   // 리스너 안에서 이 연결로 보낸 것은 지금 패킷(묶음이면 그 묶음) 앞에
            if (out == null) {
                packet.discard();
                return null;
            }
            packet = out;
        }
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

    // ---- 리스너 안에서 보낸 패킷 (ProtocolLib 2b-2 검토 R2) ----
    // 패킷 리스너(플러그인·ProtocolLib 다리)가 그 연결로 다시 보내면 예전엔 이벤트 루프 큐 맨 뒤(지금 쓰는 패킷·묶음과 그 뒤 작업들 다음)로 갔다.
    // Paper+ProtocolLib 에서는 리스너가 바깥 쓰기 작업 안에서 돌고, 안에서 보낸 것은 먼저 실제 루프로 나간다 — 그래서 "스폰을 취소하고 바꾼 것을
    // 다시 보내기"가 엔티티 → 메타데이터 순서로 도착한다. 같은 순서가 되게, 리스너가 도는 동안 같은 연결의 루프 작업을 모았다가 지금 패킷을 쓰기 전에 돌린다.

    private static final ThreadLocal<Nested> NESTED = new ThreadLocal<>();

    private static final class Nested {
        final Channel channel;
        java.util.List<Runnable> tasks;

        Nested(Channel channel) { this.channel = channel; }

        void writeAll() {
            if (tasks == null) return;
            for (Runnable t : tasks) {
                try {
                    t.run();
                } catch (RuntimeException e) {   // 루프 작업 하나가 던진 것처럼 — 바깥 쓰기는 계속
                    org.slf4j.LoggerFactory.getLogger(SharedChunkFrames.class).warn("[NET] 패킷 리스너 안에서 보낸 작업 실패", e);
                }
            }
            tasks = null;
        }
    }

    /**
     * 그 연결의 이벤트 루프에서, 그 연결 패킷 리스너가 도는 중이면 작업을 모아 두고 true(지금 패킷 앞에 쓴다). 아니면 false — 부른 쪽이 루프에 넣는다.
     */
    public static boolean deferInsideListener(Channel ch, Runnable task) {
        Nested n = NESTED.get();
        if (n == null || n.channel != ch || !ch.eventLoop().inEventLoop()) return false;
        if (n.tasks == null) n.tasks = new java.util.ArrayList<>(2);
        n.tasks.add(task);
        return true;
    }

    static byte[] frame(CompressionEncoder enc, byte[] body) {
        Frame f;
        synchronized (CACHE) {
            f = CACHE.get(body);
        }
        if (f != null && f.threshold == enc.threshold() && f.level == enc.level()) return f.bytes;
        return compressAndCache(enc, body);
    }

    private static byte[] compressAndCache(CompressionEncoder enc, byte[] body) {
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

    // ---- 미리 압축 (M12) ----
    // 본문이 만들어지는 청크 풀 스레드에서 한 번 압축해 캐시에 넣어 둔다. 예전엔 각 연결의 이벤트 루프가 처음 보낼 때 압축했는데,
    // 여러 플레이어가 같은 청크를 거의 동시에 보내면 서로 다른 이벤트 루프가 캐시 미적중 상태로 같은 본문을 각자 압축했다(플레이어 수에 비례해
    // 중복이 늘었다). 압축 결과 바이트는 연결별 인코더와 같다(같은 임계값·레벨, 패킷마다 초기화되는 zlib).

    private static volatile int precompressThreshold = -1;

    /** 서버가 켜질 때 네트워크 압축 임계값을 알린다(음수면 미리 압축 안 함). 임계값이 다른 연결은 예전처럼 이벤트 루프에서 압축한다. */
    public static void precompressThreshold(int threshold) { precompressThreshold = threshold; }

    /** 이 임계값이 지금 켜진 값일 때만 끈다(한 JVM 에 서버가 여럿일 때 남의 값을 지우지 않게). */
    public static void clearPrecompressThreshold(int threshold) {
        if (precompressThreshold == threshold) precompressThreshold = -1;
    }

    private record PreEnc(int threshold, CompressionEncoder enc) {}

    // 압축 중 쓰는 Deflater 를 스레드마다 한 개 재사용한다(파이프라인에 붙지 않은 인코더 — 이 스레드에서만 쓴다).
    private static final ThreadLocal<PreEnc> PRE = new ThreadLocal<>();

    /** 청크 본문을 미리 압축해 공유 캐시에 넣는다. 켜져 있지 않으면(임계값 음수) 아무것도 안 한다. 어느 스레드에서나 부를 수 있다. */
    public static void precompress(byte[] body) {
        int threshold = precompressThreshold;
        if (threshold < 0) return;
        PreEnc pe = PRE.get();
        if (pe == null || pe.threshold != threshold) {
            pe = new PreEnc(threshold, new CompressionEncoder(threshold));
            PRE.set(pe);
        }
        try {
            compressAndCache(pe.enc, body);
        } catch (RuntimeException e) {
            // 미리 압축은 최적화일 뿐이다 — 실패해도 본문 직렬화는 성공해야 하고, 보낼 때 이벤트 루프가 예전처럼 압축한다.
            org.slf4j.LoggerFactory.getLogger(SharedChunkFrames.class).warn("[NET] 청크 미리 압축 실패 — 보낼 때 압축한다", e);
        }
    }
}
