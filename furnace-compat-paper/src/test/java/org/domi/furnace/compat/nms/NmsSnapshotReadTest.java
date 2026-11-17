package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.api.internal.ChunkInternals;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * N2b-3(설계 3.6, V5 읽기·교착 장면): 주인 region 이 아닌 스레드의 NMS 청크 읽기는 주인이 틱 끝에 낸 공개 스냅샷을 기다리지 않고 읽는다.
 * 기다리지 않는 조회는 공개본이 없으면 null(다음 틱부터 보임), 기다리는 조회는 region 이 아닌 스레드만 기다리고 다른 region 스레드는 기다리지 않는다(D4).
 * 시험마다 다른 청크를 쓴다(공개 상태가 시험 순서에 기대지 않게).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsSnapshotReadTest {

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    CraftWorld bukkitWorld;
    org.domi.furnace.api.world.World world;
    org.domi.furnace.api.world.BlockPos at;
    int cx, cz;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Snap");
        server.tick(3);
        var loc = tp.player().location();
        at = new org.domi.furnace.api.world.BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        cx = Math.floorDiv(at.x(), 16);
        cz = Math.floorDiv(at.z(), 16);
        bukkitWorld = (CraftWorld) Compat.player(tp.player()).getWorld();
        level = bukkitWorld.getHandle();
        world = level.getWorld().furnaceHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    ChunkInternals bridge() { return NmsChunks.bridge(); }

    <T> T on(Supplier<T> task) { return server.onRegion(at, task); }

    int truth(int x, int y, int z) {
        return on(() -> server.internals().world().regionizer().ownerOf(x >> 4, z >> 4).getBlock(x, y, z));
    }

    /** 플레이어 근처의 로드된 청크(시험마다 다른 것). */
    int[] nearChunk(int dx, int dz) {
        int x = cx + dx, z = cz + dz;
        server.loadChunk(x, z);
        assertSame(server.internals().world().regionizer().ownerOf(cx, cz), server.internals().world().regionizer().ownerOf(x, z), "같은 region 이어야 하는 시험 전제");
        return new int[] {x, z};
    }

    BlockPos surface(int chunkX, int chunkZ) {
        int x = chunkX * 16 + 5, z = chunkZ * 16 + 7;
        int y = on(() -> level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
        return new BlockPos(x, y, z);
    }

    @Test
    void nonBlockingLookupIsNullUntilOwnerPublishes() {
        int[] c = nearChunk(2, 0);
        assertTrue(level.hasChunk(c[0], c[1]));
        assertNull(bridge().chunkView(world, c[0], c[1]), "아직 아무도 읽지 않은 청크는 공개본이 없다");
        assertNull(level.getChunkSource().getChunkNow(c[0], c[1]), "기다리지 않는 조회 = null(부탁만 남김)");
        assertNull(level.getChunkIfLoaded(c[0], c[1]));
        server.tick(1);
        LevelChunk ch = level.getChunkSource().getChunkAtIfLoadedImmediately(c[0], c[1]);
        assertNotNull(ch, "부탁한 다음 틱부터 보인다");
        assertSame(ch, level.getChunkSource().getChunkNow(c[0], c[1]));
        assertSame(ch, on(() -> level.getChunk(c[0], c[1])), "주인 스레드와 같은 shim");
        BlockPos p = surface(c[0], c[1]).below();
        assertSame(Block.stateById(truth(p.getX(), p.getY(), p.getZ())), ch.getBlockState(p));
    }

    @Test
    void blockingLookupFromNonRegionThreadWaitsForOwner() {
        int[] c = nearChunk(-2, 0);
        assertNull(bridge().chunkView(world, c[0], c[1]));
        LevelChunk ch = level.getChunk(c[0], c[1]);   // region 이 아닌 스레드 — 주인이 공개할 때까지(틱 없이도 주인 작업으로)
        assertNotNull(ch);
        assertNotNull(bridge().chunkView(world, c[0], c[1]));
        BlockPos p = surface(c[0], c[1]).below();
        assertSame(Block.stateById(truth(p.getX(), p.getY(), p.getZ())), ch.getBlockState(p));
        assertSame(ch, level.getChunkSource().getChunk(c[0], c[1], false), "create=false 도 같은 객체");
        int fx = cx + 400;
        assertThrows(IllegalStateException.class, () -> level.getChunk(fx, cz), "로드 안 된 청크는 동기로 불러오지 않는다");
        assertNull(level.getChunkSource().getChunk(fx, cz, false));
    }

    @Test
    void ownerWritesBecomeVisibleAfterTickWithNewVersion() {
        int[] c = nearChunk(0, 2);
        LevelChunk ch = on(() -> level.getChunk(c[0], c[1]));   // 주인 스레드에서 붙이면 그 자리에서 공개
        long v0 = bridge().chunkView(world, c[0], c[1]).version();
        BlockPos p = surface(c[0], c[1]);
        assertTrue(ch.getBlockState(p).isAir());
        on(() -> level.setBlock(p, Blocks.GLASS.defaultBlockState(), 3));
        server.tick(1);
        var v1 = bridge().chunkView(world, c[0], c[1]);
        assertTrue(v1.version() > v0, "바뀐 뒤 판 번호가 오른다");
        assertSame(Blocks.GLASS.defaultBlockState(), ch.getBlockState(p), "틱 끝 공개 뒤 다른 스레드가 새 값을 본다");
        assertEquals(p.getY() + 1, ch.getHeight(Heightmap.Types.WORLD_SURFACE, p.getX() & 15, p.getZ() & 15) + 1, "하이트맵도 같은 판");
        server.tick(1);
        assertSame(v1, bridge().chunkView(world, c[0], c[1]), "바뀐 것이 없으면 같은 판");
    }

    @Test
    void blockEntityPositionsFromSnapshot() {
        int[] c = nearChunk(0, -2);
        LevelChunk ch = on(() -> level.getChunk(c[0], c[1]));
        BlockPos p = surface(c[0], c[1]);
        on(() -> level.setBlock(p, Blocks.CHEST.defaultBlockState(), 3));
        assertTrue(on(() -> ch.getBlockEntitiesPos()).contains(p), "주인 스레드: 살아 있는 값");
        server.tick(1);
        var off = ch.getBlockEntitiesPos();
        assertTrue(off.contains(p), "다른 스레드: 공개 스냅샷 " + off);
        off.clear();
        assertFalse(ch.getBlockEntitiesPos().isEmpty(), "돌려준 집합은 새 집합(고쳐도 청크에 안 닿음)");
    }

    /** D4: 다른 region 의 스레드도 비동기 스레드와 같은 규칙 — 공개본을 읽고, 기다리는 조회는 기다리지 않는다(교착). */
    @Test
    void otherRegionThreadReadsSnapshotWithoutWaiting() {
        int fx = cx + 64, fz = cz;
        server.loadChunk(fx, fz);
        var rz = server.internals().world().regionizer();
        assertNotSame(rz.ownerOf(cx, cz), rz.ownerOf(fx, fz), "먼 청크는 다른 region");
        int[] c = nearChunk(3, 3);
        int[] bare = nearChunk(-3, -3);   // 공개본 없는 청크
        LevelChunk ch = on(() -> level.getChunk(c[0], c[1]));
        BlockPos p = surface(c[0], c[1]).below();
        int want = truth(p.getX(), p.getY(), p.getZ());
        var far = new org.domi.furnace.api.world.BlockPos(fx * 16, 64, fz * 16);
        String r = server.onRegion(far, () -> {
            assertSame(Block.stateById(want), ch.getBlockState(p), "남의 청크 shim 읽기 = 공개 스냅샷");
            assertSame(ch, level.getChunkIfLoaded(c[0], c[1]));
            assertNull(level.getChunkIfLoaded(bare[0], bare[1]), "공개본 없으면 null");
            assertThrows(IllegalStateException.class, () -> level.getChunk(bare[0], bare[1]), "다른 region 스레드는 기다리지 않는다");
            assertNull(level.getChunkSource().getChunk(bare[0], bare[1], false));
            return "ok";
        });
        assertEquals("ok", r);
        server.tick(1);
        assertNotNull(level.getChunkSource().getChunkNow(bare[0], bare[1]), "앞서 남긴 부탁으로 다음 틱부터 보인다");
    }

    /**
     * V5 교착 장면: 플러그인 잠금을 쥔 비동기 스레드가 읽는 동안 그 region 이 같은 잠금을 기다린다(FAWE sectionLock 꼴). 읽기가 주인을 기다리면
     * 서로 기다려 멈춘다 — 공개 스냅샷 읽기는 기다리지 않으므로 끝난다.
     */
    @Test
    void readsUnderPluginLockDoNotDeadlockWithBusyRegion() throws Exception {
        int[] c = nearChunk(2, 2);
        LevelChunk ch = on(() -> level.getChunk(c[0], c[1]));
        BlockPos p = surface(c[0], c[1]).below();
        BlockState want = Block.stateById(truth(p.getX(), p.getY(), p.getZ()));
        Object pluginLock = new Object();
        CountDownLatch readerHolds = new CountDownLatch(1), regionWaiting = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                synchronized (pluginLock) {
                    readerHolds.countDown();
                    if (!regionWaiting.await(10, TimeUnit.SECONDS)) throw new AssertionError("region 작업이 시작되지 않았다");
                    Thread.sleep(20);   // region 스레드가 잠금 앞에서 멈출 시간
                    for (int i = 0; i < 2000; i++) {
                        if (ch.getBlockState(p) != want) throw new AssertionError("다른 값");
                        ch.getSection(0).getStates().get(0, 0, 0);
                    }
                    if (level.getChunk(c[0], c[1]) != ch) throw new AssertionError("공개본이 있으면 기다리는 조회도 곧바로 같은 shim");
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "plugin-async-reader");
        reader.start();
        assertTrue(readerHolds.await(10, TimeUnit.SECONDS));
        CompletableFuture<Boolean> regionTask = world.scheduler(at).submit(() -> {
            regionWaiting.countDown();
            synchronized (pluginLock) { return true; }
        });
        reader.join(TimeUnit.SECONDS.toMillis(20));
        assertFalse(reader.isAlive(), "읽는 스레드가 멈췄다(교착)");
        assertTrue(server.await(regionTask));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    /** 틱마다 주인이 두 칸(다른 섹션)을 같은 값으로 바꾸는 동안 다른 스레드가 읽는 한 판 안의 두 칸은 늘 같다(한 순간의 사본), 판 번호는 줄지 않는다. */
    @Test
    void concurrentReadersSeeConsistentVersions() throws Exception {
        int[] c = nearChunk(-2, 2);
        on(() -> level.getChunk(c[0], c[1]));
        int x = c[0] * 16 + 1, z = c[1] * 16 + 1;
        int ya = 200, yb = 250;   // 다른 섹션
        int glass = Block.getId(Blocks.GLASS.defaultBlockState()), stone = Block.getId(Blocks.STONE.defaultBlockState());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicLong reads = new java.util.concurrent.atomic.AtomicLong();
        Thread[] readers = new Thread[3];
        for (int t = 0; t < readers.length; t++) {
            readers[t] = new Thread(() -> {
                long last = 0;
                try {
                    while (!stop.get()) {
                        var v = bridge().chunkView(world, c[0], c[1]);
                        if (v == null) continue;
                        if (v.version() < last) throw new AssertionError("판 번호가 줄었다 " + last + " → " + v.version());
                        last = v.version();
                        int a = v.stateId(x, ya, z), b = v.stateId(x, yb, z);
                        if (a != b) throw new AssertionError("한 판 안에서 어긋남: " + a + " / " + b + " (판 " + last + ")");
                        reads.incrementAndGet();
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }, "snapshot-reader-" + t);
            readers[t].start();
        }
        for (int i = 0; i < 30; i++) {
            BlockState s = Block.stateById(i % 2 == 0 ? glass : stone);
            on(() -> {
                level.setBlock(new BlockPos(x, ya, z), s, 2);
                level.setBlock(new BlockPos(x, yb, z), s, 2);
                return null;
            });
            server.tick(1);
        }
        stop.set(true);
        for (Thread t : readers) t.join(10_000);
        if (failure.get() != null) throw new AssertionError(failure.get());
        assertTrue(reads.get() > 0);
        var v = bridge().chunkView(world, c[0], c[1]);
        assertEquals(stone, v.stateId(x, ya, z));
        assertEquals(stone, v.stateId(x, yb, z));
    }

    // ------------------------------------------------------------------ PR #382 검토 R1: 전역 스레드는 region 을 기다리지 않는다

    org.domi.furnace.api.sched.GlobalScheduler global() { return server.api().scheduler().global(); }

    /** 대조군: 빌림 구간 안, region 이 한가하면 빌림이 성공해 주인으로 바로 공개한다. */
    @Test
    void globalBorrowGetsChunkWhenRegionIdle() {
        int[] c = nearChunk(4, 4);
        LevelChunk ch = server.onGlobal(() -> {
            try (var a = global().openRegionAccess()) {
                return level.getChunk(c[0], c[1]);
            }
        });
        assertNotNull(ch);
        assertNotNull(bridge().chunkView(world, c[0], c[1]));
    }

    /**
     * region 이 전역 작업 결과를 막고 기다리는데(GlobalWait) 그 전역 작업이 그 region 의 공개본 없는 청크를 기다리는 조회로 얻는다 — 빌림은 서로
     * 기다림을 알아채고 포기한다. 전역이 region 을 join 하면 둘 다 멈춘다. 고친 뒤: 전역은 곧바로 IllegalStateException.
     */
    @Test
    void globalLookupWhileRegionWaitsOnGlobalDoesNotDeadlock() throws Exception {
        int[] c = nearChunk(-4, 4);
        AtomicReference<CompletableFuture<Object>> inner = new AtomicReference<>();
        CountDownLatch globalStarted = new CountDownLatch(1);
        CompletableFuture<Object> regionTask = world.scheduler(new org.domi.furnace.api.world.BlockPos(c[0] * 16, 64, c[1] * 16)).submit(() -> {
            CompletableFuture<Object> g = global().submit(() -> {
                globalStarted.countDown();
                try (var a = global().openRegionAccess()) {
                    return level.getChunk(c[0], c[1]);
                } catch (Throwable t) {
                    return t;
                }
            });
            inner.set(g);
            return g.join();   // GlobalWait 표시가 달린 기다림
        });
        assertTrue(globalStarted.await(10, TimeUnit.SECONDS));
        Object result;
        try {
            result = regionTask.get(10, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            inner.get().complete("released-by-test");   // 풀어 주고 실패로
            regionTask.get(10, TimeUnit.SECONDS);
            throw new AssertionError("교착: region 은 전역을, 전역은 region 공개를 기다렸다");
        }
        assertTrue(result instanceof IllegalStateException, "전역은 기다리지 않고 예외: " + result);
    }

    /** 전역(옛 동기 작업)이 플러그인 잠금을 쥔 채 기다리는 조회를 부르고 그 region 이 같은 잠금을 기다린다 — 전역은 기다리지 않고 예외로 끝난다. */
    @Test
    void globalHoldingPluginLockDoesNotDeadlockWithRegion() throws Exception {
        int[] c = nearChunk(4, -4);
        java.util.concurrent.locks.ReentrantLock pluginLock = new java.util.concurrent.locks.ReentrantLock();
        CountDownLatch globalHolds = new CountDownLatch(1);
        AtomicReference<Thread> regionThread = new AtomicReference<>();
        CompletableFuture<Object> g = global().submit(() -> {
            pluginLock.lock();
            try {
                globalHolds.countDown();
                try { Thread.sleep(100); } catch (InterruptedException ignored) { }   // region 이 잠금 앞에서 멈출 시간
                try (var a = global().openRegionAccess()) {
                    return level.getChunk(c[0], c[1]);
                }
            } catch (Throwable t) {
                return t;
            } finally {
                pluginLock.unlock();
            }
        });
        assertTrue(globalHolds.await(10, TimeUnit.SECONDS));
        CompletableFuture<Object> regionTask = world.scheduler(new org.domi.furnace.api.world.BlockPos(c[0] * 16, 64, c[1] * 16)).submit(() -> {
            regionThread.set(Thread.currentThread());
            try {
                pluginLock.lockInterruptibly();
                pluginLock.unlock();
                return "locked";
            } catch (InterruptedException e) {
                return "interrupted";
            }
        });
        Object result;
        try {
            result = g.get(10, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            if (regionThread.get() != null) regionThread.get().interrupt();   // 풀어 주고 실패로
            g.get(10, TimeUnit.SECONDS);
            throw new AssertionError("교착: 전역은 잠금을 쥐고 region 공개를, region 은 잠금을 기다렸다");
        }
        assertTrue(result instanceof IllegalStateException, "전역은 기다리지 않고 예외: " + result);
        assertEquals("locked", regionTask.get(10, TimeUnit.SECONDS));
    }

    /**
     * FAWE 꼴: getChunkAtAsync 의 이음에서 CraftChunk.getHandle — 이음은 주인 스레드(Paper 처럼 주인이 끝냄)거나, 이미 끝난 Future 면 부른 스레드다.
     * 어느 쪽이든 받은 shim 은 곧바로 다른 스레드에서 읽힌다(R16: 주인이면 그 자리에서 공개, 아니면 기다리는 조회가 공개를 기다림).
     */
    @Test
    void handleFromAsyncChunkIsReadableEverywhere() {
        int[] c = nearChunk(3, -3);
        CompletableFuture<LevelChunk> f = bukkitWorld.getChunkAtAsync(c[0], c[1], true, false)
                .thenApply(chunk -> (LevelChunk) ((CraftChunk) chunk).getHandle(ChunkStatus.FULL));
        LevelChunk ch = server.await(f);
        assertNotNull(bridge().chunkView(world, c[0], c[1]), "넘겨준 shim 에는 공개본이 있다");
        BlockPos p = surface(c[0], c[1]).below();
        assertSame(Block.stateById(truth(p.getX(), p.getY(), p.getZ())), ch.getBlockState(p));
        assertSame(ch, ((CraftChunk) bukkitWorld.getChunkAt(c[0], c[1])).getHandle(ChunkStatus.FULL));
        // 주인 스레드에서 이음이 돌 때(Paper 의 보통 경우)
        int[] d = nearChunk(-3, 3);
        LevelChunk owned = on(() -> (LevelChunk) ((CraftChunk) bukkitWorld.getChunkAt(d[0], d[1])).getHandle(ChunkStatus.FULL));
        assertNotNull(bridge().chunkView(world, d[0], d[1]), "주인 스레드에서 붙인 shim 은 그 자리에서 공개");
        assertSame(owned, level.getChunkSource().getChunkNow(d[0], d[1]));
    }
}
