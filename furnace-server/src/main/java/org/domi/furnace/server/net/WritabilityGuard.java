package org.domi.furnace.server.net;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 쓰기 불가 상태가 오래 지속되는 느린 클라이언트를 끊는다(송신 큐 무한 증가 방지). */
public final class WritabilityGuard extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(WritabilityGuard.class);

    public static final long DEFAULT_TIMEOUT_MILLIS = 15_000L;
    private static final long CHECK_INTERVAL_MILLIS = 1_000L;

    private final long timeoutMillis;
    private final LongSupplier clockMillis;
    /** server.properties log-ips — false 면 로그에서 주소를 가린다(바닐라 Connection.getLoggableAddress). */
    private final boolean logIps;
    private long unwritableSince = -1;
    private ScheduledFuture<?> checkTask;

    public WritabilityGuard() {
        this(true);
    }

    public WritabilityGuard(boolean logIps) {
        this(DEFAULT_TIMEOUT_MILLIS, System::currentTimeMillis, logIps);
    }

    public WritabilityGuard(long timeoutMillis, LongSupplier clockMillis) {
        this(timeoutMillis, clockMillis, true);
    }

    public WritabilityGuard(long timeoutMillis, LongSupplier clockMillis, boolean logIps) {
        this.timeoutMillis = timeoutMillis;
        this.clockMillis = clockMillis;
        this.logIps = logIps;
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isWritable()) {
            unwritableSince = -1;
            cancelCheck();
        } else if (unwritableSince < 0) {
            unwritableSince = clockMillis.getAsLong();
            checkTask = ctx.executor().scheduleWithFixedDelay(() -> check(ctx),
                    CHECK_INTERVAL_MILLIS, CHECK_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        }
        ctx.fireChannelWritabilityChanged();
    }

    /** 쓰기 불가 지속 시간을 검사해 초과 시 연결을 닫는다. 닫았으면 true. */
    boolean check(ChannelHandlerContext ctx) {
        if (unwritableSince < 0 || ctx.channel().isWritable()) {
            return false;
        }
        long elapsed = clockMillis.getAsLong() - unwritableSince;
        if (elapsed < timeoutMillis) {
            return false;
        }
        log.warn("느린 클라이언트 연결 종료: {}ms 동안 쓰기 불가 ({})", elapsed,
                ConnectionHandler.loggableAddress(logIps, ctx.channel().remoteAddress()));
        cancelCheck();
        ctx.close();
        return true;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cancelCheck();
        ctx.fireChannelInactive();
    }

    private void cancelCheck() {
        if (checkTask != null) {
            checkTask.cancel(false);
            checkTask = null;
        }
    }
}
