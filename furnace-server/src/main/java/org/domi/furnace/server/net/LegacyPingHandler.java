// 구형(1.6 이하) 서버 목록 핑 — 바닐라 LegacyQueryHandler(legacy_query). 연결의 첫 바이트가 0xFE 면 VarInt 프레임이 아니라 옛 형식이다:
//   FE                  (1.3 이하)            → FF + "MOTD§접속자§최대"
//   FE 01               (1.4~1.5)             → FF + "§1\0127\0버전\0MOTD\0접속자\0최대"
//   FE 01 FA 00 0B "MC|PingHost" … (1.6)       → 위와 같음
// 답은 0xFF, 글자 수(short), UTF-16BE 문자열이고 보낸 뒤 닫는다. 0xFE 가 아니거나 형식이 맞지 않으면 아무것도 건드리지 않고 파이프라인에서 빠진다.
package org.domi.furnace.server.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LegacyPingHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(LegacyPingHandler.class);
    /** 바닐라가 구형 응답에 적는 프로토콜 번호(SharedConstants.getProtocolVersion 이 아니라 상수 127). */
    static final int LEGACY_PROTOCOL = 127;

    private final String motd;
    private final int maxPlayers;
    private final String version;
    private final IntSupplier online;

    LegacyPingHandler(String motd, int maxPlayers, String version, IntSupplier online) {
        this.motd = motd;
        this.maxPlayers = maxPlayers;
        this.version = version;
        this.online = online;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf in) || !in.isReadable()) {
            pass(ctx, msg);
            return;
        }
        int start = in.readerIndex();
        String reply = null;
        try {
            if (in.readUnsignedByte() == 0xFE) {
                if (!in.isReadable()) {
                    reply = String.format(Locale.ROOT, "%s§%d§%d", motd, online.getAsInt(), maxPlayers);
                } else if (in.readUnsignedByte() == 1 && (!in.isReadable() || readPingHost(in))) {
                    reply = String.format(Locale.ROOT, "§1\0%d\0%s\0%s\0%d\0%d", LEGACY_PROTOCOL, version, motd, online.getAsInt(), maxPlayers);
                }
            }
        } catch (RuntimeException e) {
            reply = null;   // 모자라거나 이상한 바이트 — 구형 핑이 아니다
        }
        if (reply == null) {
            in.readerIndex(start);
            pass(ctx, msg);
            return;
        }
        in.release();
        log.debug("Ping: legacy from {}", ctx.channel().remoteAddress());
        ByteBuf out = ctx.alloc().buffer();
        out.writeByte(0xFF);
        out.writeShort(reply.length());
        out.writeBytes(reply.getBytes(StandardCharsets.UTF_16BE));
        ctx.writeAndFlush(out).addListener(ChannelFutureListener.CLOSE);
        ctx.pipeline().remove(this);
    }

    /** 1.6 의 `FA "MC|PingHost" 길이 프로토콜 호스트 포트` 를 형식만 확인한다(바닐라 readCustomPayloadPacket). */
    private static boolean readPingHost(ByteBuf in) {
        if (in.readUnsignedByte() != 0xFA) return false;
        int n = in.readUnsignedShort();
        byte[] name = new byte[n * 2];
        in.readBytes(name);
        if (!"MC|PingHost".equals(new String(name, StandardCharsets.UTF_16BE))) return false;
        int len = in.readUnsignedShort();
        if (in.readableBytes() != len) return false;
        in.readUnsignedByte();                       // 클라이언트 프로토콜
        int hostChars = in.readUnsignedShort();
        in.skipBytes(hostChars * 2);                 // 호스트(UTF-16BE)
        in.readInt();                                // 포트
        return true;
    }

    private void pass(ChannelHandlerContext ctx, Object msg) {
        ctx.pipeline().remove(this);   // 첫 읽기 하나만 본다 — 그 뒤에는 보통 프레임 디코더가 받는다
        ctx.fireChannelRead(msg);
    }
}
