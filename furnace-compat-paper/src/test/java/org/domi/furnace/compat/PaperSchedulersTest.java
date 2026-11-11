// PaperSchedulers: 전역·region·엔티티·비동기 스케줄러와 Paper 의 인자 규칙·상태·취소 결과.
package org.domi.furnace.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.compat.sched.CompatTask;
import org.domi.furnace.compat.sched.CompatTask.CancelledState;
import org.domi.furnace.compat.sched.CompatTask.ExecutionState;
import org.domi.furnace.compat.sched.PluginDisabledException;
import org.domi.furnace.compat.thread.PrimaryThread;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.Test;

class PaperSchedulersTest {

    private static FurnaceTestServer start() { return FurnaceTestServer.builder().exposeCompatToNative(true).plugin(CompatTestPlugin.class).start(); }

    @Test
    void globalRunsOnGlobalThreadWithPaperDelayRules() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            Thread global = server.onGlobal(Thread::currentThread);
            List<String> log = new CopyOnWriteArrayList<>();
            AtomicReference<Thread> on = new AtomicReference<>();
            p.paper.global().execute(p, () -> on.set(Thread.currentThread()));
            p.paper.global().runDelayed(p, t -> log.add("delayed"), 3);
            p.paper.global().runAtFixedRate(p, t -> log.add("rate"), 2, 2);
            assertThrows(IllegalArgumentException.class, () -> p.paper.global().runDelayed(p, t -> {}, 0));
            assertThrows(IllegalArgumentException.class, () -> p.paper.global().runAtFixedRate(p, t -> {}, 0, 1));
            assertThrows(IllegalArgumentException.class, () -> p.paper.global().runAtFixedRate(p, t -> {}, 1, 0));
            server.tick();
            assertSame(global, on.get());
            server.tick(4);   // 틱 2: rate, 틱 3: delayed, 틱 4: rate, 틱 5: -
            assertEquals(List.of("rate", "delayed", "rate"), log);
        }
    }

    @Test
    void stateMachineAndCancelResults() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            AtomicReference<CompatTask> self = new AtomicReference<>();
            AtomicReference<ExecutionState> during = new AtomicReference<>();
            AtomicReference<CancelledState> cancelWhileRunning = new AtomicReference<>();
            CompatTask once = p.paper.global().run(p, t -> {
                during.set(t.state());
                cancelWhileRunning.set(t.cancel());
            });
            assertEquals(ExecutionState.IDLE, once.state());
            assertFalse(once.isRepeating());
            server.tick();
            assertEquals(ExecutionState.RUNNING, during.get());
            assertEquals(CancelledState.RUNNING, cancelWhileRunning.get(), "도는 한 번 작업은 취소 못 한다");
            assertEquals(ExecutionState.FINISHED, once.state());
            assertEquals(CancelledState.ALREADY_EXECUTED, once.cancel());

            AtomicInteger runs = new AtomicInteger();
            CompatTask idle = p.paper.global().runDelayed(p, t -> runs.incrementAndGet(), 5);
            assertEquals(CancelledState.CANCELLED_BY_CALLER, idle.cancel());
            assertEquals(CancelledState.CANCELLED_ALREADY, idle.cancel());
            assertTrue(idle.isCancelled());
            server.tick(6);
            assertEquals(0, runs.get());

            AtomicReference<CancelledState> repCancel = new AtomicReference<>();
            AtomicInteger repRuns = new AtomicInteger();
            self.set(p.paper.global().runAtFixedRate(p, t -> {
                repRuns.incrementAndGet();
                repCancel.compareAndSet(null, t.cancel());
            }, 1, 1));
            server.tick(3);
            assertEquals(1, repRuns.get(), "도는 중에 취소하면 다음 실행부터 사라진다");
            assertEquals(CancelledState.NEXT_RUNS_CANCELLED, repCancel.get());
            assertEquals(ExecutionState.CANCELLED, self.get().state());
            assertTrue(self.get().isRepeating());
        }
    }

    @Test
    void regionTasksRunOnRegionThreadNotGlobal() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            var world = server.overworld();
            Thread global = server.onGlobal(Thread::currentThread);
            AtomicReference<Thread> byChunk = new AtomicReference<>(), byLoc = new AtomicReference<>();
            AtomicInteger repeating = new AtomicInteger();
            p.paper.region().execute(p, world, 0, 0, () -> byChunk.set(Thread.currentThread()));
            p.paper.region().runDelayed(p, new Location("minecraft:overworld", 3.5, 64, 3.5), t -> byLoc.set(Thread.currentThread()), 2);
            p.paper.region().runAtFixedRate(p, world, 0, 0, t -> repeating.incrementAndGet(), 1, 1);
            server.tick();
            assertNotNull(byChunk.get());
            assertNotSame(global, byChunk.get());
            assertNull(byLoc.get());
            server.tick();
            assertNotNull(byLoc.get());
            assertEquals(2, repeating.get());
            assertThrows(IllegalArgumentException.class, () -> p.paper.region().run(p, new Location("minecraft:nope", 0, 0, 0), t -> {}));
            assertThrows(IllegalArgumentException.class, () -> p.paper.region().runDelayed(p, world, 0, 0, t -> {}, 0));
        }
    }

    @Test
    void entityTasksFollowEntityAndRetiredFiresOnDisconnect() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            var steve = server.join("Steve");
            var player = steve.player();
            AtomicInteger warm = new AtomicInteger();   // 플레이어가 region 에 올라야 엔티티 작업이 돈다 — 다른 시험과 섞이면 걸리는 틱 수가 달라서 한 번 돌 때까지 낸다
            CompatTask w = p.paper.entity().runAtFixedRate(p, player, t -> warm.incrementAndGet(), null, 1, 1);
            for (int i = 0; i < 200 && warm.get() == 0; i++) server.tick();
            assertTrue(warm.get() > 0);
            w.cancel();
            AtomicInteger runs = new AtomicInteger(), retired = new AtomicInteger();
            CompatTask task = p.paper.entity().runAtFixedRate(p, player, t -> runs.incrementAndGet(), retired::incrementAndGet, 1, 1);
            assertNotNull(task);
            server.tick(3);
            assertEquals(3, runs.get());
            steve.close();
            server.tick(2);
            assertEquals(1, retired.get(), "엔티티가 사라지면 retired 가 한 번");
            assertTrue(task.isCancelled());
            int after = runs.get();
            server.tick(3);
            assertEquals(after, runs.get());
            assertNull(p.paper.entity().run(p, player, t -> {}, null), "이미 사라진 엔티티는 예약하지 않는다");
            assertFalse(p.paper.entity().execute(p, player, () -> {}, null, 1));
        }
    }

    @Test
    void asyncSchedulerRunsOffTickThreadsWithWallClock() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            CountDownLatch now = new CountDownLatch(1), delayed = new CountDownLatch(1), rate = new CountDownLatch(3);
            AtomicReference<Boolean> primary = new AtomicReference<>();
            p.paper.async().runNow(p, t -> {
                primary.set(PrimaryThread.isPrimary());
                now.countDown();
            });
            assertTrue(now.await(5, TimeUnit.SECONDS), "틱을 내지 않아도 돈다");
            assertEquals(Boolean.FALSE, primary.get());
            p.paper.async().runDelayed(p, t -> delayed.countDown(), 30, TimeUnit.MILLISECONDS);
            assertTrue(delayed.await(5, TimeUnit.SECONDS));
            CompatTask rep = p.paper.async().runAtFixedRate(p, t -> rate.countDown(), 0, 10, TimeUnit.MILLISECONDS);
            assertTrue(rate.await(5, TimeUnit.SECONDS));
            assertEquals(CancelledState.CANCELLED_BY_CALLER.getClass(), rep.cancel().getClass());
            CompatTask never = p.paper.async().runDelayed(p, t -> fail("취소한 작업이 돌았다"), 100, TimeUnit.MILLISECONDS);
            never.cancel();
            Thread.sleep(250);
            assertThrows(IllegalArgumentException.class, () -> p.paper.async().runAtFixedRate(p, t -> {}, 0, 0, TimeUnit.SECONDS));
            assertThrows(IllegalArgumentException.class, () -> p.paper.async().runDelayed(p, t -> {}, -1, TimeUnit.SECONDS));
        }
    }

    @Test
    void disablingPluginCancelsEverythingAndBlocksNewTasks() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            AtomicInteger runs = new AtomicInteger();
            CompatTask g = p.paper.global().runAtFixedRate(p, t -> runs.incrementAndGet(), 1, 1);
            CompatTask r = p.paper.region().runAtFixedRate(p, server.overworld(), 0, 0, t -> runs.incrementAndGet(), 1, 1);
            CompatTask a = p.paper.async().runDelayed(p, t -> runs.incrementAndGet(), 10, TimeUnit.SECONDS);
            server.api().plugins().disable(p);
            assertTrue(g.isCancelled() && r.isCancelled() && a.isCancelled());
            server.tick(3);
            assertEquals(0, runs.get());
            assertThrows(PluginDisabledException.class, () -> p.paper.global().execute(p, () -> {}));
        }
    }

    @Test
    void exceptionInTaskDoesNotKillRepeating() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            AtomicInteger runs = new AtomicInteger();
            p.paper.global().runAtFixedRate(p, t -> {
                runs.incrementAndGet();
                throw new IllegalStateException("시험용 예외");
            }, 1, 1);
            server.tick(3);
            assertEquals(3, runs.get());
        }
    }

    @Test
    void asyncFixedRateDoesNotDropSlowRuns() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            AtomicInteger runs = new AtomicInteger();
            var task = p.paper.async().runAtFixedRate(p, t -> {
                runs.incrementAndGet();
                try {
                    Thread.sleep(60);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, 0, 50, TimeUnit.MILLISECONDS);
            Thread.sleep(1200);
            task.cancel();
            assertTrue(runs.get() >= 16, "Paper: 늦으면 바로 이어 돌린다(약 60ms 마다 ~20회, 회차를 버리면 13회). 실제 " + runs.get());
        }
    }

    @Test
    void asyncFixedRateCancelInsideFirstRunStopsTheTimer() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            AtomicInteger runs = new AtomicInteger();
            p.paper.async().runAtFixedRate(p, t -> {
                runs.incrementAndGet();
                t.cancel();
            }, 0, 10, TimeUnit.MILLISECONDS);
            Thread.sleep(200);
            assertEquals(1, runs.get());
        }
    }

    // ---- Paper 1.21.1 FoliaAsyncScheduler: 이름 붙은 플랫폼 풀

    @Test
    void asyncSchedulerRunsOnAPaperShapedPlatformPool() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            assertTrue(p.paper.usesPlatformThreads());
            AtomicReference<Thread> now = new AtomicReference<>(), delayed = new AtomicReference<>(), rep = new AtomicReference<>();
            CountDownLatch all = new CountDownLatch(3);
            p.paper.async().runNow(p, t -> {
                now.set(Thread.currentThread());
                all.countDown();
            });
            p.paper.async().runDelayed(p, t -> {
                delayed.set(Thread.currentThread());
                all.countDown();
            }, 20, TimeUnit.MILLISECONDS);
            CompatTask task = p.paper.async().runAtFixedRate(p, t -> {
                if (rep.compareAndSet(null, Thread.currentThread())) all.countDown();
            }, 0, 10, TimeUnit.MILLISECONDS);
            assertTrue(all.await(5, TimeUnit.SECONDS));
            task.cancel();
            for (Thread th : new Thread[]{now.get(), delayed.get(), rep.get()}) {
                assertFalse(th.isVirtual(), "가상 스레드가 아니다");
                assertTrue(th.getName().matches("Folia Async Scheduler Thread #\\d+"), th.getName());
                assertTrue(th.isDaemon(), "멈춘 작업이 JVM 종료를 붙잡지 않는다");
                assertEquals(Thread.NORM_PRIORITY - 1, th.getPriority());
            }
            assertTrue(Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.getName().equals("Folia Async Scheduler Thread Timer")
                    && t.getPriority() == Thread.NORM_PRIORITY + 1), "시간 재는 스레드(Paper 이름·우선순위)");
        }
    }

    /** Paper 풀은 대기열이 없다(SynchronousQueue) — 코어 수보다 많은 작업이 동시에 서 있어도 서로 기다리지 않는다. */
    @Test
    void asyncSchedulerPoolDoesNotQueueBehindBlockedTasks() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            int n = Runtime.getRuntime().availableProcessors() * 2 + 8;
            CountDownLatch started = new CountDownLatch(n), release = new CountDownLatch(1);
            for (int i = 0; i < n; i++) {
                Object lock = new Object();
                p.paper.async().runNow(p, t -> {
                    synchronized (lock) {
                        started.countDown();
                        try {
                            release.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            }
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS), "전부 동시에 선다");
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void virtualOptionRunsPaperAsyncTasksOnTheServersVirtualThreads() throws Exception {
        try (var server = FurnaceTestServer.builder().exposeCompatToNative(true).plugin(CompatTestPlugin.class)
                .compatOption(org.domi.furnace.compat.sched.LegacyScheduler.ASYNC_THREADS_OPTION, "virtual").start()) {
            var p = server.plugin(CompatTestPlugin.class);
            assertFalse(p.paper.usesPlatformThreads());
            AtomicReference<Thread> th = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            p.paper.async().runNow(p, t -> {
                th.set(Thread.currentThread());
                done.countDown();
            });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertTrue(th.get().isVirtual());
        }
    }

    @Test
    void closingTheSchedulersLetsARunningAsyncTaskFinishWithoutInterrupt() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            CountDownLatch inside = new CountDownLatch(1);
            AtomicReference<Boolean> interrupted = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            p.paper.async().runNow(p, t -> {
                inside.countDown();
                try {
                    Thread.sleep(300);
                    interrupted.set(false);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                }
                done.countDown();
            });
            assertTrue(inside.await(5, TimeUnit.SECONDS));
            p.paper.close();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(Boolean.FALSE, interrupted.get(), "닫아도 도는 작업을 끊지 않는다");
        }
    }
}
