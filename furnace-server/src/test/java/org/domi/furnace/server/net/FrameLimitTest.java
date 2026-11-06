// 단계별 프레임 상한: 핸드셰이크·상태 단계에서 큰 프레임은 본문을 버퍼링하기 전에 끊고, 바닐라 최대 크기 패킷은 받는다
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.net.io.FrameDecoder;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.ConnectionState;
import org.junit.jupiter.api.Test;

class FrameLimitTest {

    private static EmbeddedChannel channel() {
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        ch.pipeline().addLast("frameDecoder", new FrameDecoder());
        ch.pipeline().addLast("handler", new ConnectionHandler("t", -1, null, null, null));
        return ch;
    }

    /** 1.21.1 최대 크기 핸드셰이크(주소 255자 × 3바이트 UTF-8, 5바이트 VarInt 들). */
    private static ByteBuf maxHandshake(int nextState) {
        ByteBuf body = Unpooled.buffer();
        Types.writeVarInt(body, 0x00);
        Types.writeVarInt(body, Integer.MIN_VALUE);
        Types.writeString(body, "가".repeat(255));
        body.writeShort(25565);
        Types.writeVarInt(body, nextState);
        ByteBuf frame = Unpooled.buffer();
        Types.writeVarInt(frame, body.readableBytes());
        frame.writeBytes(body);
        body.release();
        return frame;
    }

    @Test
    void limits_matchProtocolSizes() {
        ByteBuf hs = maxHandshake(1);
        assertTrue(hs.readableBytes() <= ConnectionHandler.frameLimit(ConnectionState.HANDSHAKE));
        hs.release();
        assertEquals(Integer.MAX_VALUE, ConnectionHandler.frameLimit(ConnectionState.LOGIN));
        assertEquals(Integer.MAX_VALUE, ConnectionHandler.frameLimit(ConnectionState.PLAY));
    }

    @Test
    void handshake_acceptsMaximumVanillaSize_thenStatusLimitApplies() {
        EmbeddedChannel ch = channel();
        ch.writeInbound(maxHandshake(1));
        assertTrue(ch.isOpen());
        assertEquals(ConnectionHandler.STATUS_FRAME_MAX, ch.pipeline().get(FrameDecoder.class).maxLength());
        ByteBuf big = Unpooled.buffer();
        Types.writeVarInt(big, 1000);                  // 상태 단계에 1000B 프레임 — 본문 없이 길이만으로 끊는다
        ch.writeInbound(big);
        assertFalse(ch.isOpen());
        ch.finishAndReleaseAll();
    }

    @Test
    void handshake_rejectsOversizedFrame() {
        EmbeddedChannel ch = channel();
        assertEquals(ConnectionHandler.HANDSHAKE_FRAME_MAX, ch.pipeline().get(FrameDecoder.class).maxLength());
        ByteBuf big = Unpooled.buffer();
        Types.writeVarInt(big, 100_000);
        ch.writeInbound(big);
        assertFalse(ch.isOpen());
        ch.finishAndReleaseAll();
    }
}
