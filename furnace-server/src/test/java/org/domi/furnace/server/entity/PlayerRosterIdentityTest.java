// T-016: 차원별 플레이어 목록(바닐라 ServerLevel.players 순서)과 청크 격자 근접 색인이 옛 조회와 같은 답을 내는지(IdentityKit 차분).
//
// 오라클 = 바닐라 목록 규칙(접속·차원 이동·리스폰 때 끝에 붙고 떠나면 빠짐)으로 만든 목록 + 옛 ChunkLevel 조회 사본(LegacyPlayerQueries).
// 후보 = PlayerRoster(실제 접속자 집합) + ChunkLevel 의 새 조회(격자 후보). 같은 각본(접속·이동·순간이동·탑승 위치·차원 이동·리스폰·
// 퇴장·다시 접속·조회 섞음)을 양쪽에 돌려 목록 순서와 조회 결과(고른 플레이어 eid, 상자 안 플레이어 순서)를 한 줄씩 비교한다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.identity.Differential;
import org.domi.furnace.server.identity.legacy.players.LegacyPlayerQueries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.PlayerRoster;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerRosterIdentityTest {

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    static final String[] DIMS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
    static final GameMode[] MODES = {GameMode.SURVIVAL, GameMode.SURVIVAL, GameMode.SURVIVAL, GameMode.CREATIVE, GameMode.SPECTATOR, GameMode.ADVENTURE};
    static final double[] DISTS = {-1, -1, -1, 2, 5, 8, 16, 24, 32, 44, 64, 127.5, 128, 129, 200, 600, Double.NaN};
    static final double[] BOXES = {0.6, 0.9, 1.4, 3, 8, 20, 130, 300};
    static final TargetingConditions[] CONDS = {
            TargetingConditions.forNonCombat().range(8.0).ignoreLineOfSight(),
            TargetingConditions.forNonCombat().range(16.0).ignoreLineOfSight().ignoreInvisibilityTesting(),
            TargetingConditions.forCombat().range(35.0).ignoreLineOfSight(),
            TargetingConditions.forCombat().range(128.0).ignoreLineOfSight(),
            TargetingConditions.forNonCombat().range(1.5).ignoreLineOfSight(),
            TargetingConditions.forCombat().range(300.0).ignoreLineOfSight(),
            TargetingConditions.forNonCombat().ignoreLineOfSight(),
    };

    /** 각본: 플레이어 시작 상태 + 단계들. 양쪽이 각자 새 Player 를 만들어 같은 단계를 밟는다. */
    record Scene(int n, int[] dim, int[] mode, boolean[] dead, double[][] pos, int[] joinOrder, List<double[]> steps) {
        String summary() { return "players=" + n + " steps=" + steps.size(); }
    }

    static double coord(SplittableRandom r, double[] centers) {
        return switch (r.nextInt(4)) {
            case 0 -> r.nextDouble(-1500, 1500);                                       // 흩어짐
            case 1 -> centers[r.nextInt(centers.length)] + r.nextDouble(-40, 40);     // 무리
            case 2 -> Math.round(r.nextDouble(-300, 300));                              // 정수 — 같은 거리 동률
            default -> 128.0 * r.nextInt(-6, 7) + r.nextDouble(-3, 3);                  // 격자 칸 경계 근처
        };
    }

    static Scene scene(SplittableRandom r) {
        int n = r.nextInt(1, 90);
        double[] cx = {r.nextDouble(-500, 500), r.nextDouble(-500, 500), 0.0}, cz = {r.nextDouble(-500, 500), r.nextDouble(-500, 500), 0.0};
        int[] dim = new int[n], mode = new int[n];
        boolean[] dead = new boolean[n];
        double[][] pos = new double[n][];
        for (int i = 0; i < n; i++) {
            dim[i] = r.nextInt(10) < 7 ? 0 : 1 + r.nextInt(2);
            mode[i] = r.nextInt(MODES.length);
            dead[i] = r.nextInt(12) == 0;
            pos[i] = new double[] {coord(r, cx), r.nextInt(3) == 0 ? Math.round(r.nextDouble(-60, 120)) : r.nextDouble(-60, 120), coord(r, cz)};
        }
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = r.nextInt(i + 1), t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        List<double[]> steps = new ArrayList<>();
        int m = r.nextInt(20, 90);
        for (int s = 0; s < m; s++) {
            int kind = r.nextInt(14);
            int who = r.nextInt(n);
            double qx = r.nextBoolean() ? pos[r.nextInt(n)][0] + r.nextDouble(-30, 30) : coord(r, cx);
            double qz = r.nextBoolean() ? pos[r.nextInt(n)][2] + r.nextDouble(-30, 30) : coord(r, cz);
            double qy = r.nextDouble(-60, 120);
            if (r.nextInt(3) == 0) { qx = Math.round(qx); qy = Math.round(qy); qz = Math.round(qz); }
            steps.add(new double[] {kind, who, qx, qy, qz, r.nextInt(1 << 20), r.nextDouble(-3, 3), r.nextDouble(-3, 3)});
        }
        return new Scene(n, dim, mode, dead, pos, order, steps);
    }

    static Player newPlayer(Scene s, int i) {
        var p = new Player(1000 + i, new UUID(0, i), "P" + i, new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, MODES[s.mode[i]], false);
        p.applyMove(s.pos[i][0], s.pos[i][1], s.pos[i][2], 0, 0, true);
        if (s.dead[i]) p.vitals().setHealth(0.0f);
        return p;
    }

    /** 한쪽 실행: 목록 관리와 조회. */
    interface Side {
        void join(Player p);
        void leave(Player p);
        void changeDim(Player p, String d);
        void respawn(Player p);
        List<Player> list(String dim);
        // 오버월드 레벨 조회
        Player nearest(double x, double y, double z, double dist, boolean ignoreCreative);
        Player nearestFrom(Cow from, double dist);
        boolean hasAlive(double x, double y, double z, double range);
        void inBox(AABB box, Consumer<Player> c);
        Player nearestCond(TargetingConditions c, Mob source, double x, double y, double z);
        BlockPlacementTest.TestLevel level();
    }

    /** 오라클: 바닐라 목록 규칙 + 옛 조회 사본. reorder 는 변이 점검용(옛 eid 해시 순 흉내). */
    static final class OracleSide implements Side {
        final Map<String, List<Player>> lists = new HashMap<>();
        final Set<Player> online = new HashSet<>();
        final BlockPlacementTest.TestLevel level = new BlockPlacementTest.TestLevel();
        final boolean eidOrder;

        OracleSide(boolean eidOrder) { this.eidOrder = eidOrder; }

        List<Player> l(String d) { return lists.computeIfAbsent(d, k -> new ArrayList<>()); }

        public void join(Player p) {
            if (online.add(p)) l(p.dimension()).add(p);
        }

        public void leave(Player p) {
            if (online.remove(p)) l(p.dimension()).remove(p);
        }

        public void changeDim(Player p, String d) {
            if (d.equals(p.dimension())) return;   // 바닐라 changeDimension 같은 차원 = 순간이동만(목록 그대로)
            if (online.contains(p)) {
                l(p.dimension()).remove(p);
                p.dimension(d);
                l(d).add(p);
            } else {
                p.dimension(d);
            }
        }

        public void respawn(Player p) {
            if (!online.contains(p)) return;
            l(p.dimension()).remove(p);
            l(p.dimension()).add(p);
        }

        public List<Player> list(String dim) {
            List<Player> out = new ArrayList<>(l(dim));
            if (eidOrder) out.sort((a, b) -> Integer.compare(a.entityId(), b.entityId()));
            return out;
        }

        List<Player> here() { return list(DIMS[0]); }

        public Player nearest(double x, double y, double z, double dist, boolean ic) { return LegacyPlayerQueries.getNearestPlayer(here(), x, y, z, dist, ic); }
        public Player nearestFrom(Cow from, double dist) { return LegacyPlayerQueries.getNearestPlayerFrom(here(), from.x(), from.y(), from.z(), dist); }
        public boolean hasAlive(double x, double y, double z, double range) { return LegacyPlayerQueries.hasNearbyAlivePlayer(here(), x, y, z, range); }
        public void inBox(AABB box, Consumer<Player> c) { LegacyPlayerQueries.forEachPlayerIn(here(), box, c); }
        public Player nearestCond(TargetingConditions c, Mob source, double x, double y, double z) {
            return LegacyPlayerQueries.getNearestPlayer(here(), c, source, x, y, z);
        }
        public BlockPlacementTest.TestLevel level() { return level; }
    }

    /** 후보: 실제 접속자 집합(PlayerRoster) + 격자 색인을 쓰는 ChunkLevel 조회. */
    static final class RosterSide implements Side {
        final PlayerRoster roster = new PlayerRoster();
        final PlayerRoster.Level here = roster.level(DIMS[0]);
        final BlockPlacementTest.TestLevel level = new BlockPlacementTest.TestLevel() {
            final LevelServices services = new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public void forEachPlayer(Consumer<Player> c) { here.forEach(c); }
                @Override public PlayerRoster.Level playerIndex() { return here; }
            };

            @Override protected LevelServices services() { return services; }
        };
        final Map<Integer, Player> online = new HashMap<>();

        public void join(Player p) {
            roster.put(p);
            online.put(p.entityId(), p);
            check();
        }

        public void leave(Player p) {
            roster.remove(p.entityId());
            online.remove(p.entityId());
            check();
        }

        public void changeDim(Player p, String d) {
            p.dimension(d);
            check();
        }

        public void respawn(Player p) {
            roster.relist(p);
            check();
        }

        /** 목록에 든 집합 = 옛 정의(접속 중이고 차원 필드가 그 차원). */
        void check() {
            for (String d : DIMS) {
                Set<Player> want = new HashSet<>();
                for (Player p : online.values()) if (d.equals(p.dimension())) want.add(p);
                Set<Player> got = new HashSet<>(List.of(roster.level(d).toArray()));
                if (!want.equals(got)) throw new AssertionError("집합 다름 " + d);
            }
        }

        public List<Player> list(String dim) { return List.of(roster.level(dim).toArray()); }

        public Player nearest(double x, double y, double z, double dist, boolean ic) { return level.getNearestPlayer(x, y, z, dist, ic); }
        public Player nearestFrom(Cow from, double dist) { return level.getNearestPlayer(from, dist); }
        public boolean hasAlive(double x, double y, double z, double range) { return level.hasNearbyAlivePlayer(x, y, z, range); }
        public void inBox(AABB box, Consumer<Player> c) { level.forEachPlayerIn(box, c); }
        public Player nearestCond(TargetingConditions c, Mob source, double x, double y, double z) { return level.getNearestPlayer(c, source, x, y, z); }
        public BlockPlacementTest.TestLevel level() { return level; }
    }

    static String id(Player p) { return p == null ? "-" : Integer.toString(p.entityId()); }

    static String ids(List<Player> l) {
        StringBuilder b = new StringBuilder();
        for (Player p : l) b.append(p.entityId()).append(',');
        return b.toString();
    }

    /** 각본을 한쪽에서 돌려 기록 줄들을 돌려준다. */
    static List<String> run(Scene s, Side side) {
        List<String> out = new ArrayList<>();
        Player[] ps = new Player[s.n];
        for (int i = 0; i < s.n; i++) ps[i] = newPlayer(s, i);
        for (int i : s.joinOrder) {
            ps[i].dimension(DIMS[s.dim[i]]);
            side.join(ps[i]);
        }
        Cow cow = new Cow(side.level(), side.level().nextEntityId());
        cow.moveTo(0, -60, 0, 0, 0);
        side.level().addFreshEntity(cow);
        Set<Player> left = new HashSet<>();
        for (double[] st : s.steps) {
            int kind = (int) st[0];
            Player p = ps[(int) st[1]];
            double qx = st[2], qy = st[3], qz = st[4], ex = st[6], ez = st[7];
            int bits = (int) st[5];
            switch (kind) {
                case 0 -> p.applyMove(p.x() + ex * 10, p.y(), p.z() + ez * 10, 0, 0, true);   // 걸음(칸 경계를 넘기도)
                case 1 -> p.setPosRaw(qx, qy, qz);                                            // 순간이동
                case 2 -> p.setRidingPos(p.x() + ex, p.y() + 0.5, p.z() + ez);                // 탈것이 옮김
                case 3 -> side.changeDim(p, DIMS[bits % 3]);
                case 4 -> side.respawn(p);
                case 5 -> {
                    side.leave(p);
                    left.add(p);
                }
                case 6 -> {
                    if (left.remove(p)) side.join(p);
                }
                default -> {   // 조회
                    double dist = DISTS[bits % DISTS.length];
                    boolean ic = (bits >> 5 & 1) == 1;
                    cow.moveTo(qx, qy, qz, 0, 0);
                    switch ((bits >> 6) % 5) {
                        case 0 -> out.add("near " + id(side.nearest(qx, qy, qz, dist, ic)));
                        case 1 -> out.add("from " + id(side.nearestFrom(cow, dist)));
                        case 2 -> out.add("alive " + side.hasAlive(qx, qy, qz, Double.isNaN(dist) ? 5 : dist));
                        case 3 -> {
                            double h = BOXES[(bits >> 9) % BOXES.length] / 2;
                            List<Player> got = new ArrayList<>();
                            side.inBox(new AABB(qx - h, qy - h, qz - h, qx + h, qy + h, qz + h), got::add);
                            out.add("box " + ids(got));
                        }
                        default -> {
                            TargetingConditions c = CONDS[(bits >> 9) % CONDS.length];
                            out.add("cond " + id(side.nearestCond(c, cow, qx, qy + 1, qz)) + " " + id(side.nearestCond(c, null, qx, qy, qz)));
                        }
                    }
                }
            }
            if (kind <= 6) out.add("list " + ids(side.list(DIMS[0])) + " | " + ids(side.list(DIMS[1])));
        }
        return out;
    }

    /** 처음 다른 줄(번호·양쪽 줄). */
    static String firstDifference(List<String> a, List<String> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            if (!a.get(i).equals(b.get(i))) return "줄 " + i + " 옛=" + a.get(i) + " 새=" + b.get(i) + (i > 0 ? " 앞줄=" + a.get(i - 1) : "");
        }
        return a.size() == b.size() ? null : "줄 수 " + a.size() + " / " + b.size();
    }

    static Differential<Scene, List<String>> differential() {
        return Differential.<Scene, List<String>>of("t016-player-roster", PlayerRosterIdentityTest::scene)
                .seed(0x7016L).iterations(400).describe(Scene::summary).compare(PlayerRosterIdentityTest::firstDifference);
    }

    @Test
    void sameListsAndQueriesAsVanillaOrderedLegacy() {
        differential().check(s -> run(s, new OracleSide(false)), s -> run(s, new RosterSide()));
    }

    /** 변이 점검: 옛 순서(eid 순 — 예전 ConcurrentHashMap 값 순서 흉내)로 돌리면 이 차분이 잡는다(순서가 결과에 드러난다). */
    @Test
    void detectsOldEidOrder() {
        differential().iterations(100).assertDetects(s -> run(s, new OracleSide(false)), s -> run(s, new OracleSide(true)));
    }

    /** 격자 후보는 상자 안 플레이어를 모두 담고 목록 순서를 지킨다(같은 스레드에서 위치를 바꾼 직후에도). */
    @Test
    void nearCandidatesAreSupersetInListOrder() {
        SplittableRandom r = new SplittableRandom(16);
        for (int round = 0; round < 300; round++) {
            PlayerRoster roster = new PlayerRoster();
            PlayerRoster.Level lv = roster.level(DIMS[0]);
            int n = r.nextInt(1, 200);
            List<Player> ps = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                var p = new Player(5000 + i, new UUID(1, i), "Q" + i, new ConcurrentEmbeddedChannel());
                p.applyMove(r.nextDouble(-700, 700), 0, r.nextDouble(-700, 700), 0, 0, true);
                roster.put(p);
                ps.add(p);
            }
            for (int k = 0; k < 40; k++) {
                Player mv = ps.get(r.nextInt(n));
                mv.setPosRaw(r.nextDouble(-700, 700), 0, r.nextDouble(-700, 700));
                double x0 = r.nextDouble(-800, 800), z0 = r.nextDouble(-800, 800);
                double x1 = x0 + r.nextDouble(0, 400), z1 = z0 + r.nextDouble(0, 400);
                List<Player> got = new ArrayList<>();
                lv.forEachNear(x0, z0, x1, z1, got::add);
                List<Player> order = List.of(lv.toArray());
                int last = -1;
                for (Player p : got) {
                    int at = order.indexOf(p);
                    assertTrue(at > last, "목록 순서");
                    last = at;
                }
                for (Player p : order) {
                    if (p.x() >= x0 && p.x() <= x1 && p.z() >= z0 && p.z() <= z1) assertTrue(got.contains(p), "상자 안 플레이어 빠짐");
                }
            }
        }
    }

    /** 바닐라 순서 고정 예: 같은 차원 리스폰은 끝으로, 차원 이동은 떠난 쪽에서 빠지고 닿은 쪽 끝에. */
    @Test
    void vanillaOrderExamples() {
        PlayerRoster roster = new PlayerRoster();
        Player a = new Player(1, new UUID(2, 1), "A", new ConcurrentEmbeddedChannel());
        Player b = new Player(2, new UUID(2, 2), "B", new ConcurrentEmbeddedChannel());
        Player c = new Player(3, new UUID(2, 3), "C", new ConcurrentEmbeddedChannel());
        roster.put(c);
        roster.put(a);
        roster.put(b);
        assertEquals(List.of(c, a, b), List.of(roster.level(DIMS[0]).toArray()), "접속 순");
        roster.relist(c);
        assertEquals(List.of(a, b, c), List.of(roster.level(DIMS[0]).toArray()), "리스폰은 끝으로");
        a.dimension(DIMS[1]);
        assertEquals(List.of(b, c), List.of(roster.level(DIMS[0]).toArray()));
        a.dimension(DIMS[0]);
        assertEquals(List.of(b, c, a), List.of(roster.level(DIMS[0]).toArray()), "돌아오면 끝에");
        roster.put(b);   // 멱등(Region.adopt) — 순서 그대로
        assertEquals(List.of(b, c, a), List.of(roster.level(DIMS[0]).toArray()));
        roster.remove(c.entityId());
        assertEquals(List.of(b, a), List.of(roster.level(DIMS[0]).toArray()));
        assertNull(c.rosterLevel());
        c.dimension(DIMS[1]);   // 접속 안 한 플레이어의 차원 바꾸기는 목록에 영향 없음
        assertTrue(roster.level(DIMS[1]).isEmpty());
    }
}
