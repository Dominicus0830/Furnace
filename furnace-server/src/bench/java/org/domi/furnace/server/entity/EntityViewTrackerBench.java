// 추적기 성능(:furnace-server:bench 작업에서만 돈다): 플레이어가 움직일 때마다 도는 보임 판정(ChunkMap.move = playerMoved)과
// 변화 방송(trackEntities → 연결 쓰기)을 엔티티 E × 플레이어 P 로 잰다. K2(보이기·변장 규칙) 전후 비교용 —
// 기준 커밋에서도 컴파일되게 규칙은 리플렉션으로 건다(-Dfurnace.bench.rules=none|unrelated|all|disguise).
//   none      규칙 없음(바닐라 그대로의 빠른 길)
//   unrelated 다른 엔티티 1000개에만 규칙(규칙 저장소는 켜져 있고 이 엔티티들은 조회만)
//   all       모든 엔티티를 기본 숨김 + 모든 플레이어에게 보여 줌(모든 쌍이 규칙을 읽는 최악)
//   disguise  모든 엔티티를 전역 변장(모든 변화 패킷이 연결별 변환을 거침)
package org.domi.furnace.server.entity;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityViewTrackerBench {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @Test
    void trackerCost() throws Exception {
        int e = Integer.getInteger("furnace.bench.entities", 500), np = Integer.getInteger("furnace.bench.players", 20);
        int iters = Integer.getInteger("furnace.bench.iters", 400), reps = Integer.getInteger("furnace.bench.reps", 5);
        String rules = System.getProperty("furnace.bench.rules", "none");
        // spread>0: 몹·플레이어를 ±spread 블록에 흩는다(0 = 옛 몰림 배치). path=full 이면 옛 길(전체 훑기)로 잰다(T-017 A/B)
        int spread = Integer.getInteger("furnace.bench.spread", 0);
        // path=ab: 같은 JVM 에서 회차마다 full·auto 를 번갈아(JVM 사이 잡음 없이 비교)
        String pathProp = System.getProperty("furnace.bench.path", "auto");
        java.util.SplittableRandom rnd = new java.util.SplittableRandom(0x7017L);
        List<Player> players = new ArrayList<>();
        List<EmbeddedChannel> chans = new ArrayList<>();
        for (int i = 0; i < np; i++) {
            EmbeddedChannel ch = new org.domi.furnace.server.InlineEmbeddedChannel();
            Player p = new Player(2_000_000 + i, UUID.randomUUID(), "P" + i, ch);
            p.inventory(new PlayerInventory());
            if (spread > 0) p.applyMove(rnd.nextDouble(-spread, spread), -60, rnd.nextDouble(-spread, spread), 0, 0, true);
            else p.applyMove(i % 5, -60, i / 5, 0, 0, true);
            players.add(p);
            chans.add(ch);
        }
        var level = new BlockPlacementTest.TestLevel() {
            @Override
            protected Iterable<Player> trackingPlayers() { return players; }
        };
        List<Entity> mobs = new ArrayList<>();
        for (int i = 0; i < e; i++) {
            Zombie z = new Zombie(level, level.nextEntityId());
            if (spread > 0) z.moveTo(rnd.nextDouble(-spread, spread), -60, rnd.nextDouble(-spread, spread), 0, 0);
            else z.moveTo((i % 25) - 12 + 0.5, -60, (i / 25) % 25 - 12 + 0.5, 0, 0);
            mobs.add(z);
        }
        installRules(rules, mobs, players);
        for (Entity m : mobs) level.addFreshEntity(m);
        drainAll(chans);
        for (int rep = 0; rep < reps * (pathProp.equals("ab") ? 2 : 1); rep++) {
            boolean fullScan = pathProp.equals("ab") ? (rep & 1) == 0 : pathProp.equals("full");
            long moveNs = 0, sendNs = 0;
            for (int it = 0; it < iters; it++) {
                long t0 = System.nanoTime();
                for (Player p : players) {   // 보임 판정 E × P
                    if (fullScan) level.playerMovedFullScan(p);
                    else level.playerMoved(p);
                }
                long t1 = System.nanoTime();
                for (int i = 0; i < mobs.size(); i++) {
                    Entity m = mobs.get(i);
                    m.setPos(m.x() + ((it & 1) == 0 ? 0.01 : -0.01), m.y(), m.z());
                    if ((i & 7) == 0) m.setSilent((it & 2) == 0);   // 메타데이터 변화도 조금
                }
                level.trackEntities(k -> true, p -> true);       // 변화 방송 → 연결 E × P 패킷
                drainAll(chans);                                  // 이벤트 루프 몫(변장 변환 포함)
                long t2 = System.nanoTime();
                moveNs += t1 - t0;
                sendNs += t2 - t1;
            }
            System.out.printf("[bench] rules=%s path=%s spread=%d E=%d P=%d rep=%d  playerMoved %.1f ns/쌍   방송+쓰기 %.1f ns/쌍%n", rules,
                    fullScan ? "full" : "auto", spread, e, np, rep,
                    (double) moveNs / iters / (e * np), (double) sendNs / iters / (e * np));
        }
        clearRules();
    }

    private static long sink;

    private static void drainAll(List<EmbeddedChannel> chans) {
        for (EmbeddedChannel ch : chans) {
            ch.runPendingTasks();
            Object o;
            while ((o = ch.readOutbound()) != null) {
                sink += ((ByteBuf) o).readableBytes();
                ((ByteBuf) o).release();
            }
        }
    }

    // ---- K2 규칙(리플렉션 — 기준 커밋에는 클래스가 없다)

    private static Class<?> rulesClass() {
        try {
            return Class.forName("org.domi.furnace.server.entity.view.EntityViewRules");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static void installRules(String mode, List<Entity> mobs, List<Player> players) throws Exception {
        if (mode.equals("none")) return;
        Class<?> c = rulesClass();
        if (c == null) throw new IllegalStateException("이 커밋에는 규칙 저장소가 없다 — rules=none 만");
        var hide = c.getMethod("hide", Object.class, Player.class, UUID.class, EntityTasks.class, boolean.class);
        var show = c.getMethod("show", Object.class, Player.class, UUID.class, EntityTasks.class, boolean.class);
        var def = c.getMethod("setHiddenByDefault", Object.class, UUID.class, boolean.class, EntityTasks.class, boolean.class);
        Object owner = "bench";
        switch (mode) {
            case "unrelated" -> {
                for (int i = 0; i < 1000; i++) hide.invoke(null, owner, players.get(i % players.size()), UUID.randomUUID(), null, false);
            }
            case "all" -> {
                for (Entity m : mobs) {
                    def.invoke(null, owner, m.uuid(), true, null, false);
                    for (Player p : players) show.invoke(null, owner, p, m.uuid(), null, false);
                }
            }
            case "disguise" -> {
                Class<?> ac = Class.forName("org.domi.furnace.server.entity.view.Appearance");
                Object creeper = ac.getConstructors()[0].newInstance(EntityType.CREEPER.id(), "minecraft:creeper", 0, new int[0], null, null, null);
                var set = c.getMethod("setAppearance", Object.class, UUID.class, Player.class, ac, EntityTasks.class, boolean.class);
                for (Entity m : mobs) set.invoke(null, owner, m.uuid(), null, creeper, null, false);
            }
            default -> throw new IllegalArgumentException(mode);
        }
    }

    private static void clearRules() throws Exception {
        Class<?> c = rulesClass();
        if (c != null) c.getMethod("clearForTest").invoke(null);
    }
}
