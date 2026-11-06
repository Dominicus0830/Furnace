// ConnectionHandler.send 가 같은 이벤트 루프 순간의 패킷을 flush 한 번으로 내보내는지(쓰기 순서·개수는 그대로)
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.net.protocol.play.KeepAliveClientbound;
import org.junit.jupiter.api.Test;

class SendFlushCoalescingTest {

    private static final int N = 50;

    private static final class FlushCounter extends ChannelDuplexHandler {
        final AtomicInteger flushes = new AtomicInteger();
        @Override public void flush(ChannelHandlerContext ctx) throws Exception { flushes.incrementAndGet(); super.flush(ctx); }
    }

    @Test
    void 연속_전송은_flush_한_번으로_나가고_순서는_그대로다() throws Exception {
        DefaultEventLoopGroup group = new DefaultEventLoopGroup(2);
        try {
            ConnectionHandler handler = new ConnectionHandler("t", -1, null, null, new EntityIdAllocator());
            FlushCounter counter = new FlushCounter();
            CountDownLatch received = new CountDownLatch(N);
            List<Long> ids = new CopyOnWriteArrayList<>();
            Throwable[] err = new Throwable[1];
            ChannelHandlerContext[] ctxBox = new ChannelHandlerContext[1];
            LocalAddress addr = new LocalAddress("flush-coalescing-" + System.nanoTime());
            Channel server = new ServerBootstrap().group(group).channel(LocalServerChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override protected void initChannel(Channel c) { c.pipeline().addLast(counter, handler); ctxBox[0] = c.pipeline().context(handler); }
                    }).bind(addr).sync().channel();
            Channel client = new Bootstrap().group(group).channel(LocalChannel.class)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override public void channelRead(ChannelHandlerContext c, Object m) {
                            ByteBuf b = (ByteBuf) m;
                            try {
                                while (b.isReadable()) {   // 패킷 하나는 [id 1바이트][long 8바이트]
                                    assertEquals(KeepAliveClientbound.ID, b.readByte() & 0xFF);
                                    ids.add(b.readLong());
                                    received.countDown();
                                }
                            } finally { b.release(); }
                        }
                        @Override public void exceptionCaught(ChannelHandlerContext c, Throwable t) { err[0] = t; }
                    }).connect(addr).sync().channel();
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (ctxBox[0] == null && System.nanoTime() < deadline) Thread.sleep(10);
            assertNotNull(ctxBox[0], "서버 채널이 열리지 않음");
            ChannelHandlerContext ctx = ctxBox[0];
            ctx.executor().submit(() -> {   // 이벤트 루프 한 순간에 N 개를 보낸다
                for (int i = 0; i < N; i++) handler.send(ctx, new KeepAliveClientbound(i));
            }).sync();
            assertTrue(received.await(5, TimeUnit.SECONDS), "N 개가 모두 도착해야 한다 (도착 " + ids.size() + ", flush " + counter.flushes.get() + ", 오류 " + err[0] + ", 활성 " + ctx.channel().isActive() + ")");
            assertEquals(1, counter.flushes.get(), "한 순간에 쌓인 " + N + "개는 flush 한 번");
            for (int i = 0; i < N; i++) assertEquals(i, ids.get(i), "쓴 순서대로");
            client.close().sync();
            server.close().sync();
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }
}
