// createWorldNow 동시성(PR #388 독립 검토 재현 시험을 옮김).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.world.WorldCreator;
import org.domi.furnace.api.world.WorldEnvironment;
import org.domi.furnace.api.world.WorldGenerator;
import org.domi.furnace.api.worldgen.DimensionTypeDefinition;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.BedBlock;
import org.domi.furnace.server.block.types.RespawnAnchorBlock;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CreateWorldNowConcurrencyTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    Dimensions dims;
    WorldManager manager;
    BlockRegistry reg;
    RegistrySet registries;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp.resolve("world"), sched);
        dims = new Dimensions();
        dims.register(core.world());
        core.dimensions(dims);
        reg = BlockRegistry.load("blocks/1.21.1.txt");
        // 플러그인 차원 타입(W2) 하나가 클라이언트 레지스트리에 있는 상태를 흉내 낸다
        registries = RegistrySet.load("registries/1.21.1.txt")
                .withExtras(List.of(new RegistrySet.Extra("minecraft:dimension_type", "test:low", new org.domi.furnace.server.net.io.nbt.NbtCompound())));
        var io = TestServers.ioPool();
        manager = new WorldManager(new WorldManager.Parts(tmp, reg, ItemRegistry.load("items/1.21.1.txt"), registries,
                registries.indexOf("minecraft:worldgen/biome", "minecraft:plains"), 7, new ChunkWorkerPool(1), sched, null,
                Config.DEFAULT, false, io, io), dims, core.levelData(), "world");
        core.worldManager(manager);
    }

    @AfterEach
    void tearDown() {
        manager.closeAllForShutdown();
        sched.close();
    }

    private World create(WorldCreator c) throws Exception { return manager.create(c).get(60, TimeUnit.SECONDS); }

    private static Throwable cause(Throwable t) { return t instanceof ExecutionException && t.getCause() != null ? t.getCause() : t; }

    private int stoneId() { return reg.defaultStateId("minecraft:stone"); }

    private void placeStone(World w, int x, int y, int z) {
        ChunkPos cp = ChunkPos.ofBlock(x, z);
        w.awaitChunk(cp);
        assertTrue(w.submitAtChunk(cp, r -> r.level().setBlock(new Pos(x, y, z), stoneId(), 3)).join());
    }

    private int blockAt(World w, int x, int y, int z) {
        ChunkPos cp = ChunkPos.ofBlock(x, z);
        w.awaitChunk(cp);
        return w.submitAtChunk(cp, r -> r.level().getBlockState(new Pos(x, y, z))).join();
    }

    private org.domi.furnace.api.plugin.FurnacePlugin testPlugin() {
        var host = core.plugins();
        var plugin = new org.domi.furnace.api.plugin.FurnacePlugin() {};
        plugin.init(new org.domi.furnace.api.plugin.PluginContext() {
            final org.domi.furnace.api.plugin.PluginDescription d = new org.domi.furnace.api.plugin.PluginDescription("mw", "1", "x", "",
                    List.of(), null, null, List.of(), List.of(), List.of(), null, java.util.Map.of(), List.of(), null);
            public org.domi.furnace.api.plugin.PluginDescription description() { return d; }
            public org.domi.furnace.api.Furnace server() { return host; }
            public java.util.logging.Logger logger() { return java.util.logging.Logger.getLogger("mw"); }
            public Path configFolder() { return tmp.resolve("mw"); }
            public Path dataFolder() { return tmp.resolve("mw"); }
            public Path file() { return tmp.resolve("mw.jar"); }
            public ClassLoader classLoader() { return getClass().getClassLoader(); }
            public org.domi.furnace.api.registry.PluginRegistries registries() { return null; }
        });
        plugin.setEnabled(true);
        return plugin;
    }


    /**
     * 1) WorldUnloadEvent 처리기(전역)가 createWorldNow 를 부르면: 월드 스레드는 그 처리기를, 처리기는 월드 스레드를 기다려 30초 멈췄다(검토 R1).
     * 이제는 곧바로 IllegalStateException 이고 내리기는 끝난다.
     */
    @Test
    void r1UnloadHandlerCreatingWorldNow() throws Exception {
        var host = core.plugins();
        var plugin = testPlugin();
        World a = create(WorldCreator.named("rea").generator(WorldGenerator.VOID).build());
        host.eventsImpl().subscribe(plugin, org.domi.furnace.api.event.world.WorldUnloadEvent.class,
                e -> host.createWorldNow(WorldCreator.named("inner").generator(WorldGenerator.VOID).build()));
        long t0 = System.nanoTime();
        String result;
        try {
            result = "unload=" + manager.unload(a, false).get(90, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            result = "unload failed: " + e.getCause();
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        Thread.sleep(500);
        String state = result + " ms=" + ms + " inDims=" + (core.world("minecraft:rea") != null) + " inExtras=" + (manager.extraWorld("rea") != null)
                + " isUnloaded=" + a.isUnloaded() + " inner=" + (core.world("minecraft:inner") != null);
        System.out.println("[R388] " + state);
        assertTrue(ms < 10_000, state);
        assertEquals(core.world("minecraft:rea") == null, manager.extraWorld("rea") == null, "목록과 extras 가 어긋나면 안 된다: " + state);
        // 처리기 안의 동기 만들기는 곧바로 개발자용 예외(처리기 예외는 이벤트 버스가 기록), 내리기는 그대로 끝난다
        assertEquals("unload=true", result, state);
        assertTrue(a.isUnloaded(), state);
        assertNull(core.world("minecraft:inner"), state);
    }

    /** 2) 막힌 전역이 도움 대기열로 돌린 내리기 확인은 정확히 한 번(FutureTask) — WorldUnloadEvent 1회. */
    @Test
    void helpRunsUnloadBodyExactlyOnce() throws Exception {
        var host = core.plugins();
        var plugin = testPlugin();
        java.util.concurrent.atomic.AtomicInteger unloads = new java.util.concurrent.atomic.AtomicInteger();
        host.eventsImpl().subscribe(plugin, org.domi.furnace.api.event.world.WorldUnloadEvent.class, e -> unloads.incrementAndGet());
        for (int i = 0; i < 5; i++) {
            World first = create(WorldCreator.named("o" + i).generator(WorldGenerator.VOID).build());
            final int n = i;
            var u = sched.global().submit(() -> {
                var f = manager.unload(first, false);
                host.createWorldNow(WorldCreator.named("n" + n).generator(WorldGenerator.VOID).build());
                return f;
            }).get(60, TimeUnit.SECONDS);
            assertTrue(u.get(60, TimeUnit.SECONDS));
            Thread.sleep(100);
            assertEquals(i + 1, unloads.get(), "내리기 알림은 한 번");
        }
    }

    /** 3) region 스레드에서 부르면 GlobalWait 로 기다리고, Init·Load 는 전역에서 한 번. */
    @Test
    void regionThreadCallerGetsEventsOnGlobalOnce() throws Exception {
        var host = core.plugins();
        var plugin = testPlugin();
        List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
        host.eventsImpl().subscribe(plugin, org.domi.furnace.api.event.world.WorldInitEvent.class,
                e -> seen.add("init g=" + org.domi.furnace.server.sched.GlobalThread.isGlobalThread()));
        host.eventsImpl().subscribe(plugin, org.domi.furnace.api.event.world.WorldLoadEvent.class,
                e -> seen.add("load g=" + org.domi.furnace.server.sched.GlobalThread.isGlobalThread()));
        World ow = core.world();
        ChunkPos cp = new ChunkPos(0, 0);
        ow.awaitChunk(cp);
        String r = ow.submitAtChunk(cp, reg -> {
            var w = host.createWorldNow(WorldCreator.named("fromregion").generator(WorldGenerator.VOID).build());
            return w.name() + " " + seen;
        }).get(60, TimeUnit.SECONDS);
        assertEquals("fromregion [init g=true, load g=true]", r);
    }

    /** 4) 같은 이름을 여러 스레드(전역·region·보통)에서 동시에 — 알림은 한 번, 손잡이는 하나. */
    @Test
    void concurrentSameNameFiresOnce() throws Exception {
        var host = core.plugins();
        var plugin = testPlugin();
        java.util.Map<String, java.util.concurrent.atomic.AtomicInteger> loads = new java.util.concurrent.ConcurrentHashMap<>();
        host.eventsImpl().subscribe(plugin, org.domi.furnace.api.event.world.WorldLoadEvent.class,
                e -> loads.computeIfAbsent(e.name(), k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet());
        World ow = core.world();
        ChunkPos cp = new ChunkPos(0, 0);
        ow.awaitChunk(cp);
        List<String> order = new java.util.concurrent.CopyOnWriteArrayList<>();
        for (int i = 0; i < 6; i++) {
            String name = "same" + i;
            var spec = WorldCreator.named(name).generator(WorldGenerator.VOID).build();
            var start = new java.util.concurrent.CountDownLatch(1);
            var g = sched.global().submit(() -> { try { start.await(); } catch (InterruptedException e) { throw new RuntimeException(e); } var w = host.createWorldNow(spec); order.add(name + " g loads=" + loads.getOrDefault(name, new java.util.concurrent.atomic.AtomicInteger()).get()); return w; });
            var rr = ow.submitAtChunk(cp, x -> { try { start.await(); } catch (InterruptedException e) { throw new RuntimeException(e); } return host.createWorldNow(spec); });
            var plain = java.util.concurrent.CompletableFuture.supplyAsync(() -> { try { start.await(); } catch (InterruptedException e) { throw new RuntimeException(e); } return host.createWorldNow(spec); });
            var async = java.util.concurrent.CompletableFuture.supplyAsync(() -> { try { start.await(); } catch (InterruptedException e) { throw new RuntimeException(e); } return host.createWorld(spec).join(); });
            start.countDown();
            var a = g.get(60, TimeUnit.SECONDS);
            assertSame(a, rr.get(60, TimeUnit.SECONDS));
            assertSame(a, plain.get(60, TimeUnit.SECONDS));
            assertSame(a, async.get(60, TimeUnit.SECONDS));
            Thread.sleep(200);
            assertEquals(1, loads.get(name).get(), "WorldLoadEvent 한 번: " + name);
        }
        System.out.println("[R388] order " + order);
    }

    /** 내리기 알림 처리기가 30초를 넘기면 내리기는 시간 초과로 실패하고, 늦게 끝난 몸체는 목록에서 떼지 않는다(반쯤 내림 없음). */
    @Test
    void timedOutUnloadBodyDoesNotHalfUnload() throws Exception {
        var host = core.plugins();
        var plugin = testPlugin();
        World a = create(WorldCreator.named("slow").generator(WorldGenerator.VOID).build());
        java.util.concurrent.atomic.AtomicBoolean slow = new java.util.concurrent.atomic.AtomicBoolean(true);
        var done = new java.util.concurrent.CountDownLatch(1);
        host.eventsImpl().subscribe(plugin, org.domi.furnace.api.event.world.WorldUnloadEvent.class, e -> {
            if (slow.getAndSet(false)) {
                try { Thread.sleep(32_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                done.countDown();
            }
        });
        String result;
        try {
            result = "unload=" + manager.unload(a, false).get(90, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            result = "failed " + e.getCause().getClass().getSimpleName();
        }
        assertTrue(done.await(30, TimeUnit.SECONDS));
        Thread.sleep(500);
        String state = result + " inDims=" + (core.world("minecraft:slow") != null) + " inExtras=" + (manager.extraWorld("slow") != null)
                + " isUnloaded=" + a.isUnloaded();
        System.out.println("[R388b] " + state);
        assertEquals("failed TimeoutException inDims=true inExtras=true isUnloaded=false", state);
        assertTrue(manager.unload(a, false).get(60, TimeUnit.SECONDS), "다시 내리면 끝까지");
        assertTrue(a.isUnloaded());
        assertNull(core.world("minecraft:slow"));
        assertNull(manager.extraWorld("slow"));
    }
}
