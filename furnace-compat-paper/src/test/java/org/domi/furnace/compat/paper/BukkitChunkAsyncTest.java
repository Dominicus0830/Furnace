package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.*;

/**
 * T-141 E3: World#getChunkAtAsync 묶음과 refreshChunk. 콜백은 그 청크의 region 스레드에서 돌아 곧바로 블록을 읽을 수 있고(Paper 는 주 스레드),
 * 생성하지 않음이면 저장된 청크가 없을 때 null. 이 시험 서버는 디스크 저장소가 없어 "저장된 청크" 경로는 서버 시험(ChunkAsyncLoadApiTest)이 잰다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitChunkAsyncTest {

    FurnaceTestServer server;
    TestPlayer tp;
    Player player;
    World world;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        tp = server.join("Mira");
        server.tick(3);
        player = Compat.player(tp.player());
        world = Compat.world(server.overworld());
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T await(CompletableFuture<T> f) {
        for (int i = 0; i < 400 && !f.isDone(); i++) server.tick(1);
        try {
            return f.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void callbackRunsOnOwnerWithChunkLoaded() {
        int cx = 300, cz = -300;   // 다른 region, 아직 없는 땅
        assertFalse(world.isChunkLoaded(cx, cz));
        AtomicReference<Material> seen = new AtomicReference<>();
        Chunk c = await(world.getChunkAtAsync(cx, cz).thenApply(chunk -> {
            seen.set(chunk.getBlock(0, -64, 0).getType());   // 소유 스레드가 아니면 여기서 ThreadOwnershipException
            return chunk;
        }));
        assertNotNull(c);
        assertEquals(cx, c.getX());
        assertEquals(cz, c.getZ());
        assertSame(world, c.getWorld());
        assertEquals(Material.BEDROCK, seen.get(), "평지 맨 아래");
        assertTrue(world.isChunkLoaded(cx, cz));
    }

    @Test
    void generateFalseWithoutSavedChunkIsNull() {
        int cx = -400, cz = 400;
        assertNull(await(world.getChunkAtAsync(cx, cz, false)));
        assertFalse(world.isChunkLoaded(cx, cz), "만들지 않는다");
        assertNull(await(world.getChunkAtAsyncUrgently(new org.bukkit.Location(world, cx * 16 + 3, 0, cz * 16 + 3), false)));
        // 없음(false)도 그 청크 region 스레드에서 완료된다
        var fw = server.overworld();
        BlockPos at = new BlockPos(cx * 16, 0, cz * 16);
        boolean onOwner = await(fw.loadChunkAsync(cx, cz, false).thenApply(loaded -> !loaded && Owned.owns(fw.scheduler(at))));
        assertTrue(onOwner);
    }

    @Test
    void consumerVariantAndExceptionIsSwallowed() {
        CompletableFuture<Chunk> got = new CompletableFuture<>();
        world.getChunkAtAsync(500, 500, (java.util.function.Consumer<Chunk>) got::complete);
        assertEquals(500, await(got).getX());
        // 콜백이 던져도 Paper 처럼 경고만 남고 퍼지지 않는다
        CompletableFuture<Boolean> ran = new CompletableFuture<>();
        CompletableFuture<java.util.logging.LogRecord> warned = new CompletableFuture<>();
        var handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord r) {
                if ("Exception in chunk load callback".equals(r.getMessage())) warned.complete(r);
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        Bukkit.getLogger().addHandler(handler);
        try {
            assertDoesNotThrow(() -> world.getChunkAtAsync(501, 500, true, chunk -> { ran.complete(true); throw new IllegalStateException("콜백 실패"); }));
            assertTrue(await(ran));
            var r = await(warned);
            assertEquals(java.util.logging.Level.WARNING, r.getLevel());
            assertEquals("콜백 실패", r.getThrown().getCause() != null ? r.getThrown().getCause().getMessage() : r.getThrown().getMessage());
        } finally {
            Bukkit.getLogger().removeHandler(handler);
        }
    }

    /** Paper CraftChunk.getBlock: 청크 안 좌표 검사 문구, y 는 최고 높이까지 받는다. */
    @Test
    void chunkGetBlockChecksRange() {
        Chunk c = world.getChunkAt(2, 3);
        var b = c.getBlock(15, world.getMaxHeight(), 0);
        assertEquals(2 * 16 + 15, b.getX());
        assertEquals(3 * 16, b.getZ());
        assertEquals("x out of range (expected 0-15, got 16)", assertThrows(IllegalArgumentException.class, () -> c.getBlock(16, 0, 0)).getMessage());
        assertEquals("y out of range (expected " + world.getMinHeight() + "-" + world.getMaxHeight() + ", got " + (world.getMinHeight() - 1) + ")",
                assertThrows(IllegalArgumentException.class, () -> c.getBlock(0, world.getMinHeight() - 1, 0)).getMessage());
        assertEquals("z out of range (expected 0-15, got -1)", assertThrows(IllegalArgumentException.class, () -> c.getBlock(0, 0, -1)).getMessage());
    }

    @Test
    void refreshChunkResendsToViewers() {
        var loc = player.getLocation();
        int cx = loc.getBlockX() >> 4, cz = loc.getBlockZ() >> 4;
        assertFalse(world.refreshChunk(-9000, -9000), "올라와 있지 않으면 false");
        tp.clearReceived();
        assertTrue(world.refreshChunk(cx, cz));
        server.tick(2);
        assertNotNull(tp.awaitPacket(PacketTypes.Clientbound.LEVEL_CHUNK_WITH_LIGHT, p -> true));
    }
}
