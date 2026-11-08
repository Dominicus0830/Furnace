// PR #152 독립 검토 반영 회귀 시험: 감긴 yaw 헛 이벤트·텔레포트 속도·죽은 플레이어.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.event.player.PlayerMoveEvent;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.entity.PlayerMoveValidator;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MovementReviewTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);
    private static final String OVERWORLD = "minecraft:overworld";

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private final TestPlugins.Gamma plugin = new TestPlugins.Gamma();

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        plugin.setEnabled(true);
        core.world().submitAtChunk(C00, r -> r.chunkAtBlocking(C00)).get(30, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        host.eventsImpl().unregisterAll(plugin);
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private Player player(List<double[]> corrections) {
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        p.inventory(new PlayerInventory());
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        p.teleporter(t -> { corrections.add(t); p.clearMoves(); });
        return p;
    }

    private static void rotOnly(Player p, float yaw, float pitch) {
        p.pushCheckedMove(new PlayerMoveValidator.Check(false, true, p.x(), p.y(), p.z(), yaw, pitch), true);
    }

    /**
     * R1: 클라이언트 yaw 는 감기지 않은 값(179 → 181)을 보낸다. CraftBukkit 은 to.setYaw(packet.yRot) 로 그 원값끼리 비교하므로
     * 2° 회전은 부르지 않는다. PR 은 감긴 서버 yaw(179 → -179)로 비교해 358° 로 보고 부른다 — 북쪽을 지날 때마다 헛 이벤트.
     */
    @Test
    void crossingNorthByTwoDegreesDoesNotFire() throws Exception {
        List<float[]> seen = new ArrayList<>();
        host.eventsImpl().subscribe(plugin, PlayerMoveEvent.class, e -> seen.add(new float[] {e.from().yaw(), e.to().yaw()}));
        Player p = player(new ArrayList<>());
        core.world().submitAtChunk(C00, r -> {
            r.adopt(p);
            r.tick();
            p.pushMove(8.5, -60, 8.5, true);
            r.tick();
            rotOnly(p, 179, 0);   // 0 → 179: 부른다(정상)
            r.tick();
            rotOnly(p, 181, 0);   // 원값 2° — CraftBukkit 은 안 부른다
            r.tick();
            return null;
        }).get(30, TimeUnit.SECONDS);
        assertEquals(1, seen.size(), "북쪽(±180)을 2° 지난 것은 이벤트가 아니다; 실제 = " + seen.stream().map(a -> a[0] + "->" + a[1]).toList());
        assertEquals(179f, seen.get(0)[1], 1e-4f);
    }

    /** R2: Paper CraftEntity#teleport 는 setYHeadRot(yaw)(SPIGOT-619) 를 하고, Paper moveTo 는 속도를 0 으로 만든다. */
    @Test
    void entityTeleportSyncsHeadYawAndClearsVelocity() throws Exception {
        float[] out = core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(8, -50, 8, 0, 0);
            level.addFreshEntity(cow);
            cow.setYHeadRot(0);
            cow.setDeltaMovement(0.8, 0.4, 0.0);
            boolean ok = EntityHandle.of(host, cow).teleportNow(new Location(OVERWORLD, 4, -50, 4, 90, 0));
            return new float[] {ok ? 1 : 0, cow.yHeadRot(), (float) cow.vx(), (float) cow.vy()};
        }).get(30, TimeUnit.SECONDS);
        assertEquals(1f, out[0]);
        assertEquals(90f, out[1], 1e-4f, "머리 yaw 도 요청한 yaw 로(Paper setYHeadRot)");
        assertEquals(0f, out[2], 1e-6f, "Paper moveTo: 텔레포트하면 속도를 지운다");
        assertEquals(0f, out[3], 1e-6f);
    }

    /** R3: Paper/CraftBukkit CraftPlayer#teleport 는 체력 0(죽은) 플레이어에게 false(기억 기준 — 1.21.1 원문 미확인). */
    @Test
    void deadPlayerPluginTeleportIsFalse() throws Exception {
        List<double[]> corrections = new ArrayList<>();
        Player p = player(corrections);
        p.vitals().env(core.vitalsEnv());
        var h = host.player(p);
        Object[] out = core.world().submitAtChunk(C00, r -> {
            r.adopt(p);
            r.tick();
            p.vitals().setHealth(0.0F);
            boolean ok = h.teleportNow(new Location(OVERWORLD, 12.5, -60, 8.5));
            return new Object[] {ok, corrections.size()};
        }).get(30, TimeUnit.SECONDS);
        assertEquals(false, out[0], "죽은 플레이어 텔레포트는 false");
        assertEquals(0, out[1], "보정 텔레포트도 없다");
    }
}
