// 청크 저장·로드 파이프라인 단계별 비용(:furnace-server:bench 작업에서만 돈다). 노이즈 오버월드 실제 생성 청크(빛 포함)로 잰다.
// 저장: 스냅샷(forSave) → toNbt → NbtIo.write → 압축 → .mca 기록. 로드: .mca 읽기(압축 본문) → 압축 풀기 → NbtIo.read → fromNbt.
// 단계마다 한 스레드 CPU ns(ThreadMXBean)·할당 바이트를 청크당으로 적는다. 라운드 5회, 앞 2회는 예열.
package org.domi.furnace.server.world.anvil;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSnapshot;
import org.domi.furnace.server.world.ItemRegistry;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;

class ChunkIoPipelineBench {

    static volatile long sink;

    @FunctionalInterface
    interface Step { void run(int i) throws Exception; }

    static final com.sun.management.ThreadMXBean MX = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    /** 청크 n 개에 step 을 돌려 {청크당 CPU us, 청크당 할당 KB}. */
    static double[] measure(int n, Step step) throws Exception {
        long tid = Thread.currentThread().getId();
        long a0 = MX.getThreadAllocatedBytes(tid), c0 = MX.getCurrentThreadCpuTime();
        for (int i = 0; i < n; i++) step.run(i);
        long c1 = MX.getCurrentThreadCpuTime(), a1 = MX.getThreadAllocatedBytes(tid);
        return new double[]{(c1 - c0) / 1000.0 / n, (a1 - a0) / 1024.0 / n};
    }

