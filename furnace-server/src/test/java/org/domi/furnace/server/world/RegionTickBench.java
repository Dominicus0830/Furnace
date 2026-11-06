// 플레이어 1명이 걸어 다니는 region 틱 측정(기본 꺼짐: -Dfurnace.bench=true). 실제 Region.tick 경로를 단계별로 잰다.
package org.domi.furnace.server.world;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class RegionTickBench {

    @Test
    void walkingPlayerTick() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        int view = Integer.getInteger("furnace.bench.view", 10);
        int mobs = Integer.getInteger("furnace.bench.count", 100);
        int ticks = Integer.getInteger("furnace.bench.ticks", 1200);
        double speed = Double.parseDouble(System.getProperty("furnace.bench.speed", "0"));
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var ioPool = Executors.newSingleThreadExecutor();
        boolean noise = Boolean.getBoolean("furnace.bench.noise");
        org.domi.furnace.server.world.gen.WorldSource src;
        double sx = 8.5, sy = -60, sz = 8.5;
        if (noise) {   // 실제 서버처럼 노이즈 지형(물·풀·잎 — 무작위 틱·조명이 많다)
            var ns = org.domi.furnace.server.world.gen.NoiseWorldSource.overworld(12345L, reg, RegistrySet.load("registries/1.21.1.txt"));
            var sp = ns.defaultSpawn();
            sx = sp.x() + 0.5; sy = sp.y(); sz = sp.z() + 0.5;
            src = ns;
        } else src = new SuperflatWorldSource(reg, 0);
        var genPool = Executors.newFixedThreadPool(4);
        World w = new World(src, new ChunkWorkerPool(2),
                sched, sched.regionThreadCount(), 16, view, view, reg.directBits(), 7, ioPool, genPool);
        w.setRegistries(reg, org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        // 서버(ServerCore)처럼 바이옴을 실제로 답한다 — 없으면(null) 자연 스폰이 바이옴 표를 못 찾아 전혀 돌지 않는다.
        // biomeIs 는 바닐라 태그(데이터 팩)가 없어 비슷한 크기의 목록(전체 바이옴 이름)을 훑는 것으로 비용만 흉내 낸다.
        var biomeNames = RegistrySet.load("registries/1.21.1.txt").entries("minecraft:worldgen/biome");
        long zoomSeed = org.domi.furnace.server.net.protocol.play.LoginPlay.obfuscateSeed(12345L);
        java.util.function.IntFunction<String> nameOf = id -> id >= 0 && id < biomeNames.size() ? biomeNames.get(id) : null;
        w.levelServices(new org.domi.furnace.server.block.LevelServices.Delegating(w.levelServices()) {
            @Override public String biomeAt(int x, int y, int z) {
                int[] q = BiomeZoom.quart(zoomSeed, x, y, z);
                int bx = q[0] >> 2, bz = q[2] >> 2;
                if (!w.ownsChunkOnCurrentThread(bx, bz)) return null;
                return nameOf.apply(w.regionizer().ownerOf(bx, bz).biomeIdAtQuart(q[0], q[1], q[2]));
            }
            @Override public String roughBiomeAt(int x, int y, int z) {   // ServerCore 와 같은 길(확대 없음)
                if (!w.ownsChunkOnCurrentThread(x >> 4, z >> 4)) return null;
                return nameOf.apply(w.regionizer().ownerOf(x >> 4, z >> 4).noiseBiomeIdAtBlock(x, y, z));
            }
            @Override public boolean biomeIs(int x, int y, int z, String tag) {
                String b = biomeAt(x, y, z);
                return b != null && biomeNames.subList(0, biomeNames.size() / 2).contains(b);
            }
        });
        RegionPos rp = new RegionPos((int) Math.floor(sx) >> 4, (int) Math.floor(sz) >> 4);
        final double cx0 = sx, cy0 = sy, cz0 = sz;
        var ch = new ConcurrentEmbeddedChannel();
        Player p = new Player(w.entityIds().next(), UUID.randomUUID(), "A", ch);
        p.inventory(new PlayerInventory());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        w.clock().setDayTime(Long.getLong("furnace.bench.daytime", 18000L));
        p.applyMove(sx, sy, sz, 0, 0, true);
        p.publishPosition(sx, sy, sz, 0, 0, true);
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            r.adopt(p);
            var level = r.level();
            String[] types = System.getProperty("furnace.bench.types", "minecraft:cow,minecraft:sheep,minecraft:pig,minecraft:chicken,minecraft:zombie,minecraft:skeleton,minecraft:creeper,minecraft:spider").split(",");
            java.util.Random rnd = new java.util.Random(1);
            for (int i = 0; i < mobs; i++) {
                r.chunkAtBlocking(new ChunkPos(((int) cx0 >> 4) + rnd.nextInt(5) - 2, ((int) cz0 >> 4) + rnd.nextInt(5) - 2));
            }
            rnd = new java.util.Random(1);
            for (int i = 0; i < mobs; i++) {
                var e = Entities.create(level, types[i % types.length]);
                e.moveTo(cx0 + rnd.nextInt(60) - 30, cy0 + 2, cz0 + rnd.nextInt(60) - 30, rnd.nextFloat() * 360, 0);
                level.addFreshEntity(e);
            }
        }).get();
        long[] samples = new long[ticks];
        long[] rawSamples = new long[ticks];
        long[] ph = new long[5];
        // -Dfurnace.bench.cpujfr=true: region 스레드 CPU 샘플(jdk.ExecutionSample 2ms)을 자기·포함 비율로 찍는다
        jdk.jfr.Recording rec = null;
        if (Boolean.getBoolean("furnace.bench.cpujfr")) {
            rec = new jdk.jfr.Recording();
            rec.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(2)).withStackTrace();
            rec.start();
        }
        long cpu0 = MultiDimensionTickBench.processCpuNanos(), wall0 = System.nanoTime();
        for (int t = 0; t < ticks; t++) {
            // 반지름 24 블록 원을 걷는다(틱당 ~0.2 블록 — 걷기 속도)
            // furnace.bench.speed>0 이면 그 속도(블록/틱)로 +x 직진 — 새 청크 로드·전송·퇴출이 계속 일어난다
            double a = t * 0.2 / 24.0;
            double x = cx0 + Math.cos(a) * 24, z = cz0 + Math.sin(a) * 24;
            if (speed > 0) { x = cx0 + t * speed; z = cz0; }
            p.publishPosition(x, cy0, z, (float) Math.toDegrees(a), 0, true);
            p.pushMove(x, cy0, z, true);
            final int tt = t;
            RegionPos pr = new RegionPos(p.chunkPos().x(), p.chunkPos().z());
            sched.region(pr).execute(() -> {
                Region r = w.regionAt(pr);
                long[] before = r.phaseSum.clone();
                long t0 = System.nanoTime();
                if (Boolean.getBoolean("furnace.bench.runtick")) r.runTick(true); else r.tick();
                samples[tt] = System.nanoTime() - t0;
                rawSamples[tt] = samples[tt];
                if (tt >= Integer.getInteger("furnace.bench.skip", ticks / 4)) for (int i = 0; i < 5; i++) ph[i] += r.phaseSum[i] - before[i];
            }).get();
            // 엔티티가 많으면 EmbeddedChannel 의 보류 작업이 재귀로 깊어진다 — 큰 스택 스레드에서 비운다
            Thread dr = new Thread(null, () -> {
                ch.runPendingTasks();
                Object o;
                while ((o = ch.readOutbound()) != null) io.netty.util.ReferenceCountUtil.release(o);
            }, "bench-drain", 1L << 30);
            dr.start();
            dr.join();
        }
        long cpu = MultiDimensionTickBench.processCpuNanos() - cpu0, wall = System.nanoTime() - wall0;
        if (rec != null) {
            rec.stop();
            java.nio.file.Path f = java.nio.file.Files.createTempFile("rbench", ".jfr");
            rec.dump(f);
            rec.close();
            cpuProfile(f, Integer.getInteger("furnace.bench.top", 40));
            java.nio.file.Files.deleteIfExists(f);
        }
        System.out.printf("RBENCH cpu=%.0fms wall=%.0fms (틱 %d)%n", cpu / 1e6, wall / 1e6, ticks);
        if (Boolean.getBoolean("furnace.bench.dump")) {
            StringBuilder sbd = new StringBuilder("RBENCH per-tick ms:");
            for (int t = 0; t < Math.min(ticks, 200); t++) sbd.append(String.format(" %.0f", rawSamples[t] / 1e6));
            System.out.println(sbd);
        }
        int from = Integer.getInteger("furnace.bench.skip", ticks / 4), n = ticks - from;
        java.util.Arrays.sort(samples, from, ticks);
        long sum = 0;
        for (int i = from; i < ticks; i++) sum += samples[i];
        System.out.printf("RBENCH view=%d mobs=%d avg=%.3fms p50=%.3fms p95=%.3fms max=%.3fms%n", view, mobs,
                sum / (double) n / 1e6, samples[from + n / 2] / 1e6, samples[from + n * 95 / 100] / 1e6, samples[ticks - 1] / 1e6);
        System.out.printf("RBENCH phases(avg ms) block=%.3f light=%.3f player=%.3f vis=%.3f misc=%.3f%n",
                ph[0] / (double) n / 1e6, ph[1] / (double) n / 1e6, ph[2] / (double) n / 1e6, ph[3] / (double) n / 1e6, ph[4] / (double) n / 1e6);
        sched.region(new RegionPos(p.chunkPos().x(), p.chunkPos().z())).execute(() -> {
            Region r = w.regionAt(new RegionPos(p.chunkPos().x(), p.chunkPos().z()));
            System.out.printf("RBENCH loaded=%d entities=%d%n", p.loadedChunks().size(), r.level().entities().size());
        }).get();
        long[] is = new long[4];   // region 별 표의 합(흡수·없어진 region 몫은 빠진다)
        for (Region rr : w.regionizer().regionsSnapshot()) for (int i = 0; i < 4; i++) is[i] += rr.installStats[i];
        System.out.printf("RBENCH install(누적, 100틱 리셋 없음) count=%d put=%dms light=%dms ent=%dms%n", is[0], is[1] / 1000000, is[2] / 1000000, is[3] / 1000000);
        ioPool.shutdown();
        genPool.shutdown();
        sched.close();
    }

    /** region 스레드 CPU 샘플: 자기(맨 위 프레임)·포함(스택에 한 번이라도) 상위 n. */
    static void cpuProfile(java.nio.file.Path file, int n) throws Exception {
        java.util.Map<String, int[]> self = new java.util.HashMap<>(), incl = new java.util.HashMap<>();
        java.util.Map<String, java.util.Map<String, int[]>> callers = new java.util.HashMap<>();
        int depth = Integer.getInteger("furnace.bench.callers", 5);
        int total = 0;
        try (var rf = new jdk.jfr.consumer.RecordingFile(file)) {
            while (rf.hasMoreEvents()) {
                var e = rf.readEvent();
                if (!e.getEventType().getName().equals("jdk.ExecutionSample")) continue;
                var th = e.getThread("sampledThread");
                if (th == null || th.getJavaName() == null || !th.getJavaName().startsWith("furnace-region")) continue;
                var st = e.getStackTrace();
                if (st == null || st.getFrames().isEmpty()) continue;
                total++;
                var fr = st.getFrames();
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (int i = 0; i < fr.size(); i++) {
                    var m = fr.get(i).getMethod();
                    String k = m.getType().getName().replace("org.domi.furnace.server.", "") + "." + m.getName();
                    if (i == 0) {
                        self.computeIfAbsent(k, x -> new int[1])[0]++;
                        StringBuilder sb = new StringBuilder();
                        for (int j = 1; j < Math.min(fr.size(), 1 + depth); j++) {
                            var mj = fr.get(j).getMethod();
                            sb.append(" <- ").append(mj.getType().getName().replace("org.domi.furnace.server.", "")).append('.').append(mj.getName()).append(':').append(fr.get(j).getLineNumber());
                        }
                        callers.computeIfAbsent(k, x -> new java.util.HashMap<>()).computeIfAbsent(sb.toString(), x -> new int[1])[0]++;
                    }
                    if (seen.add(k)) incl.computeIfAbsent(k, x -> new int[1])[0]++;
                }
            }
        }
        System.out.printf("RPROF 샘플=%d%n", total);
        for (var pair : java.util.List.of(java.util.Map.entry("self", self), java.util.Map.entry("incl", incl))) {
            var l = new java.util.ArrayList<>(pair.getValue().entrySet());
            l.sort((a, b) -> b.getValue()[0] - a.getValue()[0]);
            for (int i = 0; i < Math.min(n, l.size()); i++)
                System.out.printf("RPROF %s %5.1f%% %s%n", pair.getKey(), 100.0 * l.get(i).getValue()[0] / total, l.get(i).getKey());
        }
        var ls = new java.util.ArrayList<>(self.entrySet());
        ls.sort((a, b) -> b.getValue()[0] - a.getValue()[0]);
        for (int i = 0; i < Math.min(12, ls.size()); i++) {
            var cl = new java.util.ArrayList<>(callers.get(ls.get(i).getKey()).entrySet());
            cl.sort((a, b) -> b.getValue()[0] - a.getValue()[0]);
            for (int j = 0; j < Math.min(3, cl.size()); j++)
                System.out.printf("RCALL %s %5.1f%%%s%n", ls.get(i).getKey(), 100.0 * cl.get(j).getValue()[0] / total, cl.get(j).getKey());
        }
    }
}
