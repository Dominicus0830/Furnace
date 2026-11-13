// 나가는 패킷 녹음기: 시험 채널(EmbeddedChannel·ConcurrentEmbeddedChannel)에서 나간 메시지를 꺼내 (패킷 id, 본문 바이트) 열로 모은다.
// 옛·새 구현이 같은 바이트를 같은 순서로 보냈는지 대조하고(assertSame), 해시·사람이 읽을 줄로 금 파일에 남긴다(O15 보고서 3.4).
//
// 채널 파이프라인에 따라 꺼낸 바이트의 모양이 다르다 — Wire 로 알려 준다:
//   PLAIN             [id][본문]                          인코더 없는 시험 채널(대부분의 시험)
//   COMPRESSED        [VarInt 원래 길이 | 0][zlib 또는 원본]  CompressionEncoder 만
//   FRAMED            [VarInt 길이][id][본문] 여러 개         FrameEncoder 만
//   FRAMED_COMPRESSED [VarInt 길이][압축 형식] 여러 개        실서버와 같은 순서(압축 → 프레임)
// 압축된 것은 **풀어서** 비교한다: 압축 바이트는 zlib 수준·임계값·버퍼 재사용에 따라 달라질 수 있지만 푼 결과는 같아야 한다
// (O03-04·05 처럼 압축 경로를 바꾸는 최적화의 증명). 압축 바이트 자체를 비교하려면 rawFrames() 를 쓴다.
//
// 스레드: EmbeddedEventLoop 큐는 채널을 만지는 스레드에서만 꺼낸다(ChunkSendPacketOrderGoldenTest 의 drain 처럼 region 스레드 안에서).
package org.domi.furnace.server.identity;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.domi.furnace.server.net.io.CompressionEncoder;
import org.domi.furnace.server.net.io.Types;

public final class PacketTape {

    public enum Wire { PLAIN, COMPRESSED, FRAMED, FRAMED_COMPRESSED }

    /** 패킷 하나: id 와 id 뒤의 본문 바이트. */
    public record Packet(int id, byte[] body) {
        @Override
        public boolean equals(Object o) { return o instanceof Packet p && p.id == id && Arrays.equals(p.body, body); }

        @Override
        public int hashCode() { return 31 * id + Arrays.hashCode(body); }

        @Override
        public String toString() { return "Packet[id=0x" + Integer.toHexString(id) + ", " + body.length + "B]"; }

        public long hash() { return new IdentityHash().add(id).add(body).value(); }
    }

    private final List<Packet> packets = new ArrayList<>();
    private final List<byte[]> rawFrames = new ArrayList<>();

