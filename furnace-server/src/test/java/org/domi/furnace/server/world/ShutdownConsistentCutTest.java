// A03-01 회귀: 종료 저장(FurnaceMain FLUSH_WORLDS)은 차원마다 차례로 저장한다. 그 전에 World.holdForShutdown 으로 기준점(틱 붙잡기·새 이동 막기·
// 날아가는 이동 도착)을 잡으므로, 먼저 저장된 차원과 나중 차원 사이에서 엔티티가 둘이 되거나(복제) 사라지지(소실) 않는다.
// 바닐라는 서버 루프가 멈춘 뒤 saveAllChunks 를 하므로 저장 사이에 틱도 이동도 없다. 2차 점검 재현 시험(ReviewA03ShutdownTest)을 고친 순서로 바꿨다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShutdownConsistentCutTest {

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    private static final List<String> BIOMES = RegistrySet.load("registries/1.21.1.txt").entries("minecraft:worldgen/biome");
    private static final int PLAINS = BIOMES.indexOf("minecraft:plains");
    private static final ChunkPos CP = new ChunkPos(0, 0);
    /** 올라와 있지 않은 네더 청크 — 그리로 가는 이동은 목적 청크를 읽는 동안 날아가는 중이다. */
    private static final ChunkPos FAR = new ChunkPos(40, 40);

    @TempDir Path tmp;

    private static World world(DimensionType type, Path root, FurnaceSchedulerImpl sched, ExecutorService io) {
        Path d = type.storageDir(root);
        AnvilWorldSource src = new AnvilWorldSource(root, new RegionFileStore(d),
                new AnvilChunkCodec(REG, ItemRegistry.load("items/1.21.1.txt"), BIOMES, PLAINS, ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT),
                new SuperflatWorldSource(REG, PLAINS));
        src.entityStore(new RegionFileStore(d, "entities"));
        World w = new World(type, src, new ChunkWorkerPool(1), sched, 1, 4, 1, 2, 2, REG.directBits(), 7, io, io);
        w.setRegistries(REG, ItemRegistry.load("items/1.21.1.txt"));
        w.configurePersistence(1_000_000, 1_000_000);
        w.worldDir(root);
        return w;
    }

    private static <T> T on(World w, ChunkPos cp, java.util.function.Function<Region, T> f) throws Exception {
        return w.submitAtChunk(cp, f).get(10, TimeUnit.SECONDS);
    }

    private static UUID spawnCow(World w) throws Exception {
        return on(w, CP, r -> {
            var lvl = r.level();
            Cow c = new Cow(lvl, lvl.nextEntityId());
            c.setNoAi(true);
            c.setNoGravity(true);
            c.moveTo(8.5, 100, 8.5, 0, 0);
            lvl.addFreshEntity(c);
            return c.uuid();
        });
    }

    private static Entity find(Region r, UUID id) {
        for (Entity e : r.level().entities().tickSnapshot()) if (e.uuid().equals(id) && !e.isRemoved()) return e;
        return null;
    }

    private static List<UUID> cows(World w, ChunkPos cp) throws Exception {
        w.awaitChunk(cp);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!on(w, cp, r -> w.loadedChunk(cp.x(), cp.z()).entitiesLoaded())) {
            assertTrue(System.nanoTime() < deadline, "엔티티 묶음 읽기 대기 시간 초과");
            Thread.sleep(10);
        }
        return on(w, cp, r -> {
            List<UUID> out = new ArrayList<>();
            for (Entity e : r.level().entities().tickSnapshot()) if (e instanceof Cow && !e.isRemoved()) out.add(e.uuid());
            return out;
        });
    }

    private record Pair(World over, World nether, FurnaceSchedulerImpl sched, ExecutorService io) {
        void close() throws Exception {
            sched.close();
            io.shutdown();
            io.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private Pair open(boolean tick) {
        ExecutorService io = Executors.newSingleThreadExecutor();
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        World over = world(DimensionType.OVERWORLD, tmp, sched, io);
        World nether = world(DimensionType.NETHER, tmp, sched, io);
        Dimensions dims = new Dimensions();
        dims.register(over);
        dims.register(nether);
        if (tick) {
            for (World w : List.of(over, nether)) {
                w.setChunkForced(CP.x(), CP.z(), true);
                w.awaitChunk(CP);
                w.startTicking();
            }
        }
        return new Pair(over, nether, sched, io);
    }

    /** FLUSH_WORLDS 순서: 기준점 → 차원마다 차례로 저장 → (CLOSE_STORAGE) IO 비우기. between 은 첫 차원 저장 뒤·둘째 저장 전에 돈다. */
    private static void shutdownFlush(Pair p, Runnable between) throws Exception {
        assertTrue(World.holdForShutdown(List.of(p.over(), p.nether()), null, null, System.nanoTime() + TimeUnit.SECONDS.toNanos(20)));
        assertEquals(0, p.over().flushAllDrained(TimeUnit.SECONDS.toNanos(30)));
        between.run();
        assertEquals(0, p.nether().flushAllDrained(TimeUnit.SECONDS.toNanos(30)));
        p.over().ioBarrier().get(30, TimeUnit.SECONDS);
        p.nether().ioBarrier().get(30, TimeUnit.SECONDS);
        p.close();
    }

    /** 기준점 뒤에는 게임 틱이 돌지 않고, 저장 사이에 차원 이동을 시도해도 일어나지 않는다 — 두 소 모두 정확히 한 마리씩 남는다. */
    @Test
    void crossingBetweenDimensionFlushesNeitherDuplicatesNorLoses() throws Exception {
        Pair p = open(true);
        UUID overToNether = spawnCow(p.over());
        UUID netherToOver = spawnCow(p.nether());
        int[] ages = new int[2];
        boolean[] moved = new boolean[2];
        shutdownFlush(p, () -> {
            try {
                ages[0] = on(p.over(), CP, r -> find(r, overToNether).tickCount);
                Thread.sleep(300);
                ages[1] = on(p.over(), CP, r -> find(r, overToNether).tickCount);
                // 저장 사이 이동 시도(포털·명령 대역 — 실제 차원 이동 경로): 기준점 뒤라 받아들이지 않는다
                CountDownLatch done = new CountDownLatch(2);
                moved[0] = on(p.over(), CP, r -> find(r, overToNether).teleportToDimension("minecraft:the_nether", 8.5, 100, 8.5, 0, 0, done::countDown));
                moved[1] = on(p.nether(), CP, r -> find(r, netherToOver).teleportToDimension("minecraft:overworld", 8.5, 100, 8.5, 0, 0, done::countDown));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals(ages[0], ages[1], "기준점 뒤에도 게임 틱이 돌았다");
        assertFalse(moved[0] || moved[1], "기준점 뒤 차원 이동이 받아들여졌다");

        Pair q = open(false);
        try {
            List<UUID> all = new ArrayList<>(cows(q.over(), CP));
            all.addAll(cows(q.nether(), CP));
            assertEquals(1, all.stream().filter(overToNether::equals).count(), "먼저 저장된 차원의 소: " + all);
            assertEquals(1, all.stream().filter(netherToOver::equals).count(), "나중 저장된 차원의 소: " + all);
        } finally {
            q.close();
        }
    }

    /**
     * 기준점은 작업 큐를 멈추지 않는다: 네트워크 정지 뒤 큐에 들어간 퇴장 처리·저장 작업(여기서는 대역)은 기준점이 끝나기 전에 넣은 순서대로 돌고,
     * 기준점 뒤에 넣은 작업(저장)은 그 뒤에 돈다 — 종료 순서 "네트워크 정지 → 큐 비우기 → 틱 붙잡기 → 다시 비우기 → 저장".
     */
    @Test
    void tasksQueuedBeforeCutRunBeforeItCompletes() throws Exception {
        Pair p = open(true);
        try {
            List<Integer> order = java.util.Collections.synchronizedList(new ArrayList<>());
            CountDownLatch busy = new CountDownLatch(1);
            p.over().runAtChunk(CP, r -> {
                busy.countDown();
                try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                order.add(1);
            });
            assertTrue(busy.await(10, TimeUnit.SECONDS));
            p.over().runAtChunk(CP, r -> order.add(2));   // 첫 작업이 도는 동안 큐에 선다(퇴장 저장 대역)
            p.nether().runAtChunk(CP, r -> order.add(3));
            assertTrue(World.holdForShutdown(List.of(p.over(), p.nether()), null, null, System.nanoTime() + TimeUnit.SECONDS.toNanos(20)));
            assertTrue(order.containsAll(List.of(1, 2, 3)), "기준점 전에 넣은 작업이 다 돌지 않았다: " + order);
            assertTrue(order.indexOf(1) < order.indexOf(2), "같은 청크 작업 순서: " + order);
            assertEquals(4, (int) on(p.over(), CP, r -> { order.add(4); return 4; }), "기준점 뒤에도 작업은 돈다");
        } finally {
            p.close();
        }
    }

    /** 기준점을 잡을 때 목적 청크를 읽느라 날아가는 중인 이동은 도착한 뒤 저장된다(소실 없음), 떠난 차원에도 남지 않는다(복제 없음). */
    @Test
    void travelInFlightAtCutArrivesBeforeSave() throws Exception {
        Pair p = open(true);
        UUID cow = spawnCow(p.over());
        CountDownLatch done = new CountDownLatch(1);
        boolean started = on(p.over(), CP, r -> find(r, cow).teleportToDimension("minecraft:the_nether", FAR.x() * 16 + 8.5, 100, FAR.z() * 16 + 8.5, 0, 0, done::countDown));
        assertTrue(started);
        shutdownFlush(p, () -> { });
        assertEquals(0, done.getCount(), "기준점이 날아가는 이동을 기다리지 않았다");

        Pair q = open(false);
        try {
            List<UUID> all = new ArrayList<>(cows(q.over(), CP));
            all.addAll(cows(q.nether(), FAR));
            assertEquals(1, all.stream().filter(cow::equals).count(), "날아가던 소: " + all);
        } finally {
            q.close();
        }
    }
}
