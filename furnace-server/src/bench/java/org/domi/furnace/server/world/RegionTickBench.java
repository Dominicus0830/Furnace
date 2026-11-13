// 플레이어 1명이 걸어 다니는 region 틱 측정(:furnace-server:bench 작업에서만 돈다). 실제 Region.tick 경로를 단계별로 잰다.
package org.domi.furnace.server.world;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.bench.BenchReport;
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
import org.junit.jupiter.api.Test;

class RegionTickBench {

    // 속성은 시작 때 한 번만 읽는다 — 틱·게임규칙 질의마다 System.getProperty 를 부르면 벤치가 만든 비용이 프로파일에 섞인다(O07 지적: isRaining 2.5%).
    static final String SCENE = System.getProperty("furnace.bench.scene", "");
    static final boolean RUN_TICK = Boolean.getBoolean("furnace.bench.runtick");
    static final boolean NO_SPAWN = Boolean.getBoolean("furnace.bench.nospawn");
    /** 기본은 실서버 모양의 이벤트 입구(처리기 0개여도 구독 확인을 한다). 끄려면 -Dfurnace.bench.realEvents=false(옛 GameEvents.NONE 길). */
    static final boolean REAL_EVENTS = !"false".equals(System.getProperty("furnace.bench.realEvents"));
    static final int SCENE_SIZE = Integer.getInteger("furnace.bench.sceneSize", 200);
    static final int SCENE_PERIOD = Integer.getInteger("furnace.bench.scenePeriod", 20);
    static final boolean HOPPER_IDLE = Boolean.getBoolean("furnace.bench.hopperIdle");

