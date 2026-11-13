// T-016(O05-01·02) 벤치: 몹마다 매 틱 도는 플레이어 조회(checkDespawn 의 getNearestPlayer(몹, -1) + pushEntities 의 forEachPlayerIn(몸 상자))
// 비용을 세 방식으로 교대로 잰다.
//   legacy  = 옛 World.forEachPlayerHere 모양(서버 전체 접속자 배열 + 차원 문자열 비교, 같은 문자열 인스턴스 = 실서버 꼴) + 옛 조회
//   dim     = 차원별 목록만(격자 없이 — playerIndex null)
//   grid    = 차원별 목록 + 청크 격자 후보(지금 운영 코드)
// 장면(-Dfurnace.bench.scene): spread = 플레이어·몹 모두 ±1000 고르게(O05 원래 장면, 몹 상당수가 모든 플레이어에서 128 밖),
// real = 몹이 아무 플레이어 둘레 100 칸 안(실서버 꼴 — 128 밖 몹은 사라진다), cluster = 모두 원점 ±150.
// 속성: furnace.bench.players(100) / entities(3000) / iters(200) / reps(3).
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.bench.BenchReport;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.world.PlayerRoster;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerScanBench {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final String OVERWORLD = "minecraft:overworld";

    @Test
    void scan() {
        String scene = System.getProperty("furnace.bench.scene", "real");
        int np = Integer.getInteger("furnace.bench.players", 100), ne = Integer.getInteger("furnace.bench.entities", 3000);
        int iters = Integer.getInteger("furnace.bench.iters", 200), reps = Integer.getInteger("furnace.bench.reps", 3);
        java.util.Random r = new java.util.Random(1);
        int spread = scene.equals("cluster") ? 150 : 1000;
        PlayerRoster roster = new PlayerRoster();
        PlayerRoster.Level here = roster.level(OVERWORLD);
        Player[] all = new Player[np];
        List<Player> hereL = new ArrayList<>();
        for (int i = 0; i < np; i++) {
            Player p = new Player(3_000_000 + i, UUID.randomUUID(), "P" + i, new org.domi.furnace.server.InlineEmbeddedChannel());
            p.applyMove(r.nextInt(2 * spread) - spread, -60, r.nextInt(2 * spread) - spread, 0, 0, true);
            p.dimension(i % 4 == 3 ? "minecraft:the_nether" : OVERWORLD);
            all[i] = p;
            roster.put(p);
            if (i % 4 != 3) hereL.add(p);
        }
        boolean[] gridOn = {true};
        // 옛 모양: 서버 전체 접속자 배열 + 차원 문자열 비교(옛 World.forEachPlayerHere), 색인 없음 = 옛 ChunkLevel 조회 그대로
        var legacy = new BlockPlacementTest.TestLevel() {
            final LevelServices services = new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public void forEachPlayer(Consumer<Player> c) {
                    String dim = OVERWORLD;
                    for (Player p : all) if (dim.equals(p.dimension())) c.accept(p);
                }
            };

            @Override protected LevelServices services() { return services; }
        };
        var level = new BlockPlacementTest.TestLevel() {
            final LevelServices services = new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public void forEachPlayer(Consumer<Player> c) { here.forEach(c); }
                @Override public PlayerRoster.Level playerIndex() { return gridOn[0] ? here : null; }
            };

            @Override protected LevelServices services() { return services; }
        };
        List<Zombie> mobs = new ArrayList<>();
        for (int i = 0; i < ne; i++) {
            Zombie z = new Zombie(level, level.nextEntityId());
            double x, zz;
            if (scene.equals("real")) {
                Player near = hereL.get(r.nextInt(hereL.size()));
                x = near.x() + r.nextInt(200) - 100 + 0.5;
                zz = near.z() + r.nextInt(200) - 100 + 0.5;
            } else {
                x = r.nextInt(2 * spread) - spread + 0.5;
                zz = r.nextInt(2 * spread) - spread + 0.5;
            }
            z.moveTo(x, -60, zz, 0, 0);
            mobs.add(z);
        }
        String[] modes = {"legacy", "dim", "grid"};
        long[][] best = new long[3][1];
        for (long[] b : best) b[0] = Long.MAX_VALUE;
        long sink = 0;
        for (int rep = 0; rep < reps * 3 + 3; rep++) {   // 첫 바퀴(3번)는 예열
            int mode = rep % 3;
            gridOn[0] = mode == 2;
            long t0 = System.nanoTime();
            for (int it = 0; it < iters; it++) {
                for (Zombie z : mobs) {
                    var lv = mode == 0 ? legacy : level;
                    int[] c = {0};
                    Player n = lv.getNearestPlayer(z, -1.0);                  // Mob.checkDespawn
                    lv.forEachPlayerIn(z.boundingBox(), p -> c[0]++);         // pushEntities
                    if (n != null) sink += n.entityId();
                    sink += c[0];
                }
            }
            long ns = (System.nanoTime() - t0) / iters;
            if (rep >= 3) best[mode][0] = Math.min(best[mode][0], ns);
            System.out.printf("[bench] %s %-6s P=%d(여기 %d) E=%d  틱당 %.3f ms%n", scene, modes[mode], np, hereL.size(), ne, ns / 1e6);
        }
        System.out.println("sink " + sink);
        BenchReport.of("PlayerScanBench").scene(scene).seed(1L).put("players", np).put("entities", ne)
                .put("legacy_tick_ms", best[0][0] / 1e6).put("dim_tick_ms", best[1][0] / 1e6).put("grid_tick_ms", best[2][0] / 1e6).write();
    }
}
