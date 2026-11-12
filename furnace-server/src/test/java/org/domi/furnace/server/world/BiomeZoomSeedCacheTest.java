// T-010(O00-01) 회귀: 바이옴 확대 지터 시드(BiomeManager.obfuscateSeed = SHA-256)를 월드가 시드를 정할 때 한 번만 계산해도 값이 그대로인가.
// 기대값은 파이썬 hashlib.sha256(struct.pack('<q', seed))[:8] 을 little-endian long 으로 읽은 독립 계산이다(바닐라 Guava hashLong(seed).asLong() 과 같은 정의).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.concurrent.Executors;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.protocol.play.LoginPlay;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class BiomeZoomSeedCacheTest {

    private static final long[][] PYTHON = {
            {0L, 8794265229978523055L},
            {1L, -6467378160175308932L},
            {-1L, 6759447113877070610L},
            {12345L, 293737985876514017L},
            {-4172144997902289642L, 2159143436479834350L},
            {Long.MAX_VALUE, 7179146226492139882L},
            {Long.MIN_VALUE, 6374347445474471398L},
            {987654321012345L, 897728280244972572L},
    };

    /** 다른 코드 경로로 쓴 참조 구현(바이트 시프트). */
    private static long reference(long seed) throws Exception {
        byte[] in = new byte[8];
        for (int i = 0; i < 8; i++) in[i] = (byte) (seed >>> (8 * i));
        byte[] h = MessageDigest.getInstance("SHA-256").digest(in);
        long r = 0;
        for (int i = 7; i >= 0; i--) r = (r << 8) | (h[i] & 0xFFL);
        return r;
    }

    @Test
    void obfuscateSeedMatchesIndependentValues() {
        for (long[] c : PYTHON) {
            assertEquals(c[1], BiomeZoom.obfuscateSeed(c[0]), "seed " + c[0]);
            assertEquals(c[1], LoginPlay.obfuscateSeed(c[0]), "LoginPlay seed " + c[0]);
        }
    }

    @Test
    void obfuscateSeedMatchesReferenceOverManySeeds() throws Exception {
        java.util.Random r = new java.util.Random(20261112L);
        for (int i = 0; i < 20000; i++) {
            long s = i < 10000 ? i - 5000 : r.nextLong();
            assertEquals(reference(s), BiomeZoom.obfuscateSeed(s), "seed " + s);
        }
    }

    @Test
    void worldCachesZoomSeedAtSetterAndFollowsReseed() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, sched.regionThreadCount(),
                16, 2, 2, reg.directBits(), 7, Executors.newSingleThreadExecutor());
        assertFalse(w.hasSeed());
        assertEquals(0L, w.seed());
        for (long[] c : PYTHON) {
            w.seed(c[0]);
            assertTrue(w.hasSeed());
            assertEquals(c[0], w.seed());
            assertEquals(c[1], w.zoomSeed());
            assertEquals(c[1], w.zoomSeed(), "두 번째 읽기도 같은 값");
        }
    }
}
