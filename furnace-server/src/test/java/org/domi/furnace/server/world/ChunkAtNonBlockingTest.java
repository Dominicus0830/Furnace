// region 스레드의 chunkAt 운영 경로가 미로드 청크를 동기 생성하지 않는지(R2, 규칙 6) 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ChunkAtNonBlockingTest {

    private record Env(FurnaceSchedulerImpl sched, World w, java.util.concurrent.ExecutorService io) {
        void close() { io.shutdown(); sched.close(); }
    }

    private static Env env() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, io);
        return new Env(sched, w, io);
    }

    @Test
    void regionThreadOperationalPathsDoNotGenerateSynchronously() throws Exception {
        Env e = env();
        try {
            RegionPos rp = new RegionPos(0, 0);
            ChunkPos cp = new ChunkPos(2, 2);
            Object[] got = new Object[6];
            e.sched().region(rp).execute(() -> {
                Region r = e.w().regionAt(rp);
                got[1] = r.getBlock(33, -61, 33);               // 미로드 → 공기(로드 요청도 없음)
                got[2] = r.chestAt(33, -60, 33);                // 미로드 → null
                r.setChestItem(33, -60, 33, 0, org.domi.furnace.server.entity.ItemStack.EMPTY);   // 미로드 → 무시
                r.markChestChanged(33, -60, 33);
                got[3] = r.breakBlock(null, 33, -61, 33);       // 미로드 → false
                got[5] = e.w().loadingChunk(Regionizer.chunkKey(cp.x(), cp.z()));   // 운영 조회는 로드를 걸지 않는다
                got[0] = r.chunkAt(cp);                         // 미로드 → null(비동기 로드만 건다)
                got[4] = e.w().loadedChunk(cp.x(), cp.z());     // 같은 호출 안에서는 아직 없다
            }).get(5, TimeUnit.SECONDS);
            assertNull(got[0]);
            assertEquals(ChunkSection.AIR, got[1]);
            assertNull(got[2]);
            assertEquals(false, got[3]);
            assertNull(got[4], "region 스레드에서 동기 생성이 일어나면 안 된다");
            assertNull(got[5]);
            assertEquals(0, e.w().blockingLoads(), "운영 경로가 동기 생성 경로를 탔다");

            // 비동기 로드가 끝나면 같은 조회가 실제 블록을 본다(슈퍼플랫 -61 은 풀 블록)
            ChunkColumn col = e.w().awaitChunk(cp);
            assertNotNull(col);
            int[] block = new int[1];
            e.sched().region(rp).execute(() -> block[0] = e.w().regionAt(rp).getBlock(33, -61, 33)).get(5, TimeUnit.SECONDS);
            assertEquals(col.getBlock(1, -61, 1), block[0]);
            assertNotEquals(ChunkSection.AIR, block[0]);
            assertEquals(0, e.w().blockingLoads());
        } finally {
            e.close();
        }
    }

    @Test
    void externalThreadStillWaitsAndRegionThreadMayNot() throws Exception {
        Env e = env();
        try {
            ChunkPos cp = new ChunkPos(0, 0);
            ChunkColumn col = e.w().awaitChunk(cp);   // 외부(시험) 스레드는 기다려서 받는다
            assertNotNull(col);
            assertSame(col, e.w().awaitChunk(cp));
            ChunkPos far = new ChunkPos(200, 200);
            e.w().awaitChunk(far);   // 먼 청크를 다른 region 소유로 만든다
            RegionPos rp = new RegionPos(0, 0);
            Throwable[] err = new Throwable[2];
            assertNotSame(e.w().regionizer().ownerOf(0, 0), e.w().regionizer().ownerOf(200, 200));
            e.sched().region(rp).execute(() -> {
                try { e.w().awaitChunk(cp); } catch (Throwable t) { err[0] = t; }                     // 자기 청크
                try { e.w().awaitChunk(far); } catch (Throwable t) { err[1] = t; }   // 다른 region 소유 청크
            }).get(5, TimeUnit.SECONDS);
            assertInstanceOf(IllegalStateException.class, err[0]);
            assertInstanceOf(IllegalStateException.class, err[1], "다른 region 청크라도 region 스레드에서는 기다리면 안 된다");
            assertEquals(0, e.w().blockingLoads());
        } finally {
            e.close();
        }
    }
}
