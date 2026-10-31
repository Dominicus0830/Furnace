package org.domi.furnace.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** max-tick-time 워치독: 판정은 시계 주입으로 결정적, 스레드 경로는 짧은 임계로. */
class ServerWatchdogTest {

    private static final class FakeProbe implements ServerWatchdog.Probe {
        final String name;
        volatile long stalled;

        FakeProbe(String name) { this.name = name; }

        @Override public String name() { return name; }

        @Override public long stalledNanos(long now) { return stalled; }

        @Override public Thread thread() { return Thread.currentThread(); }
    }

    @Test
    void enabledOnlyForPositive() {
        assertFalse(ServerWatchdog.enabled(-1));
        assertFalse(ServerWatchdog.enabled(0));
        assertTrue(ServerWatchdog.enabled(60_000));
    }

    @Test
    void checkReportsWorstProbeOverThreshold() {
        FakeProbe a = new FakeProbe("global"), b = new FakeProbe("region");
        var wd = new ServerWatchdog(1000, System::nanoTime, () -> List.of(a, b), s -> {});
        assertNull(wd.check());
        a.stalled = TimeUnit.MILLISECONDS.toNanos(1000);   // 정확히 임계는 아직 아니다
        assertNull(wd.check());
        a.stalled = TimeUnit.MILLISECONDS.toNanos(1500);
        b.stalled = TimeUnit.MILLISECONDS.toNanos(2500);
        var s = wd.check();
        assertSame(b, s.probe());
        assertEquals(TimeUnit.MILLISECONDS.toNanos(2500), s.stalledNanos());
    }

    @Test
    void watchdogThreadFiresOnceWhenAProbeStalls() throws Exception {
        FakeProbe p = new FakeProbe("global tick");
        CountDownLatch fired = new CountDownLatch(1);
        AtomicReference<ServerWatchdog.Stall> got = new AtomicReference<>();
        AtomicLong calls = new AtomicLong();
        try (var wd = new ServerWatchdog(100, System::nanoTime, () -> List.of(p), s -> {
            calls.incrementAndGet();
            got.set(s);
            fired.countDown();
        })) {
            wd.start();
            Thread.sleep(300);
            assertEquals(1, fired.getCount());   // 멈추지 않았으면 울리지 않는다
            p.stalled = TimeUnit.MILLISECONDS.toNanos(500);
            assertTrue(fired.await(10, TimeUnit.SECONDS));
            assertSame(p, got.get().probe());
        }
        assertEquals(1, calls.get());
    }

    @Test
    void crashHandlerWritesReportWithThreadDumpAndExits(@TempDir Path tmp) throws Exception {
        Path dir = tmp.resolve("crash-reports");
        boolean[] exited = new boolean[1];
        FakeProbe p = new FakeProbe("region tick minecraft:overworld");
        ServerWatchdog.crashHandler(dir, () -> exited[0] = true).accept(new ServerWatchdog.Stall(p, TimeUnit.SECONDS.toNanos(61)));
        assertTrue(exited[0]);
        try (var files = Files.list(dir)) {
            Path f = files.findFirst().orElseThrow();
            String s = Files.readString(f);
            assertTrue(s.contains("Description: Watching Server (region tick minecraft:overworld)"), s);
            assertTrue(s.contains("java.lang.Error: Watchdog: region tick minecraft:overworld"), s);
            assertTrue(s.contains("-- Thread Dump --"), s);
            assertTrue(s.contains(Thread.currentThread().getName()), s);
            assertNotNull(f);
        }
    }
}
