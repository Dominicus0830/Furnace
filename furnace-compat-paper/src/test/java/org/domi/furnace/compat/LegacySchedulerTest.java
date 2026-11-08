// LegacyScheduler: Bukkit 스케줄러 의미(틱 규칙·인자 보정·예외 계속·취소·플러그인 비활성 정리)를 결정적 틱으로 확인한다.
package org.domi.furnace.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.domi.furnace.compat.sched.LegacyScheduler;
import org.domi.furnace.compat.sched.LegacyTask;
import org.domi.furnace.compat.sched.PluginDisabledException;
import org.domi.furnace.compat.thread.PrimaryThread;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.Test;

class LegacySchedulerTest {

    private static FurnaceTestServer start() { return FurnaceTestServer.builder().exposeCompatToNative(true).plugin(CompatTestPlugin.class).start(); }

    @Test
    void syncTaskRunsNextTickOnGlobalThreadMarkedPrimary() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            Thread global = server.onGlobal(Thread::currentThread);
            AtomicReference<Thread> ranOn = new AtomicReference<>();
            AtomicReference<Boolean> primary = new AtomicReference<>();
            p.legacy.runTask(p, () -> {
                ranOn.set(Thread.currentThread());
                primary.set(PrimaryThread.isPrimary());
            });
            assertNull(ranOn.get(), "틱 전에는 돌지 않는다");
            assertFalse(PrimaryThread.isPrimary(), "시험 스레드는 주 스레드가 아니다");
            server.tick();
            assertSame(global, ranOn.get());
            assertTrue(primary.get());
        }
    }

    @Test
    void delayRulesMatchCraftScheduler() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            List<String> log = new CopyOnWriteArrayList<>();
            AtomicInteger tick = new AtomicInteger();
            p.legacy.runTaskLater(p, () -> log.add("d3@" + tick.get()), 3);
            p.legacy.runTaskLater(p, () -> log.add("d0@" + tick.get()), 0);
            p.legacy.runTaskLater(p, () -> log.add("d1@" + tick.get()), 1);
            p.legacy.runTaskLater(p, () -> log.add("neg@" + tick.get()), -5);
            for (int i = 1; i <= 4; i++) {
                tick.set(i);
                server.tick();
            }
            assertEquals(List.of("d0@1", "neg@1", "d1@1", "d3@3"), log, "0·1·음수는 다음 틱, 3 은 세 번째 틱, 같은 틱 안에서는 (다음 실행 틱, 번호) 순 — CraftScheduler 와 같다");
        }
    }

    @Test
    void timerPeriodsAndArgumentNormalization() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            List<Integer> every3 = new ArrayList<>(), zero = new ArrayList<>(), once = new ArrayList<>(), minusTwo = new ArrayList<>();
            AtomicInteger tick = new AtomicInteger();
            p.legacy.runTaskTimer(p, () -> every3.add(tick.get()), 2, 3);
            p.legacy.runTaskTimer(p, () -> zero.add(tick.get()), 0, 0);       // 주기 0 은 1
            p.legacy.runTaskTimer(p, () -> once.add(tick.get()), 1, -1);      // 반복 없음
            p.legacy.runTaskTimer(p, () -> minusTwo.add(tick.get()), 1, -9);  // -1 보다 작으면 반복 없음
            for (int i = 1; i <= 9; i++) {
                tick.set(i);
                server.tick();
            }
            assertEquals(List.of(2, 5, 8), every3);
            assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), zero);
            assertEquals(List.of(1), once);
            assertEquals(List.of(1), minusTwo);
        }
    }

    @Test
    void exceptionDoesNotStopRepeatingTask() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            AtomicInteger runs = new AtomicInteger();
            p.legacy.runTaskTimer(p, () -> {
                runs.incrementAndGet();
                throw new IllegalStateException("boom(시험용 — 로그에 한 줄)");
            }, 1, 1);
            server.tick(4);
            assertEquals(4, runs.get());
        }
    }

    @Test
    void cancelFromInsideAndOutside() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            AtomicInteger inside = new AtomicInteger(), outside = new AtomicInteger();
            AtomicReference<LegacyTask> self = new AtomicReference<>();
            self.set(p.legacy.runTaskTimer(p, () -> {
                if (inside.incrementAndGet() == 2) self.get().cancel();
            }, 1, 1));
            LegacyTask out = p.legacy.runTaskTimer(p, outside::incrementAndGet, 1, 1);
            assertTrue(p.legacy.isQueued(out.taskId()));
            server.tick(3);
            assertEquals(2, inside.get(), "안에서 취소하면 그 뒤로 안 돈다");
            assertTrue(self.get().isCancelled());
            assertEquals(3, outside.get());
            p.legacy.cancelTask(out.taskId());
            server.tick(3);
            assertEquals(3, outside.get());
            assertFalse(p.legacy.isQueued(out.taskId()));
            assertTrue(out.isCancelled());
        }
    }

    @Test
    void idsQueueAndPendingTasks() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            LegacyTask a = p.legacy.runTaskLater(p, () -> {}, 5);
            LegacyTask b = p.legacy.runTaskTimer(p, () -> {}, 1, 1);
            int c = p.legacy.scheduleSyncDelayedTask(p, () -> {}, 2);
            assertTrue(a.taskId() >= 1 && b.taskId() > a.taskId() && c > b.taskId());
            assertEquals(List.of(a.taskId(), b.taskId(), c), p.legacy.pendingTasks().stream().map(LegacyTask::taskId).toList());
            server.tick(2);
            assertFalse(p.legacy.isQueued(c), "한 번 작업은 돈 뒤 목록에서 빠진다");
            assertTrue(p.legacy.isQueued(b.taskId()), "반복 작업은 남는다");
            assertTrue(a.isSync());
            assertSame(p, a.owner());
        }
    }

    @Test
    void isCurrentlyRunningInsideTask() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            AtomicInteger id = new AtomicInteger();
            AtomicReference<Boolean> during = new AtomicReference<>();
            id.set(p.legacy.runTask(p, () -> during.set(p.legacy.isCurrentlyRunning(id.get()))).taskId());
            server.tick();
            assertEquals(Boolean.TRUE, during.get());
            assertFalse(p.legacy.isCurrentlyRunning(id.get()));
        }
    }

    @Test
    void asyncTaskArrivesWithTickThenRunsOffThread() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Thread> ranOn = new AtomicReference<>();
            AtomicReference<Boolean> primary = new AtomicReference<>();
            AtomicReference<String> nameInside = new AtomicReference<>();
            LegacyTask t = p.legacy.runTaskLaterAsynchronously(p, () -> {
                ranOn.set(Thread.currentThread());
                nameInside.set(Thread.currentThread().getName());
                primary.set(PrimaryThread.isPrimary());
                done.countDown();
            }, 2);
            assertFalse(t.isSync());
            server.tick();
            assertFalse(done.await(200, TimeUnit.MILLISECONDS), "한 틱 뒤에는 아직");
            server.tick();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertFalse(ranOn.get().isVirtual(), "기본은 플랫폼 스레드 풀(Paper 모양) — 가상 스레드가 아니다");
            assertTrue(p.legacy.usesPlatformThreads());
            assertTrue(nameInside.get().matches("Craft Scheduler Thread - \\d+ - " + java.util.regex.Pattern.quote(p.name())),
                    "일하는 동안 스레드 이름에 플러그인 이름이 붙는다(Paper): " + nameInside.get());
            assertEquals(Boolean.FALSE, primary.get());
            Thread global = server.onGlobal(Thread::currentThread);
            assertNotSame(global, ranOn.get());
        }
    }

    @Test
    void asyncRepeatingKeepsTickRhythmAndWorkersAreListed() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            AtomicInteger runs = new AtomicInteger();
            CountDownLatch inside = new CountDownLatch(1), release = new CountDownLatch(1);
            LegacyTask t = p.legacy.runTaskTimerAsynchronously(p, () -> {
                if (runs.incrementAndGet() == 1) {
                    inside.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, 1, 1);
            server.tick();
            assertTrue(inside.await(5, TimeUnit.SECONDS));
            assertTrue(p.legacy.isCurrentlyRunning(t.taskId()));
            assertEquals(1, p.legacy.activeWorkers().size());
            assertEquals(t.taskId(), p.legacy.activeWorkers().get(0).taskId());
            release.countDown();
            t.cancel();
            server.tick(2);
            assertTrue(t.isCancelled());
        }
    }

    @Test
    void callSyncMethodReturnsValueAndPropagatesExceptions() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            Future<Boolean> ok = p.legacy.callSyncMethod(p, PrimaryThread::isPrimary);
            Future<String> bad = p.legacy.callSyncMethod(p, () -> { throw new IllegalArgumentException("x"); });
            assertFalse(ok.isDone());
            server.tick();
            assertTrue(ok.get(1, TimeUnit.SECONDS));
            ExecutionException e = assertThrows(ExecutionException.class, () -> bad.get(1, TimeUnit.SECONDS));
            assertInstanceOf(IllegalArgumentException.class, e.getCause());
        }
    }

    @Test
    void disablingPluginCancelsTasksAndFuturesAndBlocksNewOnes() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            AtomicInteger runs = new AtomicInteger();
            LegacyTask rep = p.legacy.runTaskTimer(p, runs::incrementAndGet, 1, 1);
            Future<String> pending = p.legacy.callSyncMethod(p, () -> "never");
            server.api().plugins().disable(p);
            assertTrue(rep.isCancelled());
            assertTrue(pending.isCancelled() || pending.isDone());
            assertThrows(CancellationException.class, () -> pending.get(1, TimeUnit.SECONDS));
            server.tick(3);
            assertEquals(0, runs.get());
            assertThrows(PluginDisabledException.class, () -> p.legacy.runTask(p, () -> {}));
        }
    }

    @Test
    void cancelTasksOnlyAffectsThatPlugin() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            AtomicInteger a = new AtomicInteger();
            p.legacy.runTaskTimer(p, a::incrementAndGet, 1, 1);
            server.tick();
            p.legacy.cancelTasks(p);
            server.tick(2);
            assertEquals(1, a.get());
            assertTrue(p.legacy.pendingTasks().isEmpty());
        }
    }

    @Test
    void argumentValidation() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            assertThrows(IllegalArgumentException.class, () -> p.legacy.runTask(null, () -> {}));
            assertThrows(IllegalArgumentException.class, () -> p.legacy.runTask(p, null));
        }
    }

    // ---- 독립 검토(PR #130) 반영

    @Test
    void sameTickOrderFollowsCreationLikeCraftScheduler() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            List<String> log = new CopyOnWriteArrayList<>();
            AtomicInteger tick = new AtomicInteger();
            p.legacy.runTaskTimer(p, () -> log.add("A@" + tick.get()), 1, 2);   // 먼저 만든 반복 작업
            p.legacy.runTaskLater(p, () -> log.add("B@" + tick.get()), 3);
            for (int i = 1; i <= 3; i++) {
                tick.set(i);
                server.tick();
            }
            assertEquals(List.of("A@1", "A@3", "B@3"), log, "같은 틱에서는 만든 순(번호) — 반복 작업은 다시 걸려도 순서를 지킨다");
        }
    }

    @Test
    void taskScheduledInsideTaskWithZeroDelayRunsSameTick() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            List<String> log = new CopyOnWriteArrayList<>();
            p.legacy.runTask(p, () -> {
                log.add("outer");
                p.legacy.runTask(p, () -> log.add("inner"));
                p.legacy.runTaskTimer(p, () -> log.add("timer"), 0, 1);
            });
            server.tick();
            assertEquals(List.of("outer", "inner", "timer"), log, "지연 0 은 같은 박동에 이어서(반복 작업의 첫 회차도), 다음 회차는 다음 틱");
            server.tick();
            assertEquals(List.of("outer", "inner", "timer", "timer"), log);
        }
    }

    @Test
    void callSyncMethodFutureCancelStopsCallable() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            java.util.concurrent.atomic.AtomicBoolean called = new java.util.concurrent.atomic.AtomicBoolean();
            Future<String> f = p.legacy.callSyncMethod(p, () -> {
                called.set(true);
                return "x";
            });
            assertTrue(f.cancel(false));
            assertTrue(f.isCancelled());
            server.tick(2);
            assertFalse(called.get(), "CraftFuture.cancel 은 아직 안 돈 callable 을 막는다");
            Future<String> done = p.legacy.callSyncMethod(p, () -> "y");
            server.tick();
            assertEquals("y", done.get(1, TimeUnit.SECONDS));
            assertFalse(done.cancel(false), "이미 끝난 future 는 취소할 수 없다");
        }
    }

    @Test
    void isQueuedStaysTrueWhileOneShotRuns() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            AtomicReference<Boolean> seen = new AtomicReference<>();
            LegacyTask[] t = new LegacyTask[1];
            t[0] = p.legacy.runTask(p, () -> seen.set(p.legacy.isQueued(t[0].taskId())));
            server.tick();
            assertEquals(Boolean.TRUE, seen.get());
            assertFalse(p.legacy.isQueued(t[0].taskId()));
        }
    }

    @Test
    void concurrentCancelNeverLeavesCallSyncFutureHanging() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            List<Future<Integer>> futures = new ArrayList<>();
            List<LegacyTask> tasks = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                int v = i;
                var f = p.legacy.callSyncMethod(p, () -> v);
                futures.add(f);
            }
            for (var t : p.legacy.pendingTasks()) tasks.add(t);
            Thread canceller = new Thread(() -> {
                for (LegacyTask t : tasks) t.cancel();
            });
            canceller.start();
            server.tick(3);
            canceller.join(10_000);
            for (Future<Integer> f : futures) {
                assertTrue(f.isDone() || awaitDone(f), "취소와 도착이 엇갈려도 future 는 끝난다(값 또는 취소)");
            }
        }
    }

    private static boolean awaitDone(Future<?> f) {
        try {
            f.get(2, TimeUnit.SECONDS);
            return true;
        } catch (CancellationException | ExecutionException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void asyncRepeatingTaskCanOverlapAndEachRunIsAWorker() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            CountDownLatch two = new CountDownLatch(2), release = new CountDownLatch(1);
            LegacyTask t = p.legacy.runTaskTimerAsynchronously(p, () -> {
                two.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }, 1, 1);
            server.tick();
            server.tick();
            assertTrue(two.await(5, TimeUnit.SECONDS), "회차가 겹칠 수 있다(Bukkit 비동기 반복)");
            assertEquals(2, p.legacy.activeWorkers().size());
            t.cancel();
            release.countDown();
        }
    }

    // ---- Paper 1.21.1 CraftAsyncScheduler: 이름 붙은 플랫폼 풀, 종료 대기, getMainThreadExecutor

    @Test
    void platformPoolThreadsAreNamedAndNameIsRestoredAfterTheTask() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            AtomicReference<Thread> th = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            p.legacy.runTaskAsynchronously(p, () -> {
                th.set(Thread.currentThread());
                done.countDown();
            });
            server.tick();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (th.get().getName().contains(p.name()) && System.nanoTime() < until) Thread.sleep(10);
            assertTrue(th.get().getName().matches("Craft Scheduler Thread - \\d+"), "끝나면 플러그인 이름이 빠진다: " + th.get().getName());
            assertTrue(th.get().isDaemon(), "멈춘 작업이 JVM 종료를 붙잡지 않는다");
        }
    }

    @Test
    void virtualOptionRunsAsyncTasksOnTheServersVirtualThreads() throws Exception {
        try (var server = FurnaceTestServer.builder().exposeCompatToNative(true).plugin(CompatTestPlugin.class)
                .compatOption(LegacyScheduler.ASYNC_THREADS_OPTION, "virtual").start()) {
            var p = server.plugin(CompatTestPlugin.class);
            assertFalse(p.legacy.usesPlatformThreads());
            AtomicReference<Thread> th = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            p.legacy.runTaskAsynchronously(p, () -> {
                th.set(Thread.currentThread());
                done.countDown();
            });
            server.tick();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertTrue(th.get().isVirtual());
        }
    }

    /** JDK 21 가상 스레드라면 synchronized 안에서 기다리는 작업이 운반 스레드(코어 수)를 다 붙잡을 수 있는 모양 — 플랫폼 풀은 전부 동시에 선다. */
    @Test
    void manyTasksBlockedInsideSynchronizedAllRunAtOnce() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            int n = Runtime.getRuntime().availableProcessors() * 2 + 8;
            CountDownLatch started = new CountDownLatch(n), release = new CountDownLatch(1);
            for (int i = 0; i < n; i++) {
                Object lock = new Object();
                p.legacy.runTaskAsynchronously(p, () -> {
                    synchronized (lock) {
                        started.countDown();
                        try {
                            release.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            }
            server.tick();
            try {
                assertTrue(started.await(10, TimeUnit.SECONDS), "코어 수보다 많은 작업이 synchronized 안에서 기다려도 모두 시작한다");
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void cancelledTaskStillRunningCountsAsActiveWorkerUntilItEnds() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            CountDownLatch inside = new CountDownLatch(1), release = new CountDownLatch(1);
            LegacyTask t = p.legacy.runTaskAsynchronously(p, () -> {
                inside.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            server.tick();
            assertTrue(inside.await(5, TimeUnit.SECONDS));
            t.cancel();
            assertTrue(t.isCancelled());
            assertEquals(1, p.legacy.activeWorkers().size(), "취소해도 몸통이 끝나기 전까지는 일꾼이다(Bukkit CraftAsyncTask.cancel0)");
            assertTrue(p.legacy.isCurrentlyRunning(t.taskId()));
            release.countDown();
            assertTrue(p.legacy.awaitAsyncWorkers(5_000).isEmpty());
            assertEquals(0, p.legacy.activeWorkers().size());
            assertFalse(p.legacy.isCurrentlyRunning(t.taskId()));
        }
    }

    @Test
    void awaitAsyncWorkersWaitsForWorkersThatFinishWithinTheLimit() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            CountDownLatch inside = new CountDownLatch(1);
            AtomicBoolean finished = new AtomicBoolean();
            p.legacy.runTaskAsynchronously(p, () -> {
                inside.countDown();
                try {
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                finished.set(true);
            });
            server.tick();
            assertTrue(inside.await(5, TimeUnit.SECONDS));
            assertTrue(p.legacy.awaitAsyncWorkers(5_000).isEmpty(), "제한 안에 끝나면 남은 일꾼이 없다");
            assertTrue(finished.get(), "끝날 때까지 기다렸다");
        }
    }

    @Test
    void awaitAsyncWorkersNagsAboutWorkersThatOutliveTheLimit() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            CountDownLatch inside = new CountDownLatch(1), release = new CountDownLatch(1);
            p.legacy.runTaskAsynchronously(p, () -> {
                inside.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            server.tick();
            assertTrue(inside.await(5, TimeUnit.SECONDS));
            List<String> nags = new CopyOnWriteArrayList<>();
            try {
                long t0 = System.nanoTime();
                var overdue = p.legacy.awaitAsyncWorkers(300, nags::add);
                long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
                assertEquals(1, overdue.size());
                assertTrue(ms >= 280 && ms < 3_000, "한도만큼만 기다린다: " + ms + "ms");
            } finally {
                release.countDown();
            }
            assertEquals(1, nags.size(), "일꾼마다 한 줄: " + nags);
            assertTrue(nags.get(0).startsWith("Nag author(s): ") && nags.get(0).contains("This plugin is not properly shutting down its async tasks"), nags.get(0));
        }
    }

    @Test
    void mainThreadExecutorRunsOnTheNextTickAsASyncTask() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            Thread global = server.onGlobal(Thread::currentThread);
            AtomicReference<Thread> ranOn = new AtomicReference<>();
            AtomicReference<Boolean> primary = new AtomicReference<>();
            Executor ex = p.legacy.mainThreadExecutor(p);
            ex.execute(() -> {
                ranOn.set(Thread.currentThread());
                primary.set(PrimaryThread.isPrimary());
            });
            assertNull(ranOn.get(), "틱 전에는 돌지 않는다");
            server.tick();
            assertSame(global, ranOn.get());
            assertTrue(primary.get());
            assertThrows(IllegalArgumentException.class, () -> ex.execute(null));
            assertThrows(IllegalArgumentException.class, () -> p.legacy.mainThreadExecutor(null));
        }
    }

    @Test
    void awaitAsyncWorkersSwallowsInterruptsAndKeepsWaiting() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            CountDownLatch inside = new CountDownLatch(1);
            p.legacy.runTaskAsynchronously(p, () -> {
                inside.countDown();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            server.tick();
            assertTrue(inside.await(5, TimeUnit.SECONDS));
            AtomicReference<Boolean> stillInterrupted = new AtomicReference<>();
            AtomicReference<Integer> overdue = new AtomicReference<>();
            Thread waiter = new Thread(() -> {
                overdue.set(p.legacy.awaitAsyncWorkers(5_000, m -> {}).size());
                stillInterrupted.set(Thread.currentThread().isInterrupted());
            });
            waiter.start();
            Thread.sleep(150);
            waiter.interrupt();   // 종료 스레드가 인터럽트를 받아도 기다림을 이어 가고, 표시를 남기지 않는다
            waiter.join(10_000);
            assertEquals(0, overdue.get(), "일꾼이 끝날 때까지 기다렸다");
            assertEquals(Boolean.FALSE, stillInterrupted.get(), "뒤 단계가 중단되지 않게 인터럽트 표시를 다시 세우지 않는다");
        }
    }

    @Test
    void cancelledBeforeItArrivesNeverCountsAsAWorker() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            AtomicInteger runs = new AtomicInteger();
            LegacyTask t = p.legacy.runTaskAsynchronously(p, runs::incrementAndGet);
            t.cancel();
            server.tick(2);
            assertEquals(0, runs.get());
            assertTrue(p.legacy.activeWorkers().isEmpty());
        }
    }

    /** 주기가 몸통보다 짧아 회차가 겹치는 반복 작업: 도는 회차가 하나라도 있으면 늘 일꾼 목록에 있다. */
    @Test
    void overlappingRoundsOfARepeatingTaskStayListedAsWorkers() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            AtomicBoolean missing = new AtomicBoolean();
            AtomicInteger runs = new AtomicInteger();
            LegacyTask t = p.legacy.runTaskTimerAsynchronously(p, () -> {
                runs.incrementAndGet();
                long until = System.nanoTime() + 300_000;
                while (System.nanoTime() < until) {
                    Thread self = Thread.currentThread();
                    if (p.legacy.activeWorkers().stream().noneMatch(w -> w.thread() == self)) missing.set(true);
                }
            }, 1, 1);
            for (int i = 0; i < 400; i++) server.tick();
            t.cancel();
            assertTrue(p.legacy.awaitAsyncWorkers(5_000, m -> {}).isEmpty());
            assertTrue(runs.get() > 100, "회차가 충분히 돌았다: " + runs.get());
            assertFalse(missing.get(), "도는 회차는 항상 일꾼 목록에 있다");
        }
    }
}
