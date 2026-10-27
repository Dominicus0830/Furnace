// F-01: 잠금 밖 deflate 가 예전(잠금 안 deflate)과 같은 .mca 바이트를 만들고, 같은 파일 동시 기록에도 안전한지 대조.
package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionDeflateOutsideLockTest {

    @TempDir Path tmp;

    /** 압축이 잘 되는 NBT 비슷한 바이트(반복 + 잡음). */
    static byte[] payload(Random r, int len) {
        byte[] b = new byte[len];
        for (int i = 0; i < len; i++) b[i] = (byte) (r.nextInt(10) == 0 ? r.nextInt(256) : (i / 64) & 0x1F);
        return b;
    }

    /** 타임스탬프 섹터(4096..8191)는 기록 시각이라 비교에서 뺀다. */
    static byte[] withoutTimestamps(Path p) throws Exception {
        byte[] b = Files.readAllBytes(p);
        Arrays.fill(b, 4096, 8192, (byte) 0);
        return b;
    }

    @Test
    void sameBytesAsLegacyAndReadsBack() throws Exception {
        Random r = new Random(0xC101);
        Path a = tmp.resolve("new"), b = tmp.resolve("old");
        RegionFileStore store = new RegionFileStore(a);
        Files.createDirectories(b.resolve("region"));
        RegionFile legacy = new RegionFile(b.resolve("region").resolve("r.0.0.mca"));
        List<int[]> order = new ArrayList<>();
        byte[][] last = new byte[1024][];
        for (int n = 0; n < 1500; n++) {
            int cx = r.nextInt(32), cz = r.nextInt(32);
            // 크기 다양: in-place 재사용·append·외부(.mcc, 255섹터 초과) 경로를 모두 탄다
            int len = r.nextInt(50) == 0 ? 1_400_000 + r.nextInt(100_000) : 200 + r.nextInt(r.nextBoolean() ? 3000 : 60000);
            byte[] data = len > 1_000_000 || r.nextInt(60) == 0 ? randomBytes(r, len) : payload(r, len);
            store.writeChunk(cx, cz, data);
            synchronized (legacy) { legacy.writeChunk(cx, cz, data); }   // 예전: 잠금 안 deflate + 기록
            last[cx + cz * 32] = data;
            order.add(new int[]{cx, cz});
        }
        store.closeAll();
        legacy.close();
        assertArrayEquals(withoutTimestamps(b.resolve("region/r.0.0.mca")), withoutTimestamps(a.resolve("region/r.0.0.mca")));
        try (var s = Files.list(b.resolve("region"))) {
            for (Path p : s.toList()) assertArrayEquals(
                    p.getFileName().toString().endsWith(".mca") ? withoutTimestamps(p) : Files.readAllBytes(p),
                    p.getFileName().toString().endsWith(".mca") ? withoutTimestamps(a.resolve("region").resolve(p.getFileName()))
                            : Files.readAllBytes(a.resolve("region").resolve(p.getFileName())), p.toString());
        }
        RegionFileStore again = new RegionFileStore(a);
        for (int i = 0; i < 1024; i++) if (last[i] != null) assertArrayEquals(last[i], again.readChunk(i & 31, i >> 5));
        again.closeAll();
    }

    static byte[] randomBytes(Random r, int len) { byte[] b = new byte[len]; r.nextBytes(b); return b; }

    @Test
    void concurrentWritesSameFileReadBack() throws Exception {
        RegionFileStore store = new RegionFileStore(tmp);
        byte[][] expect = new byte[1024][];
        ExecutorService ex = Executors.newFixedThreadPool(8);
        List<Future<?>> fs = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            int th = t;
            fs.add(ex.submit(() -> {
                Random r = new Random(th);
                for (int round = 0; round < 3; round++)
                    for (int i = th; i < 1024; i += 8) {   // 스레드마다 다른 청크, 같은 파일
                        byte[] d = payload(r, 500 + r.nextInt(20000));
                        store.writeChunk(i & 31, i >> 5, d);
                        expect[i] = d;
                    }
                return null;
            }));
        }
        for (Future<?> f : fs) f.get();
        ex.shutdown();
        store.closeAll();
        RegionFileStore again = new RegionFileStore(tmp);
        for (int i = 0; i < 1024; i++) assertArrayEquals(expect[i], again.readChunk(i & 31, i >> 5), "청크 " + i);
        again.closeAll();
    }

    // -Dfurnace.bench=true 일 때만. 같은 region 파일에 T 스레드가 서로 다른 청크를 기록: 전(잠금 안 deflate) / 후(잠금 밖).
    // 교대 5라운드, 라운드마다 워밍업 1회 + 측정 1회(스레드당 청크 256개, 본문 ~40KB).
    @Test
    void bench() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        Random r = new Random(3);
        byte[][] bodies = new byte[64][];
        for (int i = 0; i < bodies.length; i++) bodies[i] = payload(r, 40_000);
        for (int threads : new int[]{1, 4, 8}) {
            for (int round = 0; round < 5; round++) {
                for (int g = 0; g < 2; g++) {
                    boolean old = g == 0;
                    double ms = 0;
                    for (int rep = 0; rep < 2; rep++) {   // rep 0 = 워밍업
                        Path dir = tmp.resolve("b" + threads + "_" + round + "_" + g + "_" + rep);
                        Files.createDirectories(dir.resolve("region"));
                        RegionFile rf = new RegionFile(dir.resolve("region/r.0.0.mca"));
                        Object lock = new Object();
                        ExecutorService ex = Executors.newFixedThreadPool(threads);
                        long t0 = System.nanoTime();
                        List<Future<?>> fs = new ArrayList<>();
                        for (int t = 0; t < threads; t++) {
                            int th = t;
                            fs.add(ex.submit(() -> {
                                for (int n = 0; n < 256; n++) {
                                    int i = (n * threads + th) & 1023;
                                    byte[] body = bodies[(n + th) & 63];
                                    if (old) {
                                        synchronized (lock) { rf.writeCompressed(i & 31, i >> 5, Compression.deflate(body)); }
                                    } else {
                                        byte[] c = Compression.deflate(body);
                                        synchronized (lock) { rf.writeCompressed(i & 31, i >> 5, c); }
                                    }
                                }
                                return null;
                            }));
                        }
                        for (Future<?> f : fs) f.get();
                        long t1 = System.nanoTime();
                        ex.shutdown();
                        rf.close();
                        if (rep == 1) ms = (t1 - t0) / 1e6;
                    }
                    System.out.printf("[BENCH] F-01 threads=%d round=%d %s %.1f ms (%d 청크)%n", threads, round, old ? "전" : "후", ms, threads * 256);
                }
            }
        }
    }
}
