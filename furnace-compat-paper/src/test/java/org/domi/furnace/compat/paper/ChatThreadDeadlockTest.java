package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-035 회귀: AsyncPlayerChatEvent 처리기가 전역 스레드에서 돌면 흔한 패턴 {@code callSyncMethod(...).get()} 이 자기 자신을 기다려
 * 교착하고(제한 없는 get 이면 영구), 막히는 처리기가 전역 틱 전체를 멈춘다. Paper 는 채팅을 "Async Chat Thread" 에서 돌려
 * 다음 틱에 값이 오고 주 스레드는 멈추지 않는다. 고치기 전 코드에서는 두 시험 모두 실패한다(시간 제한으로 — 멈추지는 않는다).
 */
class ChatThreadDeadlockTest {

    @TempDir Path tmp;

    @BeforeEach
    void reset() { RecorderPlugin.reset(); }

    private Path recorderJar() throws Exception {
        return Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
    }

    /** 채팅을 다른 스레드에서 보낸다 — chat() 은 서버가 처리를 끝낼 때까지 돌아오지 않으므로, 시험 스레드는 그동안 틱을 낸다. */
    private static Thread chatAsync(TestPlayer p, String message, AtomicReference<Throwable> failure) {
        Thread t = new Thread(() -> {
            try {
                p.chat(message);
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "test-chat-sender");
        t.setDaemon(true);
        t.start();
        return t;
    }

    @Test
    void callSyncMethodGetFromAsyncChatHandlerCompletes() throws Exception {
        var out = new CopyOnWriteArrayList<String>();
        var done = new CountDownLatch(1);
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).timeout(30_000).pluginJar(recorderJar()).start()) {
            TestPlayer dana = server.join("Dana");
            server.tick(2);
            RecorderPlugin.ACTIONS.put("AsyncPlayerChatEvent", e -> {
                var ev = (AsyncPlayerChatEvent) e;
                out.add("thread=" + Thread.currentThread().getName());
                out.add("async=" + ev.isAsynchronous());
                out.add("primary=" + Bukkit.isPrimaryThread());
                try {
                    var f = Bukkit.getScheduler().callSyncMethod(Bukkit.getPluginManager().getPlugin("Recorder"), () -> {
                        out.add("syncPrimary=" + Bukkit.isPrimaryThread());
                        return 42;
                    });
                    out.add("result=" + f.get(5, TimeUnit.SECONDS));
                } catch (Throwable t) {
                    out.add("failed=" + t);
                } finally {
                    done.countDown();
                }
            });
            var failure = new AtomicReference<Throwable>();
            Thread sender = chatAsync(dana, "hello", failure);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (done.getCount() > 0 && System.nanoTime() < deadline) {
                server.tick(1);   // callSyncMethod 는 다음 틱에 전역에서 돈다
                Thread.sleep(10);
            }
            assertTrue(done.await(1, TimeUnit.SECONDS), "처리기가 끝나지 않았다: " + out);
            sender.join(TimeUnit.SECONDS.toMillis(15));
            assertFalse(sender.isAlive(), "chat() 이 돌아오지 않았다");
            if (failure.get() != null) throw new AssertionError(failure.get());

            assertTrue(out.contains("result=42"), "callSyncMethod(...).get() 이 값을 받아야 한다: " + out);
            assertTrue(out.contains("syncPrimary=true"), "callSyncMethod 작업은 주 스레드에서: " + out);
            assertTrue(out.contains("async=true"), out.toString());
            assertTrue(out.contains("primary=false"), "채팅 처리기는 주 스레드가 아니다(Paper 와 같게): " + out);
            assertTrue(out.get(0).startsWith("thread=Async Chat Thread"), out.toString());
            // 처리기가 끝난 뒤 채팅은 평소대로 방송된다(chat() 이 돌아왔으면 사슬이 끝났다)
            assertTrue(consoleHas(server, "<Dana> hello"), server.consoleMessages().toString());
        }
    }

    @Test
    void blockingChatHandlerDoesNotStallGlobalThread() throws Exception {
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).timeout(30_000).pluginJar(recorderJar()).start()) {
            TestPlayer dana = server.join("Dana");
            server.tick(2);
            RecorderPlugin.ACTIONS.put("AsyncPlayerChatEvent", e -> {
                entered.countDown();
                try {
                    release.await(20, TimeUnit.SECONDS);   // DB·웹훅 조회를 기다리는 처리기
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
            var failure = new AtomicReference<Throwable>();
            Thread sender = chatAsync(dana, "slow", failure);
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS), "처리기가 불리지 않았다");
                // 처리기가 막혀 있는 동안에도 전역 스레드는 일한다: 작업·틱·콘솔 명령
                assertEquals(7, server.internals().scheduler().global().submit(() -> 7).get(3, TimeUnit.SECONDS));
                long before = server.gameTime();
                server.tick(3);
                assertEquals(before + 3, server.gameTime());
                assertTrue(server.execute("time set 1000").success());
                assertTrue(sender.isAlive(), "처리기가 풀리기 전에는 채팅 처리가 끝나지 않는다");
            } finally {
                release.countDown();
            }
            sender.join(TimeUnit.SECONDS.toMillis(15));
            assertFalse(sender.isAlive(), "chat() 이 돌아오지 않았다");
            if (failure.get() != null) throw new AssertionError(failure.get());
            assertTrue(consoleHas(server, "<Dana> slow"), server.consoleMessages().toString());
        }
    }

    private static boolean consoleHas(FurnaceTestServer server, String text) {
        return server.consoleMessages().stream().anyMatch(t -> t.plainText().contains(text));
    }
}