    @Test
    void bench() throws Exception {
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        var biomes = registries.entries("minecraft:worldgen/biome");
        int plains = biomes.indexOf("minecraft:plains");
        AnvilChunkCodec codec = new AnvilChunkCodec(blocks, ItemRegistry.load("items/1.21.1.txt"), biomes, plains,
                ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
        NoiseWorldSource source = NoiseWorldSource.overworld(12345L, blocks, registries);
        int side = Integer.getInteger("furnace.bench.side", 6);
        int bedrock = org.domi.furnace.server.block.Blocks.get().defaultState("minecraft:bedrock");
        List<ChunkColumn> cols = new ArrayList<>();
        List<int[]> pos = new ArrayList<>();
        for (int x = 0; x < side; x++) for (int z = 0; z < side; z++) {
            ChunkColumn c = source.generateWithFeatures(100 + x, -40 + z);
            org.domi.furnace.server.block.ChunkLevel.precomputeLight(c, 100 + x, -40 + z, bedrock, true);
            cols.add(c);
            pos.add(new int[]{100 + x, -40 + z});
        }
        int n = cols.size();
        ChunkSnapshot[] snaps = new ChunkSnapshot[n];
        NbtCompound[] nbts = new NbtCompound[n];
        byte[][] raw = new byte[n][];
        byte[][] comp = new byte[n][];
        RegionFile.Payload[] readComp = new RegionFile.Payload[n];
        byte[][] readRaw = new byte[n][];
        NbtCompound[] readNbt = new NbtCompound[n];
        long rawTotal = 0, compTotal = 0;
        Path dir = Files.createTempDirectory("chunkio");
        String[] names = {"forSave", "toNbt", "NbtIo.write", "compress", "region.write", "region.read", "decompress", "NbtIo.read", "fromNbt"};
        for (int round = 0; round < 5; round++) {
            Path file = dir.resolve("r." + round + ".mca");
            double[][] r = new double[names.length][];
            r[0] = measure(n, i -> snaps[i] = ChunkSnapshot.forSave(pos.get(i)[0], pos.get(i)[1], cols.get(i)));
            r[1] = measure(n, i -> nbts[i] = codec.toNbt(snaps[i]));
            r[2] = measure(n, i -> raw[i] = NbtIo.toBytes(nbts[i], ""));
            r[3] = measure(n, i -> comp[i] = Compression.compress(Compression.DEFLATE, raw[i]));
            try (RegionFile rf = new RegionFile(file)) {
                r[4] = measure(n, i -> rf.writeCompressed(pos.get(i)[0], pos.get(i)[1], comp[i], Compression.DEFLATE));
            }
            try (RegionFile rf = new RegionFile(file)) {
                r[5] = measure(n, i -> readComp[i] = rf.readPayload(pos.get(i)[0], pos.get(i)[1]));
            }
            r[6] = measure(n, i -> readRaw[i] = readComp[i].decompress());
            r[7] = measure(n, i -> readNbt[i] = NbtIo.read(readRaw[i]));
            r[8] = measure(n, i -> sink += codec.fromNbt(readNbt[i]).surfaceBlockY(3, 3));
            rawTotal = 0; compTotal = 0;
            for (int i = 0; i < n; i++) { rawTotal += raw[i].length; compTotal += comp[i].length; }
            StringBuilder sb = new StringBuilder("[BENCH] chunkio round=" + round + " n=" + n);
            double save = 0, load = 0;
            for (int k = 0; k < names.length; k++) {
                sb.append(String.format(" | %s %.0fus %.0fKB", names[k], r[k][0], r[k][1]));
                if (k < 5) save += r[k][0]; else load += r[k][0];
            }
            sb.append(String.format(" || save %.0fus load %.0fus raw %.1fKB comp %.1fKB", save, load, rawTotal / 1024.0 / n, compTotal / 1024.0 / n));
            System.out.println(sb);
        }
        // 같은 .mca 하나의 32x32 청크를 IO 레인(바탕 풀 4스레드)으로 한꺼번에 로드: 디코드를 레인 안에서(예전) / 레인 밖 풀에서(지금).
        Path wdir = dir.resolve("lanes");
        RegionFileStore store = new RegionFileStore(wdir);
        for (int i = 0; i < 1024; i++) store.writeChunk(i & 31, i >> 5, raw[i % n]);
        store.closeAll();
        for (int round = 0; round < 5; round++) {
            for (int mode = 0; mode < 2; mode++) {
                RegionFileStore st = new RegionFileStore(wdir);
                var src = new org.domi.furnace.server.world.gen.AnvilWorldSource(wdir, st, codec, (x, z) -> { throw new AssertionError(); });
                java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
                IoLanes lanes = new IoLanes(pool, 16);
                long t0 = System.nanoTime();
                List<java.util.concurrent.CompletableFuture<ChunkColumn>> fs = new ArrayList<>();
                for (int i = 0; i < 1024; i++) {
                    int x = i & 31, z = i >> 5;
                    fs.add(mode == 0 ? src.loadChunkAsync(x, z, lanes.lane(x, z), pool)
                            : src.loadChunkAsync(x, z, lanes.lane(x, z), pool, pool));
                }
                for (var f : fs) sink += f.join().surfaceBlockY(1, 1);
                double ms = (System.nanoTime() - t0) / 1e6;
                pool.shutdown();
                st.closeAll();
                System.out.printf("[BENCH] lanes round=%d %s 1024청크 %.1f ms%n", round, mode == 0 ? "레인 안 디코드" : "레인 밖 디코드", ms);
            }
        }
        String prof = System.getProperty("furnace.bench.profile");   // 단계 이름(예: toNbt) — 그 단계만 JFR 실행 샘플로 상위 프레임
        if (prof != null) {
            int idx = java.util.Arrays.asList(names).indexOf(prof);
            Step[] steps = {
                i -> snaps[i] = ChunkSnapshot.forSave(pos.get(i)[0], pos.get(i)[1], cols.get(i)),
                i -> nbts[i] = codec.toNbt(snaps[i]),
                null, null, null, null, null,
                i -> readNbt[i] = NbtIo.read(readRaw[i]),
                i -> sink += codec.fromNbt(readNbt[i]).surfaceBlockY(3, 3)};
            Path out = Files.createTempFile("chunkio", ".jfr");
            try (jdk.jfr.Recording rec = new jdk.jfr.Recording()) {
                rec.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(1));
                rec.start();
                long end = System.nanoTime() + 8_000_000_000L;
                while (System.nanoTime() < end) for (int i = 0; i < n; i++) steps[idx].run(i);
                rec.stop();
                rec.dump(out);
            }
            java.util.Map<String, Integer> self = new java.util.HashMap<>(), incl = new java.util.HashMap<>();
            int total = 0;
            for (var e : jdk.jfr.consumer.RecordingFile.readAllEvents(out)) {
                if (!e.getEventType().getName().equals("jdk.ExecutionSample")) continue;
                var st = e.getStackTrace();
                if (st == null || st.getFrames().isEmpty()) continue;
                if (!"Test worker".equals(e.getThread("sampledThread").getJavaName())) continue;
                total++;
                var fr = st.getFrames();
                self.merge(fr.get(0).getMethod().getType().getName() + "." + fr.get(0).getMethod().getName() + ":" + fr.get(0).getLineNumber(), 1, Integer::sum);
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (var f : fr) {
                    String k = f.getMethod().getType().getName().replace("org.domi.furnace.server.", "") + "." + f.getMethod().getName();
                    if (seen.add(k)) incl.merge(k, 1, Integer::sum);
                }
            }
            final int t = total;
            System.out.println("[PROF] " + prof + " samples " + t);
            self.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(20)
                .forEach(en -> System.out.printf("[PROF] self %5.1f%% %s%n", 100.0 * en.getValue() / t, en.getKey()));
            incl.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(30)
                .forEach(en -> System.out.printf("[PROF] incl %5.1f%% %s%n", 100.0 * en.getValue() / t, en.getKey()));
        }
    }
}
