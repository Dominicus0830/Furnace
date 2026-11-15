package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.server.world.ChunkTrackingView;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** (PR #391 독립 검토 시험을 옮김) 낮추기·풀기 청크와 반경 패킷, 서버 값 변경 때 다시 풀기, 접속 이벤트 안 설정, 시뮬레이션 > 시야. */
class BukkitViewDistanceReviewTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { RecorderPlugin.reset(); }

    private FurnaceTestServer start(int vd, int sim) throws Exception {
        Path jar = Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
        return FurnaceTestServer.builder().failFast(false).viewDistance(vd).serverProperty("simulation-distance", String.valueOf(sim)).pluginJar(jar).start();
    }

    private static org.domi.furnace.server.entity.Player handle(Player p) {
        return ((org.domi.furnace.server.plugin.PlayerHandle) ((org.bukkit.craftbukkit.entity.CraftPlayer) p).furnaceHandle()).handle();
    }

    private static int circle(int vd) {
        int n = 0, r = vd + 1;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) if (ChunkTrackingView.contains(0, 0, vd, dx, dz)) n++;
        return n;
    }

    /** Paper 1.21.1 공식(auto-config-send-distance 켬, 플레이어 값 없음)으로 계산한 기대값 — 시뮬레이션 > 시야 설정. */
    @Test
    void simAboveView_valuesVsPaper() throws Exception {
        try (FurnaceTestServer server = start(6, 10)) {
            server.join("Dana");
            server.tick(2);
            Player p = Bukkit.getPlayer("Dana");
            World w = Bukkit.getWorlds().get(0);
            int vd = 6, sim = 10;
            int tick = Math.min(sim, vd + 1), load = Math.max(tick + 1, vd + 1);
            String paper = "W.view=" + (load - 1) + " W.sim=" + tick + " W.send=" + (load - 1) + " P.view=" + (load - 2) + " P.sim=" + tick + " P.send=" + Math.min(load - 1, 32 + 1);
            String furnace = "W.view=" + w.getViewDistance() + " W.sim=" + w.getSimulationDistance() + " W.send=" + w.getSendViewDistance()
                    + " P.view=" + p.getViewDistance() + " P.sim=" + p.getSimulationDistance() + " P.send=" + p.getSendViewDistance();
            System.out.println("[R391-simAbove] paper   " + paper);
            System.out.println("[R391-simAbove] furnace " + furnace);
            assertEquals(w.getSimulationDistance(), p.getSimulationDistance(), "World 와 Player 의 시뮬레이션 거리가 같은 공식인가");
        }
    }

    /** 낮추기 → 보낸 반경 패킷·구독(보낼 집합)·forget·붙잡은 청크(서버 정사각) 유지 → 풀기. */
    @Test
    void lowerThenRelease_chunksAndPackets() throws Exception {
        try (FurnaceTestServer server = start(8, 6)) {
            var tp = server.join("Dana");
            Player p = Bukkit.getPlayer("Dana");
            var h = handle(p);
            for (int i = 0; i < 600 && (h.loadedChunks().size() < circle(8) || !h.pendingChunks().isEmpty()); i++) server.tick(1);
            server.tick(40);
            System.out.println("[R391-lower] before: pending=" + h.pendingChunks().size() + " sent=" + tp.received(PacketTypes.Clientbound.LEVEL_CHUNK_WITH_LIGHT).size());
            assertEquals(circle(8), h.loadedChunks().size(), "처음 시야 8 원");
            int cx = h.chunkPos().x(), cz = h.chunkPos().z();
            tp.clearReceived();

            p.setViewDistance(4);   // Paper: send 5
            var radius = tp.awaitPacket(PacketTypes.Clientbound.SET_CHUNK_CACHE_RADIUS, x -> true);
            System.out.println("[R391-lower] radius " + radius);
            server.tick(3);
            int forgets = tp.received(PacketTypes.Clientbound.FORGET_LEVEL_CHUNK).size();
            System.out.println("[R391-lower] subscribed=" + h.loadedChunks().size() + " forgets=" + forgets + " circle5=" + circle(5) + " circle8=" + circle(8));
            assertEquals(circle(5), h.loadedChunks().size());
            assertEquals(circle(8) - circle(5), forgets, "원 밖이 된 청크를 모두 forget");
            // 서버 정사각(8) 안의 청크는 그대로 붙잡는다(플레이어 티켓)
            server.tick(200);
            World w = Bukkit.getWorlds().get(0);
            assertTrue(w.isChunkLoaded(cx + 7, cz), "(7,0) 은 보내지 않아도 서버 시야 정사각 안 — 남아 있어야");
            assertTrue(w.isChunkLoaded(cx + 8, cz + 8), "정사각 모서리");
            tp.clearReceived();

            p.setViewDistance(-2);
            var r2 = tp.awaitPacket(PacketTypes.Clientbound.SET_CHUNK_CACHE_RADIUS, x -> true);
            System.out.println("[R391-release] radius " + r2);
            for (int i = 0; i < 600 && (h.loadedChunks().size() < circle(8) || !h.pendingChunks().isEmpty()); i++) server.tick(1);
            server.tick(20);
            int sent = tp.received(PacketTypes.Clientbound.LEVEL_CHUNK_WITH_LIGHT).size();
            System.out.println("[R391-release] subscribed=" + h.loadedChunks().size() + " chunksSent=" + sent);
            assertEquals(circle(8), h.loadedChunks().size());
            assertEquals(circle(8) - circle(5), sent, "풀면 원 차이만큼 다시 보낸다");
            assertEquals(0, tp.received(PacketTypes.Clientbound.FORGET_LEVEL_CHUNK).size());
        }
    }

    /** 반복 get→set(Paper 그대로면 값이 줄어든다): 서버 시야 8, 시뮬레이션 6. */
    @Test
    void getSetRoundTrip() throws Exception {
        try (FurnaceTestServer server = start(8, 6)) {
            server.join("Dana");
            server.tick(2);
            Player p = Bukkit.getPlayer("Dana");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                int v = p.getViewDistance();
                p.setViewDistance(v);
                sb.append(v).append("->send ").append(p.getSendViewDistance()).append("; ");
            }
            System.out.println("[R391-roundtrip] " + sb);
        }
    }

    private static int lastRadius(org.domi.furnace.test.TestPlayer tp) {
        var l = tp.received(PacketTypes.Clientbound.SET_CHUNK_CACHE_RADIUS);
        return l.isEmpty() ? -1 : ((Number) l.get(l.size() - 1).get("radius")).intValue();
    }

    /** 서버 시야·시뮬레이션 거리가 바뀔 때 플레이어 값이 있는 사람을 Paper 공식으로 다시 푸는가(값과 보낸 반경 패킷). */
    @Test
    void serverChangesReSolvePlayerValues() throws Exception {
        try (FurnaceTestServer server = start(8, 6)) {
            var tp = server.join("Dana");
            server.tick(2);
            Player p = Bukkit.getPlayer("Dana");
            p.setViewDistance(4);   // tick 5, load 6, send 5
            assertEquals(5, p.getSendViewDistance());
            tp.clearReceived();
            server.api().setSimulationDistance(3);   // tick min(3,5)=3, load max(4,5)=5 → send 4
            assertEquals(4, p.getSendViewDistance(), "서버 시뮬레이션 변경 뒤 다시 풀기");
            assertEquals(4, lastRadius(tp));
            tp.clearReceived();
            server.api().setViewDistance(12);        // 플레이어 load 5 그대로 → send 4
            assertEquals(4, p.getSendViewDistance());
            assertEquals(4, lastRadius(tp), "플러그인 값이 있는 플레이어에게는 서버 값이 아니라 풀린 값");
            p.setViewDistance(-2);
            p.setSimulationDistance(2);              // tick 만: load max(3, 13) = 13 → send 12
            assertEquals(12, p.getSendViewDistance());
            tp.clearReceived();
            server.api().setViewDistance(14);        // 다시 풀지 않으면 옛 상한 12 에 묶인다
            assertEquals(14, p.getSendViewDistance(), "서버 시야를 올리면 tick 만 정한 플레이어도 따라 올라간다");
            assertEquals(14, lastRadius(tp));
        }
    }

    public static final class LoginSetter implements org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler
        public void on(org.bukkit.event.player.PlayerLoginEvent e) { e.getPlayer().setViewDistance(4); }
    }

    private static String order(org.domi.furnace.test.TestPlayer tp) {
        StringBuilder sb = new StringBuilder();
        for (var pk : tp.received()) {
            if (pk.type() == PacketTypes.Clientbound.LOGIN) sb.append("LOGIN ");
            else if (sb.length() < 400 && !sb.toString().contains("RADIUS")) sb.append(pk.type().toString().replaceAll(".*minecraft:", "")).append(' ');
            if (pk.type() == PacketTypes.Clientbound.SET_CHUNK_CACHE_RADIUS) sb.append("RADIUS(").append(((org.domi.furnace.api.packet.GenericPacket) pk).get("radius")).append(") ");
        }
        return sb.toString().trim();
    }

    /** PlayerLoginEvent(Furnace PlayerCreateEvent — 플레이 login 패킷 전)에서 setViewDistance: 반경 패킷이 login 보다 먼저 가는가. */
    @Test
    void setInLoginEvent_packetOrder() throws Exception {
        try (FurnaceTestServer server = start(8, 6)) {
            Bukkit.getPluginManager().registerEvents(new LoginSetter(), Bukkit.getPluginManager().getPlugin("Recorder"));
            var tp = server.join("Dana");
            server.tick(5);
            Player p = Bukkit.getPlayer("Dana");
            String o = order(tp);
            System.out.println("[R391-login] order=" + o + " send=" + p.getSendViewDistance() + " connected=" + tp.isConnected());
            assertFalse(o.startsWith("RADIUS"), "login 전에 플레이 패킷이 가면 클라이언트는 level 이 없어 끊긴다: " + o);
            assertTrue(o.endsWith("RADIUS(5)"), "마지막으로 알린 반경이 실제 보내는 거리여야: " + o);
        }
    }

    /** PlayerJoinEvent 에서 setViewDistance. */
    @Test
    void setInJoinEvent_packetOrder() throws Exception {
        try (FurnaceTestServer server = start(8, 6)) {
            RecorderPlugin.ACTIONS.put("PlayerJoinEvent", e -> ((org.bukkit.event.player.PlayerJoinEvent) e).getPlayer().setViewDistance(4));
            var tp = server.join("Dana");
            server.tick(5);
            Player p = Bukkit.getPlayer("Dana");
            String o = order(tp);
            System.out.println("[R391-join] order=" + o + " send=" + p.getSendViewDistance());
            assertFalse(o.startsWith("RADIUS"), o);
            assertTrue(o.endsWith("RADIUS(5)"), o);
        }
    }
}
