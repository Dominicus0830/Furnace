// 서버 워치독(max-tick-time) — 바닐라 ServerWatchdog 의 멀티스레드 판. 바닐라는 서버 스레드 하나의 "다음 틱 시각" 만 보지만
// Furnace 는 틱을 하는 곳이 여럿(전역 스레드 + 차원별 region 워커)이라 각 곳을 Probe 로 보고, 어느 하나라도 max-tick-time 넘게 멈추면
// 스레드 덤프가 든 크래시 리포트를 쓰고 서버를 강제 종료한다. max-tick-time <= 0 이면 켜지 않는다(바닐라: -1 로 끈다).
// 판정(check)은 시계를 주입받아 시험에서 결정적으로 돈다. 워치독 스레드는 읽기만 하며 어떤 틱 상태도 건드리지 않는다.
package org.domi.furnace.server;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServerWatchdog implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ServerWatchdog.class);

    /** 틱이 멈췄는지 볼 곳 하나. */
    public interface Probe {
        String name();

        /** 지금 now 기준으로 이 곳이 멈춰 있던 시간(ns). 문제 없으면 0 이하. 잠금 없이 휘발성 값만 읽어야 한다. */
        long stalledNanos(long now);

        /** 멈춘 곳을 돌리는 스레드(스택을 크래시 리포트에 남긴다). 모르면 null. */
        Thread thread();
    }

    /** 임계를 넘은 곳. */
    public record Stall(Probe probe, long stalledNanos) {}

    private final long maxNanos;
    /** 감시 시작(생성) 시각. 멈춘 시간은 이보다 길게 셀 수 없다 — 기동에 오래 걸린 서버가 시작 직후 오탐으로 죽지 않게. */
    private final long watchStart;
    private final LongSupplier clock;
    private final Supplier<? extends Collection<? extends Probe>> probes;
    private final Consumer<Stall> onStall;
    private Thread thread;
    private volatile boolean running = true;

    public ServerWatchdog(long maxTickMillis, LongSupplier nanoClock, Supplier<? extends Collection<? extends Probe>> probes,
                          Consumer<Stall> onStall) {
        this.maxNanos = TimeUnit.MILLISECONDS.toNanos(maxTickMillis);
        this.clock = nanoClock;
        this.watchStart = nanoClock.getAsLong();
        this.probes = probes;
        this.onStall = onStall;
    }

    /** max-tick-time 이 양수일 때만 켠다(바닐라 bv() &gt; 0). */
    public static boolean enabled(long maxTickMillis) { return maxTickMillis > 0; }

    /** 임계를 넘은 곳 중 가장 오래 멈춘 곳(없으면 null). */
    public Stall check() {
        long now = clock.getAsLong();
        Stall worst = null;
        for (Probe p : probes.get()) {
            long s = Math.min(p.stalledNanos(now), now - watchStart);
            if (s > maxNanos && (worst == null || s > worst.stalledNanos())) worst = new Stall(p, s);
        }
        return worst;
    }

    public void start() {
        thread = new Thread(this::loop, "Server Watchdog");
        thread.setDaemon(true);
        thread.start();
    }

    private void loop() {
        // 임계의 1/4(최대 1초)마다 본다 — 바닐라는 마감까지 자지만 여기선 곳이 여럿이라 주기 검사.
        long pollMillis = Math.max(5L, Math.min(1000L, TimeUnit.NANOSECONDS.toMillis(maxNanos) / 4));
        while (running) {
            try {
                Stall s = check();
                if (s != null) {
                    onStall.accept(s);
                    return;
                }
                Thread.sleep(pollMillis);
            } catch (InterruptedException e) {
                if (!running) return;
            } catch (Throwable t) {
                log.error("워치독 검사 실패", t);
            }
        }
    }

    @Override
    public void close() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    /**
     * 운영용 처리(바닐라 apq): 치명 로그 → 모든 스레드 덤프 → 크래시 리포트 → 종료(10초 뒤에도 안 끝나면 halt).
     * exit 은 별도 훅(시험이 바꿔 끼운다).
     */
    public static Consumer<Stall> crashHandler(Path crashDir, Runnable exit) {
        return stall -> {
            log.error("A single server tick took {} seconds ({}) — max-tick-time 초과", String.format(Locale.ROOT, "%.2f", stall.stalledNanos() / 1.0E9),
                    stall.probe().name());
            log.error("Considering it to be crashed, server will forcibly shutdown.");
            StringBuilder dump = new StringBuilder();
            Error cause = new Error("Watchdog: " + stall.probe().name());
            Thread t = stall.probe().thread();
            for (ThreadInfo info : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
                if (t != null && info.getThreadId() == t.threadId()) cause.setStackTrace(info.getStackTrace());
                dump.append(info).append('\n');
            }
            CrashReport.write(crashDir, "Watching Server (" + stall.probe().name() + ")", cause, t, dump.toString());
            exit.run();
        };
    }

    /** System.exit(1), 10초 안에 못 끝나면 halt(1). */
    public static void exitOrHalt() {
        try {
            new Timer("watchdog-halt", true).schedule(new TimerTask() {
                @Override
                public void run() { Runtime.getRuntime().halt(1); }
            }, 10_000L);
            System.exit(1);
        } catch (Throwable t) {
            Runtime.getRuntime().halt(1);
        }
    }
}
