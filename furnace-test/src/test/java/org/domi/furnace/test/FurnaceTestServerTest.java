// 헤드리스 시험 서버 자체 시험: 클래스패스 플러그인 적재, 결정적 틱(전역·region·지연 작업), 메모리 월드, 명령 출력, 가짜 플레이어.
package org.domi.furnace.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.sample.CounterPlugin;
import org.junit.jupiter.api.Test;

class FurnaceTestServerTest {

    @Test
    void classpathPluginLoadsThroughRealLifecycle() {
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).start()) {
            CounterPlugin p = server.plugin(CounterPlugin.class);
            assertTrue(p.isEnabled());
            assertSame(p, server.plugin("Counter"), "설명 파일의 이름으로 올라간다");
            assertEquals("welcome", p.greeting, "설명 파일 옆의 config.yml 을 기본 설정으로 읽는다");
            assertSame(server.api(), Furnace.server(), "Furnace.server() 는 떠 있는 시험 서버");
        }
    }

    @Test
    void ticksAreDeterministicAndOnlyAdvanceOnRequest() throws Exception {
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).start()) {
            CounterPlugin p = server.plugin(CounterPlugin.class);
            long t0 = server.gameTime();
            assertEquals(0, p.globalTicks.get());
            assertEquals(0, p.regionTicks.get());
            server.tick(20);
            assertEquals(20, server.currentTick());
            assertEquals(t0 + 20, server.gameTime(), "전역 틱이 정확히 20번");
            assertEquals(20, p.globalTicks.get(), "전역 반복 작업은 틱마다 한 번");
            assertEquals(20, p.regionTicks.get(), "region 반복 작업도 틱마다 한 번");
            Thread.sleep(300);   // 벽시계로는 6틱 — 진행하면 안 된다
            assertEquals(20, p.globalTicks.get());
            assertEquals(20, p.regionTicks.get());
            assertEquals(t0 + 20, server.gameTime());
            server.tick();
            assertEquals(21, p.globalTicks.get());
            assertEquals(21, p.regionTicks.get());
        }
    }

    @Test
    void delayedTasksFireOnExactTick() {
        try (var server = FurnaceTestServer.start()) {
            AtomicLong globalAt = new AtomicLong(-1), regionAt = new AtomicLong(-1);
            var sched = server.api().scheduler();
            server.onGlobal(() -> sched.global().runLater(() -> globalAt.set(server.gameTime()), 10));
            long start = server.gameTime();
            server.onRegion(new BlockPos(0, 0, 0), () -> sched.region(new RegionPos(0, 0)).runLater(() -> regionAt.set(server.gameTime()), 10));
            server.tick(9);
            assertEquals(-1, globalAt.get());
            assertEquals(-1, regionAt.get());
            server.tick();
            // Bukkit 처럼 전역 예약 작업은 틱 머리(시간이 오르기 전), region 작업은 전역 틱 뒤에 돈다 — 둘 다 10번째 틱
            assertEquals(start + 9, globalAt.get(), "전역 runLater(10) 은 10번째 틱 머리에");
            assertEquals(start + 10, regionAt.get(), "region runLater(10) 은 10번째 틱(전역 틱 뒤)에");
        }
    }

    @Test
    void memoryWorldKeepsBlocksAcrossManyTicks() {
        try (var server = FurnaceTestServer.start()) {
            var w = server.overworld();
            BlockPos pos = new BlockPos(3, -60, 4);
            assertTrue(server.await(w.setBlock(pos, BlockData.of("minecraft:gold_block"))));
            server.tick(400);   // 운영 서버라면 구독자 없는 청크를 내렸을 시간
            assertEquals("minecraft:gold_block", server.await(w.blockAt(pos)).orElseThrow().type());
            assertEquals("minecraft:gold_block", server.onRegion(pos, () -> w.blockNow(pos).orElseThrow().type()), "region 스레드에서 동기 읽기");
        }
    }

    @Test
    void consoleCommandReturnsItsOwnOutput() {
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).start()) {
            server.tick(3);
            var out = server.execute("count");
            assertTrue(out.success());
            assertEquals("global=3 region=3", out.text());
            var set = server.execute("/setblock 1 -60 1 minecraft:diamond_block");
            assertTrue(set.success(), set.lines().toString());
            assertEquals("minecraft:diamond_block", server.await(server.overworld().blockAt(new BlockPos(1, -60, 1))).orElseThrow().type());
            var bad = server.execute("nosuchcommand");
            assertFalse(bad.success());
            assertFalse(bad.messages().isEmpty(), "모르는 명령 안내");
        }
    }

    @Test
    void fakePlayerJoinsChatsAndRunsCommands() {
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).start()) {
            CounterPlugin plugin = server.plugin(CounterPlugin.class);
            TestPlayer steve = server.join("Steve");
            assertNotNull(steve.player(), "플러그인이 보는 플레이어 핸들");
            assertEquals("Steve", steve.player().name());
            steve.awaitMessage(s -> s.equals("welcome, Steve"));
            server.tick(5);
            steve.clearMessages();
            steve.command("/count");
            assertTrue(steve.messageLines().contains("global=5 region=5"), steve.messageLines().toString());
            steve.chat("hello world");
            assertEquals(java.util.List.of("Steve:hello world"), plugin.chat);
            assertEquals(1, server.players().size());
            steve.close();
            assertNull(server.internals().playerByName("Steve"));
            assertTrue(server.players().isEmpty());
        }
    }

    @Test
    void separatedRegionsTickInLockstep() {
        try (var server = FurnaceTestServer.builder().regionThreads(2).start()) {
            var sched = server.api().scheduler();
            AtomicLong near = new AtomicLong(), far = new AtomicLong();
            server.loadChunk(200, 200);   // 멀리 떨어진 청크 — 따로 region
            sched.region(new RegionPos(0, 0)).runAtFixedRate(near::incrementAndGet, 1, 1);
            sched.region(new RegionPos(200, 200)).runAtFixedRate(far::incrementAndGet, 1, 1);
            server.tick(50);
            var w = server.overworld();
            assertNotEquals(w.regionAt(0, 0).orElseThrow().id(), w.regionAt(200, 200).orElseThrow().id(), "정말 따로인 region");
            assertEquals(50, near.get());
            assertEquals(50, far.get(), "떨어진 region 도 같은 수만큼 돈다");
        }
    }

    @Test
    void mergingRegionsKeepTheirTickCount() {
        try (var server = FurnaceTestServer.builder().regionThreads(2).start()) {
            var sched = server.api().scheduler();
            var w = server.overworld();
            AtomicLong a = new AtomicLong(), b = new AtomicLong();
            server.loadChunk(40, 0);   // 구역 2(16청크 구역, 합치기 반경 1) — 스폰(구역 0)과 따로
            sched.region(new RegionPos(0, 0)).runAtFixedRate(a::incrementAndGet, 1, 1);
            sched.region(new RegionPos(40, 0)).runAtFixedRate(b::incrementAndGet, 1, 1);
            server.tick(10);
            assertNotEquals(w.regionAt(0, 0).orElseThrow().id(), w.regionAt(40, 0).orElseThrow().id());
            server.loadChunk(20, 0);   // 가운데 구역 — 셋이 이어져 합쳐진다
            server.tick(10);
            assertEquals(w.regionAt(0, 0).orElseThrow().id(), w.regionAt(40, 0).orElseThrow().id(), "합쳐졌다");
            assertEquals(20, a.get());
            assertEquals(20, b.get(), "합쳐져도 틱을 건너뛰거나 두 번 돌지 않는다");
        }
    }

    @Test
    void pluginJarLoadsInItsOwnClassLoader(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path jar = SamplePluginJars.build(tmp, "JarProbe", "probe.JarProbe", """
                package probe;
                public final class JarProbe extends org.domi.furnace.api.plugin.FurnacePlugin {
                    public static volatile int enabled;
                    @Override public void onEnable() {
                        enabled++;
                        server().commands().register(this, "probe", (s, args) -> s.sendMessage(org.domi.furnace.api.text.Text.text("probe ok")));
                    }
                }
                """);
        try (var server = FurnaceTestServer.builder().pluginJar(jar).start()) {
            var p = server.plugin("JarProbe");
            assertNotNull(p);
            assertTrue(p.isEnabled());
            assertNotSame(getClass().getClassLoader(), p.getClass().getClassLoader(), "jar 플러그인은 운영 서버처럼 자기 클래스로더");
            assertEquals("probe ok", server.execute("probe").text());
        }
    }

    @Test
    void serversCanStartOneAfterAnother() {
        Furnace first;
        try (var a = FurnaceTestServer.start()) {
            first = a.api();
            assertSame(first, Furnace.server());
        }
        try (var b = FurnaceTestServer.start()) {
            assertNotSame(first, b.api());
            assertSame(b.api(), Furnace.server(), "앞 서버를 닫으면 다음 서버가 Furnace.server() 를 맡는다");
        }
    }
}
