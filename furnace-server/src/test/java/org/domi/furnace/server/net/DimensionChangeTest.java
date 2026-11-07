// 차원 두 개(오버월드·네더 World)를 띄운 서버에서 플레이어가 지옥문으로 넘어간다: 목적 region 이 지옥문을 만들고, 연결이 바닐라
// ServerPlayer.changeDimension 순서로 패킷을 보낸 뒤 네더 region 에 편입된다. 도착 좌표는 1/8, 퇴장하면 Dimension 이 저장된다.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.*;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.DimensionType;
import org.domi.furnace.server.world.Dimensions;
import org.domi.furnace.server.world.SpawnPoint;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DimensionChangeTest {

    /** 시험용 네더: 슈퍼플랫 높이를 쓰되 레벨 키·좌표 비·저장 폴더(DIM-1)는 네더. */
    static final DimensionType TEST_NETHER = new DimensionType("minecraft:the_nether", "minecraft:the_nether", -64, 384, 384,
            false, true, true, false, 8.0, 18000L, false, true, true, NoiseWorldSource.Dimension.NETHER);

    @BeforeAll
    static void items() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private World over, nether;
    private EmbeddedChannel ch;
    private ConnectionHandler handler;
    private java.util.concurrent.ExecutorService io;

    private static ByteBuf frame(int id, Consumer<ByteBuf> body) {
        ByteBuf b = Unpooled.buffer();
        Types.writeVarInt(b, id);
        if (body != null) body.accept(b);
        return b;
    }

    private Object field(String name) throws Exception {
        Field f = ConnectionHandler.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(handler);
    }

    @AfterEach
    void close() throws InterruptedException {
        if (sched != null) sched.close();
        if (io != null) {
            io.shutdown();
            io.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    private void start(SpawnPoint spawn) throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        io = org.domi.furnace.server.TestServers.ioPool();
        var pool = new ChunkWorkerPool(1);
        over = new World(new SuperflatWorldSource(reg, 0), pool, sched, 1, 16, 2, 2, reg.directBits(), 7, io);
        Path netherDir = TEST_NETHER.storageDir(tmp);
        var src = new AnvilWorldSource(tmp, new RegionFileStore(netherDir), new AnvilChunkCodec(reg, 0), new SuperflatWorldSource(reg, 0));
        nether = new World(TEST_NETHER, src, pool, sched, 1, 4, 1, 2, 2, reg.directBits(), 7, io, io);
        for (World w : List.of(over, nether)) {
            w.configurePersistence(Integer.MAX_VALUE, 100);
            w.worldDir(tmp);
            w.defaultSpawn(spawn);
        }
        Dimensions dims = new Dimensions();
        dims.register(over);
        dims.register(nether);
        handler = new ConnectionHandler("t", -1, sched, over, new EntityIdAllocator());
        ch = new ConcurrentEmbeddedChannel(handler);
        ch.writeInbound(frame(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost");
            b.writeShort(25565); Types.writeVarInt(b, 2); }));
        ch.writeInbound(frame(0x00, b -> { Types.writeString(b, "Tester"); Types.writeUuid(b, new UUID(0, 0)); }));
        ch.writeInbound(frame(0x03, null));
        ch.writeInbound(frame(0x07, b -> Types.writeVarInt(b, 0)));
        ch.writeInbound(frame(0x03, null));  // → PLAY
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!(boolean) field("spawnChunksDelivered")) {
            ch.runPendingTasks();
            assertTrue(System.nanoTime() < deadline, "스폰 청크 전달이 끝나지 않음");
            Thread.sleep(20);
        }
        ch.writeInbound(frame(0x00, b -> Types.writeVarInt(b, 1)));   // 스폰 텔레포트 confirm
        ch.runPendingTasks();
        drain();
    }

    private List<Integer> drain() {
        ch.runPendingTasks();
        List<Integer> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf bb = (ByteBuf) o;
            out.add(Types.readVarInt(bb));
            bb.release();
        }
        return out;
    }

    /** drain 과 같되 AddEntity·RemoveEntities 의 엔티티 번호를 모은다. */
    private List<Integer> drainIds(List<Integer> added, List<Integer> removed) {
        ch.runPendingTasks();
        List<Integer> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf bb = (ByteBuf) o;
            int id = Types.readVarInt(bb);
            out.add(id);
            if (id == RemoveEntities.ID) { int n = Types.readVarInt(bb); for (int i = 0; i < n; i++) removed.add(Types.readVarInt(bb)); }
            if (id == AddEntity.ID) added.add(Types.readVarInt(bb));
            bb.release();
        }
        return out;
    }

    private static int portalX() { return B.prop("axis").with(B.defaultState("minecraft:nether_portal"), "x"); }

    @Test
    void playerWalksIntoNetherPortal_arrivesAtEighthCoordinates_withVanillaPacketOrder_andIsSaved() throws Exception {
        start(new SpawnPoint(16, -60, 0, 0f));
        Player p = (Player) field("player");
        // 플레이어가 선 칸에 1×2 지옥문(모양 갱신 없이)
        over.submitAtChunk(new ChunkPos(1, 0), r -> {
            r.level().setBlock(new Pos(16, -60, 0), portalX(), 18);
            r.level().setBlock(new Pos(16, -59, 0), portalX(), 18);
            return null;
        }).get();
        drain();
        over.startTicking();
        nether.startTicking();
        List<Integer> out = new ArrayList<>();
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (field("world") != nether || !out.contains(LevelEventPacket.ID)) {
            out.addAll(drain());
            assertTrue(System.nanoTime() < deadline, "네더로 넘어가지 않음: " + out);
            Thread.sleep(10);
        }
        // 바닐라 ServerPlayer.changeDimension 순서
        int[] order = {Respawn.ID, ChangeDifficulty.ID, SyncPlayerPosition.ID, SetCenterChunk.ID, PlayerAbilities.ID, InitializeBorder.ID,
                SetTime.ID, SetDefaultSpawnPosition.ID, GameEvent.ID, TickingState.ID, TickingStep.ID, ContainerSetContent.ID,
                SetHeldSlot.ID, LevelEventPacket.ID};
        int at = out.indexOf(Respawn.ID);
        assertTrue(at >= 0, "리스폰 패킷: " + out);
        for (int id : order) {
            int i = indexFrom(out, id, at);
            assertTrue(i >= at, String.format("0x%02X 가 순서대로 오지 않음: %s", id, hex(out)));
            at = i;
        }
        assertEquals("minecraft:the_nether", p.dimension());
        // 1/8 좌표(16.5 → 2.06 → 블록 2)에 지옥문을 만들고 그 사각형 안의 같은 상대 위치로: (3.0, −59.7, 0.5)
        double x = (double) field("ix"), y = (double) field("iy"), z = (double) field("iz");
        assertEquals(3.0, x, 1e-9);
        assertEquals(-59.7, y, 1e-5);
        assertEquals(0.5, z, 1e-9);
        assertTrue(B.is(nether.submitAtChunk(new ChunkPos(0, 0), r -> r.level().getBlockState(new Pos(2, -60, 0))).get(), "minecraft:nether_portal"));
        assertTrue(B.is(nether.submitAtChunk(new ChunkPos(0, 0), r -> r.level().getBlockState(new Pos(1, -61, 0))).get(), "minecraft:obsidian"));
        // 네더 region 에 편입된다
        deadline = System.nanoTime() + 10_000_000_000L;
        while (nether.submitAtChunk(new ChunkPos(0, 0), r -> r.players().containsKey(p.entityId())).get() != Boolean.TRUE) {
            drain();
            assertTrue(System.nanoTime() < deadline, "네더 region 에 들어가지 않음");
            Thread.sleep(10);
        }
        assertFalse(over.submitAtChunk(new ChunkPos(1, 0), r -> r.players().containsKey(p.entityId())).get(), "오버월드에서는 빠졌다");
        // 저장: 퇴장하면 플레이어 Dimension, 차원 flush 면 DIM-1/region
        ch.close();
        // 퇴장 저장은 ch.close() 안에서 PlayerData 전용 IO 스레드에 제출된다(청크 IO 풀 io 가 아님) — 그 큐를 비운 뒤 읽는다.
        assertTrue(org.domi.furnace.server.entity.PlayerData.awaitIo(30, java.util.concurrent.TimeUnit.SECONDS), "플레이어 데이터 IO 가 끝나지 않음");
        var saved = org.domi.furnace.server.entity.PlayerData.load(tmp, p.uuid());
        assertNotNull(saved);
        assertEquals("minecraft:the_nether", saved.root().getString("Dimension"));
        assertEquals(3.0, saved.x(), 1e-9);
        for (var f : nether.flushAll()) f.get();
        io.submit(() -> { }).get();
        assertTrue(Files.exists(tmp.resolve("DIM-1").resolve("region").resolve("r.0.0.mca")), "네더 청크는 DIM-1/region 에");
    }

    /**
     * M10 미리 올리기: 플레이어가 지옥문에서 기다리는 동안(playersNetherPortalDefaultDelay) 목적 네더 청크(생성 반경 3×3)를 먼저 올려 둔다 —
     * 넘어갈 때는 이미 있는 청크로 바로 도착한다. 넘어가기 전에는 리스폰 패킷이 나가지 않는다.
     */
    @Test
    void playerWaitingInPortal_prewarmsNetherChunksBeforeTravelling() throws Exception {
        start(new SpawnPoint(16, -60, 0, 0f));
        Player p = (Player) field("player");
        over.levelServices(new org.domi.furnace.server.block.LevelServices.Delegating(over.levelServices()) {
            @Override public int gameRuleInt(String name) {
                // 3초 기다림. 시험 플레이어는 ServerCore 없이 접속해 기본 게임 모드가 크리에이티브(무적)라서 바닐라
                // getPlayerTransitionTime 은 playersNetherPortalCreativeDelay(기본 1틱)를 쓴다 — 두 규칙을 다 바꿔야 기다린다.
                return "playersNetherPortalDefaultDelay".equals(name) || "playersNetherPortalCreativeDelay".equals(name) ? 60 : super.gameRuleInt(name);
            }
        });
        over.submitAtChunk(new ChunkPos(1, 0), r -> {
            r.level().setBlock(new Pos(16, -60, 0), portalX(), 18);
            r.level().setBlock(new Pos(16, -59, 0), portalX(), 18);
            return null;
        }).get();
        drain();
        assertEquals(0, nether.loadedChunkCount(), "아직 네더 청크는 없다");
        over.startTicking();
        nether.startTicking();
        List<Integer> out = new ArrayList<>();
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (nether.loadedChunkCount() < 9) {
            out.addAll(drain());
            assertTrue(System.nanoTime() < deadline, "기다리는 동안 목적 청크를 올리지 않음: " + nether.loadedChunkCount());
            Thread.sleep(10);
        }
        out.addAll(drain());
        assertFalse(out.contains(Respawn.ID), "아직 넘어가지 않았다: " + hex(out));
        assertEquals("minecraft:overworld", p.dimension());
        deadline = System.nanoTime() + 20_000_000_000L;
        while (field("world") != nether || !out.contains(LevelEventPacket.ID)) {
            out.addAll(drain());
            assertTrue(System.nanoTime() < deadline, "네더로 넘어가지 않음: " + hex(out));
            Thread.sleep(10);
        }
        assertEquals(3.0, (double) field("ix"), 1e-9, "미리 올린 청크에 같은 자리로 도착");
    }

    /** 이벤트 취소 계약: PlayerPortalEvent 를 취소하면 넘어가지 않는다 — 플레이어는 오버월드에 남고 리스폰 패킷도 나가지 않는다(서버 시작 변화라 되돌릴 화면이 없다). */
    @Test
    void playerPortalCancelKeepsPlayerInOverworld() throws Exception {
        start(new SpawnPoint(16, -60, 0, 0f));
        Player p = (Player) field("player");
        var asked = new java.util.concurrent.atomic.AtomicInteger();
        over.levelServices(new org.domi.furnace.server.block.LevelServices.Delegating(over.levelServices()) {
            @Override public org.domi.furnace.server.plugin.GameEvents gameEvents() {
                return new org.domi.furnace.server.plugin.GameEvents() {
                    @Override public boolean playerPortal(Player pl, String toDimension, int cause) {
                        asked.incrementAndGet();
                        assertEquals("minecraft:the_nether", toDimension);
                        return false;
                    }
                };
            }
        });
        over.submitAtChunk(new ChunkPos(1, 0), r -> {
            r.level().setBlock(new Pos(16, -60, 0), portalX(), 18);
            r.level().setBlock(new Pos(16, -59, 0), portalX(), 18);
            return null;
        }).get();
        drain();
        over.startTicking();
        nether.startTicking();
        List<Integer> out = new ArrayList<>();
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (asked.get() == 0) {
            out.addAll(drain());
            assertTrue(System.nanoTime() < deadline, "PlayerPortalEvent 가 불리지 않음");
            Thread.sleep(10);
        }
        for (int i = 0; i < 20; i++) { Thread.sleep(25); out.addAll(drain()); }   // 취소 뒤에도 넘어가지 않는지 한참 본다
        assertFalse(out.contains(Respawn.ID), "리스폰 패킷이 나갔다: " + hex(out));
        assertEquals("minecraft:overworld", p.dimension());
        assertSame(over, field("world"));
    }

    /**
     * 탈것을 가진 region 스레드에서 뜬 표본: 그 플레이어가 그 region 에 있고 old 가 아닌 살아 있는 수레(그 region 레벨의)에 타 있으면
     * {수레 x, 수레 z, 플레이어 x, 플레이어 z}, 아니면 null.
     */
    private static double[] ridingNewCart(World w, ChunkPos c, Player p, Object old) throws Exception {
        return w.submitAtChunk(c, r -> {
            if (!r.players().containsKey(p.entityId())) return null;
            var v = p.vehicle();
            if (!(v instanceof org.domi.furnace.server.entity.AbstractMinecart) || v == old || v.isRemoved() || !r.level().entities().contains(v)
                    || !v.passengers().contains(p)) return null;
            return new double[] {v.x(), v.z(), p.x(), p.z()};
        }).get();
    }

    /**
     * 탈것째 지옥문(바닐라 Entity.changeDimension: 승객을 먼저 changeDimension 하고 새 탈것에 startRiding(…, true)): 수레에 탄 플레이어가
     * 수레와 함께 네더로 넘어가 건너간 새 수레에 다시 타 있다. 플레이어는 바닐라 ServerPlayer.changeDimension 순서로 리스폰 패킷을 받는다.
     */
    @Test
    void playerRidingMinecartThroughNetherPortal_crossesWithCartAndRidesAgain() throws Exception {
        start(new SpawnPoint(16, -60, 0, 0f));
        Player p = (Player) field("player");
        var oldCart = new org.domi.furnace.server.entity.AbstractMinecart[1];
        over.submitAtChunk(new ChunkPos(1, 0), r -> {
            var cart = org.domi.furnace.server.entity.AbstractMinecart.create(r.level(), 16.5, -60.0, 0.5,
                    org.domi.furnace.server.entity.AbstractMinecart.Type.RIDEABLE);
            r.level().addFreshEntity(cart);
            assertTrue(p.startRiding(cart, true));
            oldCart[0] = cart;
            r.level().setBlock(new Pos(16, -60, 0), portalX(), 18);
            r.level().setBlock(new Pos(16, -59, 0), portalX(), 18);
            return null;
        }).get();
        drain();
        java.util.UUID cartId = oldCart[0].uuid();
        over.startTicking();
        nether.startTicking();
        List<Integer> out = new ArrayList<>();
        double[] at;
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (field("world") != nether || (at = ridingNewCart(nether, new ChunkPos(0, 0), p, oldCart[0])) == null) {
            out.addAll(drain());
            assertTrue(System.nanoTime() < deadline, "수레째 네더로 넘어가 다시 타지 않음: " + hex(out) + " vehicle=" + p.vehicle());
            Thread.sleep(10);
        }
        out.addAll(drain());
        assertTrue(out.contains(Respawn.ID), "승객 플레이어도 차원 이동 패킷을 받는다");
        assertEquals("minecraft:the_nether", p.dimension());
        var cart = p.vehicle();
        assertEquals(cartId, cart.uuid(), "같은 수레(UUID)가 건너갔다");
        assertTrue(oldCart[0].isRemoved());
        assertEquals("minecraft:the_nether", cart.level().dimension());
        assertEquals(3.0, at[0], 1.0, "1/8 좌표의 지옥문으로");
        assertEquals(at[0], at[2], 1e-6, "다시 탈 때 수레 위 자리로 옮긴다(ServerPlayer.startRiding 의 positionRider)");
        assertEquals(at[1], at[3], 1e-6);
        assertFalse(over.submitAtChunk(new ChunkPos(1, 0), r -> r.players().containsKey(p.entityId())).get(), "오버월드에서는 빠졌다");
    }

    /** 탈것째 엔드 관문(같은 레벨 — 바닐라 ServerPlayer.changeDimension 의 같은 차원 쪽은 connection.teleport): 정확 출구로 함께 가서 다시 탄다. */
    @Test
    void playerRidingMinecartThroughGateway_teleportsWithCartAndRidesAgain() throws Exception {
        start(new SpawnPoint(16, -60, 0, 0f));
        Player p = (Player) field("player");
        var oldCart = new org.domi.furnace.server.entity.AbstractMinecart[1];
        over.submitAtChunk(new ChunkPos(1, 0), r -> {
            var cart = org.domi.furnace.server.entity.AbstractMinecart.create(r.level(), 16.5, -60.0, 0.5,
                    org.domi.furnace.server.entity.AbstractMinecart.Type.RIDEABLE);
            r.level().addFreshEntity(cart);
            assertTrue(p.startRiding(cart, true));
            oldCart[0] = cart;
            org.domi.furnace.server.block.entity.EndGatewayBlockEntity.placeGatewayPortal(r.level(), new Pos(16, -60, 0), new Pos(40, -60, 40), true);
            return null;
        }).get();
        drain();
        java.util.UUID cartId = oldCart[0].uuid();
        int oldId = oldCart[0].id();
        List<Integer> added = new ArrayList<>(), removed = new ArrayList<>();
        over.startTicking();
        List<Integer> out = new ArrayList<>();
        double[] at;
        long deadline = System.nanoTime() + 20_000_000_000L;
        while ((at = ridingNewCart(over, new ChunkPos(2, 2), p, oldCart[0])) == null) {
            out.addAll(drainIds(added, removed));
            assertTrue(System.nanoTime() < deadline, "수레째 출구로 가 다시 타지 않음: " + hex(out) + " vehicle=" + p.vehicle());
            Thread.sleep(10);
        }
        out.addAll(drainIds(added, removed));
        assertFalse(out.contains(Respawn.ID), "같은 레벨이라 리스폰 패킷은 없다");
        if (added.contains(oldId)) {
            assertTrue(removed.contains(oldId), "승객 클라이언트도 옛 수레를 지우라고 받는다(유령 수레 없음): added=" + added + " removed=" + removed);
        }
        var cart = p.vehicle();
        assertEquals(cartId, cart.uuid());
        assertTrue(oldCart[0].isRemoved());
        assertEquals(40.5, at[0], 1.0, "정확 출구 칸");
        assertEquals(40.5, at[1], 1.0);
        assertEquals(at[0], at[2], 1e-6, "수레 위 자리");
        assertEquals(at[1], at[3], 1e-6);
        assertEquals(40.5, (double) field("ix"), 1.0, "연결도 출구로 보정 텔레포트했다");
    }

    private static int indexFrom(List<Integer> l, int id, int from) {
        for (int i = from; i < l.size(); i++) if (l.get(i) == id) return i;
        return -1;
    }

    private static String hex(List<Integer> l) {
        StringBuilder b = new StringBuilder();
        for (int i : l) b.append(String.format("%02X ", i));
        return b.toString();
    }
}
