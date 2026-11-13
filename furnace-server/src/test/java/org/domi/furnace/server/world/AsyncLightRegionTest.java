// T-064 빛 작업 모드를 실제 World·Region 틱으로: 빛 장치를 돌려도 밀린 빛을 기다린 뒤 빛이 끈 모드와 같고, 청크 전송이 막히지 않는다.
// -Dfurnace.bench=true 면 lamps400(발광석 400개를 4틱마다 켜고 끔) region 틱을 끔·켬 교대로 잰다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.config.LightSettings;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class AsyncLightRegionTest {

    @AfterEach
    void restore() { LightSettings.install(LightSettings.DEFAULT); }

    /** 같은 장면을 끔·켬으로 돌린 뒤(켬은 밀린 빛을 끝까지 기다림) 시야 안 모든 청크의 빛 배열 해시가 같다. */
    @Test
    void lampSceneMatchesInPlaceLight() throws Exception {
        Result off = run(false, 120, 4);
        Result on = run(true, 120, 4);
        assertEquals(off.lightHash, on.lightHash, "밀린 빛을 기다린 뒤 빛이 같아야 한다");
        assertTrue(on.chunks > 0 && on.chunks == off.chunks, "같은 청크들이 올라와 빛이 있다: " + on.chunks + " / " + off.chunks);
        assertTrue(on.sent > 0, "청크·빛 패킷이 플레이어에게 간다");
        assertEquals(off.playerChunks, on.playerChunks, "올라온 청크가 끈 것과 똑같이 플레이어에게 나갔다(검토 R1 — 전송이 빛을 기다리다 굶지 않는다)");
        assertTrue(on.playerChunks >= (2 * 4 + 1) * (2 * 4 + 1), "시야 안 청크가 모두 나갔다: " + on.playerChunks);
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 30, unit = java.util.concurrent.TimeUnit.MINUTES)   // 6회 × 1000틱 — 느린 기계에서 기본 5분을 넘는다(검토 R8)
    void lamps400Bench() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        for (int round = 0; round < 3; round++) {
            for (boolean async : new boolean[] {false, true}) {
                Result r = run(async, 1000, 6);
                System.out.printf("T064 BENCH lamps400 async=%s 틱 평균 %.3fms p50 %.3fms p95 %.3fms 최대 %.3fms hash=%016x%n",
                        async, r.avgMs, r.p50Ms, r.p95Ms, r.maxMs, r.lightHash);
            }
        }
    }

    /**
     * 검토 R7: 실제 Regionizer 합치기·나누기(splitLocked → settleLightBeforeSplit, absorb → drain)를 빛 묶음이 도는 중에 되풀이해도 빛이 맞다.
     * 두 고정 구역(청크 x=8, x=40)에서 발광석을 계속 켜고 끄며 다리 구역(x=24)을 넣었다 빼 합치기·나누기를 일으킨다.
     */
    @Test
    void mergeAndSplitWithLightInFlight() throws Exception {
        LightSettings.install(new LightSettings(true, 1));
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(10_000_000L, 3);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var ioPool = Executors.newSingleThreadExecutor();
        var gen = Executors.newSingleThreadExecutor();
        try {
            World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2), sched, 3, 4, 1, 2, 2, reg.directBits(), 7, ioPool, gen);
            Regionizer rz = w.regionizer();
            int[] anchors = {8, 40};
            for (int a : anchors) rz.want(a, 0, null);
            int glow = B.defaultState("glowstone"), air = B.defaultState("air");
            // 청크마다 발광석 두 자리: on 은 끝에 켜진 채, off 는 끝에 꺼진 채(서로 15 칸 넘게 떨어짐)
            java.util.function.IntFunction<Pos> onPos = a -> new Pos(a * 16 + 2, -58, 2), offPos = a -> new Pos(a * 16 + 13, -58, 13);
            for (int a : anchors) at(w, a, r -> r.chunkAtBlocking(new ChunkPos(a, 0)));
            int merges = 0, splits = 0, toggles = 0;
            for (int cycle = 0; cycle < 3; cycle++) {
                rz.want(24, 0, null);   // 다리 → 합치기
                long until = System.currentTimeMillis() + 5_000;
                while (rz.ownerOf(8, 0) != rz.ownerOf(40, 0) && System.currentTimeMillis() < until) toggles += toggle(w, anchors, onPos, offPos, glow, air, toggles);
                if (rz.ownerOf(8, 0) == rz.ownerOf(40, 0)) merges++;
                for (int i = 0; i < 10; i++) toggles += toggle(w, anchors, onPos, offPos, glow, air, toggles);
                for (int tries = 0; tries < 10; tries++) {
                    Region o = rz.ownerOf(24, 0);
                    if (o == null) break;
                    rz.release(o, 24, 0);
                    if (!o.present.contains(Regionizer.chunkKey(24, 0))) break;
                }
                until = System.currentTimeMillis() + 10_000;   // 유예 1초 + 재계산 → 나누기
                while (rz.ownerOf(8, 0) == rz.ownerOf(40, 0) && System.currentTimeMillis() < until) toggles += toggle(w, anchors, onPos, offPos, glow, air, toggles);
                if (rz.ownerOf(8, 0) != rz.ownerOf(40, 0)) splits++;
            }
            assertTrue(merges >= 2 && splits >= 2, "합치기 " + merges + " 나누기 " + splits);
            for (int a : anchors) {
                at(w, a, r -> {
                    r.level().setBlock(onPos.apply(a), glow, 2);
                    r.level().setBlock(offPos.apply(a), air, 2);
                });
            }
            for (int a : anchors) {
                at(w, a, r -> {
                    r.level().drainLight();
                    Pos on = onPos.apply(a), off = offPos.apply(a);
                    assertEquals(15, r.level().getBlockLight(on), "켜진 발광석 " + on);
                    assertEquals(14, r.level().getBlockLight(on.east()), "옆 칸");
                    assertEquals(0, r.level().getBlockLight(off), "꺼진 자리 " + off);
                    assertEquals(0, r.level().getBlockLight(off.west()), "꺼진 자리 옆");
                });
            }
        } finally {
            ioPool.shutdown();
            gen.shutdown();
            sched.close();
        }
    }

    /** 두 자리를 엇갈려 켜고 끈다(빛 묶음이 늘 돌게). 1 을 돌려준다. */
    private static int toggle(World w, int[] anchors, java.util.function.IntFunction<Pos> onPos, java.util.function.IntFunction<Pos> offPos,
            int glow, int air, int n) throws Exception {
        for (int a : anchors) {
            at(w, a, r -> {
                r.level().setBlock(onPos.apply(a), n % 2 == 0 ? glow : air, 2);
                r.level().setBlock(offPos.apply(a), n % 2 == 0 ? air : glow, 2);
            });
        }
        Thread.sleep(15);
        return 1;
    }

    private static void at(World w, int cx, java.util.function.Consumer<Region> task) throws Exception {
        var done = new java.util.concurrent.CompletableFuture<Void>();
        w.runAtChunk(new ChunkPos(cx, 0), r -> {
            try {
                task.accept(r);
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        done.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }

    record Result(long lightHash, int chunks, int playerChunks, long sent, double avgMs, double p50Ms, double p95Ms, double maxMs) {}

    private static Result run(boolean async, int ticks, int view) throws Exception {
        LightSettings.install(new LightSettings(async, 1));
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var ioPool = Executors.newSingleThreadExecutor();
        var gen = Executors.newFixedThreadPool(2);
        try {
            double sx = 8.5, sy = -60, sz = 8.5;
            World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2), sched, sched.regionThreadCount(), 16, view, view,
                    reg.directBits(), 7, ioPool, gen);
            w.setRegistries(reg, ItemRegistry.load("items/1.21.1.txt"));
            RegionPos rp = new RegionPos(0, 0);
            var ch = new ConcurrentEmbeddedChannel();
            Player p = new Player(w.entityIds().next(), UUID.randomUUID(), "A", ch);
            p.inventory(new PlayerInventory());
            p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
            p.applyMove(sx, sy, sz, 0, 0, true);
            p.publishPosition(sx, sy, sz, 0, 0, true);
            sched.region(rp).execute(() -> w.regionAt(rp).adopt(p)).get();
            long[] sent = {0};
            Runnable drain = () -> {
                try {
                    Thread dr = new Thread(null, () -> {
                        ch.runPendingTasks();
                        Object o;
                        while ((o = ch.readOutbound()) != null) {
                            sent[0]++;
                            io.netty.util.ReferenceCountUtil.release(o);
                        }
                    }, "test-drain", 1L << 30);
                    dr.start();
                    dr.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            };
            for (int t = 0; t < 100; t++) {   // 청크를 올린다
                p.pushMove(sx, sy, sz, true);
                sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
                drain.run();
            }
            int glow = B.defaultState("glowstone"), air = B.defaultState("air");
            long[] tickNs = new long[ticks];
            for (int t = 0; t < ticks; t++) {
                final int tt = t;
                p.pushMove(sx, sy, sz, true);
                sched.region(rp).execute(() -> {
                    Region r = w.regionAt(rp);
                    long t0 = System.nanoTime();
                    if (tt % 4 == 0) {
                        int s = (tt / 4) % 2 == 0 ? glow : air;
                        for (int i = 0; i < 400; i++) r.level().setBlock(new Pos(-52 + (i % 20) * 6, -58, -52 + (i / 20) * 6), s, 2);
                    }
                    r.tick();
                    tickNs[tt] = System.nanoTime() - t0;
                }).get();
                drain.run();
            }
            long[] hash = {1469598103934665603L};
            int[] chunks = {0};
            int[] playerChunks = {0};
            sched.region(rp).execute(() -> {
                Region r = w.regionAt(rp);
                r.level().drainLight();   // 켬: 밀린 빛을 끝까지. 끔: 할 일 없음
                for (int cx = -view; cx <= view; cx++) for (int cz = -view; cz <= view; cz++) {
                    ChunkColumn col = r.level().chunkCached(cx, cz);
                    if (col == null || col.light() == null) continue;
                    chunks[0]++;
                    var light = col.light();
                    for (int i = 0; i < org.domi.furnace.server.light.ChunkLight.SECTIONS; i++) {
                        hash[0] = (hash[0] ^ java.util.Arrays.hashCode(light.sky(i))) * 1099511628211L;
                    }
                    for (int i = 0; i < org.domi.furnace.server.light.ChunkLight.SECTIONS; i++) {
                        hash[0] = (hash[0] ^ java.util.Arrays.hashCode(light.block(i))) * 1099511628211L;
                    }
                    for (int v : col.light().lowestSource) hash[0] = (hash[0] ^ v) * 1099511628211L;
                }
                playerChunks[0] = p.loadedChunks().size();
            }).get();
            long[] sorted = tickNs.clone();
            java.util.Arrays.sort(sorted);
            long sum = 0;
            for (long v : tickNs) sum += v;
            return new Result(hash[0], chunks[0], playerChunks[0], sent[0], sum / 1e6 / ticks, sorted[ticks / 2] / 1e6,
                    sorted[(int) (ticks * 0.95)] / 1e6, sorted[ticks - 1] / 1e6);
        } finally {
            ioPool.shutdown();
            gen.shutdown();
            sched.close();
        }
    }
}
