// 플러그인이 플레이어를 다른 월드(차원)로 텔레포트한다 — 조인 이벤트 안·접속 뒤 모두. Paper 1.21.1 CraftPlayer#teleport 의 다른 월드 가지는
// PlayerList.respawn(플레이어, true, CHANGED_DIMENSION, null, 목적지)이다: 리스폰(속성만 유지) → 청크 반경 → 시뮬레이션 거리 → 위치 → 스폰 지점
// → 난이도 → 경험치 → 효과 → 레벨 정보 → 권한 → 목적 월드에 넣기 → 인벤토리·든 칸 → 능력 → 효과 → 차원 발전 과제 → PlayerChangedWorldEvent.
// 조인 이벤트 안이면 이 순서가 이벤트 중(접속 탭 앞)에 나가고, 그 뒤 접속 순서는 목적 월드에서 이어진다(청크·편입). 실제 소켓으로 본다.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.api.event.player.PlayerChangedWorldEvent;
import org.domi.furnace.api.event.player.PlayerJoinEvent;
import org.domi.furnace.api.event.player.PlayerTeleportEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.ChangeDifficulty;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate;
import org.domi.furnace.server.net.protocol.play.Respawn;
import org.domi.furnace.server.net.protocol.play.SetCenterChunk;
import org.domi.furnace.server.net.protocol.play.SetChunkCacheRadius;
import org.domi.furnace.server.net.protocol.play.SetDefaultSpawnPosition;
import org.domi.furnace.server.net.protocol.play.SetExperience;
import org.domi.furnace.server.net.protocol.play.SetSimulationDistance;
import org.domi.furnace.server.net.protocol.play.SyncPlayerPosition;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.DimensionType;
import org.domi.furnace.server.world.Dimensions;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JoinWorldTeleportTest {

    /** 시험용 네더: 슈퍼플랫 높이를 쓰되 레벨 키는 네더. */
    static final DimensionType TEST_NETHER = new DimensionType("minecraft:the_nether", "minecraft:the_nether", -64, 384, 384,
            false, true, true, false, 8.0, 18000L, false, true, true, NoiseWorldSource.Dimension.NETHER);
    static final String NETHER = "minecraft:the_nether";
    static final String OVERWORLD = "minecraft:overworld";
    private static final double NX = 600.5, NY = -60.0, NZ = -300.5;   // 네더 목적지(스폰과 다른 region)
    private static final int NCX = 37, NCZ = -19;                      // 그 청크

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private World nether;
    private NetworkServer server;
    private int port;
    private final FurnacePlugin plugin = new FurnacePlugin() {};
    private final List<String> changedFrom = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        var io = TestServers.ioPool();
        nether = new World(TEST_NETHER, new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 4, 1, 2, 2, reg.directBits(), 7, io, io);
        nether.worldDir(tmp);
        Dimensions dims = new Dimensions();
        dims.register(core.world());
        dims.register(nether);
        core.dimensions(dims);
        core.world().startTicking();
        nether.startTicking();
        org.domi.furnace.server.TestPluginContexts.init(plugin, core.plugins(), "jwt", tmp);
        plugin.setEnabled(true);
        core.plugins().eventsImpl().subscribe(plugin, PlayerChangedWorldEvent.class, e -> changedFrom.add(e.from()));
        ServerLoginContext login = new ServerLoginContext(false, false, false, true, false, false, true, 20, "t",
                null, null, core.lists(), null, null).core(core);
        server = new NetworkServer(0, -1, login, sched, core.world(), new EntityIdAllocator());
        port = server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        core.plugins().eventsImpl().unregisterAll(plugin);
        if (server != null) server.stop();
        core.plugins().close();
        sched.close();
        TestServers.drainIoPools();
    }

    private void onJoin(Consumer<PlayerJoinEvent> h) {
        core.plugins().eventsImpl().subscribe(plugin, PlayerJoinEvent.class, h);
    }

    private static Location netherDest() { return new Location(NETHER, NX, NY, NZ, 45f, 5f); }

    /** 받은 프레임 하나(패킷 id 와 이 시험이 보는 내용). */
    private record Frame(int id, String dim, int keep, double x, double z, int tpId, int cx, int cz) {
        static Frame of(int id) { return new Frame(id, null, -1, Double.NaN, Double.NaN, -1, Integer.MIN_VALUE, Integer.MIN_VALUE); }
    }

    /** millis 동안 읽는다. 위치 동기화마다 실제 클라이언트처럼 확인(teleport_confirm)을 돌려보낸다. */
    private static List<Frame> read(TestClient c, long millis, java.util.function.Predicate<List<Frame>> until) throws Exception {
        List<Frame> out = new ArrayList<>();
        long deadline = System.nanoTime() + millis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            ByteBuf f = c.poll(50);
            if (f == null) continue;
            int id = Types.readVarInt(f);
            if (id == Respawn.ID) {
                Types.readVarInt(f);
                String dim = Types.readString(f, 256);
                f.skipBytes(8 + 1 + 1 + 1 + 1);
                if (f.readBoolean()) {
                    Types.readString(f, 256);
                    f.skipBytes(8);
                }
                Types.readVarInt(f);
                out.add(new Frame(id, dim, f.readByte(), Double.NaN, Double.NaN, -1, 0, 0));
            } else if (id == SyncPlayerPosition.ID) {
                double x = f.readDouble();
                f.readDouble();
                double z = f.readDouble();
                f.skipBytes(4 * 2 + 1);
                int tpId = Types.readVarInt(f);
                out.add(new Frame(id, null, -1, x, z, tpId, 0, 0));
                c.write(0x00, b -> Types.writeVarInt(b, tpId));
            } else if (id == SetCenterChunk.ID) {
                out.add(new Frame(id, null, -1, Double.NaN, Double.NaN, -1, Types.readVarInt(f), Types.readVarInt(f)));
            } else if (id == LevelChunkWithLight.ID) {
                out.add(new Frame(id, null, -1, Double.NaN, Double.NaN, -1, f.readInt(), f.readInt()));
            } else {
                out.add(Frame.of(id));
            }
            if (until.test(out)) break;
        }
        return out;
    }

    private static boolean hasChunk(List<Frame> fs, int cx, int cz) {
        return fs.stream().anyMatch(f -> f.id() == LevelChunkWithLight.ID && f.cx() == cx && f.cz() == cz);
    }

    private static int indexOf(List<Frame> fs, int id, int from) {
        for (int i = from; i < fs.size(); i++) if (fs.get(i).id() == id) return i;
        return -1;
    }

    private static List<Frame> tps(List<Frame> fs) {
        return fs.stream().filter(f -> f.id() == SyncPlayerPosition.ID).toList();
    }

    /** 서버에서 플레이어가 그 차원 region 에 편입돼 목적지에 있을 때까지. */
    private Player awaitIn(TestClient c, String dim, double x, double z) throws InterruptedException {
        Player p = null;
        for (int i = 0; i < 1000; i++) {
            p = core.playerByUuid(c.uuid);
            World w = core.world(dim);
            var cp = org.domi.furnace.server.world.ChunkPos.ofBlock(x, z);
            if (p != null && dim.equals(p.dimension()) && p.ownerRegion != null && w.regionizer().ownerOf(cp.x(), cp.z()) == p.ownerRegion
                    && p.ownerRegion.players().get(p.entityId()) == p && p.x() == x && p.z() == z) return p;
            Thread.sleep(10);
        }
        fail("플레이어가 " + dim + " (" + x + ", " + z + ") 에 편입되지 않았다: " + (p == null ? null
                : p.dimension() + " " + p.x() + "," + p.z() + " region=" + p.ownerRegion));
        return p;
    }

    /** Paper respawn(위치) 의 패킷 순서: 리스폰(속성만) → 반경 → 시뮬레이션 거리 → 위치 → 스폰 지점 → 난이도 → 경험치. */
    private static int assertPaperWorldChangeOrder(List<Frame> fs, int from) {
        int r = indexOf(fs, Respawn.ID, from);
        assertTrue(r >= 0, "리스폰 패킷이 없다: " + fs);
        assertEquals(NETHER, fs.get(r).dim());
        assertEquals(Respawn.KEEP_ATTRIBUTES, fs.get(r).keep(), "Paper 플러그인 텔레포트는 respawn(keepAll=true) — 속성만 유지(1)");
        int[] expect = {SetChunkCacheRadius.ID, SetSimulationDistance.ID, SyncPlayerPosition.ID, SetDefaultSpawnPosition.ID,
                ChangeDifficulty.ID, SetExperience.ID};
        for (int k = 0; k < expect.length; k++) {
            assertEquals(expect[k], fs.get(r + 1 + k).id(), "리스폰 뒤 " + (k + 1) + "번째 패킷: " + fs.subList(r, Math.min(fs.size(), r + 10)));
        }
        Frame pos = fs.get(r + 3);
        assertEquals(NX, pos.x());
        assertEquals(NZ, pos.z());
        return r;
    }

    /** 조인 이벤트 안에서 다른 월드로: 참을 돌려주고, 이벤트 중(접속 탭 앞)에 리스폰이 나가며, 청크·편입은 목적 월드 목적지에서. */
    @Test
    void joinEventTeleportToAnotherWorld() throws Exception {
        List<String> atJoinAfter = new CopyOnWriteArrayList<>();
        onJoin(e -> {
            assertTrue(e.player().teleportNow(netherDest()), "Paper 처럼 조인 이벤트에서 다른 월드로 보낸다");
            Location l = e.player().location();
            atJoinAfter.add(l.world() + " " + l.x() + " " + l.z());
        });
        try (TestClient c = TestClient.join(port, "Alex")) {
            List<Frame> fs = read(c, 10_000, l -> hasChunk(l, NCX, NCZ));
            assertEquals(List.of(NETHER + " " + NX + " " + NZ), atJoinAfter, "텔레포트 뒤 이벤트 안에서 본 자리는 목적지(Paper: 그 자리에서 옮긴다)");
            int r = assertPaperWorldChangeOrder(fs, 0);
            int tab = indexOf(fs, PlayerInfoUpdate.ID, 0);
            assertTrue(tab > r, "리스폰은 조인 이벤트 중 — 접속 탭보다 앞: respawn=" + r + " tab=" + tab);
            List<Frame> tps = tps(fs);
            assertEquals(2, tps.size(), "스폰(id 1) → 목적지(id 2), 재고정 없음: " + tps);
            assertEquals(1, tps.get(0).tpId());
            assertEquals(2, tps.get(1).tpId());
            assertEquals(NX, tps.get(1).x());
            int center = indexOf(fs, SetCenterChunk.ID, r);
            assertTrue(center > r);
            assertEquals(NCX, fs.get(center).cx(), "청크 중심은 목적지");
            assertEquals(NCZ, fs.get(center).cz());
            assertTrue(hasChunk(fs, NCX, NCZ), "목적지 청크가 온다");
            assertEquals(-1, indexOf(fs, Respawn.ID, r + 1), "리스폰은 한 번");
            awaitIn(c, NETHER, NX, NZ);
            Thread.sleep(600);   // 게이트 대체 해제(300ms)를 넘겨도 그대로
            Player p = awaitIn(c, NETHER, NX, NZ);
            assertTrue(tps(read(c, 300, l -> false)).isEmpty(), "게이트 해제가 스폰으로 되돌리지 않는다");
            assertEquals(List.of(OVERWORLD), changedFrom, "PlayerChangedWorldEvent 한 번(떠나온 월드)");
            assertEquals(NETHER, core.plugins().player(p).location().world());
        }
    }

    /** 접속이 끝난 뒤(region 편입 뒤) 다른 스레드에서 다른 월드로. */
    @Test
    void teleportToAnotherWorldAfterJoin() throws Exception {
        try (TestClient c = TestClient.join(port, "Alex")) {
            read(c, 10_000, l -> l.stream().anyMatch(f -> f.id() == LevelChunkWithLight.ID));
            Player p = core.playerByUuid(c.uuid);
            for (int i = 0; i < 500 && p.ownerRegion == null; i++) Thread.sleep(10);
            assertTrue(core.plugins().player(p).teleportNow(netherDest()));
            List<Frame> fs = read(c, 10_000, l -> hasChunk(l, NCX, NCZ));
            assertPaperWorldChangeOrder(fs, 0);
            assertTrue(hasChunk(fs, NCX, NCZ), "목적지 청크가 온다");
            awaitIn(c, NETHER, NX, NZ);
            for (int i = 0; i < 200 && changedFrom.isEmpty(); i++) Thread.sleep(10);
            assertEquals(List.of(OVERWORLD), changedFrom);
        }
    }

    /** teleport()(비동기판)는 도착한 뒤 참으로 끝난다. */
    @Test
    void asyncTeleportToAnotherWorldCompletesOnArrival() throws Exception {
        try (TestClient c = TestClient.join(port, "Alex")) {
            read(c, 10_000, l -> l.stream().anyMatch(f -> f.id() == LevelChunkWithLight.ID));
            Player p = core.playerByUuid(c.uuid);
            var f = core.plugins().player(p).teleport(netherDest());
            Thread reader = new Thread(() -> { try { read(c, 5_000, l -> false); } catch (Exception ignored) { } });
            reader.start();
            assertTrue(f.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(NETHER, p.dimension(), "끝났을 때 이미 목적 월드");
            Location l = core.plugins().player(p).location();
            assertEquals(NX, l.x(), "끝났을 때 location() 은 목적지");
            assertEquals(NZ, l.z());
            reader.join();
        }
    }

    /** PlayerTeleportEvent 를 취소하면 거짓이고 아무 패킷도 안 나간다. 이벤트의 목적지는 다른 월드다. */
    @Test
    void cancelledTeleportToAnotherWorldDoesNothing() throws Exception {
        List<String> seenTo = new CopyOnWriteArrayList<>();
        core.plugins().eventsImpl().subscribe(plugin, PlayerTeleportEvent.class, e -> {
            seenTo.add(e.from().world() + "->" + e.to().world());
            e.setCancelled(true);
        });
        onJoin(e -> assertFalse(e.player().teleportNow(netherDest())));
        try (TestClient c = TestClient.join(port, "Alex")) {
            List<Frame> fs = read(c, 1_500, l -> false);
            assertEquals(-1, indexOf(fs, Respawn.ID, 0), "취소됐으면 리스폰 없음");
            assertEquals(List.of(OVERWORLD + "->" + NETHER), seenTo);
            assertTrue(changedFrom.isEmpty());
            assertEquals(OVERWORLD, core.playerByUuid(c.uuid).dimension());
        }
    }

    /** 접속 직후 같은 월드 먼 곳(다른 region)으로 — 조인 이벤트 안이든 바로 뒤든 목적지 청크가 클라이언트에 도착한다. */
    @Test
    void farSameWorldDestinationChunksArrive() throws Exception {
        List<String> atJoinAfter = new CopyOnWriteArrayList<>();
        onJoin(e -> {
            e.player().teleportNow(new Location(OVERWORLD, 2000.5, -60, 2000.5, 0f, 0f));
            Location l = e.player().location();
            atJoinAfter.add(l.x() + " " + l.z());
        });
        try (TestClient c = TestClient.join(port, "Alex")) {
            List<Frame> fs = read(c, 10_000, l -> hasChunk(l, 125, 125));
            assertTrue(hasChunk(fs, 125, 125), "조인 이벤트 텔레포트 목적지 청크");
            assertEquals(List.of("2000.5 2000.5"), atJoinAfter, "조인 이벤트 안 같은 월드 텔레포트 뒤 location() 도 곧바로 목적지(Paper)");
            Player p = awaitIn(c, OVERWORLD, 2000.5, 2000.5);
            assertTrue(core.plugins().player(p).teleportNow(new Location(OVERWORLD, -3000.5, -60, 1500.5, 0f, 0f)));
            List<Frame> fs2 = read(c, 10_000, l -> hasChunk(l, -188, 93));
            assertTrue(hasChunk(fs2, -188, 93), "접속 뒤 먼 텔레포트 목적지 청크");
        }
    }
    /** 그 플레이어의 region 스레드를 붙잡는다(풀 때까지 틱이 돌지 않아 걸어 둔 텔레포트가 꺼내지지 않는다). */
    private java.util.concurrent.CountDownLatch holdRegion(Player p) throws InterruptedException {
        var held = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        core.world().runAt(p.x(), p.z(), r -> {
            held.countDown();
            try { release.await(10, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        assertTrue(held.await(5, java.util.concurrent.TimeUnit.SECONDS), "region 스레드를 붙잡지 못했다");
        return release;
    }

    private Player joinedAndAdopted(TestClient c) throws Exception {
        read(c, 10_000, l -> l.stream().anyMatch(f -> f.id() == LevelChunkWithLight.ID));
        Player p = core.playerByUuid(c.uuid);
        for (int i = 0; i < 500 && p.ownerRegion == null; i++) Thread.sleep(10);
        assertNotNull(p.ownerRegion);
        return p;
    }

    /** 걸어 둔 다른 월드 텔레포트는 그 사이 접속이 끊기면 거짓으로 끝난다(Paper: 연결이 없으면 teleport 가 거짓) — 결과가 매달리지 않는다. */
    @Test
    void pendingTeleportToAnotherWorldEndsFalseOnQuit() throws Exception {
        java.util.concurrent.CompletableFuture<Boolean> f;
        java.util.concurrent.CountDownLatch release;
        TestClient c = TestClient.join(port, "Alex");
        try {
            Player p = joinedAndAdopted(c);
            release = holdRegion(p);
            f = core.plugins().player(p).teleport(netherDest());
            assertFalse(f.isDone(), "region 이 꺼내기 전이라 걸려 있다");
        } finally {
            c.close();
        }
        try {
            assertFalse(f.get(5, java.util.concurrent.TimeUnit.SECONDS), "퇴장하면 걸어 둔 텔레포트는 거짓");
        } finally {
            release.countDown();
        }
        Thread.sleep(300);
        assertTrue(changedFrom.isEmpty(), "옮겨지지 않았다");
    }

    /**
     * 같은 틱에 다른 월드 → 같은 월드 차례로 부르면 마지막 자리가 남는다(Paper 는 둘 다 그 자리에서 해 같은 월드 목적지에서 끝난다).
     * 둘 다 참이고, 앞선 다른 월드 이동은 뒤 것에 밀려 리스폰 없이 끝난다.
     */
    @Test
    void laterSameWorldTeleportWinsOverPendingWorldChange() throws Exception {
        try (TestClient c = TestClient.join(port, "Alex")) {
            Player p = joinedAndAdopted(c);
            var h = core.plugins().player(p);
            var release = holdRegion(p);
            java.util.concurrent.CompletableFuture<Boolean> f1, f2;
            try {
                f1 = h.teleport(netherDest());
                f2 = h.teleport(new Location(OVERWORLD, 40.5, -60, 24.5, 0f, 0f));
            } finally {
                release.countDown();
            }
            List<Frame> fs = read(c, 1_500, l -> false);
            assertTrue(f1.get(5, java.util.concurrent.TimeUnit.SECONDS), "앞선 것도 참(Paper 는 둘 다 해낸다)");
            assertTrue(f2.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(-1, indexOf(fs, Respawn.ID, 0), "네더로 가지 않았다: " + fs);
            awaitIn(c, OVERWORLD, 40.5, 24.5);
            assertTrue(changedFrom.isEmpty());
            assertEquals(OVERWORLD, h.location().world());
        }
    }
}
