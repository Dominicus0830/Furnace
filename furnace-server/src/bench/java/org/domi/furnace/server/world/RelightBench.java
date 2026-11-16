// T-197 빛 다시 계산 벤치(설계 6장 M1·M2). ./gradlew -PskipOracle=true :furnace-server:bench --tests '*RelightBench*'
// 실제 지형(시드 20261115) 34×34 청크를 한 region 에 올리고 1·4·8·16·32 변 정사각형을 relight — 끝까지 벽시계, 일꾼 계산 ms/컬럼(둘레 포함),
// region 몫(타일 준비 ms, 청크 설치 ms — 설치는 층 끼우기·원천·저장 표시·패킷 만들기·보내기), 따로 잰 전 섹션 빛 패킷 만들기 ms/청크.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.server.config.LightSettings;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.protocol.play.LightUpdate;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RelightBench {

    private static final int C0 = 16;   // relight 정사각형의 아래 모서리(청크) — 구역 0(shift 6 = 64청크) 안

    @AfterEach
    void restore() { LightSettings.install(LightSettings.DEFAULT); }

    @Test
    @org.junit.jupiter.api.Timeout(value = 30, unit = TimeUnit.MINUTES)
    void relightBench() throws Exception {
        LightSettings.install(new LightSettings(false, 1));
        RegistrySet regs = RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt");
        ItemContext.install(new TagRegistries(regs));
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        ExecutorService io = Executors.newSingleThreadExecutor(), gen = Executors.newFixedThreadPool(2);
        try {
            World w = new World(NoiseWorldSource.overworld(20261115L, reg, regs), new ChunkWorkerPool(2), sched, 2, 6, 1, 2, 2,
                    reg.directBits(), 7, io, gen);
            w.regionizer().want(C0, C0, null);
            long t0 = System.nanoTime();
            at(w, r -> {
                for (int x = C0 - 1; x <= C0 + 32; x++) for (int z = C0 - 1; z <= C0 + 32; z++) r.chunkAtBlocking(new ChunkPos(x, z));
                r.level().drainLight();
            });
            System.out.printf("T197 BENCH 올리기 34×34 %.1f s, 계산 일꾼 %d%n", (System.nanoTime() - t0) / 1e9, Relight.poolThreads());
            for (int round = 0; round < 3; round++) {
                for (int side : new int[] {1, 4, 8, 16, 32}) {
                    long[] keys = new long[side * side];
                    int i = 0;
                    for (int x = 0; x < side; x++) for (int z = 0; z < side; z++) keys[i++] = Regionizer.chunkKey(C0 + x, C0 + z);
                    long[] before = w.relight().stats();
                    CompletableFuture<Integer> done = new CompletableFuture<>();
                    long s0 = System.nanoTime();
                    w.relight().relight(keys, null, done::complete);
                    assertEquals(keys.length, done.get(5, TimeUnit.MINUTES));
                    long wall = System.nanoTime() - s0;
                    long[] after = w.relight().stats();
                    long prep = after[0] - before[0], comp = after[1] - before[1], inst = after[2] - before[2];
                    long cols = after[3] - before[3], installs = after[4] - before[4], tiles = after[5] - before[5];
                    System.out.printf("T197 BENCH round %d side %2d (%4d청크): 벽시계 %7.1f ms | 일꾼 %6.2f ms/컬럼(컬럼 %d, %.2f배) | "
                            + "준비 %.3f ms/타일(%d) | 설치 %.3f ms/청크%n",
                            round, side, keys.length, wall / 1e6, comp / 1e6 / Math.max(1, cols), cols, cols / (double) keys.length,
                            prep / 1e6 / Math.max(1, tiles), tiles, inst / 1e6 / Math.max(1, installs));
                }
                // 전 섹션 빛 패킷 만들기만(설치 안에 들어 있는 몫)
                double[] pkt = new double[1];
                Object[] sink = new Object[1];
                at(w, r -> {
                    RegionLevel lv = r.level();
                    int lightBase = (lv.minBuildHeight() >> 4) - 1 - org.domi.furnace.server.light.ChunkLight.MIN_SECTION;
                    int count = ((lv.maxBuildHeight() - lv.minBuildHeight()) >> 4) + 2;
                    long all = (1L << count) - 1;
                    long p0 = System.nanoTime();
                    for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
                        sink[0] = LightUpdate.of(C0 + x, C0 + z, r.ownedChunk(C0 + x, C0 + z).light(), all, all, lightBase, count);
                    }
                    pkt[0] = (System.nanoTime() - p0) / 1e6 / 1024;
                });
                double[] src = new double[1];
                at(w, r -> {
                    long p0 = System.nanoTime();
                    for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) r.level().lightEngine().recomputeSources(C0 + x, C0 + z);
                    src[0] = (System.nanoTime() - p0) / 1e6 / 1024;
                });
                System.out.printf("T197 BENCH round %d 빛 패킷 만들기 %.3f ms/청크, 하늘빛 원천 다시 세기 %.3f ms/청크%n", round, pkt[0], src[0]);
            }
        } finally {
            io.shutdown();
            gen.shutdown();
            sched.close();
        }
    }

    private static void at(World w, java.util.function.Consumer<Region> task) throws Exception {
        var f = new CompletableFuture<Void>();
        w.runAtChunk(new ChunkPos(C0, C0), r -> {
            try {
                task.accept(r);
                f.complete(null);
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        f.get(20, TimeUnit.MINUTES);
    }
}
