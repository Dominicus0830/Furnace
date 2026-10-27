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
        // payload는 길이 프리픽스를 뺀 [9,9,9]여야 함(리더 인덱스가 한 칸 밀리면 [3,9,9]가 됨).
        for (int i = 0; i < 3; i++) assertEquals(9, out.readByte());
        out.release();
        dec.finishAndReleaseAll();
    }

    @Test
    void decoder_throws_whenLengthExceedsMax() {
        EmbeddedChannel dec = new EmbeddedChannel(new FrameDecoder());
        ByteBuf buf = Unpooled.buffer();
        Types.writeVarInt(buf, 2 * 1024 * 1024 + 1); // 2 MiB 상한 초과
        assertThrows(Exception.class, () -> dec.writeInbound(buf));
        dec.finishAndReleaseAll();
    }

    @Test
    void decoder_honorsPerStateLimit() {
        FrameDecoder fd = new FrameDecoder();
        fd.maxLength(1024);
        EmbeddedChannel dec = new EmbeddedChannel(fd);
        ByteBuf ok = Unpooled.buffer();
        Types.writeVarInt(ok, 1024);
        ok.writeZero(1024);
        assertTrue(dec.writeInbound(ok));
        ((ByteBuf) dec.readInbound()).release();
        ByteBuf big = Unpooled.buffer();
        Types.writeVarInt(big, 1025);                // 본문이 오기 전에 길이만으로 끊는다
        assertThrows(Exception.class, () -> dec.writeInbound(big));
        dec.finishAndReleaseAll();
    }

    @Test
    void decoder_limitCannotExceedProtocolMaximum() {
        FrameDecoder fd = new FrameDecoder();
        fd.maxLength(Integer.MAX_VALUE);
        assertEquals(2 * 1024 * 1024, fd.maxLength());
    }
}
