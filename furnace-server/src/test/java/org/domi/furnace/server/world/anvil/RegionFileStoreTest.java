// 여러 청크(다른 .mca 매핑)를 store로 read/write, 닫기 검증
package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionFileStoreTest {

    @Test
    void writeRead_acrossRegionFiles(@TempDir Path dir) throws Exception {
        RegionFileStore store = new RegionFileStore(dir);
        store.writeChunk(0, 0, "a".getBytes());
        store.writeChunk(40, 0, "b".getBytes());   // 다른 .mca(r.1.0)
        assertArrayEquals("a".getBytes(), store.readChunk(0, 0));
        assertArrayEquals("b".getBytes(), store.readChunk(40, 0));
        assertNull(store.readChunk(5, 5));
        store.closeAll();
    }

    @Test
    void lru_closesOldestHandles_andReopensOnDemand(@TempDir Path dir) throws Exception {
        RegionFileStore store = new RegionFileStore(dir);
        int files = RegionFileStore.MAX_OPEN + 20;
        for (int i = 0; i < files; i++) store.writeChunk(i * 32, 0, ("c" + i).getBytes());
        assertEquals(RegionFileStore.MAX_OPEN, store.openCount());   // 바닐라처럼 256개 넘게 열어 두지 않는다
        for (int i = 0; i < files; i++) assertArrayEquals(("c" + i).getBytes(), store.readChunk(i * 32, 0));
        assertEquals(RegionFileStore.MAX_OPEN, store.openCount());
        store.closeAll();
    }

    @Test
    void lru_concurrentAccessSurvivesEviction(@TempDir Path dir) throws Exception {
        RegionFileStore store = new RegionFileStore(dir);
        int files = RegionFileStore.MAX_OPEN * 2;
        for (int i = 0; i < files; i++) store.writeChunk(i * 32, 0, ("c" + i).getBytes());
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            java.util.List<java.util.concurrent.Future<?>> fs = new java.util.ArrayList<>();
            for (int t = 0; t < 8; t++) {
                int seed = t;
                fs.add(pool.submit(() -> {
                    java.util.Random r = new java.util.Random(seed);
                    for (int n = 0; n < 2000; n++) {
                        int i = r.nextInt(files);
                        assertArrayEquals(("c" + i).getBytes(), store.readChunk(i * 32, 0));
                    }
                    return null;
                }));
            }
            for (var f : fs) f.get();
        } finally {
            pool.shutdownNow();
        }
        store.closeAll();
    }

    /**
     * 경합 회귀: A 가 파일 K 에 쓰는 중(잠금 보유) LRU 가 K 를 내보내고, C 가 같은 K 를 다시 열어 쓴다.
     * 두 핸들이 동시에 열리면 C 의 섹터를 A 가 낡은 섹터 표로 덮어써 C 의 청크가 사라지거나 섞인다.
     */
    @Test
    void lru_evictionDuringWrite_doesNotOpenSecondHandle(@TempDir Path dir) throws Exception {
        RegionFileStore store = new RegionFileStore(dir);
        java.util.concurrent.CountDownLatch inWrite = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        Thread owner = Thread.currentThread();
        java.util.concurrent.atomic.AtomicReference<Thread> aThread = new java.util.concurrent.atomic.AtomicReference<>();
        store.beforeOpHook = () -> {
            if (Thread.currentThread() == aThread.get() && inWrite.getCount() > 0) {
                inWrite.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        };
        byte[] a = new byte[3000];
        java.util.Arrays.fill(a, (byte) 'a');
        byte[] c = new byte[3000];
        java.util.Arrays.fill(c, (byte) 'c');
        // 파일 K = r.0.0 을 미리 만들어 둔다(청크 2 개)
        store.writeChunk(5, 5, "seed".getBytes());
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var fa = pool.submit(() -> { aThread.set(Thread.currentThread()); store.writeChunk(0, 0, a); return null; });
            assertTrue(inWrite.await(5, java.util.concurrent.TimeUnit.SECONDS));
            // K 를 LRU 맨 뒤로 밀어 내보낸다(내보내는 쪽은 별도 스레드 — A 가 잠금을 쥐어 닫기가 막힐 수 있다)
            var evictor = pool.submit(() -> {
                for (int i = 1; i <= RegionFileStore.MAX_OPEN; i++) store.writeChunk(i * 32, 0, "x".getBytes());
                return null;
            });
            Thread.sleep(300);
            Thread cThread = new Thread(() -> {
                try { store.writeChunk(1, 0, c); } catch (Exception e) { throw new RuntimeException(e); }
            });
            cThread.start();
            Thread.sleep(300);           // 수정 전 코드라면 C 는 이 사이 두 번째 핸들로 이미 썼다
            release.countDown();
            fa.get(5, java.util.concurrent.TimeUnit.SECONDS);
            evictor.get(10, java.util.concurrent.TimeUnit.SECONDS);
            cThread.join(5000);
            assertFalse(cThread.isAlive());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertSame(owner, Thread.currentThread());
        store.closeAll();
        RegionFileStore fresh = new RegionFileStore(dir);
        assertArrayEquals(a, fresh.readChunk(0, 0));
        assertArrayEquals(c, fresh.readChunk(1, 0));
        assertArrayEquals("seed".getBytes(), fresh.readChunk(5, 5));
        fresh.closeAll();
    }
}
