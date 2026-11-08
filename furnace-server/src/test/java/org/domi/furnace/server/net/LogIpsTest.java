// log-ips=false 면 접속 주소를 로그에 남기지 않는다(바닐라 Connection.getLoggableAddress) — 파이프라인 앞쪽 핸들러 두 곳(A02-04 회귀 시험).
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LogIpsTest {

    private static List<String> capture(Class<?> type, Runnable body) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        ListAppender<ILoggingEvent> app = new ListAppender<>();
        app.start();
        logger.addAppender(app);
        try {
            body.run();
        } finally {
            logger.detachAppender(app);
        }
        return app.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static EmbeddedChannel peerChannel(io.netty.channel.ChannelHandler h, String ip) {
        return new EmbeddedChannel(h) {
            @Override
            protected SocketAddress remoteAddress0() {
                return new InetSocketAddress(ip, 40000);
            }
        };
    }

    private static List<String> slowClientLog(boolean logIps) {
        AtomicLong now = new AtomicLong();
        WritabilityGuard g = new WritabilityGuard(15_000, now::get, logIps);
        EmbeddedChannel ch = peerChannel(g, "198.51.100.77");
        ChannelHandlerContext ctx = ch.pipeline().context(g);
        ch.unsafe().outboundBuffer().setUserDefinedWritability(1, false);
        ch.runPendingTasks();
        now.set(20_000);
        return capture(WritabilityGuard.class, () -> assertTrue(g.check(ctx)));
    }

    @Test
    void writabilityGuardHonoursLogIps() {
        List<String> hidden = slowClientLog(false);
        assertEquals(1, hidden.size());
        assertFalse(hidden.get(0).contains("198.51.100.77"), hidden.get(0));
        assertTrue(hidden.get(0).contains("<ip address withheld>"), hidden.get(0));
        assertTrue(slowClientLog(true).get(0).contains("198.51.100.77"));
    }

    private static List<String> untrustedProxyLog(boolean logIps) throws Exception {
        ConnectionLimits l = ConnectionDefenseTest.limits("proxy-protocol", "true", "proxy-protocol.trusted", "10.0.0.0/8");
        EmbeddedChannel ch = ConnectionDefenseTest.proxyChannel(l, InetAddress.getByName("203.0.113.5"), new java.util.ArrayList<>(), logIps);
        List<String> out = capture(ProxyProtocolHandler.class, () -> ch.writeInbound(ConnectionDefenseTest.v2Tcp4(new int[]{1, 2, 3, 4}, 1)));
        assertFalse(ch.isOpen());
        ch.finishAndReleaseAll();
        return out;
    }

    @Test
    void proxyProtocolRejectionHonoursLogIps() throws Exception {
        List<String> hidden = untrustedProxyLog(false);
        assertEquals(1, hidden.size());
        assertFalse(hidden.get(0).contains("203.0.113.5"), hidden.get(0));
        assertTrue(hidden.get(0).contains("<ip address withheld>"), hidden.get(0));
        assertTrue(untrustedProxyLog(true).get(0).contains("203.0.113.5"));
    }
}
