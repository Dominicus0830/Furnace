// T-034(결정 D1): 플러그인 비동기 실행기 — 기본 플랫폼 풀(이름·데몬·인터럽트·이름 되돌리기·예외 뒤 재사용), virtual 옵션, 닫힘, 설정 키,
// 그리고 운반 스레드 수(코어 수)보다 많은 작업이 synchronized 안에서 기다려도 모두 동시에 서는지(가상 스레드 붙잡힘 회귀).
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class PluginAsyncExecutorTest {

    private final PluginAsyncExecutor ex = new PluginAsyncExecutor();

    @AfterEach
    void close() throws InterruptedException {
        ex.shutdownNow();
        ex.awaitTermination(5, TimeUnit.SECONDS);
    }

    private Thread runAndGetThread() throws Exception {
        return CompletableFuture.supplyAsync(Thread::currentThread, ex).get(5, TimeUnit.SECONDS);
    }

    @Test
    void defaultIsNamedDaemonPlatformThreads() throws Exception {
        assertFalse(ex.usesVirtualThreads());
        Thread t = runAndGetThread();
        assertFalse(t.isVirtual(), "기본은 플랫폼 스레드(결정 D1)");
        assertTrue(t.isDaemon(), "멈춘 플러그인 작업이 JVM 종료를 붙잡지 않는다");
        assertTrue(t.getName().matches("Furnace Async Thread - \\d+"), t.getName());
    }

    @Test
    void virtualOptionRunsOnVirtualThreads() throws Exception {
        ex.useVirtualThreads(true);
        Thread t = runAndGetThread();
        assertTrue(t.isVirtual());
        assertTrue(t.getName().startsWith(PluginAsyncExecutor.VIRTUAL_THREAD_PREFIX), t.getName());
    }

    /** 플랫폼 스레드는 다시 쓰이므로 앞 작업이 남긴 인터럽트 표시·바꾼 이름이 다음 작업에 새지 않는다. */
    @Test
    void reusedThreadDoesNotLeakInterruptOrName() throws Exception {
        // 코어 스레드 4개를 먼저 다 띄운다 — 그 뒤로는 놀고 있는 스레드가 받는다(SynchronousQueue 는 마지막에 쉬러 간 스레드부터 준다)
        for (int i = 0; i < PluginAsyncExecutor.CORE_THREADS * 2; i++) runAndGetThread();
        AtomicReference<Thread> first = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        ex.execute(() -> {
            first.set(Thread.currentThread());
            Thread.currentThread().setName("my-plugin-worker");
            Thread.currentThread().interrupt();
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        // 같은 스레드가 다시 잡힐 때까지 돌린다
        for (int i = 0; i < 200; i++) {
            AtomicBoolean interrupted = new AtomicBoolean();
            Thread t = CompletableFuture.supplyAsync(() -> {
                interrupted.set(Thread.currentThread().isInterrupted());
                return Thread.currentThread();
            }, ex).get(5, TimeUnit.SECONDS);
            assertFalse(interrupted.get(), "앞 작업의 인터럽트가 남지 않는다");
            assertTrue(t.getName().startsWith(PluginAsyncExecutor.PLATFORM_THREAD_PREFIX), "이름이 되돌아온다: " + t.getName());
            if (t == first.get()) return;
        }
        fail("같은 스레드가 다시 쓰이지 않았다");
    }

    @Test
    void uncaughtExceptionIsLoggedAndThreadKeepsWorking() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("Furnace");
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            CountDownLatch thrown = new CountDownLatch(1);
            ex.execute(() -> {
                thrown.countDown();
                throw new IllegalStateException("boom-t034");
            });
            assertTrue(thrown.await(5, TimeUnit.SECONDS));
            assertFalse(runAndGetThread().isVirtual(), "다음 작업도 돈다");
            for (int i = 0; i < 100 && appender.list.isEmpty(); i++) Thread.sleep(20);
            var ev = appender.list.stream().filter(e -> e.getThrowableProxy() != null && "boom-t034".equals(e.getThrowableProxy().getMessage()))
                    .findFirst().orElseThrow(() -> new AssertionError("예외가 서버 로그에 남는다: " + appender.list));
            assertTrue(ev.getFormattedMessage().contains(PluginAsyncExecutor.PLATFORM_THREAD_PREFIX), "스레드 이름이 로그에: " + ev.getFormattedMessage());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void rejectsAfterShutdown() throws Exception {
        runAndGetThread();
        ex.shutdown();
        assertTrue(ex.isShutdown());
        assertThrows(RejectedExecutionException.class, () -> ex.execute(() -> { }));
        assertTrue(ex.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(ex.isTerminated());
    }

    @Test
    void neverUsedPoolTerminatesOnShutdown() {
        ex.shutdown();
        assertTrue(ex.isTerminated(), "처음 쓰기 전에 닫으면 바로 끝난다");
    }

    /**
     * O12-02 회귀: 운반 스레드 수(코어 수)보다 많은 작업이 저마다 synchronized 안에서 기다린다. JDK 21 가상 스레드였다면 코어 수만큼만
     * 들어서고 나머지는 운반 스레드를 못 얻어 장벽에 닿지 못한다(옛 JDBC 드라이버 모양). 플랫폼 풀은 모두 동시에 선다.
     */
    @Test
    void moreBlockedSynchronizedTasksThanCoresAllRunTogether() throws Exception {
        int n = Runtime.getRuntime().availableProcessors() + 2;
        CountDownLatch arrived = new CountDownLatch(n);
        CountDownLatch release = new CountDownLatch(1);
        List<CompletableFuture<Void>> all = new ArrayList<>();
        try {
            for (int i = 0; i < n; i++) {
                Object monitor = new Object();
                all.add(CompletableFuture.runAsync(() -> {
                    synchronized (monitor) {
                        arrived.countDown();
                        try {
                            release.await(15, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }, ex));
            }
            assertTrue(arrived.await(10, TimeUnit.SECONDS), "모든 작업이 동시에 들어선다(남은 수 " + arrived.getCount() + "/" + n + ")");
            // 그 와중에 가벼운 작업도 바로 돈다(O12PinBench: 가상 452ms, 플랫폼 1ms)
            long t0 = System.nanoTime();
            CompletableFuture.runAsync(() -> { }, ex).get(5, TimeUnit.SECONDS);
            assertTrue(System.nanoTime() - t0 < TimeUnit.SECONDS.toNanos(2), "가벼운 작업이 줄 서지 않는다");
        } finally {
            release.countDown();
        }
        CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
    }

    @Test
    void settingsKeyParsesStrictly() {
        Properties p = new Properties();
        var log = LoggerFactory.getLogger("t034");
        assertFalse(PluginSettings.from(p, log).asyncVirtual(), "안 적으면 platform");
        assertFalse(PluginSettings.DEFAULT.asyncVirtual());
        p.setProperty(PluginSettings.PLUGIN_ASYNC_THREADS_KEY, " Virtual ");
        assertTrue(PluginSettings.from(p, log).asyncVirtual());
        p.setProperty(PluginSettings.PLUGIN_ASYNC_THREADS_KEY, "platform");
        assertFalse(PluginSettings.from(p, log).asyncVirtual());
        p.setProperty(PluginSettings.PLUGIN_ASYNC_THREADS_KEY, "fiber");
        var e = assertThrows(IllegalArgumentException.class, () -> PluginSettings.from(p, log));
        assertTrue(e.getMessage().contains(PluginSettings.PLUGIN_ASYNC_THREADS_KEY) && e.getMessage().contains("fiber"), e.getMessage());
        // with… 복사가 값을 지킨다
        var s = PluginSettings.DEFAULT.withAsyncVirtual(true);
        assertTrue(s.withCompatNms(false).withLibraries(s.libraries()).withExposeCompatToNative(true).withCompat(false)
                .withChunkTicketLimit(5).withCompatOptions(java.util.Map.of()).asyncVirtual());
    }
}
