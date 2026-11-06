// /locate poi: 내릴 때 넣은 POI 저장(savePoiAsync)이 IO 레인에 걸려 있어도, 디스크 읽기는 그 뒤에 돌아 내용을 본다.
package org.domi.furnace.server.world.poi;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PoiLocatorDiskTest {
    @TempDir Path tmp;

    /** 손으로 돌리는 IO 풀. */
    static final class ManualPool extends AbstractExecutorService {
        private final ArrayDeque<Runnable> q = new ArrayDeque<>();
        @Override public synchronized void execute(Runnable r) { q.add(r); }
        int runAll() {
            int n = 0;
            while (true) {
                Runnable r;
                synchronized (this) { r = q.poll(); }
                if (r == null) return n;
                r.run();
                n++;
            }
        }
        @Override public void shutdown() { }
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long t, TimeUnit u) { return true; }
    }

    @Test
    void pendingSaveIsSeen() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var anvil = new AnvilWorldSource(tmp, new RegionFileStore(tmp), new AnvilChunkCodec(reg, 0), new SuperflatWorldSource(reg, 0));
        anvil.poiStore(new RegionFileStore(tmp, "poi"));
        ManualPool io = new ManualPool();
        World w = new World(anvil, new ChunkWorkerPool(1), new FurnaceSchedulerImpl(50_000_000L, 1), 1, 16, 2, 2, reg.directBits(), 7, io);

        int minSection = w.dimensionType().minY() >> 4;
        PoiChunk c = new PoiChunk(minSection, w.dimensionType().height() >> 4);
        c.getOrCreate(4).add(new Pos(3, 70, 5), PoiTypes.HOME);
        w.savePoiAsync(0, 0, c.save(AnvilChunkCodec.DATA_VERSION));   // 아직 안 썼다

        var f = PoiLocator.findClosest(w, t -> t == PoiTypes.HOME, new Pos(0, 64, 0), 256, Runnable::run, () -> false);
        assertFalse(f.isDone());
        while (io.runAll() > 0) { }
        var e = f.join();
        assertNotNull(e);
        assertEquals(new Pos(3, 70, 5), e.pos());
        assertSame(PoiTypes.HOME, e.type());
    }

    @Test
    void cancelled() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ManualPool io = new ManualPool();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), new FurnaceSchedulerImpl(50_000_000L, 1), 1, 16, 2, 2, reg.directBits(), 7, io);
        var f = PoiLocator.findClosest(w, t -> true, new Pos(0, 64, 0), 256, Runnable::run, () -> true);
        while (io.runAll() > 0) { }
        assertThrows(java.util.concurrent.CompletionException.class, f::join);
    }
}
