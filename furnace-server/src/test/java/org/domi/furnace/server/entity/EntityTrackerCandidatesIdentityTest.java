// T-017 결과 동일 증명: 플레이어가 움직일 때 엔티티 보임을 다시 보는 길(ChunkLevel.playerMoved)을
//   옛 길 = 레벨의 모든 엔티티를 틱 목록 순서로(playerMovedFullScan — 기준 커밋의 playerMoved 그대로) 와
//   새 길 = (a) 그 플레이어를 보는 엔티티 역색인 ∪ (b) 추적 거리 안 청크 열, 틱 목록 순서로(playerMovedCandidates)
// 에 같은 무작위 장면을 따로 돌려, 플레이어마다 받은 패킷 열(PacketTape, 바이트·순서)과 엔티티마다 보는 사람 목록(순서까지)이
// 걸음마다 같은지 본다. 장면에는 역색인을 흔드는 길을 모두 넣었다: 멀리 순간이동(playerMoved 없이 옮긴 뒤 나중에 판정 포함),
// 시야 줄이기, 엔티티 이동·순간이동(구역 바뀜 → trackEntities), 넣기·지우기, region 사이 넘김(adoptEntity), 합치기·나누기
// (absorb·splitTo), 플레이어 region 넘김(grace)·접속 끊김·차원 이동, 청크 받음 여부 바뀜, 스폰 이벤트 처리기가 엔티티를 옮김.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.identity.Differential;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.PacketTape;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityTrackerCandidatesIdentityTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    /** 장면 입력: 이 씨앗에서 장면 전체(엔티티·플레이어·조작 열)가 결정된다. */
    record Scene(long seed, int entities, int players, int steps, int spread, boolean listener) {
        String summary() {
            return "seed=" + Long.toHexString(seed) + " E=" + entities + " P=" + players + " steps=" + steps + " spread=" + spread + " listener=" + listener;
        }
    }

    /** 보임 다시 보기를 어떤 길로. */
    enum Mode { FULL, CANDIDATES, ADAPTIVE, MUTANT_NEARBY_ONLY, MUTANT_REVERSED }

    private static Scene scene(SplittableRandom r) {
        int spread = r.nextInt(4) == 0 ? 40 : 160 + r.nextInt(400);   // 가끔은 몰림(모두 시야 안)
        return new Scene(r.nextLong(), 40 + r.nextInt(220), 2 + r.nextInt(6), 150 + r.nextInt(150), spread, false);
    }

    /** 절반은 PlayerTrackEntityEvent 처리기(판정 도중 월드를 바꾼다)가 가끔 켜지는 장면. */
    private static Scene sceneWithListener(SplittableRandom r) {
        Scene s = scene(r);
        return new Scene(s.seed, s.entities, s.players, s.steps, s.spread, r.nextBoolean());
    }

    @Test
    void candidatesMatchFullScan() {
        Differential<Scene, List<String>> d = Differential.<Scene, List<String>>of("tracker-candidates", EntityTrackerCandidatesIdentityTest::sceneWithListener)
                .seed(0x7017_0001L).iterations(40).describe(Scene::summary);
        d.check(s -> run(s, Mode.FULL), s -> run(s, Mode.CANDIDATES));
        // 변이 점검: 역색인을 빼거나(가까운 것만) 순서를 뒤집으면 잡혀야 한다
        d.assertDetects(s -> run(s, Mode.FULL), s -> run(s, Mode.MUTANT_NEARBY_ONLY));
        d.assertDetects(s -> run(s, Mode.FULL), s -> run(s, Mode.MUTANT_REVERSED));
    }

    @Test
    void adaptivePlayerMovedMatchesFullScan() {
        Differential.<Scene, List<String>>of("tracker-candidates-adaptive", EntityTrackerCandidatesIdentityTest::sceneWithListener)
                .seed(0x7017_0002L).iterations(20).describe(Scene::summary)
                .check(s -> run(s, Mode.FULL), s -> run(s, Mode.ADAPTIVE));
    }

    /** 장면 하나를 돌려 걸음마다 "플레이어별 새 패킷 해시 + 엔티티별 보는 사람 목록" 줄을 돌려준다. */
    static List<String> run(Scene s, Mode mode) {
        SplittableRandom r = new SplittableRandom(s.seed);
        int[] epoch = {0};
        Sim sim = new Sim(mode, epoch);
        // 플레이어
        List<Player> players = new ArrayList<>();   // 접속 중(조작 대상)
        List<Player> recorded = new ArrayList<>();   // 기록 대상(끊긴 뒤에도 마지막 패킷까지)
        Map<Player, EmbeddedChannel> chans = new IdentityHashMap<>();
        Map<Player, PacketTape> tapes = new IdentityHashMap<>();
        for (int i = 0; i < s.players; i++) {
            EmbeddedChannel ch = new org.domi.furnace.server.InlineEmbeddedChannel();
            Player p = new Player(3_000_000 + i, new UUID(0x7017L, i), "P" + i, ch);
            p.inventory(new PlayerInventory());
            p.applyMove(rc(r, s.spread), -60, rc(r, s.spread), 0, 0, true);
            p.viewDistance(2 + r.nextInt(11));
            players.add(p);
            recorded.add(p);
            chans.put(p, ch);
            tapes.put(p, new PacketTape());
            sim.member(p, r.nextInt(2));
        }
        // 엔티티
        int[] nextId = {10_000};
        for (int i = 0; i < s.entities; i++) sim.spawn(newEntity(sim.levels[r.nextInt(2)], nextId[0]++, r, s.spread));
        List<String> out = new ArrayList<>();
        for (int step = 0; step < s.steps; step++) {
            int op = r.nextInt(100);
            if (op < 30) {   // 플레이어가 조금 걷는다
                Player p = pick(r, players);
                if (p != null) {
                    p.applyMove(p.x() + r.nextDouble(-24, 24), -60, p.z() + r.nextDouble(-24, 24), 0, 0, true);
                    sim.moved(p);
                }
            } else if (op < 36) {   // 멀리 순간이동
                Player p = pick(r, players);
                if (p != null) {
                    p.applyMove(rc(r, s.spread * 2), -60, rc(r, s.spread * 2), 0, 0, true);
                    sim.moved(p);
                }
            } else if (op < 40) {   // 판정 없이 옮김(다음 판정까지 보던 것이 멀리 남는다)
                Player p = pick(r, players);
                if (p != null) p.applyMove(rc(r, s.spread), -60, rc(r, s.spread), 0, 0, true);
            } else if (op < 44) {   // 시야 바꿈(판정은 다음 이동 때)
                Player p = pick(r, players);
                if (p != null) p.viewDistance(2 + r.nextInt(11));
            } else if (op < 62) {   // 엔티티 몇 개가 움직인다(조금 또는 멀리)
                for (int k = r.nextInt(12); k > 0; k--) {
                    Entity e = sim.anyEntity(r);
                    if (e == null) break;
                    if (r.nextInt(5) == 0) e.setPos(rc(r, s.spread), -60, rc(r, s.spread));
                    else e.setPos(e.x() + r.nextDouble(-20, 20), -60, e.z() + r.nextDouble(-20, 20));
                }
                // 넘김 대기 중인 것도 움직이고, 옛 레벨이 보임을 다시 정한다(플러그인 refreshViews 처럼 — 옛 레벨 색인에 붙는다)
                if (!sim.pending.isEmpty() && r.nextInt(3) == 0) {
                    Entity e = sim.pending.get(r.nextInt(sim.pending.size()));
                    e.setPos(e.x() + r.nextDouble(-30, 30), -60, e.z() + r.nextDouble(-30, 30));
                    e.level().refreshViews(e, null, false);
                }
            } else if (op < 72) {   // 틱 끝 추적(구역 바뀐 엔티티 다시 보기·변화 방송·grace)
                sim.track();
            } else if (op < 76) {   // 새 엔티티
                sim.spawn(newEntity(sim.levels[r.nextInt(2)], nextId[0]++, r, s.spread));
            } else if (op < 79) {   // 지움
                Entity e = sim.anyEntity(r);
                if (e != null) e.discard();
            } else if (op < 81) {   // 다른 region 으로 넘김(추적 상태째, 바로)
                Entity e = sim.anyEntity(r);
                if (e != null) sim.handOff(e);
            } else if (op < 83) {   // 넘김 시작: 옛 저장소에서 빼고 새 region 이 받기 전(어느 저장소에도 없음, level = 옛 레벨)
                Entity e = sim.anyEntity(r);
                if (e != null) sim.beginHandOff(e);
            } else if (op < 84) {   // 기다리던 넘김을 받는다
                sim.finishHandOffs();
            } else if (op < 87) {   // 플레이어가 다른 region 으로(옛 region 엔티티는 grace 동안 계속 본다)
                Player p = pick(r, players);
                if (p != null) {
                    sim.member(p, 1 - sim.memberOf(p));
                    sim.moved(p);
                }
            } else if (op < 89) {   // region 합치기 → 나누기
                sim.mergeAndSplit(r.nextInt(40) - 20);
            } else if (op < 92) {   // 청크 받음 여부가 바뀜
                epoch[0]++;
                // 처리기 켜고 끄기(그 장면만): 켠 동안 판정 밖 길로 바뀐 seenBy 가 역색인에 남는지 — 끈 뒤 자동 길이 후보 길을 쓴다
                if (s.listener) sim.trackListener = !sim.trackListener;
            } else if (op < 93) {   // 차원 이동(패킷 없이 잊음) — 같은 자리로 돌아온다
                Player p = pick(r, players);
                if (p != null) {
                    sim.levels[sim.memberOf(p)].playerChangedDimension(p);
                    sim.moved(p);
                }
            } else if (op < 94 && players.size() > 1) {   // 접속 끊김
                Player p = pick(r, players);
                if (p != null) {
                    p.markDisconnected();
                    sim.levels[sim.memberOf(p)].playerLeft(p);
                    sim.leave(p);
                    players.remove(p);
                }
            } else {   // 모두 한 번씩 판정(바닐라: 이동 패킷 받은 플레이어마다)
                for (Player p : players) sim.moved(p);
            }
            // 걸음마다 기록
            IdentityHash h = new IdentityHash();
            StringBuilder line = new StringBuilder("step ").append(step).append(" op ").append(op);
            for (Player p : recorded) {
                PacketTape t = tapes.get(p);
                t.clear();
                t.drain(chans.get(p), PacketTape.Wire.PLAIN);
                line.append(" p").append(p.entityId() - 3_000_000).append('=').append(t.size()).append(':').append(Long.toHexString(t.hash()));
            }
            for (ChunkLevel lv : sim.levels) {
                for (Entity e : lv.entities().all()) {
                    if (e.tracker == null) continue;
                    h.add(e.id());
                    for (Player v : e.tracker.viewersSnapshot()) h.add(v.entityId());
                    h.add(-1);
                }
                h.add(-2);
            }
            out.add(line.append(" seen=").append(Long.toHexString(h.value())).toString());
        }
        for (EmbeddedChannel ch : chans.values()) ch.finishAndReleaseAll();
        return out;
    }

    private static double rc(SplittableRandom r, int spread) { return r.nextDouble(-spread, spread); }

    private static Player pick(SplittableRandom r, List<Player> ps) { return ps.isEmpty() ? null : ps.get(r.nextInt(ps.size())); }

    private static Entity newEntity(ChunkLevel lv, int id, SplittableRandom r, int spread) {
        Entity e = switch (r.nextInt(3)) {
            case 0 -> new Zombie(lv, id);    // 추적 8 청크
            case 1 -> new Pig(lv, id);       // 10 청크
            default -> new ItemEntity(lv, id, 0, -60, 0, new ItemStack(ItemContext.id("minecraft:item", "minecraft:stone"), 1), 0, 0, 0);   // 6 청크
        };
        e.setUuid(new UUID(0x7017_0E00L, id));
        e.moveTo(rc(r, spread), -60, rc(r, spread), 0, 0);
        if (e instanceof LivingEntity le) le.setYHeadRot(0);
        return e;
    }

    /** region 둘(레벨 둘)을 흉내 낸다: 플레이어 소속, 넘김, 합치기·나누기. */
    static final class Sim {
        final Mode mode;
        final int[] epoch;
        final Level[] levels = new Level[2];
        final Map<Player, Integer> membership = new IdentityHashMap<>();
        /** 켜져 있으면 PlayerTrackEntityEvent 처리기가 있는 서버(자동 길은 전체 훑기로 간다). */
        boolean trackListener;
        final List<Player> order = new ArrayList<>();   // 소속 목록 순서(넣은 순)

        Sim(Mode mode, int[] epoch) {
            this.mode = mode;
            this.epoch = epoch;
            levels[0] = new Level(this, 0);
            levels[1] = new Level(this, 1);
        }

        void member(Player p, int lv) {
            if (!membership.containsKey(p)) order.add(p);
            membership.put(p, lv);
        }

        int memberOf(Player p) { return membership.get(p); }

        void leave(Player p) {
            membership.remove(p);
            order.remove(p);
        }

        List<Player> playersOf(int lv) {
            List<Player> out = new ArrayList<>();
            for (Player p : order) if (membership.get(p) == lv) out.add(p);
            return out;
        }

        void spawn(Entity e) { ((Level) e.level()).addFreshEntity(e); }

        Entity anyEntity(SplittableRandom r) {
            Level lv = levels[r.nextInt(2)];
            int n = lv.entities().size();
            if (n == 0) return null;
            int k = r.nextInt(n);
            for (Entity e : lv.entities().all()) if (k-- == 0) return e;
            return null;
        }

        void moved(Player p) {
            Level lv = levels[memberOf(p)];
            switch (mode) {
                case FULL -> lv.playerMovedFullScan(p);
                case CANDIDATES -> assertTrue(lv.playerMovedCandidates(p), "후보 길이 전체 훑기로 빠짐");
                case ADAPTIVE -> lv.playerMoved(p);
                case MUTANT_NEARBY_ONLY, MUTANT_REVERSED -> mutant(lv, p, mode);
            }
        }

        /** 일부러 틀린 길(변이 점검용): 역색인 없이 가까운 것만, 또는 순서를 뒤집어. */
        private static void mutant(Level lv, Player p, Mode mode) {
            List<Entity> c = new ArrayList<>();
            if (mode == Mode.MUTANT_REVERSED) {
                for (Entity e : lv.entities().all()) c.add(e);
                java.util.Collections.reverse(c);
            } else {
                int reach = Math.max(2, p.viewDistance()) * 16 + 1;
                lv.entities().forEachInColumns(Entity.floor(p.x() - reach) >> 4, Entity.floor(p.x() + reach) >> 4,
                        Entity.floor(p.z() - reach) >> 4, Entity.floor(p.z() + reach) >> 4, c::add);
                Entity[] buf = c.toArray(new Entity[0]);
                int n = lv.entities().orderAndDedupe(buf, buf.length);
                c = new ArrayList<>(List.of(buf).subList(0, n));
            }
            for (Entity e : c) if (e.tracker != null && !e.isRemoved()) e.tracker.updatePlayer(p, lv.viewers());
        }

        void track() {
            for (int i = 0; i < 2; i++) {
                final int lvIdx = i;
                levels[i].trackEntities(k -> true, p -> membership.get(p) != null && membership.get(p) == lvIdx);
            }
        }

        /** 엔티티를 다른 region 으로(handOffEntities 처럼: 옛 저장소에서 빼고 새 레벨이 adoptEntity). */
        void handOff(Entity e) {
            Level from = (Level) e.level(), to = levels[1 - from.index];
            if (!from.entities().contains(e)) return;
            from.entities().remove(e);
            to.adoptEntity(e);
        }

        /** 넘김 대기(handOffEntities 의 remove 와 runAtChunk 사이). */
        final List<Entity> pending = new ArrayList<>();

        void beginHandOff(Entity e) {
            Level from = (Level) e.level();
            if (!from.entities().contains(e)) return;
            from.entities().remove(e);
            pending.add(e);
        }

        void finishHandOffs() {
            for (Entity e : pending) {
                if (e.isRemoved()) continue;
                Level from = (Level) e.level();
                levels[1 - from.index].adoptEntity(e);
            }
            pending.clear();
        }

        /** 합치기(1 → 0) 뒤 x 청크가 split 보다 작은 쪽을 새 레벨 1 로 나눈다(플레이어도 그 기준으로). */
        void mergeAndSplit(int split) {
            finishHandOffs();
            levels[0].absorb(levels[1]);
            for (Player p : order) membership.put(p, 0);
            Level fresh = new Level(this, 1);
            levels[1] = fresh;
            levels[0].splitTo(fresh, (cx, cz) -> cx < split);
            for (Player p : order) if (Entity.floor(p.x()) >> 4 < split) membership.put(p, 1);
        }
    }

    /** 시험 레벨: 소속 플레이어만 추적, 청크 받음 여부는 (플레이어, 청크, epoch) 의 결정적 함수, 스폰 이벤트가 가끔 엔티티를 옮긴다. */
    static final class Level extends BlockPlacementTest.TestLevel {
        final Sim sim;
        int index;
        private final LevelServices svc;

        Level(Sim sim, int index) {
            this.sim = sim;
            this.index = index;
            org.domi.furnace.server.plugin.GameEvents events = new org.domi.furnace.server.plugin.GameEvents() {
                /** 플러그인 EntitySpawnEvent 처리기 흉내: id 가 7 의 배수인 엔티티를 멀리(다른 청크 열로) 옮긴다. */
                @Override
                public boolean entitySpawn(Entity e) {
                    if (e.id() % 7 == 0) e.setPos(e.x() + 97.0, e.y(), e.z() - 61.0);
                    return true;
                }

                /**
                 * PlayerTrackEntityEvent 처리기 흉내(켜져 있을 때만): 판정 도중 월드를 바꾼다 — 다음 id 엔티티를 보는 사람 곁으로 옮기고,
                 * 반은 그 엔티티의 보임을 바로 다시 정한다(refreshViews — 판정 밖 길로 seenBy 가 바뀜). 나머지는 같은 판정이 뒤에서
                 * 그 엔티티를 만나야 한다(옛 길은 틱 목록 순서로 계속 도니까 — 후보 길은 변경을 알아채고 나머지를 전체로 돈다).
                 */
                @Override
                public void playerTrackEntity(Player viewer, Entity e) {
                    if (!sim.trackListener || e.id() % 3 != 0) return;
                    Entity next = Level.this.entities().get(e.id() + 1);
                    if (next == null || next.isRemoved()) return;
                    next.setPos(viewer.x() + 3.0, next.y(), viewer.z() - 2.0);
                    if (e.id() % 2 == 0) Level.this.refreshViews(next, viewer, false);   // 반은 바로 다시 정하고, 반은 판정이 뒤에서 만나게 둔다
                }
            };
            this.svc = new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override
                public org.domi.furnace.server.plugin.GameEvents gameEvents() { return events; }
            };
        }

        @Override
        protected Iterable<Player> trackingPlayers() { return sim.playersOf(sim.levels[0] == this ? 0 : 1); }

        @Override
        protected boolean chunkTrackedBy(Player p, int cx, int cz) {
            long h = (long) p.entityId() * 0x9E3779B97F4A7C15L ^ (long) cx * 0xC2B2AE3D27D4EB4FL ^ (long) cz * 0x165667B19E3779F9L ^ sim.epoch[0];
            return (h >>> 61) != 0;   // 약 1/8 은 아직 안 받은 청크
        }

        @Override
        protected LevelServices services() { return svc; }
    }
}
