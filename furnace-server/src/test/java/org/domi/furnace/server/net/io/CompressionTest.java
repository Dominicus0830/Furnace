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
}
