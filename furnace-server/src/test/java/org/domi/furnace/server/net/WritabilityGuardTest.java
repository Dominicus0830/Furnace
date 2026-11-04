package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class WritabilityGuardTest {
    @Test
    void 오래_쓰기불가면_끊고_회복되면_유지() {
        AtomicLong now = new AtomicLong(0);
        WritabilityGuard guard = new WritabilityGuard(15_000, now::get);
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel(guard);
        ChannelHandlerContext ctx = ch.pipeline().context(guard);

        // 쓰기 불가 진입 후 임계 전: 유지, 회복 시 타이머 초기화
        ch.unsafe().outboundBuffer().setUserDefinedWritability(1, false);
        ch.runPendingTasks();
        assertFalse(ch.isWritable());
        now.set(10_000);
        assertFalse(guard.check(ctx));
        ch.unsafe().outboundBuffer().setUserDefinedWritability(1, true);
        ch.runPendingTasks();
        assertTrue(ch.isWritable());
        now.set(30_000);
        assertFalse(guard.check(ctx));
        assertTrue(ch.isOpen());

        // 다시 쓰기 불가, 15초 경과 시 종료
        ch.unsafe().outboundBuffer().setUserDefinedWritability(1, false);
        ch.runPendingTasks();
        assertFalse(ch.isWritable());
        now.set(44_000);
        assertFalse(guard.check(ctx));
        now.set(45_000);
        assertTrue(guard.check(ctx));
        assertFalse(ch.isOpen());
    }
}
