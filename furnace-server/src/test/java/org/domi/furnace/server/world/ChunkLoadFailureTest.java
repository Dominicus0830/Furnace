// 청크 로드가 한 번 실패해도 loading에 영구 잔류하지 않고 다음 요청에서 재시도되는지, region이 비어 reap 가능한지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.domi.furnace.server.world.gen.WorldSource;
import org.junit.jupiter.api.Test;

class ChunkLoadFailureTest {

    @Test
    void failedLoad_isNotCached_andRetries() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        SuperflatWorldSource good = new SuperflatWorldSource(reg, 0);
        AtomicInteger calls = new AtomicInteger();
        WorldSource flaky = (x, z) -> {
            if (calls.getAndIncrement() == 0) throw new IllegalStateException("손상된 청크");
            return good.generate(x, z);
        };
        var io = Executors.newSingleThreadExecutor();
        World w = new World(flaky, new ChunkWorkerPool(1), sched, 1, 16, 2, 2, reg.directBits(), 7, io);
        RegionPos rp = new RegionPos(0, 0);
        ChunkPos cp = new ChunkPos(0, 0);
        try {
            CompletableFuture<CompletableFuture<ChunkColumn>> first = new CompletableFuture<>();
            sched.region(rp).execute(() -> first.complete(w.regionAt(rp).loadChunk(cp))).get();
            assertThrows(ExecutionException.class, () -> first.get().get());

            boolean[] empty = new boolean[1];
            sched.region(rp).execute(() -> empty[0] = w.regionAt(rp).isIdleEmpty()).get();
            assertTrue(empty[0], "실패한 로드가 loading에 남아 region이 reap되지 않는다");

            CompletableFuture<CompletableFuture<ChunkColumn>> second = new CompletableFuture<>();
            sched.region(rp).execute(() -> second.complete(w.regionAt(rp).loadChunk(cp))).get();
            assertNotNull(second.get().get(), "재시도가 성공해야 한다");
        } finally {
            io.shutdown();
            sched.close();
        }
    }
}
