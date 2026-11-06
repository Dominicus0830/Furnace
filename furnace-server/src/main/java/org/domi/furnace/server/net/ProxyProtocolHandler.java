// PROXY 프로토콜(HAProxy v1/v2) 헤더를 받아 프록시 뒤 실제 클라이언트 주소를 채널에 남긴다. furnace.properties proxy-protocol=true 일 때만 파이프라인에 들어간다.
// 헤더를 읽는 건 netty HAProxyMessageDecoder(맨 앞)이고, 이 핸들러는 그 결과를 받아 신뢰 검사 후 주소를 기록하고 스스로 빠진다.
// 켜면 모든 접속이 헤더를 보내야 한다(없으면 거부). 앞단이 아닌 곳에서 직접 오는 접속이 주소를 속이지 못하도록
// proxy-protocol.trusted 로 앞단 주소(CIDR)를 제한할 수 있다.
package org.domi.furnace.server.net;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.haproxy.HAProxyCommand;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyProxiedProtocol;
import io.netty.util.AttributeKey;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ProxyProtocolHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ProxyProtocolHandler.class);
    static final AttributeKey<InetSocketAddress> SOURCE = AttributeKey.valueOf("furnace.proxy-protocol.source");

    private final ConnectionLimits limits;
    private final AtomicBoolean warned;

    ProxyProtocolHandler(ConnectionLimits limits, AtomicBoolean warned) {
        this.limits = limits;
        this.warned = warned;
    }

    /**
     * 연결의 첫 바이트가 PROXY 헤더(v1 "PROXY", v2 서명 0x0D)가 아니면 바로 거부한다. netty 디코더는 헤더가 아닌 바이트를 v1 텍스트로 보고
     * 줄바꿈이 올 때까지(최대 108B) 기다려서, 마인크래프트 핸드셰이크를 보낸 직접 접속이 한참 걸려서야 정리된다.
     */
    static final class Sniffer extends ChannelInboundHandlerAdapter {
        private final AtomicBoolean warned;

        Sniffer(AtomicBoolean warned) { this.warned = warned; }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof io.netty.buffer.ByteBuf b && b.isReadable()) {
                int first = b.getByte(b.readerIndex()) & 0xFF;
                if (first != 'P' && first != 0x0D) {
                    io.netty.util.ReferenceCountUtil.release(msg);
                    if (warned.compareAndSet(false, true)) {
                        log.warn("proxy-protocol=true 인데 PROXY 헤더 없이 접속한 연결을 거부했다 (앞단의 send-proxy 설정을 확인하세요)");
                    }
                    ctx.close();
                    return;
                }
                ctx.pipeline().remove(this);
            }
            ctx.fireChannelRead(msg);
        }
    }

    /** PROXY 헤더로 받은 실제 접속 주소(없으면 null). */
    static InetSocketAddress sourceOf(io.netty.channel.Channel ch) {
        return ch.attr(SOURCE).get();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof HAProxyMessage m)) {
            ctx.fireChannelRead(msg);
            return;
        }
        try {
            InetAddress peer = ctx.channel().remoteAddress() instanceof InetSocketAddress isa ? isa.getAddress() : null;
            if (!limits.proxyTrusts(peer)) {
                log.warn("신뢰하지 않는 주소 {} 가 PROXY 헤더를 보내 거부했다 (proxy-protocol.trusted 확인)", peer);
                ctx.close();
                return;
            }
            // LOCAL(앞단 자체 점검)이나 주소 없는 UNKNOWN 은 실제 접속 주소를 그대로 쓴다.
            HAProxyProxiedProtocol proto = m.proxiedProtocol();
            if (m.command() == HAProxyCommand.PROXY
                    && (proto == HAProxyProxiedProtocol.TCP4 || proto == HAProxyProxiedProtocol.TCP6)) {
                ctx.channel().attr(SOURCE).set(new InetSocketAddress(InetAddress.getByName(m.sourceAddress()), m.sourcePort()));
            }
        } catch (java.net.UnknownHostException | RuntimeException e) {
            log.debug("PROXY 헤더 주소를 읽지 못했다: {}", e.toString());
            ctx.close();
            return;
        } finally {
            m.release();
        }
        ctx.pipeline().remove(this);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 헤더가 없거나 깨진 접속(직접 접속·잘못된 앞단 설정). 로그가 넘치지 않게 처음 한 번만 알린다.
        if (warned.compareAndSet(false, true)) {
            log.warn("proxy-protocol=true 인데 PROXY 헤더가 없거나 잘못된 접속을 거부했다 (앞단의 send-proxy 설정을 확인하세요): {}", cause.getMessage());
        } else {
            log.debug("PROXY 헤더 오류, 연결 종료: {}", cause.toString());
        }
        ctx.close();
    }
}
