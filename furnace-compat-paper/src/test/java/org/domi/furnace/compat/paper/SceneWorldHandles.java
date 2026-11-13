// 호환 기준 장면 compat-world-handles — 어댑터 호출이 모두 지나는 월드 손잡이(Compat.world·PluginHost.handleFor)의 의미와, 같은 읽기를
// 스레드 종류별(소유 region·다른 region·전역·시험 스레드·비동기)로 했을 때의 결과(값 또는 예외 종류)를 기록한다.
// T-071(handleFor 빠른 길)·T-072(상태 id 표의 스레드 축)가 "결과 같음"을 증명하는 바탕. 벤치 한 걸음 = Compat.world 를 되풀이해 부르기(스레드 1~4 변형은 벤치에서).
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockEditOptions;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.identity.SceneHash;
import org.domi.furnace.server.scene.Probe;
import org.domi.furnace.server.scene.Size;
import org.domi.furnace.test.FurnaceTestServer;

public final class SceneWorldHandles extends CompatScene {

    static volatile Object sink;

    @Override public String name() { return "compat-world-handles"; }

    @Override public String unit() { return "batch"; }

    @Override public String covers() { return "T-071 handleFor·Compat.world 동일성, T-072 스레드 축(읽기 결과 종류), T-075"; }

    @Override
    public void play(Size size, SceneHash.Recorder rec, Probe probe) throws Exception {
        try (FurnaceTestServer s = server()) {
            s.loadChunk(0, 0);
            BlockPos pos = new BlockPos(3, -50, 3);
            var fw = s.overworld();
            s.onRegion(pos, () -> {
                fw.editBlocksNow(BlockEditOptions.FAST, ed -> {
                    ed.set(3, -50, 3, BlockData.of("minecraft:stone"));
                    ed.set(4, -50, 3, new BlockData("minecraft:oak_stairs", java.util.Map.of("facing", "east", "half", "top", "shape", "straight", "waterlogged", "false")));
                });
                return null;
            });
            // 1) 월드 목록과 손잡이 동일성
            List<String> dims = new ArrayList<>();
            for (var w : s.api().worlds()) dims.add(w.dimension());
            rec.text("dimensions " + dims);
            for (String dim : dims) {
                var a = s.api().world(dim);
                var b = s.api().world(dim);
                World bw = Compat.world(dim);
                rec.text("world " + dim + " api-same=" + (a == b) + " loaded=" + a.isLoaded() + " compat-same=" + (bw == Compat.world(dim)) + " name=" + bw.getName()
                        + " env=" + safe(() -> bw.getEnvironment()) + " min=" + safe(bw::getMinHeight) + " max=" + safe(bw::getMaxHeight)
                        + " by-name=" + (Bukkit.getWorld(bw.getName()) == bw) + " by-api=" + (Compat.world(a) == bw)
                        + " uid=" + safe(() -> bw.getUID() != null) + " seed-same=" + safe(() -> bw.getSeed() == Bukkit.getWorld(bw.getName()).getSeed()));
            }
            StringBuilder names = new StringBuilder();
            for (World w : Bukkit.getWorlds()) names.append(w.getName()).append(',');
            rec.text("bukkit-worlds " + names);

            // 2) 여러 스레드가 동시에 Compat.world 를 불러도 같은 객체 하나(원자적으로 만든다)
            String dim = dims.get(0);
            for (int threads : new int[] {1, 2, 4}) {
                ExecutorService ex = Executors.newFixedThreadPool(threads);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Set<World>>> fs = new ArrayList<>();
                for (int t = 0; t < threads; t++) fs.add(ex.submit(() -> {
                    go.await();
                    Set<World> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
                    for (int i = 0; i < 20_000; i++) seen.add(Compat.world(dim));
                    return seen;
                }));
                go.countDown();
                Set<World> all = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
                for (var f : fs) all.addAll(f.get());
                ex.shutdown();
                rec.text("threads " + threads + " distinct-world-objects " + all.size() + " same-as-main " + all.contains(Compat.world(dim)));
            }

            // 3) 월드 손잡이를 거치는 모든 길이 같은 객체를 낸다
            World w = Compat.world(dim);
            var mob = s.onRegion(pos, () -> fw.spawnEntityNow("minecraft:pig", new org.domi.furnace.api.world.Location(dim, 3.5, -49, 3.5, 0, 0)));
            var tp = s.join("Dana");
            attachAll(s);
            Block blk = w.getBlockAt(3, -50, 3);
            var pig = Compat.entity(mob);
            var pl = Compat.player(tp.player());
            rec.text("same-world block=" + (blk.getWorld() == w) + " location=" + (blk.getLocation().getWorld() == w) + " entity=" + (pig.getWorld() == w)
                    + " entity-loc=" + (pig.getLocation().getWorld() == w) + " player=" + (pl.getWorld() == w) + " player-loc=" + (pl.getLocation().getWorld() == w)
                    + " relative=" + (blk.getRelative(org.bukkit.block.BlockFace.EAST).getWorld() == w) + " chunk=" + (blk.getChunk().getWorld() == w));

            // 4) 같은 읽기를 스레드 종류별로 — 값이거나 예외 종류(소유 스레드가 아니면 막힌다)
            BlockPos far = new BlockPos(16 * 40, -50, 16 * 40);
            s.loadChunk(40, 40);
            List<String> axes = new ArrayList<>();
            axes.add("owner-region " + s.onRegion(pos, () -> reads(w, blk, pig, pl)));
            axes.add("other-region " + s.onRegion(far, () -> reads(w, blk, pig, pl)));
            axes.add("global " + s.onGlobal(() -> reads(w, blk, pig, pl)));
            axes.add("test-thread " + reads(w, blk, pig, pl));
            axes.add("async " + CompletableFuture.supplyAsync(() -> reads(w, blk, pig, pl)).get());
            for (String a : axes) rec.text(a);

            // 벤치: Compat.world 를 되풀이해 부르는 묶음(스레드 1개 — 스레드별 변형은 docs/testing/compat-scenes.md)
            int rounds = size.pick(1, 40), per = size.pick(2_000, 2_000_000);
            for (int r = 0; r < rounds; r++) {
                probe.before(r);
                for (int i = 0; i < per; i++) sink = Compat.world(dim);
                probe.after(r);
            }
        }
    }

    /** 한 스레드에서 같은 읽기 묶음 — 각 읽기는 값이거나 "!예외". */
    private static String reads(World w, Block blk, org.bukkit.entity.Entity pig, org.bukkit.entity.Player pl) {
        Block stairs = w.getBlockAt(4, -50, 3);
        return "stone=" + safe(() -> blk.getType()) + " stairs=" + safe(() -> stairs.getBlockData().getAsString())
                + " world-getBlockAt=" + safe(() -> w.getBlockAt(3, -50, 3).getType()) + " pig-loc=" + safe(() -> pig.getLocation().getY())
                + " pig-type=" + safe(() -> pig.getType()) + " player-loc-world=" + safe(() -> pl.getLocation().getWorld().getName()) + " player-world=" + safe(() -> pl.getWorld().getName());
    }
}
