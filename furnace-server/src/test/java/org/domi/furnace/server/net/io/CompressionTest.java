// 압축 인코더→디코더 라운드트립(임계값 위/아래 모두) 검증
package org.domi.furnace.server.net.io;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.junit.jupiter.api.Test;

class CompressionTest {

    private static ByteBuf bytes(int n, int fill) {
        byte[] a = new byte[n];
        java.util.Arrays.fill(a, (byte) fill);
        return Unpooled.wrappedBuffer(a);
    }

    @Test
    void roundTrips_aboveThreshold() {
        int threshold = 16;
        EmbeddedChannel enc = new ConcurrentEmbeddedChannel(new CompressionEncoder(threshold));
        EmbeddedChannel dec = new ConcurrentEmbeddedChannel(new CompressionDecoder(threshold));

        enc.writeOutbound(bytes(100, 7));
        ByteBuf compressed = enc.readOutbound();
        dec.writeInbound(compressed);
        ByteBuf out = dec.readInbound();
        assertEquals(100, out.readableBytes());
        assertEquals(7, out.readByte());
        out.release();
    }

    @Test
    void roundTrips_belowThreshold() {
        int threshold = 256;
        EmbeddedChannel enc = new ConcurrentEmbeddedChannel(new CompressionEncoder(threshold));
        EmbeddedChannel dec = new ConcurrentEmbeddedChannel(new CompressionDecoder(threshold));

        enc.writeOutbound(bytes(10, 3));
        ByteBuf small = enc.readOutbound();
        dec.writeInbound(small);
        ByteBuf out = dec.readInbound();
        assertEquals(10, out.readableBytes());
        out.release();
    }

    @Test
    void rejects_compressedPacketBelowThreshold() {
        // 바닐라: "Badly compressed packet - size of N is below server threshold of T"
        int threshold = 256;
        EmbeddedChannel enc = new ConcurrentEmbeddedChannel(new CompressionEncoder(16));
        enc.writeOutbound(bytes(100, 5));            // 임계값 16 으로 압축된 100B
        ByteBuf compressed = enc.readOutbound();
        EmbeddedChannel dec = new ConcurrentEmbeddedChannel(new CompressionDecoder(threshold));
        Exception e = assertThrows(Exception.class, () -> dec.writeInbound(compressed));
        assertTrue(e.getMessage().contains("below server threshold of 256"), e.getMessage());
        try { dec.finishAndReleaseAll(); } catch (Exception ignored) { }   // 이미 던진 예외를 다시 던진다
    }

    @Test
    void rejects_declaredSizeAboveMaximum() {
        EmbeddedChannel dec = new ConcurrentEmbeddedChannel(new CompressionDecoder(256));
        ByteBuf buf = Unpooled.buffer();
        Types.writeVarInt(buf, 2 * 1024 * 1024 + 1);   // 압축 해제 상한 초과(본문 없이 선언만)
        buf.writeByte(0x78);
        assertThrows(Exception.class, () -> dec.writeInbound(buf));
        try { dec.finishAndReleaseAll(); } catch (Exception ignored) { }   // 이미 던진 예외를 다시 던진다
    }

    /** 예전 인코더(byte[] 복사 + 기본 Deflater + 8KB 임시 배열)와 같은 방식으로 만든 기준 바이트. */
    private static byte[] reference(byte[] data, int threshold) {
        ByteBuf out = Unpooled.buffer();
        if (data.length < threshold) {
            Types.writeVarInt(out, 0);
            out.writeBytes(data);
        } else {
            Types.writeVarInt(out, data.length);
            java.util.zip.Deflater d = new java.util.zip.Deflater();
            d.setInput(data);
            d.finish();
            byte[] buffer = new byte[8192];
            while (!d.finished()) out.writeBytes(buffer, 0, d.deflate(buffer));
            d.end();
        }
        byte[] r = new byte[out.readableBytes()];
        out.readBytes(r);
        return r;
    }

    private static byte[] drain(ByteBuf b) {
        byte[] r = new byte[b.readableBytes()];
        b.readBytes(r);
        b.release();
        return r;
    }

