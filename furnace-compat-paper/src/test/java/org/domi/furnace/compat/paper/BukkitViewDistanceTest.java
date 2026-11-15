package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-141 E4c: 플레이어별 거리(Paper 1.21.1 Moonrise ViewDistanceHolder·CraftPlayer). 서버 시야 8, 시뮬레이션 6.
 * Paper 공식: tick = min(pTick|sim, pLoad|vd+1), load = max(tick+1, pLoad|vd+1), send ≤ load−1, getViewDistance = load−2.
 */
class BukkitViewDistanceTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { RecorderPlugin.reset(); }

    private FurnaceTestServer start() throws Exception {
        Path jar = Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
        return FurnaceTestServer.builder().failFast(false).viewDistance(8).serverProperty("simulation-distance", "6").pluginJar(jar).start();
    }

    private static org.domi.furnace.server.entity.Player handle(Player p) {
        return ((org.domi.furnace.server.plugin.PlayerHandle) ((org.bukkit.craftbukkit.entity.CraftPlayer) p).furnaceHandle()).handle();
    }

    @Test
    void lowerDistancesFollowPaperFormula() throws Exception {
        try (FurnaceTestServer server = start()) {
            var tp = server.join("Dana");
            server.tick(2);
            Player p = Bukkit.getPlayer("Dana");
            var h = handle(p);
            assertEquals(8, p.getSendViewDistance());
            assertEquals(7, p.getViewDistance(), "Paper 1.21.1 그대로: 불러오는 거리(9) − 2");
            assertEquals(6, p.getSimulationDistance());

            p.setViewDistance(4);   // load 5 → tick min(6,5)=5 → load max(6,5)=6 → 보내기 ≤ 5
            assertEquals(4, p.getViewDistance());
            assertEquals(5, p.getSendViewDistance());
            server.tick(2);
            assertEquals(5, h.trackedViewDistance(), "region 이 다음 틱에 추적 범위를 다시 잡는다");

            p.setSendViewDistance(3);
            assertEquals(3, p.getSendViewDistance());
            server.tick(2);
            assertEquals(3, h.trackedViewDistance());

            server.api().setViewDistance(6);   // 서버 값이 바뀌어도 플레이어 값은 남는다
            assertEquals(3, p.getSendViewDistance());
            p.setSendViewDistance(-1);
            assertEquals(5, p.getSendViewDistance());
            p.setViewDistance(-2);   // Paper: 불러오는 거리 −1 = 정하지 않음
            assertEquals(6, p.getSendViewDistance(), "모두 풀면 서버 값");
            server.tick(2);
            assertEquals(6, h.trackedViewDistance());
            assertEquals(Integer.MAX_VALUE, h.sendCap());
            tp.close();
        }
    }

    @Test
    void raisingAboveServerIsClampedAndSimulationStaysServerWide() throws Exception {
        try (FurnaceTestServer server = start()) {
            server.join("Dana");
            server.tick(2);
            Player p = Bukkit.getPlayer("Dana");
            p.setViewDistance(20);
            assertEquals(8, p.getSendViewDistance(), "서버 시야보다 크게는 아직 못 한다");
            assertEquals(7, p.getViewDistance(), "서버가 불러오는 거리(9)까지");
            p.setViewDistance(-2);
            p.setSimulationDistance(2);   // tick 2 → load max(3, 9) = 9 → 보내기 그대로
            assertEquals(8, p.getSendViewDistance());
            assertEquals(6, p.getSimulationDistance(), "서버 틱 범위는 아직 서버 값");
            p.setSimulationDistance(-1);
            assertEquals(32, p.getClientViewDistance(), "client_information 을 아직 안 보낸 시험 접속자 — 32(Furnace 기본)");
        }
    }

    @Test
    void worldDistancesAreServerWide() throws Exception {
        try (FurnaceTestServer server = start()) {
            World w = Bukkit.getWorlds().get(0);
            assertEquals(8, w.getViewDistance());
            assertEquals(6, w.getSimulationDistance());
            assertEquals(8, w.getSendViewDistance());
            assertEquals("View distance 1 is out of range of [2, 32]", assertThrows(IllegalArgumentException.class, () -> w.setViewDistance(1)).getMessage());
            assertEquals("Simulation distance 33 is out of range of [2, 32]",
                    assertThrows(IllegalArgumentException.class, () -> w.setSimulationDistance(33)).getMessage());
            w.setViewDistance(5);
            w.setSimulationDistance(4);
            assertEquals(5, server.api().viewDistance());
            assertEquals(4, server.api().simulationDistance());
            assertEquals(5, w.getViewDistance());
            assertEquals(4, w.getSimulationDistance());
            assertEquals(4, w.getNoTickViewDistance() - 1);
        }
    }
}
