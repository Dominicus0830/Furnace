// 차원 두 개(오버월드·네더 World)를 띄운 서버에서 플레이어가 지옥문으로 넘어간다: 목적 region 이 지옥문을 만들고, 연결이 바닐라
// ServerPlayer.changeDimension 순서로 패킷을 보낸 뒤 네더 region 에 편입된다. 도착 좌표는 1/8, 퇴장하면 Dimension 이 저장된다.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
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
        ch = new EmbeddedChannel(handler);
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