    // 기본 레벨 출력은 예전 인코더와 바이트까지 같고, 공유 프레임(encodeToBytes)·Precompressed 통과도 같다.
    @Test
    void wireBytesMatchPreviousEncoder_andSharedFrame() {
        int threshold = 256;
        java.util.Random rnd = new java.util.Random(7);
        CompressionEncoder sharedEnc = new CompressionEncoder(threshold);
        for (int size : new int[]{0, 10, 255, 256, 257, 5000, 70_000, 300_000}) {
            byte[] data = new byte[size];
            for (int i = 0; i < size; i++) data[i] = (byte) (rnd.nextInt(6) * (i % 7 == 0 ? 3 : 1));
            byte[] expected = reference(data, threshold);

            EmbeddedChannel heap = new ConcurrentEmbeddedChannel(new CompressionEncoder(threshold));
            heap.writeOutbound(Unpooled.wrappedBuffer(data));
            assertArrayEquals(expected, drain(heap.readOutbound()), "heap size=" + size);

            // 직접 버퍼·조각난(composite) 입력
            EmbeddedChannel direct = new ConcurrentEmbeddedChannel(new CompressionEncoder(threshold));
            ByteBuf d = Unpooled.directBuffer(Math.max(size, 1)).writeBytes(data);
            direct.writeOutbound(d);
            assertArrayEquals(expected, drain(direct.readOutbound()), "direct size=" + size);
            int half = size / 2;
            direct.writeOutbound(Unpooled.wrappedBuffer(Unpooled.wrappedBuffer(data, 0, half), Unpooled.wrappedBuffer(data, half, size - half)));
            assertArrayEquals(expected, drain(direct.readOutbound()), "composite size=" + size);

            byte[] shared = sharedEnc.encodeToBytes(data);
            assertArrayEquals(expected, shared, "shared size=" + size);
            direct.writeOutbound(new CompressionEncoder.Precompressed(Unpooled.wrappedBuffer(shared)));
            assertArrayEquals(expected, drain(direct.readOutbound()), "precompressed size=" + size);

            // 디코더 왕복(직접 버퍼 입력)
            EmbeddedChannel dec = new ConcurrentEmbeddedChannel(new CompressionDecoder(threshold));
            dec.writeInbound(Unpooled.directBuffer(expected.length).writeBytes(expected));
            assertArrayEquals(data, drain(dec.readInbound()), "decode size=" + size);
            heap.finishAndReleaseAll();
            direct.finishAndReleaseAll();
            dec.finishAndReleaseAll();
        }
    }

    // M12: 청크 풀에서 미리 압축해 두면 채널(이벤트 루프)은 압축하지 않고, 여러 채널 모두 일반 경로와 같은 바이트를 받는다.
    // 임계값이 다른 채널은 캐시를 못 써서 예전처럼 자기 인코더로 압축한다(결과는 그 채널의 일반 경로와 같다).
    @Test
    void precompressedFrame_isSharedByChannels_andMatchesNormalPath() {
        byte[] body = new byte[40_000];
        for (int i = 0; i < body.length; i++) body[i] = (byte) (i * 13 % 11);
        byte[] expected256 = normalPathBytes(body, 256);
        byte[] expected64 = normalPathBytes(body, 64);

        SharedChunkFrames.precompressThreshold(256);
        try {
            long before = SharedChunkFrames.ENCODES.get();
            SharedChunkFrames.precompress(body);   // 청크 풀 스레드 역할
            assertEquals(1, SharedChunkFrames.ENCODES.get() - before);
            for (int c = 0; c < 3; c++) {
                EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
                ch.pipeline().addLast("compress", new CompressionEncoder(256));
                ch.writeOutbound(SharedChunkFrames.encode(ch,
                        org.domi.furnace.server.net.protocol.play.LevelChunkWithLight.ofShared(body)));
                assertArrayEquals(expected256, drain(ch.readOutbound()), "channel " + c);
            }
            assertEquals(1, SharedChunkFrames.ENCODES.get() - before, "채널은 미리 압축한 프레임을 쓴다");

            EmbeddedChannel other = new ConcurrentEmbeddedChannel();
            other.pipeline().addLast("compress", new CompressionEncoder(64));
            other.writeOutbound(SharedChunkFrames.encode(other,
                    org.domi.furnace.server.net.protocol.play.LevelChunkWithLight.ofShared(body)));
            assertArrayEquals(expected64, drain(other.readOutbound()), "다른 임계값 채널");

            // 꺼져 있으면(음수) 아무것도 하지 않는다
            SharedChunkFrames.precompressThreshold(-1);
            long mid = SharedChunkFrames.ENCODES.get();
            SharedChunkFrames.precompress(new byte[5000]);
            assertEquals(mid, SharedChunkFrames.ENCODES.get());
        } finally {
            SharedChunkFrames.precompressThreshold(-1);
        }
    }

    /** 미리 압축 없이, 이 임계값 채널에 청크 패킷을 보냈을 때 나가는 바이트(일반 경로). */
    private static byte[] normalPathBytes(byte[] body, int threshold) {
        var normal = new org.domi.furnace.server.net.protocol.play.LevelChunkWithLight(Unpooled.wrappedBuffer(body.clone()));
        EmbeddedChannel plain = new ConcurrentEmbeddedChannel();
        plain.pipeline().addLast("compress", new CompressionEncoder(threshold));
        plain.writeOutbound(SharedChunkFrames.encode(plain, normal));
        return drain(plain.readOutbound());
    }

