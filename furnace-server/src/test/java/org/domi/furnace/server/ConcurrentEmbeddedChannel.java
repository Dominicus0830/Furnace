// 여러 스레드가 동시에 eventLoop().execute 를 불러도 작업을 잃지 않는 EmbeddedChannel.
// 넷티 EmbeddedEventLoop 의 작업 큐는 동기화 없는 ArrayDeque 라서, region 스레드·청크 워커가 Player.send 로 넣는 사이
// 시험 스레드가 runPendingTasks 로 꺼내면 작업이 사라지거나 큐가 깨진다(실서버의 NIO 이벤트 루프는 스레드 안전하다).
// 이 채널은 execute·schedule 과 꺼내기(runPendingTasks·readOutbound 등)를 한 잠금으로 직렬화한다.
package org.domi.furnace.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.AbstractEventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class ConcurrentEmbeddedChannel extends EmbeddedChannel {

    // 필드 초기화식을 두지 않는다 — 부모 생성자가 eventLoop() 를 부르며 먼저 채운 값을 덮어쓰지 않게.
    private GuardedLoop guarded;

    public ConcurrentEmbeddedChannel() {
        super();
        eventLoop();   // 다른 스레드에 넘기기 전에 감싼 루프를 만든다
    }

    @Override
    public EventLoop eventLoop() {
        if (guarded == null) guarded = new GuardedLoop(super.eventLoop(), this);
        return guarded;
    }

    @Override
    public void runPendingTasks() {
        synchronized (this) { super.runPendingTasks(); }
    }

    @Override
    public long runScheduledPendingTasks() {
        synchronized (this) { return super.runScheduledPendingTasks(); }
    }

    @Override
    public <T> T readOutbound() {
        synchronized (this) { return super.readOutbound(); }
    }

    @Override
    public <T> T readInbound() {
        synchronized (this) { return super.readInbound(); }
    }

    /** 넣기만 잠금 아래에서 하고 나머지는 원래 EmbeddedEventLoop 에 맡긴다. */
    private static final class GuardedLoop extends AbstractEventExecutor implements EventLoop {
        private final EventLoop real;
        private final Object lock;

        GuardedLoop(EventLoop real, Object lock) {
            this.real = real;
            this.lock = lock;
        }

        @Override public void execute(Runnable task) { synchronized (lock) { real.execute(task); } }

        @Override public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            synchronized (lock) { return real.schedule(command, delay, unit); }
        }

        @Override public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            synchronized (lock) { return real.schedule(callable, delay, unit); }
        }

        @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            synchronized (lock) { return real.scheduleAtFixedRate(command, initialDelay, period, unit); }
        }

        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            synchronized (lock) { return real.scheduleWithFixedDelay(command, initialDelay, delay, unit); }
        }

        @Override public boolean inEventLoop(Thread thread) { return real.inEventLoop(thread); }
        @Override public EventLoopGroup parent() { return real.parent(); }
        @Override public EventLoop next() { return this; }
        @Override public ChannelFuture register(Channel channel) { return real.register(channel); }
        @Override public ChannelFuture register(ChannelPromise promise) { return real.register(promise); }
        @SuppressWarnings("deprecation")
        @Override public ChannelFuture register(Channel channel, ChannelPromise promise) { return real.register(channel, promise); }
        @Override public boolean isShuttingDown() { return real.isShuttingDown(); }
        @Override public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) { return real.shutdownGracefully(quietPeriod, timeout, unit); }
        @Override public Future<?> terminationFuture() { return real.terminationFuture(); }
        @SuppressWarnings("deprecation")
        @Override public void shutdown() { real.shutdown(); }
        @Override public boolean isShutdown() { return real.isShutdown(); }
        @Override public boolean isTerminated() { return real.isTerminated(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException { return real.awaitTermination(timeout, unit); }
    }
}
