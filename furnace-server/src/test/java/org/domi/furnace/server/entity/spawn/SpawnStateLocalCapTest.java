// 지역 상한(LocalMobCapCalculator) 판정이 예전 목록 기반 구현과 같은 결과인지 — 할당 줄이기(perf-c4) 회귀 시험
package org.domi.furnace.server.entity.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.Test;

class SpawnStateLocalCapTest {

    /** 바꾸기 전 구현 그대로(playersNear 목록을 만들어 훑는다). */
    private static final class Reference {
        final List<Player> players;
        final Map<Player, EnumMap<MobCategory, Integer>> playerCounts = new HashMap<>();
        Reference(List<Player> players) { this.players = players; }
        List<Player> playersNear(int cx, int cz) {
            List<Player> out = new ArrayList<>();
            double mx = cx * 16 + 8, mz = cz * 16 + 8;
            for (Player p : players) {
                if (p.gameMode() == GameMode.SPECTATOR) continue;
                double dx = p.x() - mx, dz = p.z() - mz;
                if (dx * dx + dz * dz < 128.0 * 128.0) out.add(p);   // 바닐라 playerIsCloseEnoughForSpawning: <
            }
            return out;
        }
        void add(int cx, int cz, MobCategory cat) {
            for (Player p : playersNear(cx, cz)) playerCounts.computeIfAbsent(p, k -> new EnumMap<>(MobCategory.class)).merge(cat, 1, Integer::sum);
        }
        boolean local(MobCategory cat, int cx, int cz) {
            for (Player p : playersNear(cx, cz)) {
                var m = playerCounts.get(p);
                if (m == null || m.getOrDefault(cat, 0) < cat.max()) return true;
            }
            return false;
        }
    }

    @Test
    void localCaps_matchListBasedReference() {
        Random rnd = new Random(42);
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Player p = new Player(i + 1, UUID.randomUUID(), "P" + i, new ConcurrentEmbeddedChannel());
            p.publishPosition(rnd.nextInt(400) - 200 + 0.5, -60, rnd.nextInt(400) - 200 + 0.5, 0, 0, true);
            p.drainPending();
            if (i % 5 == 0) p.vitals().loadGameTypes(null, GameMode.SPECTATOR, true);
            players.add(p);
        }
        // 전체 상한은 걸리지 않게 청크 수를 크게 — 지역 상한만 비교한다
        NaturalSpawner.SpawnState s = new NaturalSpawner.SpawnState(1_000_000, players);
        Reference ref = new Reference(players);
        int checks = 0;
        for (int step = 0; step < 4000; step++) {
            MobCategory cat = MobCategory.values()[rnd.nextInt(MobCategory.values().length)];
            if (cat == MobCategory.MISC) continue;
            int cx = rnd.nextInt(30) - 15, cz = rnd.nextInt(30) - 15;
            assertEquals(ref.local(cat, cx, cz), s.canSpawnForCategory(cat, cx, cz), "step " + step + " " + cat + " " + cx + "," + cz);
            checks++;
            if (rnd.nextInt(3) == 0) { s.addMobToLocalCaps(cx, cz, cat); ref.add(cx, cz, cat); }
        }
        assertEquals(true, checks > 3000);
    }
}
