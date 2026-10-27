// 플레이어 N명이 같은 시야 청크를 동시에 요청할 때 본문 직렬화·공유 압축 프레임의 중복을 잰다(기본 꺼짐: -Dfurnace.bench=true).
// C-2 측정용: docs/feature-parity/perf-c2-2026-10-27.md
package org.domi.furnace.server.world;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.CompressionEncoder;
import org.domi.furnace.server.net.io.SharedChunkFrames;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class MultiPlayerChunkSendBench {

    static volatile long sink;

    @Test
    void playersSameView() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        int reps = Integer.getInteger("furnace.bench.reps", 5);
        int vd = Integer.getInteger("furnace.bench.vd", 8);
        for (int rep = 0; rep < reps; rep++) {
            for (int n : new int[] {1, 2, 4, 8}) {
                run(n, vd, rep, false);
                run(n, vd, rep, true);
            }
        }
    }

    /** staggered=false: N명이 같은 청크를 동시에 요청(같이 접속·같이 이동). true: 한 명씩 차례로(앞 사람 완료 뒤). */
    private void run(int players, int vd, int rep, boolean staggered) throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ExecutorService io = Executors.newSingleThreadExecutor(), gen = Executors.newSingleThreadExecutor();
        int cores = Runtime.getRuntime().availableProcessors();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(cores), sched, cores, 4, 1, vd, vd,
                reg.directBits(), 7, io, gen);
        w.configurePersistence(Integer.MAX_VALUE, Integer.MAX_VALUE);
        List<ChunkPos> view = new ArrayList<>();
        // 반복마다 다른 좌표(이전 반복의 로드·캐시를 피한다)
        int ox = rep * 1000 + players * 100 + (staggered ? 50 : 0);
        for (int dx = -vd; dx <= vd; dx++) for (int dz = -vd; dz <= vd; dz++) view.add(new ChunkPos(ox + dx, dz));
        // 미리 로드(생성 시간은 빼고 직렬화·압축만 본다)
        List<CompletableFuture<?>> pre = new ArrayList<>();
        for (ChunkPos cp : view) pre.add(w.submitAtChunk(cp, r -> r.loadChunk(cp)).thenCompose(f -> f));
        CompletableFuture.allOf(pre.toArray(new CompletableFuture[0])).get(120, TimeUnit.SECONDS);

        EmbeddedChannel[] chs = new EmbeddedChannel[players];
        for (int i = 0; i < players; i++) {
            chs[i] = new EmbeddedChannel();
            chs[i].pipeline().addLast("compress", new CompressionEncoder(256));
        }
        int snap0 = w.bodySnapshots.get();
        long enc0 = SharedChunkFrames.ENCODES.get();
        IdentityHashMap<byte[], Boolean> distinct = new IdentityHashMap<>();
        long t0 = System.nanoTime();
        List<List<byte[]>> got = new ArrayList<>();
        if (staggered) {
            for (int p = 0; p < players; p++) got.add(request(w, view));
        } else {
            List<CompletableFuture<List<byte[]>>> fs = new ArrayList<>();
            for (int p = 0; p < players; p++) fs.add(CompletableFuture.supplyAsync(() -> {
                try { return request(w, view); } catch (Exception e) { throw new RuntimeException(e); }
            }));
            for (var f : fs) got.add(f.get(120, TimeUnit.SECONDS));
        }
        long tSer = System.nanoTime();
        // 채널별 송출(이벤트 루프 대신 여기서 순차) — 공유 프레임 적중 여부를 본다
        for (int p = 0; p < players; p++) {
            for (byte[] body : got.get(p)) {
                distinct.put(body, Boolean.TRUE);
                chs[p].writeOutbound(SharedChunkFrames.encode(chs[p], LevelChunkWithLight.ofShared(body)));
                ByteBuf f = chs[p].readOutbound();
                sink += f.readableBytes();
                f.release();
            }
        }
        long t1 = System.nanoTime();
        System.out.printf("MPBENCH rep=%d players=%d mode=%-9s chunks=%d | 스냅샷=%d 고유본문=%d 압축=%d (이상적 %d) | 직렬화 %.1fms 송출 %.1fms 합 %.1fms%n",
                rep, players, staggered ? "staggered" : "together", view.size(),
                w.bodySnapshots.get() - snap0, distinct.size(), SharedChunkFrames.ENCODES.get() - enc0, view.size(),
                (tSer - t0) / 1e6, (t1 - tSer) / 1e6, (t1 - t0) / 1e6);
        for (EmbeddedChannel c : chs) c.finishAndReleaseAll();
        sched.close();
        w.regionizer().close();
        io.shutdownNow();
        gen.shutdownNow();
    }

    private static List<byte[]> request(World w, List<ChunkPos> view) throws Exception {
        List<CompletableFuture<byte[]>> fs = new ArrayList<>(view.size());
        for (ChunkPos cp : view) fs.add(w.serializedChunkBody(cp));
        List<byte[]> out = new ArrayList<>(fs.size());
        for (var f : fs) out.add(f.get(120, TimeUnit.SECONDS));
        return out;
    }
}
