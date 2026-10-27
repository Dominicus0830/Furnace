// 플레이어 N명이 걸어 다니며 청크를 로드할 때 region 스레드의 틱당 할당 바이트·CPU ns 를 잰다(기본 꺼짐: -Dfurnace.bench=true).
// 이동 입력은 ConnectionHandler 의 move_player_pos_rot 수락 경로와 같은 호출(publishPosition + pushMove)로 넣는다.
// region 스레드는 1개(모든 region 이 한 워커)라 그 스레드의 ThreadMXBean 차분 = 틱(인박스 포함) 비용.
// C-4 측정용: docs/feature-parity/perf-c4-2026-10-28.md
package org.domi.furnace.server.world;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class PlayerWalkTickBench {

    private static final double SPEED = 0.2806;   // 달리기 블록/틱

    @Test
    void playersWalking() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        int reps = Integer.getInteger("furnace.bench.reps", 3);
        int ticks = Integer.getInteger("furnace.bench.ticks", 200);
        int warm = Integer.getInteger("furnace.bench.warm", 40);
        int vd = Integer.getInteger("furnace.bench.vd", 8);
        String[] ns = System.getProperty("furnace.bench.players", "1,4,16,32").split(",");
        String jfr = System.getProperty("furnace.bench.jfr", "");
        jdk.jfr.Recording rec = null;
        if (!jfr.isBlank()) {
            rec = new jdk.jfr.Recording();
            rec.enable("jdk.ObjectAllocationSample").withStackTrace().with("throttle", "1000/s");
            rec.start();
        }
        for (int rep = 0; rep < reps; rep++) {
            for (String n : ns) run(Integer.parseInt(n.trim()), vd, ticks, warm, rep);
        }
        if (rec != null) {
            rec.stop();
            Path out = Path.of(jfr);
            rec.dump(out);
            rec.close();
            topStacks(out);
        }
    }

    private void run(int n, int vd, int ticks, int warm, int rep) throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ExecutorService io = Executors.newSingleThreadExecutor(), gen = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2), sched, 1, 4, 1, vd, 4,
                reg.directBits(), 7, io, gen);
        w.configurePersistence(Integer.MAX_VALUE, Integer.MAX_VALUE);
        w.setRegistries(reg, null);
        int ox = rep * 2000 + n * 40;   // 반복마다 새 좌표
        ChunkPos home = ChunkPos.ofBlock(ox, 0);
        Player[] ps = new Player[n];
        EmbeddedChannel[] chs = new EmbeddedChannel[n];
        double[][] pos = new double[n][2];
        double[][] dir = new double[n][2];
        for (int i = 0; i < n; i++) {
            chs[i] = new EmbeddedChannel();
            ps[i] = new Player(i + 1, UUID.randomUUID(), "P" + i, chs[i]);
            double a = 2 * Math.PI * i / n;
            dir[i][0] = Math.cos(a);
            dir[i][1] = Math.sin(a);
            pos[i][0] = ox + 0.5 + (i % 6);
            pos[i][1] = 0.5 + (i / 6);
            ps[i].publishPosition(pos[i][0], -60, pos[i][1], 0, 0, true);
            ps[i].drainPending();
            w.playerOnline(ps[i]);
        }
        long[] tid = new long[1];
        w.submitAtChunk(home, r -> {
            tid[0] = Thread.currentThread().threadId();
            for (Player p : ps) r.adopt(p);
            return null;
        }).get(30, TimeUnit.SECONDS);
        var mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long[] alloc = new long[ticks], cpu = new long[ticks];
        long sent = 0;
        long next = System.nanoTime();
        long a0 = 0, c0 = 0;
        for (int t = 0; t <= warm + ticks; t++) {
            for (int i = 0; i < n; i++) {
                pos[i][0] += dir[i][0] * SPEED;
                pos[i][1] += dir[i][1] * SPEED;
                float yaw = (float) ((t * 3 + i * 17) % 360);
                ps[i].publishPosition(pos[i][0], -60, pos[i][1], yaw, 0, true);
                ps[i].pushMove(pos[i][0], -60, pos[i][1], true);
            }
            // 틱 시작 사이 간격의 차분 = 그 틱 + 그 사이 인박스 작업(청크 로드·본문 홉) 전부
            long a1 = mx.getThreadAllocatedBytes(tid[0]), c1 = mx.getThreadCpuTime(tid[0]);
            if (t > warm) { alloc[t - warm - 1] = a1 - a0; cpu[t - warm - 1] = c1 - c0; }
            a0 = a1;
            c0 = c1;
            w.tickOnce().get(30, TimeUnit.SECONDS);
            for (EmbeddedChannel ch : chs) sent += drain(ch);
            next += 50_000_000L;
            long sleep = next - System.nanoTime();
            if (sleep > 0) Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
        }
        System.out.printf("WALKBENCH rep=%d players=%d ticks=%d | 틱당할당 평균=%.1fKB p50=%.1fKB p95=%.1fKB | 틱당CPU 평균=%.3fms p50=%.3fms p95=%.3fms | 패킷=%d 로드청크=%d%n",
                rep, n, ticks, mean(alloc) / 1024, pct(alloc, 50) / 1024, pct(alloc, 95) / 1024,
                mean(cpu) / 1e6, pct(cpu, 50) / 1e6, pct(cpu, 95) / 1e6, sent, w.loadedChunkCount());
        for (EmbeddedChannel c : chs) c.finishAndReleaseAll();
        sched.close();
        w.regionizer().close();
        io.shutdownNow();
        gen.shutdownNow();
    }

    private static long drain(EmbeddedChannel ch) {
        ch.runPendingTasks();
        long k = 0;
        Object o;
        while ((o = ch.readOutbound()) != null) { ((ByteBuf) o).release(); k++; }
        return k;
    }

    private static double mean(long[] a) { return Arrays.stream(a).average().orElse(0); }

    private static double pct(long[] a, int p) {
        long[] s = a.clone();
        Arrays.sort(s);
        return s[Math.min(s.length - 1, s.length * p / 100)];
    }

    /** region 스레드 할당 샘플의 무게(weight) 합 상위 10 스택(위 8 프레임). */
    private static void topStacks(Path file) throws Exception {
        Map<String, long[]> agg = new HashMap<>();
        long total = 0;
        try (var rf = new jdk.jfr.consumer.RecordingFile(file)) {
            while (rf.hasMoreEvents()) {
                var e = rf.readEvent();
                if (!e.getEventType().getName().equals("jdk.ObjectAllocationSample")) continue;
                var th = e.getThread("eventThread");
                if (th == null || th.getJavaName() == null || !th.getJavaName().startsWith("furnace-region")) continue;
                long wgt = e.getLong("weight");
                total += wgt;
                StringBuilder sb = new StringBuilder(e.getClass("objectClass").getName());
                var st = e.getStackTrace();
                if (st != null) {
                    List<jdk.jfr.consumer.RecordedFrame> fr = st.getFrames();
                    for (int i = 0; i < Math.min(8, fr.size()); i++) {
                        var m = fr.get(i).getMethod();
                        sb.append("\n      at ").append(m.getType().getName()).append('.').append(m.getName())
                                .append(':').append(fr.get(i).getLineNumber());
                    }
                }
                agg.computeIfAbsent(sb.toString(), k -> new long[1])[0] += wgt;
            }
        }
        List<Map.Entry<String, long[]>> l = new ArrayList<>(agg.entrySet());
        l.sort((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0]));
        System.out.printf("WALKJFR region 스레드 샘플 무게 합=%.1fMB%n", total / 1e6);
        for (int i = 0; i < Math.min(10, l.size()); i++) {
            System.out.printf("WALKJFR #%d %.1f%% %.1fMB %s%n", i + 1, 100.0 * l.get(i).getValue()[0] / total,
                    l.get(i).getValue()[0] / 1e6, l.get(i).getKey());
        }
        Files.deleteIfExists(file);
    }
}
