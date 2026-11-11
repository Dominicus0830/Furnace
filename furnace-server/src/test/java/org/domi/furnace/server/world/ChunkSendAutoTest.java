// chunks-per-tick=auto: 준비된 청크만 배치로, 클라이언트 속도(최대 64/틱)만큼 보내고, 없는 청크는 동시 상한 안에서 미리 불러온다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ChunkSendAutoTest {

    private static final int CHUNK_BATCH_START = 0x0D;
    private static final int CHUNK_BATCH_FINISHED = 0x0C;
    private static final int LEVEL_CHUNK = 0x27;
    private static final int VD = 4;   // 9×9 = 81 청크(구역 16×16 하나 안)

    private FurnaceSchedulerImpl sched;
    private ExecutorService io;
    private World w;
    private final RegionPos rp = new RegionPos(0, 0);

    private World world(boolean auto) {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        io = Executors.newSingleThreadExecutor();
        w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, sched.regionThreadCount(), 16, VD, VD,
                reg.directBits(), 7, io);
        w.configureStreaming(0, auto ? Config.CHUNKS_PER_TICK_AUTO : 16);
        return w;
    }

    @AfterEach
    void close() {
        if (io != null) io.shutdown();
        if (sched != null) sched.close();
    }

    private Player join(EmbeddedChannel ch) throws Exception {
        Player p = new Player(1, UUID.randomUUID(), "A", ch);
        p.viewDistance(VD);
        p.applyMove(8, -60, 8, 0, 0, true);
        sched.region(rp).execute(() -> w.regionAt(rp).adopt(p)).get();
        return p;
    }

    private void tick() throws Exception {
        sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
    }

    /** 나간 패킷을 배치 단위로: 배치마다 {Start~Finished 사이 청크 수, Finished 의 수}. */
    private static void drain(EmbeddedChannel ch, List<int[]> batches, int[] open) {
        ch.runPendingTasks();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf b = (ByteBuf) o;
            int id = Types.readVarInt(b);
            if (id == CHUNK_BATCH_START) open[0] = 0;
            else if (id == LEVEL_CHUNK) open[0]++;
            else if (id == CHUNK_BATCH_FINISHED) batches.add(new int[]{open[0], Types.readVarInt(b)});
            b.release();
        }
    }

    @Test
    void loadedChunks_goOutAtClientRate_notCappedAt16() throws Exception {
        world(true);
        for (int x = -VD; x <= VD; x++)
            for (int z = -VD; z <= VD; z++) w.awaitChunk(new ChunkPos(x, z));   // 모두 미리 로드
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        join(ch);
        List<int[]> batches = new ArrayList<>();
        int[] open = {0};
        // 첫 틱은 본문 직렬화만 건다(본문이 준비된 청크만 배치에) — 직렬화가 끝나 캐시에 들어가면 다음 틱에 나간다
        tick();
        drain(ch, batches, open);
        assertTrue(batches.isEmpty(), "본문이 준비 안 된 청크로 배치를 열면 안 된다");
        Thread.sleep(500);
        for (int i = 0; i < 100 && batches.isEmpty(); i++) {
            tick();
            Thread.sleep(10);
            drain(ch, batches, open);
        }
        assertFalse(batches.isEmpty(), "본문이 준비되면 배치가 나가야 한다");
        // 첫 응답 전에도 auto 상한(64)까지 한 배치로 — 예전 고정 기본값 16 보다 많다
        assertEquals(Region.AUTO_MAX_CHUNKS_PER_TICK, batches.get(0)[1]);
        assertEquals(batches.get(0)[1], batches.get(0)[0], "Finished 수 == 실제 보낸 청크 수");
    }

    @Test
    void unloadedChunks_arePrefetched_notBatched_andAllArriveEventually() throws Exception {
        world(true);
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        Player p = join(ch);
        tick();
        List<int[]> batches = new ArrayList<>();
        int[] open = {0};
        drain(ch, batches, open);
        // 첫 틱에는 로드된 청크가 없다 → 배치 없음, 생성만 동시 상한까지 걸린다
        assertTrue(batches.isEmpty(), "준비 안 된 청크로 배치를 열면 안 된다");
        int prefetching = p.chunkPrefetches().size();
        assertTrue(prefetching > 0 && prefetching <= w.maxChunkSendsInFlight(), "미리 불러오기 수 " + prefetching);

        int total = (2 * VD + 1) * (2 * VD + 1);
        int sent = 0;
        for (int i = 0; i < 400 && sent < total; i++) {
            tick();
            Thread.sleep(5);
            drain(ch, batches, open);
            sent = batches.stream().mapToInt(b -> b[1]).sum();
        }
        assertEquals(total, sent, "시야 안 청크가 모두 와야 한다");
        for (int[] b : batches) assertEquals(b[1], b[0], "배치마다 Finished 수 == 실제 보낸 청크 수");
        assertTrue(p.pendingChunks().isEmpty());
    }

    /**
     * 바닐라 PlayerChunkSender 처럼 배치는 한 틱 안에서 Start → 청크 → Finished 로 끝난다. 바닐라 클라이언트(ChunkBatchSizeCalculator)는
     * Start~Finished 시간을 배치 크기로 나눠 틱당 원하는 청크 수를 정하므로, Finished 가 다음 틱(본문 직렬화 왕복)으로 밀리면
     * 클라이언트가 속도를 0.15청크/틱까지 낮춘다(2026-11-11 진단: 시야 10 이 22초).
     */
    @Test
    void everyBatch_finishesInTheTickItStarted() throws Exception {
        world(true);   // 디스크·생성 직후처럼 본문 캐시가 없는 청크들
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        Player p = join(ch);
        List<int[]> batches = new ArrayList<>();
        int[] open = {0};
        boolean[] inBatch = {false};
        int total = (2 * VD + 1) * (2 * VD + 1);
        int sent = 0;
        for (int i = 0; i < 400 && sent < total; i++) {
            tick();
            drainTrackingOpen(ch, batches, open, inBatch);
            // tick() 이 돌아온 뒤 Start 만 나가 있고 Finished 가 없으면 배치가 다음 틱으로 넘어간 것
            assertFalse(inBatch[0], "틱 " + i + ": 배치가 같은 틱에 닫히지 않았다");
            sent = batches.stream().mapToInt(x -> x[1]).sum();
            Thread.sleep(5);
        }
        assertEquals(total, sent, "시야 안 청크가 모두 와야 한다");
        for (int[] x : batches) assertEquals(x[1], x[0], "배치마다 Finished 수 == 실제 보낸 청크 수");
        assertTrue(p.pendingChunks().isEmpty());
    }

    private static void drainTrackingOpen(EmbeddedChannel ch, List<int[]> batches, int[] open, boolean[] inBatch) {
        ch.runPendingTasks();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf b = (ByteBuf) o;
            int id = Types.readVarInt(b);
            if (id == CHUNK_BATCH_START) { open[0] = 0; inBatch[0] = true; }
            else if (id == LEVEL_CHUNK) open[0]++;
            else if (id == CHUNK_BATCH_FINISHED) { batches.add(new int[]{open[0], Types.readVarInt(b)}); inBatch[0] = false; }
            b.release();
        }
    }

    /** 매 틱 블록이 바뀌는 청크(레드스톤)는 본문이 캐시에 못 들어가도 결국 보내야 한다 — 그 자리에서 직렬화한다. */
    @Test
    void chunkChangedEveryTick_isStillSent() throws Exception {
        world(true);
        for (int x = -VD; x <= VD; x++)
            for (int z = -VD; z <= VD; z++) w.awaitChunk(new ChunkPos(x, z));
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        Player p = join(ch);
        List<int[]> batches = new ArrayList<>();
        int[] open = {0};
        int total = (2 * VD + 1) * (2 * VD + 1);
        int sent = 0;
        for (int i = 0; i < 400 && sent < total; i++) {
            sched.region(rp).execute(() -> w.loadedChunk(0, 0).markDirty()).get();   // 직렬화가 끝날 때마다 이미 낡았다
            tick();
            Thread.sleep(5);
            drain(ch, batches, open);
            sent = batches.stream().mapToInt(b -> b[1]).sum();
        }
        assertEquals(total, sent, "매 틱 바뀌는 청크도 와야 한다");
        assertFalse(p.pendingChunks().contains(new ChunkPos(0, 0)));
    }

    /** 시야 안 청크가 모두 매 틱 바뀌어도(레드스톤 장치가 많은 곳) region 스레드 바로 직렬화는 한 틱에 상한까지만 — 그래도 모두 온다. */
    @Test
    void everyChunkChangingEachTick_syncSerializationIsCappedPerTick() throws Exception {
        world(true);
        for (int x = -VD; x <= VD; x++)
            for (int z = -VD; z <= VD; z++) w.awaitChunk(new ChunkPos(x, z));
        Thread[] regionThread = new Thread[1];
        sched.region(rp).execute(() -> regionThread[0] = Thread.currentThread()).get();
        java.util.concurrent.atomic.AtomicInteger onRegion = new java.util.concurrent.atomic.AtomicInteger();
        w.serializeFaultHook = () -> { if (Thread.currentThread() == regionThread[0]) onRegion.incrementAndGet(); };
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        join(ch);
        List<int[]> batches = new ArrayList<>();
        int[] open = {0};
        int total = (2 * VD + 1) * (2 * VD + 1), sent = 0, maxSyncInTick = 0;
        for (int i = 0; i < 200 && sent < total; i++) {
            sched.region(rp).execute(() -> {
                for (int x = -VD; x <= VD; x++) for (int z = -VD; z <= VD; z++) w.loadedChunk(x, z).markDirty();
            }).get();
            int before = onRegion.get();
            tick();
            maxSyncInTick = Math.max(maxSyncInTick, onRegion.get() - before);
            Thread.sleep(5);
            drain(ch, batches, open);
            sent = batches.stream().mapToInt(b -> b[1]).sum();
        }
        assertEquals(total, sent, "매 틱 바뀌는 청크도 모두 와야 한다");
        assertTrue(maxSyncInTick <= Region.MAX_SYNC_BODIES_PER_TICK, "한 틱 바로 직렬화 " + maxSyncInTick);
    }

    @Test
    void numericSetting_keepsFixedCap() throws Exception {
        world(false);
        for (int x = -VD; x <= VD; x++)
            for (int z = -VD; z <= VD; z++) w.awaitChunk(new ChunkPos(x, z));
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        join(ch);
        tick();
        List<int[]> batches = new ArrayList<>();
        int[] open = {0};
        for (int i = 0; i < 100 && batches.isEmpty(); i++) {
            drain(ch, batches, open);
            if (batches.isEmpty()) Thread.sleep(10);
        }
        assertEquals(16, batches.get(0)[1], "숫자 설정은 예전처럼 그 수로 고정");
    }
}
