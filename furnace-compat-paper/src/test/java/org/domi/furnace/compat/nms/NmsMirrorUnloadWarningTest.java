package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;

/**
 * N2b-4 D3: 저장 표시 없이 플러그인이 바꾼 섹션을 든 채 청크가 내려가면 경고 한 줄 — 바닐라처럼 그 변경은 저장하지 않는다. 예약 틱이 남은 청크는 저장 표시가
 * 없어도 저장되므로 그 변경도 함께 남고, 경고도 그렇게 말한다(PR #396 검토 R5 — Paper 와 다를 수 있음, 가정). 내리기를 짧게 하고 디스크 월드를 쓰므로 서버를 따로 띄운다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsMirrorUnloadWarningTest {

    FurnaceTestServer server;
    ServerLevel level;
    int cx, cz;
    Logger regionLog;
    ListAppender<ILoggingEvent> logs;

    @BeforeAll
    void start() throws java.io.IOException {
        // 디스크 월드(Anvil) — 내린 청크의 저장 결과를 다시 불러 본다(기본 시험 월드는 저장하지 않는다)
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("furnace-d3-");
        server = FurnaceTestServer.builder().failFast(false).generator((blocks, registries) -> {
            int plains = Math.max(0, registries.indexOf("minecraft:worldgen/biome", "minecraft:plains"));
            return new org.domi.furnace.server.world.gen.AnvilWorldSource(dir, new org.domi.furnace.server.world.anvil.RegionFileStore(dir),
                    new org.domi.furnace.server.world.anvil.AnvilChunkCodec(blocks, plains), new org.domi.furnace.server.world.gen.SuperflatWorldSource(blocks, plains));
        }, -60).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        var tp = server.join("MirrorUnload");
        server.tick(3);
        var loc = tp.player().location();
        cx = Math.floorDiv((int) Math.floor(loc.x()), 16);
        cz = Math.floorDiv((int) Math.floor(loc.z()), 16);
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
        regionLog = (Logger) LoggerFactory.getLogger(Region.class);
        logs = new ListAppender<>();
        logs.start();
        regionLog.addAppender(logs);
    }

    @AfterAll
    void stop() {
        regionLog.detachAppender(logs);
        server.close();
    }

    <T> T on(int chunkX, int chunkZ, Supplier<T> task) {
        return server.onRegion(new org.domi.furnace.api.world.BlockPos(chunkX * 16, 64, chunkZ * 16), task);
    }

    Region region(int chunkX, int chunkZ) { return server.internals().world().regionizer().ownerOf(chunkX, chunkZ); }

    ChunkColumn column(int chunkX, int chunkZ) { return region(chunkX, chunkZ).loadedOwnedChunk(chunkX, chunkZ); }

    int truth(int x, int y, int z) { return on(x >> 4, z >> 4, () -> region(x >> 4, z >> 4).getBlock(x, y, z)); }

    static void eventually(String what, BooleanSupplier cond) {
        long end = System.nanoTime() + 5_000_000_000L;
        while (!cond.getAsBoolean()) {
            if (System.nanoTime() > end) fail(what + " — 틱 없이 5초 안에 안 됨");
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }

    List<String> warningsFor(int chunkX, int chunkZ) {
        String key = "Chunk " + chunkX + ", " + chunkZ + " (";
        synchronized (logs.list) {
            return logs.list.stream().map(ILoggingEvent::getFormattedMessage).filter(m -> m.contains(key) && m.contains("without marking them unsaved")).toList();
        }
    }

    @Test
    void unsavedImportWarnsOnUnloadAndScheduledTicksSaveIt() {
        var world = server.internals().world();
        int ax = cx + 40, az = cz, bx = cx + 44, bz = cz + 3;   // 플레이어 시야 밖(내릴 수 있다)
        int si = 20, y = level.getMinBuildHeight() + si * 16 + 4;
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
        server.loadChunk(ax, az);
        server.loadChunk(bx, bz);
        on(ax, az, () -> {
            column(ax, az).clearDirty();   // 저장된 뒤처럼
            return null;
        });
        on(bx, bz, () -> {
            region(bx, bz).level().scheduleTick(new Pos(bx * 16 + 8, y, bz * 16 + 8), 0, 1_000_000, 0);   // 내릴 때까지 남는 예약 틱
            column(bx, bz).clearDirty();   // 예약을 걸고 자동 저장된 뒤처럼(예약은 그대로 남는다)
            return null;
        });
        // 다른 스레드의 NMS 섹션 쓰기(저장 표시 없음 — 바닐라 LevelChunkSection#setBlockState)
        level.getChunk(ax, az).getSections()[si].setBlockState(1, 4, 1, gold);
        level.getChunk(bx, bz).getSections()[si].setBlockState(1, 4, 1, gold);
        eventually("재생", () -> truth(ax * 16 + 1, y, az * 16 + 1) == gold.furnaceStateId() && truth(bx * 16 + 1, y, bz * 16 + 1) == gold.furnaceStateId());
        assertTrue(on(ax, az, () -> column(ax, az).importedUnsaved() && !column(ax, az).dirty()), "시험 전제: 저장 표시 없는 변경");
        assertTrue(on(bx, bz, () -> column(bx, bz).importedUnsaved() && !column(bx, bz).dirty() && region(bx, bz).level().hasChunkTicks(bx, bz)),
                "시험 전제: 저장 표시 없는 변경 + 예약 틱");

        world.configurePersistence(Integer.MAX_VALUE, 2);
        try {
            for (int i = 0; i < 40 && (world.loadedChunkColumn(ax, az) != null || world.loadedChunkColumn(bx, bz) != null); i++) server.tick(1);
        } finally {
            world.configurePersistence(Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
        assertNull(world.loadedChunkColumn(ax, az), "내려갔다");
        assertNull(world.loadedChunkColumn(bx, bz), "내려갔다");

        List<String> wa = warningsFor(ax, az), wb = warningsFor(bx, bz);
        assertEquals(1, wa.size(), "예약 틱 없는 청크 경고 한 줄: " + wa);
        assertTrue(wa.get(0).contains("that change is not saved") && wa.get(0).contains("setUnsaved(true)"), wa.get(0));
        assertFalse(wa.get(0).contains("scheduled ticks"), wa.get(0));
        assertEquals(1, wb.size(), "예약 틱 남은 청크 경고 한 줄: " + wb);
        assertTrue(wb.get(0).contains("scheduled ticks remain") && wb.get(0).contains("saved with it") && wb.get(0).contains("setUnsaved(true)"), wb.get(0));

        // 다시 불러 저장 결과: 저장 표시 없는 청크는 옛 블록, 예약 틱 때문에 저장된 청크는 그 변경까지
        server.loadChunk(ax, az);
        server.loadChunk(bx, bz);
        assertEquals(0, truth(ax * 16 + 1, y, az * 16 + 1), "저장하지 않았다(바닐라 D3)");
        assertEquals(gold.furnaceStateId(), truth(bx * 16 + 1, y, bz * 16 + 1), "예약 틱 때문에 저장된 청크에는 그 변경도 남는다(경고가 말한 대로)");
    }
}
