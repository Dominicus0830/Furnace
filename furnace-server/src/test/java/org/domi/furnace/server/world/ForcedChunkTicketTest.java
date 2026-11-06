// /forceload 로 붙잡은 청크는 구독자가 없어도 내려가지 않는 티켓으로 취급되고, 놓으면 사라진다. 붙잡자마자 올라온다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForcedChunkTicketTest {

    @TempDir Path tmp;

    @Test
    void forcedChunkIsTicketedAndLoaded() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            World world = TestServers.core(tmp, sched).world();
            long key = Regionizer.chunkKey(2, 3);
            assertFalse(world.ticketed(key));
            assertTrue(world.setChunkForced(2, 3, true));
            assertFalse(world.setChunkForced(2, 3, true), "이미 붙잡았다");
            assertTrue(world.ticketed(key));
            for (int i = 0; i < 200 && world.loadedChunk(2, 3) == null; i++) Thread.sleep(50);
            assertNotNull(world.loadedChunk(2, 3), "붙잡으면 바로 올라온다");
            assertTrue(world.setChunkForced(2, 3, false));
            assertFalse(world.ticketed(key));
            assertFalse(world.setChunkForced(2, 3, false));
        } finally {
            sched.close();
        }
    }
}
