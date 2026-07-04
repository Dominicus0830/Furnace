// 압축 인코더→디코더 라운드트립(임계값 위/아래 모두) 검증
package org.domi.furnace.server.net.io;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
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
        EmbeddedChannel enc = new EmbeddedChannel(new CompressionEncoder(threshold));
        EmbeddedChannel dec = new EmbeddedChannel(new CompressionDecoder(threshold));

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
        EmbeddedChannel enc = new EmbeddedChannel(new CompressionEncoder(threshold));
        EmbeddedChannel dec = new EmbeddedChannel(new CompressionDecoder(threshold));

        enc.writeOutbound(bytes(10, 3));
        ByteBuf small = enc.readOutbound();
        dec.writeInbound(small);
        ByteBuf out = dec.readInbound();
        assertEquals(10, out.readableBytes());
        out.release();
    }

    // 재사용 스크래치 버퍼 성장/축소 경계 fuzz: 0바이트, 임계치 부근, 수 MB, 감소하는 순서까지
    @Test
    void roundTrips_fuzzSizesAcrossThreshold() {
        int threshold = 64;
        EmbeddedChannel enc = new EmbeddedChannel(new CompressionEncoder(threshold));
        EmbeddedChannel dec = new EmbeddedChannel(new CompressionDecoder(threshold));

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
