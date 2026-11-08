// 이동·텔레포트 정합(PR: Paper 1.21.1 관측 동작): PlayerMoveEvent 간격(CraftBukkit 규칙)과 Entity#teleport 의 진짜 결과.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.event.player.PlayerMoveEvent;
import org.domi.furnace.api.event.player.PlayerTeleportEvent;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Boat;
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

class MovementParityTest {

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
        p.teleporter(t -> { corrections.add(t); p.clearMoves(); });   // 연결이 하는 일: 보정을 보내고 대기열·hold 를 비운다
        return p;
    }

    // ------------------------------------------------------------------ PlayerMoveEvent 간격 (C7)

    /** 위치 패킷: 같은 칸이지만 위치는 바뀐다. 회전만 바뀐 패킷은 hasPos=false. */
    private static void pos(Player p, double x, float yaw, float pitch) {
        p.pushCheckedMove(new PlayerMoveValidator.Check(true, true, x, -60, 8.5, yaw, pitch), true);
    }

    private static void rotOnly(Player p, float yaw, float pitch) {
        p.pushCheckedMove(new PlayerMoveValidator.Check(false, true, p.x(), p.y(), p.z(), yaw, pitch), true);
    }

    @Test
    void headRotationBeyondTenDegreesFiresMoveEventWithRotationOnlyChange() throws Exception {
        List<Object[]> seen = new ArrayList<>();
        host.eventsImpl().subscribe(plugin, PlayerMoveEvent.class, e -> seen.add(new Object[] {e.hasChangedPosition(), e.hasChangedOrientation(), e.from().yaw(), e.to().yaw(), e.to().pitch()}));
        Player p = player(new ArrayList<>());
        core.world().submitAtChunk(C00, r -> {
            r.adopt(p);
            r.tick();
            p.pushMove(8.5, -60, 8.5, true);   // 기준점
            r.tick();
            rotOnly(p, 8, 0);   // 합 8° — 문턱(10°) 이하
            r.tick();
            rotOnly(p, 25, 5);  // 기준(0,0)에서 합 30° — 부른다
            r.tick();
            return null;
        }).get(30, TimeUnit.SECONDS);
        assertEquals(1, seen.size(), "10° 이하 회전은 부르지 않고, 넘으면 회전만 바뀌어도 한 번 부른다");
        Object[] e = seen.get(0);
        assertEquals(false, e[0], "위치는 그대로");
        assertEquals(true, e[1], "시선이 바뀌었다");
        assertEquals(0f, (float) e[2], 0f, "from 은 마지막으로 알린 시선(문턱 아래로 돌린 8° 는 기준을 옮기지 않는다)");
        assertEquals(25f, (float) e[3], 1e-4f);
        assertEquals(5f, (float) e[4], 1e-4f);
    }

    @Test
    void tinyJitterBelowThresholdIsSilentAndAccumulatesUntilItCrosses() throws Exception {
        List<double[]> seen = new ArrayList<>();
        host.eventsImpl().subscribe(plugin, PlayerMoveEvent.class, e -> seen.add(new double[] {e.from().x(), e.to().x()}));
        Player p = player(new ArrayList<>());
        core.world().submitAtChunk(C00, r -> {
            r.adopt(p);
            r.tick();
            p.pushMove(8.5, -60, 8.5, true);
            r.tick();
            pos(p, 8.53, 0, 0);   // 거리² 0.0009 < 1/256
            r.tick();
            pos(p, 8.56, 0, 0);   // 기준(8.5)에서 거리² 0.0036 < 0.00390625
            r.tick();
            pos(p, 8.6, 0, 0);    // 기준에서 0.01 > 1/256 — 부른다
            r.tick();
            return null;
        }).get(30, TimeUnit.SECONDS);
        assertEquals(1, seen.size(), "문턱 아래 움직임은 부르지 않는다");
        assertEquals(8.5, seen.get(0)[0], 1e-9, "from 은 마지막으로 알린 자리(문턱 아래 움직임은 기준을 옮기지 않는다)");
        assertEquals(8.6, seen.get(0)[1], 1e-9);
    }

    @Test
    void cancelledRotationOnlyEventSendsTheViewBackToTheLastReportedLook() throws Exception {
        List<double[]> corrections = new ArrayList<>();
        int[] fired = {0};
        host.eventsImpl().subscribe(plugin, PlayerMoveEvent.class, e -> { fired[0]++; e.setCancelled(true); });
        Player p = player(corrections);
        Object[] out = core.world().submitAtChunk(C00, r -> {
            r.adopt(p);
            r.tick();
            p.pushMove(8.5, -60, 8.5, true);
            r.tick();
            rotOnly(p, 40, 10);
            r.tick();
            return new Object[] {p.yaw(), p.pitch(), corrections.size(), p.x()};
        }).get(30, TimeUnit.SECONDS);
        assertEquals(1, fired[0]);
        assertEquals(0f, (float) out[0], 1e-4f, "취소하면 서버 시선도 알린 자리의 시선으로");
        assertEquals(0f, (float) out[1], 1e-4f);
        assertEquals(1, (int) out[2], "보정 텔레포트 한 번");
        assertEquals(5, corrections.get(0).length);
        assertEquals(0.0, corrections.get(0)[3], 1e-4, "보정의 시선도 알린 시선");
        assertEquals(8.5, (double) out[3], 1e-9);
    }

    @Test
    void afterATeleportTheNextPacketRebasesInsteadOfFiringForTheJump() throws Exception {
        List<Double> froms = new ArrayList<>();
        host.eventsImpl().subscribe(plugin, PlayerMoveEvent.class, e -> froms.add(e.from().x()));
        Player p = player(new ArrayList<>());
        core.world().submitAtChunk(C00, r -> {
            r.adopt(p);
            r.tick();
            p.pushMove(8.5, -60, 8.5, true);
            r.tick();
            pos(p, 9.0, 0, 0);     // 일반 이동: 부른다(from 8.5)
            r.tick();
            p.pushTeleport(10.5, -60, 8.5);   // 서버가 정한 자리
            r.tick();
            pos(p, 10.6, 0, 0);    // 도착 자리 기준 거리² 0.01 — 부른다, from 은 도착 자리
            r.tick();
            return null;
        }).get(30, TimeUnit.SECONDS);
        assertEquals(List.of(8.5, 10.5), froms, "텔레포트 뒤 from 은 도착 자리다(옛 자리에서 먼 이동으로 보이지 않는다)");
    }

    // ------------------------------------------------------------------ teleport 결과 (D1)

    @Test
    void entityTeleportReportsTheTrueResult() throws Exception {
        Object[] out = core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(8, -50, 8, 0, 0);
            level.addFreshEntity(cow);
            Cow other = new Cow(level, level.nextEntityId());
            other.moveTo(9, -50, 9, 0, 0);
            level.addFreshEntity(other);
            Boat boat = new Boat(level, level.nextEntityId());
            boat.moveTo(6, -50, 6, 0, 0);
            level.addFreshEntity(boat);
            var h = EntityHandle.of(host, cow);
            var to = new Location(OVERWORLD, 4, -50, 4);
            boolean plain = h.teleportNow(to);
            double movedX = cow.x();

            // 승객이 있는 탈것은 옮기지 않는다
            boolean riderStarted = other.startRiding(boat, true);
            var bh = EntityHandle.of(host, boat);
            boolean withPassenger = bh.teleportNow(new Location(OVERWORLD, 3, -50, 3));
            double boatXAfterRefusal = boat.x();

            // 탈것을 탄 쪽은 먼저 내린 뒤 옮긴다
            var oh = EntityHandle.of(host, other);
            boolean rider = oh.teleportNow(new Location(OVERWORLD, 5, -50, 5));
            Object stillRiding = other.vehicle();

            // 다른 차원·사라진 엔티티
            boolean otherDim = h.teleportNow(new Location("minecraft:the_nether", 4, 64, 4));
            cow.discard();
            boolean removed = h.teleportNow(to);
            return new Object[] {plain, movedX, riderStarted, withPassenger, boatXAfterRefusal, rider, stillRiding, other.x(), otherDim, removed};
        }).get(30, TimeUnit.SECONDS);
        assertEquals(true, out[0], "보통 엔티티는 옮기고 true");
        assertEquals(4.0, (double) out[1], 1e-9);
        assertEquals(true, out[2]);
        assertEquals(false, out[3], "승객이 타고 있으면 false");
        assertEquals(6.0, (double) out[4], 1e-9, "거절하면 아무것도 바꾸지 않는다");
        assertEquals(true, out[5], "탄 쪽은 내려서 옮긴다");
        assertNull(out[6], "탈것에서 내렸다");
        assertEquals(5.0, (double) out[7], 1e-9);
        assertEquals(false, out[8], "다른 차원은 false");
        assertEquals(false, out[9], "사라진 엔티티는 false");
    }

    @Test
    void entityTeleportNowRefusesNonOwnerThreadAndAsyncFutureCarriesTheSameResult() throws Exception {
        org.domi.furnace.api.entity.Entity[] handle = new org.domi.furnace.api.entity.Entity[1];
        core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(8, -50, 8, 0, 0);
            level.addFreshEntity(cow);
            handle[0] = EntityHandle.of(host, cow);
            return null;
        }).get(30, TimeUnit.SECONDS);
        assertThrows(org.domi.furnace.api.concurrent.ThreadOwnershipException.class,
                () -> handle[0].teleportNow(new Location(OVERWORLD, 4, -50, 4)), "소유 스레드가 아니면 곧바로 옮길 수 없다");
        assertTrue(handle[0].teleport(new Location(OVERWORLD, 4, -50, 4)).get(10, TimeUnit.SECONDS));
    }

    @Test
    void playerTeleportResultFollowsTheEventAndDismountsFirst() throws Exception {
        List<double[]> corrections = new ArrayList<>();
        List<float[]> eventTo = new ArrayList<>();
        boolean[] cancel = {false};
        host.eventsImpl().subscribe(plugin, PlayerTeleportEvent.class, e -> {
            eventTo.add(new float[] {e.to().yaw(), e.to().pitch()});
            e.setCancelled(cancel[0]);
        });
        Player p = player(corrections);
        p.vitals().env(core.vitalsEnv());   // 실제 접속이 하는 일: 플러그인 이벤트 입구 연결
        var h = host.player(p);
        Object[] out = core.world().submitAtChunk(C00, r -> {
            r.adopt(p);
            r.tick();
            var level = r.level();
            boolean ok = h.teleportNow(new Location(OVERWORLD, 12.5, -60, 8.5, 90, 15));
            int afterOk = corrections.size();
            cancel[0] = true;
            boolean cancelled = h.teleportNow(new Location(OVERWORLD, 20.5, -60, 8.5, 0, 0));
            int afterCancel = corrections.size();
            cancel[0] = false;
            boolean otherDim = h.teleportNow(new Location("minecraft:the_nether", 1, 64, 1));
            // 탈것을 타고 있으면 먼저 내린다
            Boat boat = new Boat(level, level.nextEntityId());
            boat.moveTo(8.5, -60, 8.5, 0, 0);
            level.addFreshEntity(boat);
            boolean mounted = p.startRiding(boat, true);
            boolean fromBoat = h.teleportNow(new Location(OVERWORLD, 14.5, -60, 8.5, 0, 0));
            r.tick();
            return new Object[] {ok, afterOk, cancelled, afterCancel, otherDim, mounted, fromBoat, p.vehicle(), corrections.get(afterCancel + 0 < corrections.size() ? corrections.size() - 1 : 0)[0]};
        }).get(30, TimeUnit.SECONDS);
        assertEquals(true, out[0]);
        assertEquals(1, out[1], "보정 텔레포트 한 번");
        assertEquals(false, out[2], "PlayerTeleportEvent 가 취소되면 false");
        assertEquals(1, out[3], "취소하면 보정도 보내지 않는다");
        assertEquals(false, out[4], "다른 차원은 false");
        assertEquals(true, out[5]);
        assertEquals(true, out[6]);
        assertNull(out[7], "타고 있던 탈것에서 내렸다");
        assertEquals(14.5, (double) out[8], 1e-9, "마지막 보정이 요청한 자리");
        assertEquals(90f, eventTo.get(0)[0], 0f, "이벤트의 도착 시선은 요청한 값");
        assertEquals(15f, eventTo.get(0)[1], 0f);
    }

    @Test
    void disconnectedOrDetachedPlayerTeleportIsFalse() {
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "B", new ConcurrentEmbeddedChannel());
        p.inventory(new PlayerInventory());
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        var h = host.player(p);   // 보정 텔레포트를 보낼 연결이 없다
        assertFalse(h.teleportNow(new Location(OVERWORLD, 1, -60, 1)));
    }

    /** CraftPlayer#teleport: 이벤트가 통과하면 열린 창을 닫는다(취소·실패면 그대로). 창 없이도 문제없다. */
    @Test
    void pluginTeleportClosesOpenWindowOnlyWhenItHappens() throws Exception {
        boolean[] cancel = {false};
        host.eventsImpl().subscribe(plugin, PlayerTeleportEvent.class, e -> e.setCancelled(cancel[0]));
        Player p = player(new ArrayList<>());
        p.vitals().env(core.vitalsEnv());
        var h = host.player(p);
        List<String> closed = new ArrayList<>();
        var open = org.domi.furnace.server.menu.ChestMenu.threeRows(1, new org.domi.furnace.server.menu.SimpleContainer(36), new org.domi.furnace.server.menu.SimpleContainer(27), null, null);
        p.inventoryHost(new Player.InventoryHost() {
            public org.domi.furnace.server.entity.ItemStack slot(int w) { return org.domi.furnace.server.entity.ItemStack.EMPTY; }
            public int selected() { return 0; }
            public void replace(int w, org.domi.furnace.server.entity.ItemStack e, org.domi.furnace.server.entity.ItemStack n) {}
            public int permissionLevel() { return 0; }
            public void execute(Runnable r) { r.run(); }
            public void closeContainer(org.domi.furnace.server.menu.AbstractContainerMenu expect) {
                if (p.containerMenu() == expect) { closed.add("close"); p.setContainerMenu(p.inventoryMenu()); }
            }
        });
        Object[] out = core.world().submitAtChunk(C00, r -> {
            r.adopt(p);
            r.tick();
            p.setContainerMenu(open);
            cancel[0] = true;
            boolean cancelled = h.teleportNow(new Location(OVERWORLD, 20.5, -60, 8.5));
            int afterCancel = closed.size();
            boolean otherDim = h.teleportNow(new Location("minecraft:the_nether", 1, 64, 1));
            int afterDim = closed.size();
            cancel[0] = false;
            boolean ok = h.teleportNow(new Location(OVERWORLD, 12.5, -60, 8.5));
            boolean noWindow = h.teleportNow(new Location(OVERWORLD, 13.5, -60, 8.5));
            return new Object[] {cancelled, afterCancel, otherDim, afterDim, ok, closed.size(), noWindow};
        }).get(30, TimeUnit.SECONDS);
        assertEquals(false, out[0]);
        assertEquals(0, out[1], "취소된 텔레포트는 창을 닫지 않는다");
        assertEquals(false, out[2]);
        assertEquals(0, out[3], "실패한 텔레포트도 닫지 않는다");
        assertEquals(true, out[4]);
        assertEquals(1, out[5], "성공한 텔레포트는 창을 한 번 닫는다");
        assertEquals(true, out[6]);
        assertSame(p.inventoryMenu(), p.containerMenu());
    }
}