    // 같은 본문 배열을 두 채널로 보내면 압축은 한 번, 바이트는 일반 경로(패킷 인코딩→인코더)와 같다.
    @Test
    void sharedChunkFrame_compressesOnceAndMatchesNormalPath() {
        byte[] body = new byte[40_000];
        for (int i = 0; i < body.length; i++) body[i] = (byte) (i * 31 % 17);
        var normal = new org.domi.furnace.server.net.protocol.play.LevelChunkWithLight(Unpooled.wrappedBuffer(body.clone()));
        EmbeddedChannel plain = new ConcurrentEmbeddedChannel();
        plain.pipeline().addLast("compress", new CompressionEncoder(256));
        plain.writeOutbound(SharedChunkFrames.encode(plain, normal));
        byte[] expected = drain(plain.readOutbound());

        long before = SharedChunkFrames.ENCODES.get();
        for (int c = 0; c < 2; c++) {
            EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
            ch.pipeline().addLast("compress", new CompressionEncoder(256));
            ch.writeOutbound(SharedChunkFrames.encode(ch,
                    org.domi.furnace.server.net.protocol.play.LevelChunkWithLight.ofShared(body)));
            assertArrayEquals(expected, drain(ch.readOutbound()), "channel " + c);
        }
        assertEquals(1, SharedChunkFrames.ENCODES.get() - before, "두 번째 채널은 캐시를 쓴다");

        // 압축 없는 연결은 원래 경로(비압축 패킷)
        EmbeddedChannel none = new ConcurrentEmbeddedChannel();
        Object msg = SharedChunkFrames.encode(none, org.domi.furnace.server.net.protocol.play.LevelChunkWithLight.ofShared(body));
        assertInstanceOf(ByteBuf.class, msg);
        assertEquals(body.length + 1, ((ByteBuf) msg).readableBytes());
        ((ByteBuf) msg).release();
    }

    @Test
    void levelIsConfigurableAndStillZlib() {
        byte[] data = new byte[50_000];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 13);
        for (int level : new int[]{0, 1, 4, 9}) {
            EmbeddedChannel enc = new ConcurrentEmbeddedChannel(new CompressionEncoder(64, level));
            EmbeddedChannel dec = new ConcurrentEmbeddedChannel(new CompressionDecoder(64));
            enc.writeOutbound(Unpooled.wrappedBuffer(data));
            dec.writeInbound((ByteBuf) enc.readOutbound());
            assertArrayEquals(data, drain(dec.readInbound()), "level=" + level);
        }
        assertThrows(IllegalArgumentException.class, () -> new CompressionEncoder(64, 10));
    }

    @Test
    void truncatedZlibIsRejected() {
        byte[] data = new byte[5000];
        new java.util.Random(3).nextBytes(data);
        byte[] full = reference(data, 64);
        EmbeddedChannel dec = new ConcurrentEmbeddedChannel(new CompressionDecoder(64));
        assertThrows(Exception.class, () -> dec.writeInbound(Unpooled.wrappedBuffer(java.util.Arrays.copyOf(full, full.length / 2))));
    }

    // 재사용 스크래치 버퍼 성장/축소 경계 fuzz: 0바이트, 임계치 부근, 수 MB, 감소하는 순서까지
    @Test
    void roundTrips_fuzzSizesAcrossThreshold() {
        int threshold = 64;
        EmbeddedChannel enc = new ConcurrentEmbeddedChannel(new CompressionEncoder(threshold));
        EmbeddedChannel dec = new ConcurrentEmbeddedChannel(new CompressionDecoder(threshold));

        int[] sizes = {0, 1, 63, 64, 65, 100, 8192, 8193, 1_000_000, 2 * 1024 * 1024, 1, 0, 500_000};
        for (int size : sizes) {
            int fill = (size % 251) + 1; // 0 회피(가변 패턴)
            enc.writeOutbound(bytes(size, fill));
            ByteBuf frame = enc.readOutbound();
            dec.writeInbound(frame);
            ByteBuf out = dec.readInbound();
            assertEquals(size, out.readableBytes(), "size=" + size);
            byte[] actual = new byte[size];
            out.readBytes(actual);
            byte[] expected = new byte[size];
            java.util.Arrays.fill(expected, (byte) fill);
            assertArrayEquals(expected, actual, "content mismatch size=" + size);
            out.release();
        }
    }
}