    /** 채널의 대기 작업을 돌리고 나간 메시지를 모두 꺼내 붙인다. 붙인 패킷 수를 돌려준다. 꺼낸 ByteBuf 는 놓아준다. */
    public int drain(EmbeddedChannel ch, Wire wire) {
        ch.runPendingTasks();
        int before = packets.size();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            if (o instanceof CompressionEncoder.Precompressed p) {
                // 압축 인코더를 거치지 않은 미리 압축한 프레임: 언제나 [VarInt 원래 길이][zlib] 라 wire 와 무관하게 압축 형식으로 푼다
                append(p.payload(), Wire.COMPRESSED);
            } else if (o instanceof ByteBuf b) {
                append(b, wire);
            } else {
                throw new IllegalStateException("ByteBuf 가 아닌 나가는 메시지: " + o.getClass().getName());
            }
        }
        return packets.size() - before;
    }

    private void append(ByteBuf b, Wire wire) {
        try {
            byte[] bytes = new byte[b.readableBytes()];
            b.getBytes(b.readerIndex(), bytes);
            rawFrames.add(bytes);
            packets.addAll(decode(bytes, wire));
        } finally {
            b.release();
        }
    }

    /** 꺼낸 바이트 한 덩어리를 패킷 목록으로 푼다. */
    public static List<Packet> decode(byte[] bytes, Wire wire) {
        ByteBuf in = Unpooled.wrappedBuffer(bytes);
        List<Packet> out = new ArrayList<>();
        switch (wire) {
            case PLAIN -> out.add(plain(in));
            case COMPRESSED -> out.add(plain(Unpooled.wrappedBuffer(inflate(in))));
            case FRAMED, FRAMED_COMPRESSED -> {
                while (in.isReadable()) {
                    int len = Types.readVarInt(in);
                    if (len < 0 || len > in.readableBytes()) throw new IllegalStateException("프레임 길이 " + len + " > 남은 " + in.readableBytes());
                    ByteBuf frame = in.readSlice(len);
                    out.add(wire == Wire.FRAMED ? plain(frame) : plain(Unpooled.wrappedBuffer(inflate(frame))));
                }
            }
        }
        return out;
    }

    private static Packet plain(ByteBuf in) {
        int id = Types.readVarInt(in);
        byte[] body = new byte[in.readableBytes()];
        in.readBytes(body);
        return new Packet(id, body);
    }

    /** [VarInt 원래 길이(0 이면 비압축)][본문] → 원래 [id][본문]. */
    private static byte[] inflate(ByteBuf in) {
        int dataLen = Types.readVarInt(in);
        byte[] rest = new byte[in.readableBytes()];
        in.readBytes(rest);
        if (dataLen == 0) return rest;
        Inflater inf = new Inflater();
        try {
            inf.setInput(rest);
            byte[] out = new byte[dataLen];
            int n = 0;
            while (n < dataLen) {
                int got = inf.inflate(out, n, dataLen - n);
                if (got == 0 && (inf.finished() || inf.needsInput() || inf.needsDictionary())) break;
                n += got;
            }
            if (n != dataLen || !inf.finished()) {
                throw new IllegalStateException("압축 해제 길이 " + n + " != 표시 " + dataLen + (inf.finished() ? "" : " (스트림 안 끝남)"));
            }
            if (inf.getRemaining() != 0) throw new IllegalStateException("zlib 스트림 뒤에 남은 바이트 " + inf.getRemaining());
            return out;
        } catch (DataFormatException e) {
            throw new IllegalStateException("zlib 형식 오류", e);
        } finally {
            inf.end();
        }
    }

    public List<Packet> packets() { return Collections.unmodifiableList(packets); }

    /** 꺼낸 바이트 그대로(압축·프레임 포함). 압축 결과 바이트까지 같아야 할 때. */
    public List<byte[]> rawFrames() { return Collections.unmodifiableList(rawFrames); }

    public int size() { return packets.size(); }

    public void clear() { packets.clear(); rawFrames.clear(); }

    public int[] ids() { return packets.stream().mapToInt(Packet::id).toArray(); }

    /** 고른 id 의 패킷만 남긴 새 녹음(원본 바이트 목록은 비운다). */
    public PacketTape only(IntPredicate ids) {
        PacketTape t = new PacketTape();
        for (Packet p : packets) if (ids.test(p.id)) t.packets.add(p);
        return t;
    }

    /** 순서·id·본문 바이트 모두를 섞은 해시. */
    public long hash() {
        IdentityHash h = new IdentityHash();
        h.add(packets.size());
        for (Packet p : packets) h.add(p.id).add(p.body);
        return h.value();
    }

    /** 금 파일용 줄: "번호 이름 길이 해시". 이름 함수가 null 을 주면 0x id. */
    public List<String> lines(IntFunction<String> names) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < packets.size(); i++) {
            Packet p = packets.get(i);
            String n = names == null ? null : names.apply(p.id);
            out.add(i + " " + (n != null ? n : "0x" + Integer.toHexString(p.id)) + " " + p.body.length + " " + IdentityHash.hex(p.hash()));
        }
        return out;
    }

    /** 두 녹음이 같은 패킷을 같은 순서로 담아야 한다. 다르면 첫 차이(번호·id·첫 다른 바이트 위치)를 알려 준다. */
    public static void assertSame(PacketTape expected, PacketTape actual) {
        List<Packet> a = expected.packets, b = actual.packets;
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            Packet x = a.get(i), y = b.get(i);
            if (x.equals(y)) continue;
            if (x.id != y.id) {
                throw new AssertionError("패킷 " + i + " id 가 다르다: 옛 0x" + Integer.toHexString(x.id) + " 새 0x" + Integer.toHexString(y.id)
                        + " (옛 " + a.size() + "개 / 새 " + b.size() + "개)");
            }
            int off = Arrays.mismatch(x.body, y.body);
            throw new AssertionError("패킷 " + i + " (id 0x" + Integer.toHexString(x.id) + ") 본문이 다르다: 길이 옛 " + x.body.length + " 새 " + y.body.length
                    + ", 첫 다른 바이트 " + off);
        }
        if (a.size() != b.size()) {
            int i = Math.min(a.size(), b.size());
            throw new AssertionError("패킷 수가 다르다: 옛 " + a.size() + " 새 " + b.size() + " — 첫 남는 것 " + (a.size() > i ? a.get(i) : b.get(i)));
        }
    }
}
