// T-011 측정: 이미 만든 노이즈 땅(디스크)에 N명이 시야 V 로 동시에 냉 접속할 때 청크 본문 직렬화 횟수·풀/region CPU·시야 다 받기 틱(:furnace-server:bench 작업에서만 돈다).
// O00-05 장면의 축소판: 디스크 청크는 빛을 다시 계산하고(precomputeLight) 올릴 때 이웃과 가장자리를 맞추므로(attachPrecomputed) 이웃이 늦게 올라오면
// 이미 올라온 청크의 본문 캐시가 비워진다. 빠른 클라이언트(배치 응답 없음 = auto 상한 64/틱)라 서버 쪽 비용이 그대로 보인다.
//   ./gradlew -PskipOracle=true :furnace-server:bench --tests '*ColdJoinReserializeBench*' -Dfurnace.bench.players=4 -Dfurnace.bench.vd=10
package org.domi.furnace.server.world;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;

class ColdJoinReserializeBench {

    private static final int LEVEL_CHUNK = 0x27;
    /** 사람 사이 거리(청크). 시야가 겹치지 않고 서로 다른 region 이 되게. */
    private static final int SPACING = 64;

    @Test
    void coldJoin() throws Exception {
        int players = Integer.getInteger("furnace.bench.players", 4);
        int vd = Integer.getInteger("furnace.bench.vd", 10);
        int reps = Integer.getInteger("furnace.bench.reps", 3);
        long seed = Long.getLong("furnace.bench.seed", 20261112L);
        int cores = Runtime.getRuntime().availableProcessors();
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet regs = RegistrySet.load("registries/1.21.1.txt");
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(regs));
        // 같은 땅을 여러 실행(전/후 커밋)이 쓰게 -Dfurnace.bench.dir 로 고정할 수 있다 — 없으면 만든다. 실행마다 그 사본에서 시작한다.
        String fixed = System.getProperty("furnace.bench.dir", "");
        Path dir = fixed.isBlank() ? Files.createTempDirectory("coldjoin") : Path.of(fixed);
        if (!Files.exists(dir.resolve("generated.ok"))) {
            Files.createDirectories(dir);
            long tg = System.nanoTime();
            generate(dir, reg, regs, seed, players, vd + 2, cores);
            Files.writeString(dir.resolve("generated.ok"), players + " " + vd + " " + seed);
            System.out.printf("COLDJOIN 생성 %d명×반경 %d: %.1f초%n", players, vd + 2, (System.nanoTime() - tg) / 1e9);
        }
        for (int rep = 0; rep < reps; rep++) {
            Path copy = Files.createTempDirectory("coldjoin-run");
            copyTree(dir, copy);
            run(copy, reg, regs, seed, players, vd, cores, rep);
        }
    }

    private static void copyTree(Path from, Path to) throws java.io.IOException {
        try (var st = Files.walk(from)) {
            for (Path p : (Iterable<Path>) st::iterator) {
                Path q = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(q);
                else Files.copy(p, q, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static ChunkPos center(int i) { return new ChunkPos(i * SPACING, 0); }

    /** 1단계: 노이즈 땅을 만들어 디스크에 저장한다(측정 밖). */
    private static void generate(Path dir, BlockRegistry reg, RegistrySet regs, long seed, int players, int radius, int cores) throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, cores);
        ExecutorService io = Executors.newFixedThreadPool(2), gen = Executors.newFixedThreadPool(cores);
        RegionFileStore store = new RegionFileStore(dir);
        var src = new AnvilWorldSource(dir, store, new AnvilChunkCodec(reg, 0), NoiseWorldSource.overworld(seed, reg, regs));
        World w = new World(src, new ChunkWorkerPool(cores), sched, cores, 4, 1, 10, 10, reg.directBits(), 7, io, gen);
        w.configurePersistence(Integer.MAX_VALUE, Integer.MAX_VALUE);
        List<CompletableFuture<?>> fs = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            ChunkPos c = center(i);
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos cp = new ChunkPos(c.x() + dx, c.z() + dz);
                fs.add(w.submitAtChunk(cp, r -> r.loadChunk(cp)).thenCompose(f -> f));
            }
        }
        CompletableFuture.allOf(fs.toArray(new CompletableFuture[0])).get(30, TimeUnit.MINUTES);
        int left = w.flushAllDrained(TimeUnit.MINUTES.toNanos(5));
        if (left != 0) throw new IllegalStateException("저장 못 한 청크 " + left);
        io.shutdown();
        io.awaitTermination(5, TimeUnit.MINUTES);
        sched.close();
        w.regionizer().close();
        gen.shutdownNow();
        store.closeAll();
    }

    /** 2단계: 새 World(냉 기동)에 N명이 같이 들어와 시야를 다 받을 때까지. */
    private static void run(Path dir, BlockRegistry reg, RegistrySet regs, long seed, int players, int vd, int cores, int rep) throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, cores);
        ExecutorService io = Executors.newFixedThreadPool(2), gen = Executors.newFixedThreadPool(cores);
        RegionFileStore store = new RegionFileStore(dir);
        var src = new AnvilWorldSource(dir, store, new AnvilChunkCodec(reg, 0), NoiseWorldSource.overworld(seed, reg, regs));
        // 직렬화 풀 스레드 수(기본 코어 수 = 서버와 같음). 실서버는 엔티티·몹·네트워크·JFR 가 CPU 를 나눠 직렬화가 4.7ms(이 벤치 0.6~1.1ms)라
        // 풀이 밀렸다(O00 hop.serpool 9.4초) — 1 로 줄이면 그 "풀이 로드 앞줄보다 느린" 상태를 흉내 낸다.
        int poolThreads = Integer.getInteger("furnace.bench.pool", cores);
        World w = new World(src, new ChunkWorkerPool(poolThreads), sched, cores, 4, 1, vd, vd, reg.directBits(), 7, io, gen);
        w.configurePersistence(Integer.MAX_VALUE, Integer.MAX_VALUE);
        // 직렬화 한 번에 일부러 더하는 지연(ms). 실서버 O00 장면의 ser 평균 4.7ms(이 벤치 0.6~1.1ms)를 흉내 내 풀이 밀린 상태를 만든다.
        int serDelayMs = Integer.getInteger("furnace.bench.serDelayMs", 0);
        if (serDelayMs > 0) w.serializeFaultHook = () -> {
            try { Thread.sleep(serDelayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        };
        w.configureStreaming(0, Config.CHUNKS_PER_TICK_AUTO);
        w.setRegistries(reg, null);
        int total = ChunkTrackingViewTest.vanillaCount(vd);
        Player[] ps = new Player[players];
        EmbeddedChannel[] chs = new EmbeddedChannel[players];
        for (int i = 0; i < players; i++) {
            chs[i] = new ConcurrentEmbeddedChannel();
            ps[i] = new Player(i + 1, UUID.randomUUID(), "P" + rep + "_" + i, chs[i]);
            ps[i].viewDistance(vd);
            ChunkPos c = center(i);
            ps[i].publishPosition(c.x() * 16 + 8.5, 200, c.z() * 16 + 8.5, 0, 0, true);
            ps[i].drainPending();
            w.playerOnline(ps[i]);
        }
        if (ChunkTrace.ON) ChunkTrace.dump();   // 앞 단계 값을 비운다
        System.out.println("COLDJOIN-START rep=" + rep);
        long[] w0 = threadStats("chunk-worker"), r0 = threadStats("furnace-region");
        int snap0 = w.bodySnapshots.get();
        for (int i = 0; i < players; i++) {
            Player p = ps[i];
            w.submitAtChunk(center(i), r -> { r.adopt(p); return null; }).get(30, TimeUnit.SECONDS);
        }
        int[] got = new int[players];
        // 가장 가까운 청크가 언제 오나(이웃을 기다리면 늦어지는 몫, PR #301 검토 R4): 가운데 청크, 3×3 모두
        int[] centerAt = new int[players], near3At = new int[players];
        Arrays.fill(centerAt, -1);
        Arrays.fill(near3At, -1);
        List<java.util.Set<Long>> seen = new ArrayList<>();
        for (int i = 0; i < players; i++) seen.add(new java.util.HashSet<>());
        int[] doneAt = new int[players];
        Arrays.fill(doneAt, -1);
        long[] tickNs = new long[4000];
        int t = 0;
        long next = System.nanoTime();
        for (; t < tickNs.length; t++) {
            long ts = System.nanoTime();
            w.tickOnce().get(60, TimeUnit.SECONDS);
            tickNs[t] = System.nanoTime() - ts;
            boolean all = true;
            for (int i = 0; i < players; i++) {
                got[i] += drainChunks(chs[i], seen.get(i));
                ChunkPos cc = center(i);
                if (centerAt[i] < 0 && seen.get(i).contains(key(cc.x(), cc.z()))) centerAt[i] = t;
                if (near3At[i] < 0) {
                    boolean ok = true;
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) ok &= seen.get(i).contains(key(cc.x() + dx, cc.z() + dz));
                    if (ok) near3At[i] = t;
                }
                if (got[i] >= total && doneAt[i] < 0) doneAt[i] = t;
                all &= doneAt[i] >= 0;
            }
            if (all) break;
            next += 50_000_000L;
            long sleep = next - System.nanoTime();
            if (sleep > 0) Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
        }
        int ticks = t + 1;
        long[] w1 = threadStats("chunk-worker"), r1 = threadStats("furnace-region");
        long sent = Arrays.stream(got).sum();
        int snaps = w.bodySnapshots.get() - snap0;
        long[] used = Arrays.copyOf(tickNs, ticks);
        Arrays.sort(used);
        int[] doneSorted = doneAt.clone();
        Arrays.sort(doneSorted);
        System.out.printf("COLDJOIN rep=%d players=%d vd=%d pool=%d serDelay=%dms | 다 받기 틱 중앙=%d 최대=%d (총 %d틱) | 보낸 청크=%d 직렬화(스냅샷)=%d (청크당 %.2f) 낡아 건너뜀=%s 이웃 기다림=%s"
                        + " | 풀CPU=%.0fms regionCPU=%.0fms | 틱 p50=%.1fms p95=%.1fms 최대=%.1fms%n",
                rep, players, vd, poolThreads, Integer.getInteger("furnace.bench.serDelayMs", 0), doneSorted[players / 2], doneSorted[players - 1], ticks, sent, snaps, snaps / (double) Math.max(1, sent),
                counter(w, "staleBodySkips"), counter(w, "bodyLightDeferrals"),
                (w1[0] - w0[0]) / 1e6, (r1[0] - r0[0]) / 1e6,
                used[ticks / 2] / 1e6, used[Math.min(ticks - 1, ticks * 95 / 100)] / 1e6, used[ticks - 1] / 1e6);
        int[] cs = centerAt.clone(), ns = near3At.clone();
        Arrays.sort(cs);
        Arrays.sort(ns);
        // 틱 시간은 일을 몇 틱에 나눴는지에 따라 달라진다 — 같은 일을 적은 틱에 몰면 틱이 길어진다. 그래서 틱당 region CPU 도 같이 본다.
        System.out.printf("COLDJOIN-NEAR rep=%d | 가운데 청크 도착 틱 중앙=%d 최대=%d | 3×3 도착 틱 중앙=%d 최대=%d | 틱당 regionCPU=%.1fms%n",
                rep, cs[players / 2], cs[players - 1], ns[players / 2], ns[players - 1], (r1[0] - r0[0]) / 1e6 / ticks);
        if (ChunkTrace.ON) ChunkTrace.dump();
        System.out.println("COLDJOIN-END rep=" + rep);
        for (EmbeddedChannel c : chs) c.finishAndReleaseAll();
        sched.close();
        w.regionizer().close();
        io.shutdownNow();
        gen.shutdownNow();
        store.closeAll();
    }

    /** 이 PR 에서 생긴 계수(예전 커밋에는 없다 — 그때는 "-"). */
    private static String counter(World w, String name) {
        try {
            var f = World.class.getDeclaredField(name);
            f.setAccessible(true);
            return String.valueOf(((java.util.concurrent.atomic.AtomicInteger) f.get(w)).get());
        } catch (ReflectiveOperationException e) {
            return "-";
        }
    }

    private static int drainChunks(EmbeddedChannel ch, java.util.Set<Long> seen) {
        ch.runPendingTasks();
        int n = 0;
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf b = (ByteBuf) o;
            if (Types.readVarInt(b) == LEVEL_CHUNK) {
                n++;
                int x = b.readInt(), z = b.readInt();
                seen.add(key(x, z));
            }
            b.release();
        }
        return n;
    }

    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }

    static long[] threadStats(String prefix) {
        var mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long cpu = 0, alloc = 0;
        for (Thread th : Thread.getAllStackTraces().keySet()) {
            if (!th.getName().startsWith(prefix)) continue;
            cpu += Math.max(0, mx.getThreadCpuTime(th.threadId()));
            alloc += Math.max(0, mx.getThreadAllocatedBytes(th.threadId()));
        }
        return new long[] {cpu, alloc};
    }
}
