// T-071: PluginHost.handleFor 의 스레드 확장성을 잰다 — 옛 방식(ConcurrentHashMap.compute, bin 잠금)과 새 방식(get 빠른 길)을 같은 입력으로
// 번갈아 돌려 1/2/4 스레드 처리량(M/s)을 찍는다(:furnace-server:bench 작업에서만 돈다).
//   ./gradlew -PskipOracle=true :furnace-server:bench --tests '*HandleForBench*'
package org.domi.furnace.server.plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HandleForBench {

    @TempDir Path tmp;

    @Test
    void throughput() throws Exception {
        var sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            PluginHost host = core.plugins();
            World w = core.world();
            // 옛 구현 그대로(PluginHost 의 T-071 이전 handleFor)
            ConcurrentHashMap<String, WorldHandle> old = new ConcurrentHashMap<>();
            Supplier<Object> oldWay = () -> old.compute(w.dimension(), (k, o) -> o != null && o.handle() == w ? o : new WorldHandle(host, w));
            Supplier<Object> newWay = () -> host.handleFor(w);
            if (oldWay.get() == null || newWay.get() != host.handleFor(w)) throw new AssertionError("준비 실패");
            for (int th : new int[] {1, 2, 4}) {
                double bestOld = 0, bestNew = 0;
                for (int round = 0; round < 5; round++) {
                    bestOld = Math.max(bestOld, run(th, oldWay));
                    bestNew = Math.max(bestNew, run(th, newWay));
                }
                System.out.printf("handleFor %d threads: old(compute) %7.2f M/s   new(get) %7.2f M/s   x%.1f%n", th, bestOld, bestNew, bestNew / bestOld);
            }
        } finally {
            sched.close();
            TestServers.drainIoPools();
        }
    }

    private static double run(int threads, Supplier<Object> op) throws Exception {
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        try {
            int per = 3_000_000;
            CountDownLatch go = new CountDownLatch(1);
            List<Future<?>> fs = new ArrayList<>();
            for (int t = 0; t < threads; t++) fs.add(ex.submit(() -> {
                go.await();
                for (int i = 0; i < per; i++) if (op.get() == null) throw new AssertionError();   // 공유 sink 쓰기는 그 자체가 경합이라 쓰지 않는다
                return null;
            }));
            long t0 = System.nanoTime();
            go.countDown();
            for (var f : fs) f.get();
            return (double) threads * per / ((System.nanoTime() - t0) / 1e9) / 1e6;
        } finally {
            ex.shutdown();
        }
    }
}
