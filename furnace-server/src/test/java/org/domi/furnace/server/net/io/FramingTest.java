// 길이 프리픽스 프레이밍(인코더/디코더) 라운드트립·부분수신 테스트
package org.domi.furnace.server.net.io;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class FramingTest {

    @Test
    void encodeThenDecode_roundTrips() {
        EmbeddedChannel enc = new EmbeddedChannel(new FrameEncoder());
        ByteBuf payload = Unpooled.wrappedBuffer(new byte[]{1, 2, 3, 4, 5});
        assertTrue(enc.writeOutbound(payload));
        ByteBuf framed = enc.readOutbound();

        EmbeddedChannel dec = new EmbeddedChannel(new FrameDecoder());
        assertTrue(dec.writeInbound(framed));
        ByteBuf out = dec.readInbound();
        assertEquals(5, out.readableBytes());
        for (int i = 1; i <= 5; i++) assertEquals(i, out.readByte());
        out.release();
    }

    @Test
    void decoder_buffersPartialFrame() {
        EmbeddedChannel dec = new EmbeddedChannel(new FrameDecoder());
        assertFalse(dec.writeInbound(Unpooled.wrappedBuffer(new byte[]{3})));
        assertFalse(dec.writeInbound(Unpooled.wrappedBuffer(new byte[]{9, 9})));
        assertTrue(dec.writeInbound(Unpooled.wrappedBuffer(new byte[]{9})));
        ByteBuf out = dec.readInbound();
        assertEquals(3, out.readableBytes());
        out.release();
    }
}
