// T-011(재직렬화 폭주): 이웃 청크가 올라오는 중이면 본문 직렬화를 미루고, 기다리는 요청 없는 미리 직렬화가 시작 전에 낡으면 풀이 건너뛴다.
// 증명: (1) 보낸 본문은 언제나 보낼 때의 청크 상태를 그 자리에서 직렬화한 것과 바이트가 같다(이웃 빛이 번지는 장면), (2) 시야 청크가 모두 온다,
// (3) 기다리는 요청이 붙은 일은 건너뛰지 않는다(예전 결과 그대로).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.ChunkSerializer;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.domi.furnace.server.world.gen.WorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ChunkBodyLightDeferTest {

    private static final RegionPos R00 = new RegionPos(0, 0);
    private static final ChunkPos C00 = new ChunkPos(0, 0);
    private static final int LEVEL_CHUNK = 0x27;
    private static final int VD = 5;

    private FurnaceSchedulerImpl sched;
    private ExecutorService io;
    private ChunkWorkerPool pool;
    private World w;
    private BlockRegistry reg;

    @AfterEach
    void close() {
        if (io != null) io.shutdownNow();
        if (sched != null) sched.close();
    }

    /**
     * 돌 속 굴(y −60..−58)이 모든 청크를 잇고, 청크마다 가장자리에 발광석이 있다 — 이웃이 올라와 빛을 붙이면(attachPrecomputed) 블록빛이
     * 가장자리를 넘어 이미 올라온 청크로 번진다(실서버 동굴·광원의 축소판). 위는 평지라 하늘빛도 있다.
     */
    private WorldSource litTunnels() {
        SuperflatWorldSource flat = new SuperflatWorldSource(reg,
                List.of(new SuperflatWorldSource.Layer("minecraft:bedrock", 1), new SuperflatWorldSource.Layer("minecraft:stone", 10)), 0);
        int air = reg.defaultStateId("minecraft:air");
        int glow = reg.defaultStateId("minecraft:glowstone");
        return (cx, cz) -> {
            ChunkColumn c = flat.generate(cx, cz);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -60; y <= -58; y++) c.setBlock(x, y, z, air);
            c.setBlock(0, -59, Math.floorMod(cx * 7 + cz * 3, 16), glow);
            c.setBlock(15, -59, Math.floorMod(cx * 5 + cz * 11, 16), glow);
            c.setBlock(Math.floorMod(cx * 3 + cz, 16), -59, 0, glow);
            c.setBlock(Math.floorMod(cx + cz * 13, 16), -59, 15, glow);
            return c;
        };
    }

    private World world(WorldSource src, boolean auto) {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        io = Executors.newSingleThreadExecutor();
        pool = new ChunkWorkerPool(1);
        w = new World(src, pool, sched, sched.regionThreadCount(), 16, VD, VD, reg.directBits(), 7, io);
        if (auto) w.configureStreaming(0, Config.CHUNKS_PER_TICK_AUTO);
        return w;
    }

    /** 주인 스레드에서 지금 청크를 직접 직렬화한 정답 본문. */
    private byte[] freshBodyOnOwner(ChunkPos cp) {
        ChunkColumn col = w.loadedChunk(cp.x(), cp.z());
        ByteBuf b = Unpooled.buffer();
        try {
            ChunkSerializer.serialize(b, ChunkSnapshot.of(cp.x(), cp.z(), col), reg.directBits(), 7);
            byte[] out = new byte[b.readableBytes()];
            b.getBytes(0, out);
            return out;
        } finally {
            b.release();
        }
    }

    private <T> T onOwner(java.util.function.Supplier<T> c) throws Exception {
        return sched.region(R00).submit(c).get(10, TimeUnit.SECONDS);
    }

    private static int drainChunks(EmbeddedChannel ch) {
        ch.runPendingTasks();
        int n = 0;
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf b = (ByteBuf) o;
            if (Types.readVarInt(b) == LEVEL_CHUNK) n++;
            b.release();
        }
        return n;
    }

    /** 이웃 빛이 번지는 장면: 보낸 본문 = 보낼 때 청크 상태, 시야 청크가 모두 온다. 예전 구현에서도 같은 단언이 성립한다(이 PR 은 시점만 바꾼다). */
    @Test
    void sentBodyEqualsChunkStateAtSendTime_withNeighbourLightSpreading() throws Exception {
        reg = BlockRegistry.load("blocks/1.21.1.txt");
        world(litTunnels(), true);
        List<String> mismatches = java.util.Collections.synchronizedList(new ArrayList<>());
        int[] checked = {0};
        w.chunkSendTap = (cp, body) -> {
            Region owner = w.regionizer().ownerOf(cp.x(), cp.z());
            if (owner == null || !owner.isOwnerThread()) {
                mismatches.add(cp + " 주인 스레드 밖에서 보냄");
                return;
            }
            checked[0]++;
            if (!Arrays.equals(freshBodyOnOwner(cp), body)) mismatches.add(cp + " 본문이 보낼 때 상태와 다름");
        };
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        Player p = new Player(1, UUID.randomUUID(), "A", ch);
        p.viewDistance(VD);
        p.applyMove(8, -59, 8, 0, 0, true);
        sched.region(R00).execute(() -> w.regionAt(R00).adopt(p)).get();
        int total = ChunkTrackingViewTest.vanillaCount(VD);
        int sent = 0;
        for (int i = 0; i < 1500 && sent < total; i++) {
            sched.region(R00).execute(() -> w.regionAt(R00).tick()).get();
            Thread.sleep(2);
            sent += drainChunks(ch);
        }
        assertEquals(total, sent, "시야 안 청크가 모두 와야 한다");
        assertTrue(mismatches.isEmpty(), mismatches.toString());
        assertEquals(total, checked[0]);
        // 이 장면은 실제로 이웃 빛 때문에 미루기가 일어나야 의미가 있다(장면 점검)
        assertTrue(w.bodyLightDeferrals.get() > 0, "이웃이 올라오는 중이라 미룬 일이 한 번도 없다 — 장면이 질문을 안 한다");
    }

    /** 이웃(대각선 포함)이 올라오는 중이면 미리 직렬화도, 보내려는 청크의 직렬화도 걸지 않는다. 다 올라오면 건다. */
    @Test
    void neighbourLoading_defersSerialization_untilLoaded() throws Exception {
        reg = BlockRegistry.load("blocks/1.21.1.txt");
        world(new SuperflatWorldSource(reg, 0), true);
        onOwner(() -> w.regionAt(R00).chunkAtBlocking(C00));
        long diag = Regionizer.chunkKey(1, -1);
        CompletableFuture<ChunkColumn> fake = new CompletableFuture<>();
        w.putLoading(diag, fake);
        int snaps0 = w.bodySnapshots.get();
        assertEquals(World.BODY_PENDING, (int) onOwner(() -> w.chunkBodyReadyOrPrefetch(C00, false, true)));
        assertEquals(World.BODY_PENDING, (int) onOwner(() -> w.chunkBodyReadyOrPrefetch(C00, true, true)));
        assertEquals(snaps0, w.bodySnapshots.get(), "이웃이 올라오는 중에는 스냅샷을 뜨지 않는다");
        assertEquals(0, (int) onOwner(() -> w.loadedChunk(0, 0).bodyWaits()), "미루는 동안 바로 직렬화 기다림을 세지 않는다");
        w.removeLoading(diag, fake);
        assertEquals(World.BODY_PENDING, (int) onOwner(() -> w.chunkBodyReadyOrPrefetch(C00, true, true)));
        assertEquals(snaps0 + 1, w.bodySnapshots.get(), "이웃이 다 올라오면 직렬화를 건다");
    }

    /** 플레이어가 곧 받을 청크(대기열) 안의 이웃이 아직 안 올라왔으면 올라오는 중과 같이 미룬다. 시야 밖 이웃(대기열에 없음)은 기다리지 않는다. */
    @Test
    void upcomingNeighbourNotYetLoaded_defers_outsideViewDoesNot() throws Exception {
        reg = BlockRegistry.load("blocks/1.21.1.txt");
        world(new SuperflatWorldSource(reg, 0), true);
        onOwner(() -> w.regionAt(R00).chunkAtBlocking(C00));
        int snaps0 = w.bodySnapshots.get();
        java.util.Set<ChunkPos> upcoming = new java.util.LinkedHashSet<>(List.of(new ChunkPos(0, 1)));
        assertEquals(World.BODY_PENDING, (int) onOwner(() -> w.chunkBodyReadyOrPrefetch(C00, true, true, upcoming)));
        assertEquals(snaps0, w.bodySnapshots.get(), "곧 올라올 이웃이 있으면 직렬화를 걸지 않는다");
        onOwner(() -> w.regionAt(R00).chunkAtBlocking(new ChunkPos(0, 1)));
        assertEquals(World.BODY_PENDING, (int) onOwner(() -> w.chunkBodyReadyOrPrefetch(C00, true, true, upcoming)));
        assertEquals(snaps0 + 1, w.bodySnapshots.get(), "이웃이 올라왔으면(나머지 이웃은 시야 밖) 직렬화를 건다");
    }

    /** 이웃 로드가 끝나지 않아도 보내려는 청크는 MAX_LIGHT_WAITS 번만 미루고 그 뒤에는 예전처럼 직렬화한다(영영 못 보내지 않게). */
    @Test
    void neighbourLoading_sendDeferralIsBounded() throws Exception {
        reg = BlockRegistry.load("blocks/1.21.1.txt");
        world(new SuperflatWorldSource(reg, 0), true);
        onOwner(() -> w.regionAt(R00).chunkAtBlocking(C00));
        CompletableFuture<ChunkColumn> fake = new CompletableFuture<>();
        w.putLoading(Regionizer.chunkKey(-1, 0), fake);
        int snaps0 = w.bodySnapshots.get();
        for (int i = 0; i < World.MAX_LIGHT_WAITS; i++) onOwner(() -> w.chunkBodyReadyOrPrefetch(C00, true, true));
        assertEquals(snaps0, w.bodySnapshots.get());
        onOwner(() -> w.chunkBodyReadyOrPrefetch(C00, true, true));
        assertEquals(snaps0 + 1, w.bodySnapshots.get(), "상한 뒤에는 직렬화를 건다");
        w.removeLoading(Regionizer.chunkKey(-1, 0), fake);
    }

    /** 기다리는 요청 없는 미리 직렬화가 풀에서 시작하기 전에 청크가 바뀌면 건너뛴다 — 캐시에도 안 들어가고 다음 요청은 새 상태를 받는다. */
    @Test
    void speculativeJob_staleBeforeStart_isSkipped() throws Exception {
        reg = BlockRegistry.load("blocks/1.21.1.txt");
        world(new SuperflatWorldSource(reg, 0), true);
        onOwner(() -> w.regionAt(R00).chunkAtBlocking(C00));
        CountDownLatch gate = new CountDownLatch(1);
        pool.submit(() -> { try { gate.await(); } catch (InterruptedException ignored) { } return null; });
        assertEquals(World.BODY_PENDING, (int) onOwner(() -> w.chunkBodyReadyOrPrefetch(C00, false, true)));
        onOwner(() -> { w.regionAt(R00).breakBlock(0, -61, 0); return null; });
        gate.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (w.staleBodySkips.get() == 0 && System.nanoTime() < deadline) Thread.onSpinWait();
        assertEquals(1, w.staleBodySkips.get(), "낡은 미리 직렬화는 건너뛴다");
        assertNull(onOwner(() -> w.loadedChunk(0, 0).cachedSerializedBody()));
        byte[] got = w.serializedChunkBody(C00).get(10, TimeUnit.SECONDS);
        assertArrayEquals(onOwner(() -> freshBodyOnOwner(C00)), got, "다음 요청은 바뀐 상태의 본문");
    }

    /** 미리 직렬화에 기다리는 요청이 붙으면 낡아도 건너뛰지 않고 예전처럼 그 결과(스냅샷 때 상태)를 준다. */
    @Test
    void speculativeJob_withWaiter_isNotSkipped() throws Exception {
        reg = BlockRegistry.load("blocks/1.21.1.txt");
        world(new SuperflatWorldSource(reg, 0), true);
        onOwner(() -> w.regionAt(R00).chunkAtBlocking(C00));
        byte[] before = onOwner(() -> freshBodyOnOwner(C00));
        CountDownLatch gate = new CountDownLatch(1);
        pool.submit(() -> { try { gate.await(); } catch (InterruptedException ignored) { } return null; });
        onOwner(() -> w.chunkBodyReadyOrPrefetch(C00, false, true));
        CompletableFuture<byte[]> waiter = w.serializedChunkBody(C00);
        // 요청은 주인 스레드를 두 번 거쳐(로드 확인 → 붙기) 진행 중 일에 붙는다 — 그 뒤에 바꾼다
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline && !onOwner(() -> w.loadedChunk(0, 0).inflightSerializedBody() instanceof World.BodyJob j && !j.speculative)) {
            Thread.onSpinWait();
        }
        onOwner(() -> { w.regionAt(R00).breakBlock(0, -61, 0); return null; });   // 붙은 뒤에 바뀜
        gate.countDown();
        assertArrayEquals(before, waiter.get(10, TimeUnit.SECONDS), "붙은 요청은 예전처럼 스냅샷 때 본문을 받는다");
        assertEquals(0, w.staleBodySkips.get());
        assertEquals(1, w.bodySnapshots.get(), "붙은 요청은 새로 직렬화하지 않는다");
    }

    /** 이웃 기다림은 틱당 한 번만 센다(여럿이 같은 청크를 기다려도 상한 20틱 그대로), 한 틱이라도 끊기면 1 부터 다시 센다(PR #301 검토 R1). */
    @Test
    void lightWaits_countOncePerTick_andRestartAfterGap() throws Exception {
        reg = BlockRegistry.load("blocks/1.21.1.txt");
        world(new SuperflatWorldSource(reg, 0), true);
        onOwner(() -> w.regionAt(R00).chunkAtBlocking(C00));
        CompletableFuture<ChunkColumn> fake = new CompletableFuture<>();
        w.putLoading(Regionizer.chunkKey(1, 0), fake);
        for (long t = 100; t < 105; t++) {
            long tick = t;
            for (int player = 0; player < 3; player++) onOwner(() -> w.chunkBodyReadyOrPrefetch(C00, true, true, null, tick));
        }
        assertEquals(5, (int) onOwner(() -> w.loadedChunk(0, 0).lightWaits()), "세 명이 5틱 기다리면 5");
        onOwner(() -> w.chunkBodyReadyOrPrefetch(C00, true, true, null, 107));   // 105·106 은 대기열에서 빠져 있었다
        assertEquals(1, (int) onOwner(() -> w.loadedChunk(0, 0).lightWaits()), "끊긴 뒤에는 1 부터");
        int snaps0 = w.bodySnapshots.get();
        for (long t = 108; t < 108 + World.MAX_LIGHT_WAITS - 1; t++) {
            long tick = t;
            onOwner(() -> w.chunkBodyReadyOrPrefetch(C00, true, true, null, tick));
        }
        assertEquals(snaps0, w.bodySnapshots.get(), "연속 20틱까지는 미룬다");
        onOwner(() -> w.chunkBodyReadyOrPrefetch(C00, true, true, null, 108 + World.MAX_LIGHT_WAITS - 1));
        assertEquals(snaps0 + 1, w.bodySnapshots.get(), "상한 뒤에는 직렬화를 건다");
        w.removeLoading(Regionizer.chunkKey(1, 0), fake);
    }

    /**
     * 실제 경합(PR #301 검토 R3, 검토자 ReviewPr301RaceTest 를 줄임): 풀 시작 시점을 0~4ms 흔들며 미리 직렬화·붙기·바꾸기를 섞어도
     * 기다리는 요청은 null(건너뛴 일의 결과)을 받지 않는다. 다른 스레드에서 붙는 경로도 섞는다.
     */
    @Test
    void realRace_waitersNeverGetNull() throws Exception {
        reg = BlockRegistry.load("blocks/1.21.1.txt");
        world(new SuperflatWorldSource(reg, 0), true);
        onOwner(() -> w.regionAt(R00).chunkAtBlocking(C00));
        java.util.Random rnd = new java.util.Random(301);
        int stone = reg.defaultStateId("minecraft:stone"), air = reg.defaultStateId("minecraft:air");
        int nulls = 0;
        for (int i = 0; i < 100; i++) {
            int mode = rnd.nextInt(4), x1 = rnd.nextInt(16), z1 = rnd.nextInt(16), x2 = rnd.nextInt(16), z2 = rnd.nextInt(16);
            int st = (i & 1) == 0 ? stone : air;
            int blockUs = rnd.nextInt(4000);
            pool.submit(() -> {
                long end = System.nanoTime() + blockUs * 1000L;
                while (System.nanoTime() < end) Thread.onSpinWait();
                return null;
            });
            // 같은 주인 태스크 안에서: 캐시 비우기 → 미리 직렬화 → (가끔) 바꾸기 → 붙기 → (가끔) 바꾸기
            CompletableFuture<byte[]> waiter = onOwner(() -> {
                w.loadedChunk(0, 0).setBlock(x1, 12, z2, st);
                w.chunkBodyReadyOrPrefetch(C00, false, true);
                if (mode == 0) w.loadedChunk(0, 0).setBlock(x1, 10, z1, st);
                CompletableFuture<byte[]> f = w.serializedChunkBody(C00, true);
                if (mode >= 2) w.loadedChunk(0, 0).setBlock(x2, 11, z2, st);
                return f;
            });
            if (waiter.get(10, TimeUnit.SECONDS) == null) nulls++;
            CompletableFuture<byte[]> ext = w.serializedChunkBody(C00);
            onOwner(() -> w.chunkBodyReadyOrPrefetch(C00, false, true));
            if (ext.get(10, TimeUnit.SECONDS) == null) nulls++;
        }
        assertEquals(0, nulls, "기다리는 요청이 건너뛴 일의 null 을 받았다");
        assertTrue(w.staleBodySkips.get() > 0, "건너뛰기 경로가 실제로 돌아야 의미가 있다");
    }
}