    @Test
    void walkingPlayerTick() throws Exception {
        int view = Integer.getInteger("furnace.bench.view", 10);
        int mobs = Integer.getInteger("furnace.bench.count", 100);
        int ticks = Integer.getInteger("furnace.bench.ticks", 1200);
        double speed = Double.parseDouble(System.getProperty("furnace.bench.speed", "0"));
        final int skip = Integer.getInteger("furnace.bench.skip", ticks / 4);   // 앞 구간(청크 로드·예열)은 통계에서 뺀다
        if (!Regionizer.STATS) System.out.println("RBENCH 경고: -Dfurnace.tickstats 가 꺼져 단계별 시간(phases)은 0 으로 나온다(:furnace-server:bench 작업은 켠다)");
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
            // 실서버처럼 플러그인 이벤트 입구가 버스 구독 확인을 하게(처리기 0개). PluginGameEvents.blockPhysics·blockRedstone 첫 줄과 같은 일.
            // GameEvents.NONE 이면 이 확인이 통째로 건너뛰어져 레드스톤 장면이 약 5% 싸게 나온다(O08 반론 B).
            final org.domi.furnace.server.plugin.EventBusImpl rbBus = new org.domi.furnace.server.plugin.EventBusImpl();
            final org.domi.furnace.server.plugin.GameEvents rbEvents = new org.domi.furnace.server.plugin.GameEvents() {
                @Override public boolean blockPhysics(String d, org.domi.furnace.server.block.Pos pos, int st, org.domi.furnace.server.block.Pos sp, int sb) {
                    return !rbBus.hasSubscribers(org.domi.furnace.api.event.block.BlockPhysicsEvent.class) || true;
                }
                @Override public int blockRedstone(String d, org.domi.furnace.server.block.Pos pos, int st, int o, int n) {
                    return rbBus.hasSubscribers(org.domi.furnace.api.event.block.BlockRedstoneEvent.class) ? n : n;
                }
            };
            @Override public org.domi.furnace.server.plugin.GameEvents gameEvents() {
                return REAL_EVENTS ? rbEvents : super.gameEvents();
            }
            @Override public boolean gameRule(String name) {   // furnace.bench.nospawn=true: 자연 스폰 끔(다른 비용을 보려고)
                if (NO_SPAWN && name.equals("doMobSpawning")) return false;
                return super.gameRule(name);
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
            if (!SCENE.isEmpty())
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) r.chunkAtBlocking(new ChunkPos(((int) cx0 >> 4) + dx, ((int) cz0 >> 4) + dz));
            buildScene(level, SCENE, (int) Math.floor(cx0), (int) Math.floor(cy0), (int) Math.floor(cz0));
        }).get();
        long[] samples = new long[ticks];
        long[] rawSamples = new long[ticks];
        long[] ph = new long[5];
        // -Dfurnace.bench.cpujfr=true: region 스레드 CPU 샘플(jdk.ExecutionSample 2ms)을 자기·포함 비율로 찍는다
        jdk.jfr.Recording rec = null;
        final boolean wantJfr = Boolean.getBoolean("furnace.bench.cpujfr");
        final int jfrFrom = skip;   // 앞 구간은 프로파일에서도 뺀다
        final long[] alloc = new long[ticks];   // 틱마다 region 스레드가 할당한 바이트(결정적 계수 후보 — 시간과 달리 러너 속도에 안 흔들린다)
        long cpu0 = MultiDimensionTickBench.processCpuNanos(), wall0 = System.nanoTime();
        for (int t = 0; t < ticks; t++) {
            if (wantJfr && t == jfrFrom) {
                rec = new jdk.jfr.Recording();
                rec.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(1)).withStackTrace();
                rec.start();
            }
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
                long a0 = BenchReport.threadAllocatedBytes();
                long t0 = System.nanoTime();
                sceneTick(r.level(), SCENE, (int) Math.floor(cx0), (int) Math.floor(cy0), (int) Math.floor(cz0), tt);
                if (RUN_TICK) r.runTick(true); else r.tick();
                samples[tt] = System.nanoTime() - t0;
                rawSamples[tt] = samples[tt];
                alloc[tt] = BenchReport.threadAllocatedBytes() - a0;
                if (tt >= skip) for (int i = 0; i < 5; i++) ph[i] += r.phaseSum[i] - before[i];
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
        int from = skip, n = ticks - from;
        java.util.Arrays.sort(samples, from, ticks);
        long sum = 0;
        for (int i = from; i < ticks; i++) sum += samples[i];
        System.out.printf("RBENCH view=%d mobs=%d avg=%.3fms p50=%.3fms p95=%.3fms max=%.3fms%n", view, mobs,
                sum / (double) n / 1e6, samples[from + n / 2] / 1e6, samples[from + n * 95 / 100] / 1e6, samples[ticks - 1] / 1e6);
        System.out.printf("RBENCH phases(avg ms) block=%.3f light=%.3f player=%.3f vis=%.3f misc=%.3f%n",
                ph[0] / (double) n / 1e6, ph[1] / (double) n / 1e6, ph[2] / (double) n / 1e6, ph[3] / (double) n / 1e6, ph[4] / (double) n / 1e6);
        final int[] loadedEntities = new int[2];
        sched.region(new RegionPos(p.chunkPos().x(), p.chunkPos().z())).execute(() -> {
            Region r = w.regionAt(new RegionPos(p.chunkPos().x(), p.chunkPos().z()));
            loadedEntities[0] = p.loadedChunks().size();
            loadedEntities[1] = r.level().entities().size();
            System.out.printf("RBENCH loaded=%d entities=%d%n", loadedEntities[0], loadedEntities[1]);
        }).get();
        long[] is = new long[4];   // region 별 표의 합(흡수·없어진 region 몫은 빠진다)
        for (Region rr : w.regionizer().regionsSnapshot()) for (int i = 0; i < 4; i++) is[i] += rr.installStats[i];
        System.out.printf("RBENCH install(누적, 100틱 리셋 없음) count=%d put=%dms light=%dms ent=%dms%n", is[0], is[1] / 1000000, is[2] / 1000000, is[3] / 1000000);
        long allocSum = 0;
        boolean allocOk = true;
        for (int i = from; i < ticks; i++) { if (alloc[i] < 0) allocOk = false; allocSum += alloc[i]; }
        BenchReport rep = BenchReport.of("RegionTickBench").variant(SCENE.isEmpty() ? "walk" : SCENE.replace(',', '+')).scene(SCENE.isEmpty() ? "walk" : SCENE).seed(12345L)
                .put("view", view).put("mobs", mobs).put("ticks", ticks).put("skip", skip)
                .put("realEvents", REAL_EVENTS).put("noise", noise).put("tickstats", Regionizer.STATS)
                .timesNs("tick_", samples, from, ticks)   // samples 는 위에서 이미 정렬됨(정렬해도 같은 값)
                .put("cpu_ms", cpu / 1e6).put("wall_ms", wall / 1e6)
                .put("loadedChunks", loadedEntities[0]).put("entities", loadedEntities[1])
                .put("installCount", is[0]);
        if (allocOk) rep.put("allocBytesPerTick", allocSum / (double) n);
        if (Regionizer.STATS) rep.put("phase_block_ms", ph[0] / (double) n / 1e6).put("phase_light_ms", ph[1] / (double) n / 1e6)
                .put("phase_player_ms", ph[2] / (double) n / 1e6).put("phase_vis_ms", ph[3] / (double) n / 1e6).put("phase_misc_ms", ph[4] / (double) n / 1e6);
        rep.write();
        ioPool.shutdown();
        genPool.shutdown();
        sched.close();
    }


    /** X1 성능 점검용 장면: hoppers(깔때기 줄·저장 상자), rails(전동 레일 줄 + 레드스톤 횃불/와이어), wire(레드스톤 배선 격자), tempt(음식 든 플레이어 근처 동물). */
    static void buildScene(RegionLevel level, String scene, int cx, int cy, int cz) {
        int n = SCENE_SIZE;
        for (String sc : scene.split(",")) {
            switch (sc.trim()) {
                case "hoppers" -> {
                    // n 줄: 상자(물건 가득) → 깔때기 7개(동쪽) → 상자. 아래쪽 줄은 비어 있는 채 놀고 있는 깔때기.
                    int facingEast = org.domi.furnace.server.block.B.prop("facing").with(org.domi.furnace.server.block.B.defaultState("hopper"), "east");
                    int facingDown = org.domi.furnace.server.block.B.prop("facing").with(org.domi.furnace.server.block.B.defaultState("hopper"), "down");
                    for (int row = 0; row < n; row++) {
                        int z = cz - 30 + (row % 60), y = cy + 3 + row / 60 * 2, x0 = cx - 30;
                        var top = new org.domi.furnace.server.block.Pos(x0, y + 1, z);
                        level.setBlock(top, org.domi.furnace.server.block.B.defaultState("chest"), 3);
                        if (!HOPPER_IDLE && level.getBlockEntity(top) instanceof org.domi.furnace.server.block.entity.ContainerBlockEntity c)
                            for (int i = 0; i < c.items.length; i++) c.items[i] = new org.domi.furnace.server.entity.ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "minecraft:cobblestone"), 64);
                        for (int i = 0; i < 7; i++) level.setBlock(new org.domi.furnace.server.block.Pos(x0 + i, y, z), facingEast, 3);
                        level.setBlock(new org.domi.furnace.server.block.Pos(x0 + 7, y, z), facingDown, 3);
                        level.setBlock(new org.domi.furnace.server.block.Pos(x0 + 7, y - 1, z), org.domi.furnace.server.block.B.defaultState("chest"), 3);
                    }
                }
                case "rails", "wire" -> {
                    boolean rails = sc.trim().equals("rails");
                    for (int row = 0; row < n; row++) {
                        int z = cz - 30 + (row % 60), y = cy + 3 + row / 60 * 3, x0 = cx - 30;
                        for (int i = 0; i < 30; i++) {
                            var pos = new org.domi.furnace.server.block.Pos(x0 + i, y, z);
                            level.setBlock(new org.domi.furnace.server.block.Pos(x0 + i, y - 1, z), org.domi.furnace.server.block.B.defaultState("stone"), 3);
                            level.setBlock(pos, org.domi.furnace.server.block.B.defaultState(rails ? "powered_rail" : "redstone_wire"), 3);
                        }
                    }
                }
                default -> {}
            }
        }
    }

    /** 장면 틱 훅: rails/wire 줄 끝의 레드스톤 블록을 주기로 껐다 켠다. */
    static void sceneTick(RegionLevel level, String scene, int cx, int cy, int cz, int t) {
        int n = SCENE_SIZE;
        int period = SCENE_PERIOD;
        if (!(scene.contains("rails") || scene.contains("wire")) || t % period != 0) return;
        boolean on = (t / period) % 2 == 0;
        int rb = org.domi.furnace.server.block.B.defaultState("redstone_block"), air = org.domi.furnace.server.block.B.defaultState("air");
        for (int row = 0; row < n; row++) {
            int z = cz - 30 + (row % 60), y = cy + 3 + row / 60 * 3, x0 = cx - 30;
            level.setBlock(new org.domi.furnace.server.block.Pos(x0 - 1, y, z), on ? rb : air, 3);
        }
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
                        if (Boolean.getBoolean("furnace.bench.lines")) k = k + ":" + fr.get(0).getLineNumber();
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
