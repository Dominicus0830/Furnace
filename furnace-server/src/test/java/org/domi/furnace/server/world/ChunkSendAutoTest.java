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
        tick();
        List<int[]> batches = new ArrayList<>();
        int[] open = {0};
        for (int i = 0; i < 100 && batches.isEmpty(); i++) {
            drain(ch, batches, open);
            if (batches.isEmpty()) Thread.sleep(10);
        }
        assertFalse(batches.isEmpty(), "첫 틱에 배치가 나가야 한다");
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
