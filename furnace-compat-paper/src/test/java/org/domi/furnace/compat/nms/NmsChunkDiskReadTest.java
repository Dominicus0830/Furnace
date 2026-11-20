package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor;
import ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

/**
 * NMS shim 5e: voxy 가 먼 거리 지형을 디스크에서 읽는 두 길 — 바닐라 {@code ChunkMap#read}(설정 background_priority 끔)와 Paper moonrise
 * {@code RegionFileIOThread#loadDataAsync}(기본). 디스크에 저장된 청크 NBT 를 바닐라 꼴 그대로 돌려주는가, 없는 청크는 빈 값인가.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsChunkDiskReadTest {

    @TempDir
    static Path tmp;

    FurnaceTestServer server;
    World world;
    ServerLevel level;
    int cx = 5, cz = -3;
    int bx = cx * 16 + 4, by = 70, bz = cz * 16 + 9;

    @BeforeAll
    void start() throws Exception {
        Path jar = Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
        server = FurnaceTestServer.builder().failFast(false).multiWorld(true).pluginJar(jar).start();
        world = new WorldCreator("lod").type(WorldType.FLAT).createWorld();
        level = ((CraftWorld) world).getHandle();
        var fw = ((CraftWorld) world).furnaceHandle();
        assertTrue(fw.loadChunkAsync(cx, cz, true).get(60, TimeUnit.SECONDS));
        fw.scheduler(new org.domi.furnace.api.world.BlockPos(bx, by, bz)).submit(() -> {
            world.getBlockAt(bx, by, bz).setType(Material.DIAMOND_BLOCK);
            return null;
        }).get(60, TimeUnit.SECONDS);
        var sw = ((org.domi.furnace.server.plugin.WorldHandle) fw).handle();
        for (CompletableFuture<Void> f : sw.flushAll()) f.get(60, TimeUnit.SECONDS);
    }

    @AfterAll
    void stop() { server.close(); }

    /** 저장된 청크 NBT 에 그 칸의 블록이 바닐라 꼴(sections[].block_states.palette)로 들어 있는가. */
    void assertStoredChunk(CompoundTag tag) {
        assertNotNull(tag);
        assertEquals(cx, tag.getInt("xPos"));
        assertEquals(cz, tag.getInt("zPos"));
        assertEquals("minecraft:full", tag.getString("Status"));
        ListTag sections = tag.getList("sections", 10);
        CompoundTag sec = null;
        for (int i = 0; i < sections.size(); i++) if (sections.getCompound(i).getByte("Y") == (by >> 4)) sec = sections.getCompound(i);
        assertNotNull(sec, "그 칸의 섹션");
        ListTag palette = sec.getCompound("block_states").getList("palette", 10);
        boolean found = false;
        for (int i = 0; i < palette.size(); i++) found |= palette.getCompound(i).getString("Name").equals("minecraft:diamond_block");
        assertTrue(found, "놓은 블록이 팔레트에");
    }

    @Test
    void chunkMapReadsTheStoredChunk() throws Exception {
        var map = level.getChunkSource().chunkMap;
        assertStoredChunk(map.read(new ChunkPos(cx, cz)).get(60, TimeUnit.SECONDS).orElseThrow());
        assertFalse(map.read(new ChunkPos(4000, 4000)).get(60, TimeUnit.SECONDS).isPresent(), "저장된 적 없는 청크는 빈 값");
    }

    @Test
    void moonriseLoadDataAsyncCallsBackWithTheStoredChunk() throws Exception {
        CompletableFuture<Object[]> got = new CompletableFuture<>();
        var c = RegionFileIOThread.loadDataAsync(level, cx, cz, RegionFileIOThread.RegionFileType.CHUNK_DATA,
                (tag, err) -> got.complete(new Object[] {tag, err}), false, PrioritisedExecutor.Priority.LOW);
        Object[] r = got.get(60, TimeUnit.SECONDS);
        assertNull(r[1]);
        assertStoredChunk((CompoundTag) r[0]);
        assertFalse(c.cancel(), "이미 불린 뒤라 취소되지 않는다");
        // 없는 청크: 콜백은 (null, null)
        CompletableFuture<Object[]> none = new CompletableFuture<>();
        RegionFileIOThread.loadDataAsync(level, 4000, 4000, RegionFileIOThread.RegionFileType.CHUNK_DATA, (tag, err) -> none.complete(new Object[] {tag, err}), false);
        Object[] n = none.get(60, TimeUnit.SECONDS);
        assertNull(n[0]);
        assertNull(n[1]);
        assertThrows(IllegalArgumentException.class, () -> RegionFileIOThread.loadDataAsync(level, cx, cz, RegionFileIOThread.RegionFileType.CHUNK_DATA,
                (tag, err) -> {}, false, PrioritisedExecutor.Priority.COMPLETING));
    }

    @Test
    void moonriseLoadDataBlocksForTheStoredChunk() throws Exception {
        assertStoredChunk(RegionFileIOThread.loadData(level, cx, cz, RegionFileIOThread.RegionFileType.CHUNK_DATA, PrioritisedExecutor.Priority.BLOCKING));
        assertNull(RegionFileIOThread.loadData(level, 4000, 4000, RegionFileIOThread.RegionFileType.POI_DATA, PrioritisedExecutor.Priority.NORMAL));
        assertFalse(RegionFileIOThread.isRegionFileThread());
    }

    @Test
    void prioritiesLikePaper() {
        assertEquals(-1, PrioritisedExecutor.Priority.COMPLETING.priority);
        assertEquals(0, PrioritisedExecutor.Priority.BLOCKING.priority);
        assertEquals(8, PrioritisedExecutor.Priority.IDLE.priority);
        assertEquals(10, PrioritisedExecutor.Priority.TOTAL_PRIORITIES);
        assertEquals(9, PrioritisedExecutor.Priority.TOTAL_SCHEDULABLE_PRIORITIES);
        assertEquals(4, PrioritisedExecutor.Priority.NORMAL.priority);
        assertSame(PrioritisedExecutor.Priority.NORMAL, PrioritisedExecutor.Priority.getPriority(4));
        assertSame(PrioritisedExecutor.Priority.HIGH, PrioritisedExecutor.Priority.max(PrioritisedExecutor.Priority.HIGH, PrioritisedExecutor.Priority.LOW));
        assertTrue(PrioritisedExecutor.Priority.HIGH.isHigherPriority(PrioritisedExecutor.Priority.LOW));
        assertFalse(PrioritisedExecutor.Priority.isValidPriority(PrioritisedExecutor.Priority.COMPLETING));
    }
}
