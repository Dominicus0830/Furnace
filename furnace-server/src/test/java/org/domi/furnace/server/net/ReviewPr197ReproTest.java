// PR #197 검토 재현을 회귀 시험으로 남김: 디코드 실패로 끊기기로 한 뒤에는 패킷을 더 처리하지 않고 Disconnect 도 한 번만 보낸다.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.net.io.FrameDecoder;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.ConnectionState;
import org.junit.jupiter.api.Test;

class ReviewPr197ReproTest {

    /** 소켓 송신 버퍼가 꽉 찬 클라이언트(읽지 않음)처럼: 쓰기를 받아 두기만 하고 완료시키지 않는다. */
    static final class StalledWrites extends ChannelOutboundHandlerAdapter {
        final List<ByteBuf> held = new ArrayList<>();
        @Override public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (msg instanceof ByteBuf b) held.add(b); // promise 는 끝내지 않는다 → CLOSE 리스너가 돌지 않는다
        }
        @Override public void flush(ChannelHandlerContext ctx) { }
    }

    private static ByteBuf frames(ByteBuf... bodies) {
        ByteBuf out = Unpooled.buffer();
        for (ByteBuf b : bodies) { Types.writeVarInt(out, b.readableBytes()); out.writeBytes(b); b.release(); }
        return out;
    }

    private static ByteBuf clientInfo(String locale) {
        ByteBuf b = Unpooled.buffer();
        Types.writeVarInt(b, 0x00);
        Types.writeString(b, locale); b.writeByte(8); Types.writeVarInt(b, 0); b.writeBoolean(true);
        b.writeByte(0x7F); Types.writeVarInt(b, 1); b.writeBoolean(false); b.writeBoolean(true);
        return b;
    }

    private static ByteBuf truncatedClientInfo() {
        ByteBuf b = Unpooled.buffer();
        Types.writeVarInt(b, 0x00);
        Types.writeString(b, "xx_yy");   // 렌더 거리부터 없음
        return b;
    }

    private static Object field(Object o, String name) throws Exception {
        var f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    /**
     * 바닐라 Connection.exceptionCaught: 첫 오류에만 사유 패킷을 보내고 setReadOnly()(autoRead=false), 두 번째부터는 "Double fault" 로 그냥 닫는다.
     * PR: protocolError 가 closeFlushed(즉시 닫기) 대신 kick(보낸 뒤 닫기)로 바뀌어, 보내기가 밀려 있으면 그 사이 뒤 패킷을 계속 처리한다.
     */
    @Test
    void afterDecodeFailure_laterPacketsAreNotProcessed_andOnlyOneDisconnectIsSent() throws Exception {
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        StalledWrites stalled = new StalledWrites();
        ch.pipeline().addLast("stalled", stalled);
        ch.pipeline().addLast("frameDecoder", new FrameDecoder());
        ConnectionHandler h = new ConnectionHandler(ServerLoginContext.offline("t").limits(ConnectionLimits.fromProperties(new Properties(), m -> {})), -1, null, null, null);
        ch.pipeline().addLast("handler", h);
        h.state(ConnectionState.CONFIGURATION);

        // 잘린 ClientInformation 두 개 + 올바른 것 하나를 한 번에(한 TCP 읽기)
        ch.writeInbound(frames(truncatedClientInfo(), truncatedClientInfo(), clientInfo("ko_kr")));
        ch.runPendingTasks();

        int disconnects = stalled.held.size();
        Object locale = field(h, "clientLocale");
        boolean autoRead = ch.config().isAutoRead();
        stalled.held.forEach(ByteBuf::release);
        ch.finishAndReleaseAll();

        assertAll(
                () -> assertEquals("en_us", locale, "끊기로 한 뒤 들어온 ClientInformation 이 적용됐다(바닐라는 읽기를 멈춘다)"),
                () -> assertEquals(1, disconnects, "Disconnect 를 오류마다 하나씩 보냈다(바닐라는 첫 오류만, 그 뒤는 Double fault)"),
                () -> assertFalse(autoRead, "바닐라 setReadOnly 처럼 읽기를 멈추지 않았다"));
    }
}
