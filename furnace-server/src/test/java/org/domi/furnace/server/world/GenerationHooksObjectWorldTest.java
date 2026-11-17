// T-217: 월드젠 훅 객체(Bukkit ChunkGenerator 다리 자리)로 만드는 보통 월드 — 월드 정보(prepare)·attached·고정 스폰, level.dat 표시와 다시 열기 규칙.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.api.world.WorldCreator;
import org.domi.furnace.api.world.WorldEnvironment;
import org.domi.furnace.api.world.WorldGenerator;
import org.domi.furnace.api.worldgen.BiomeProvider;
import org.domi.furnace.api.worldgen.Climate;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerationHooksObjectWorldTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    Dimensions dims;
    WorldManager manager;
    BlockRegistry reg;
    RegistrySet registries;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp.resolve("world"), sched);
        dims = new Dimensions();
        dims.register(core.world());
        core.dimensions(dims);
        reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        registries = RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt");
        var io = TestServers.ioPool();
        manager = new WorldManager(new WorldManager.Parts(tmp, reg, ItemRegistry.load("items/" + GameVersion.RESOURCE_TAG + ".txt"), registries,
                registries.indexOf("minecraft:worldgen/biome", "minecraft:plains"), 7, new ChunkWorkerPool(1), sched, null,
                Config.DEFAULT, false, io, io), dims, core.levelData(), "world");
        core.worldManager(manager);
    }

    @AfterEach
    void tearDown() {
        manager.closeAllForShutdown();
        sched.close();
    }

    /** 받은 월드 정보·attached·고정 스폰 질의를 기록한다. 노이즈 단계 뒤 y=-64 에 금 블록 한 층. */
    static final class Recording implements org.domi.furnace.api.worldgen.ChunkGenerationHooks {
        final List<BiomeProvider.Context> prepared = new java.util.concurrent.CopyOnWriteArrayList<>();
        final List<String> attached = new java.util.concurrent.CopyOnWriteArrayList<>();
        final java.util.concurrent.atomic.AtomicInteger spawnAsks = new java.util.concurrent.atomic.AtomicInteger();
        volatile org.domi.furnace.api.world.Location spawn;

        @Override public String name() { return "test:recording"; }
        @Override public void prepare(BiomeProvider.Context world) { prepared.add(world); }
        @Override public void attached(String levelKey) { attached.add(levelKey); }

        @Override
        public org.domi.furnace.api.world.Location fixedSpawn(String levelKey) {
            spawnAsks.incrementAndGet();
            return spawn;
        }

        @Override
        public void noise(org.domi.furnace.api.worldgen.GenerationChunk c) {
            c.fill(0, -64, 0, 15, -64, 15, org.domi.furnace.api.world.BlockData.of("minecraft:gold_block"));
        }
    }

    private World create(WorldCreator c) throws Exception { return manager.create(c).get(120, TimeUnit.SECONDS); }

    private static Throwable cause(Throwable t) { return t instanceof ExecutionException && t.getCause() != null ? t.getCause() : t; }

    private String blockAt(World w, int x, int y, int z) {
        ChunkPos cp = ChunkPos.ofBlock(x, z);
        w.awaitChunk(cp);
        int s = w.submitAtChunk(cp, r -> r.getBlock(x, y, z)).join();
        return reg.nameOf(s);
    }

    @Test
    void hooksObjectWorldGetsWorldInfoAttachedAndFixedSpawn() throws Exception {
        Recording h = new Recording();
        h.spawn = new org.domi.furnace.api.world.Location("hooked", 3.7, 70, -2.2, 90f, 0f);
        World w = create(WorldCreator.named("hooked").seed(41L).generationHooksObject(h).build());
        assertEquals(List.of(new BiomeProvider.Context("hooked", "minecraft:hooked", WorldEnvironment.NORMAL, 41L, -64, 320)), h.prepared);
        assertEquals(List.of("minecraft:hooked"), h.attached);
        assertEquals(1, h.spawnAsks.get());
        assertEquals(new SpawnPoint(3, 70, -3, 90f), w.defaultSpawn(), "고정 스폰은 블록 칸으로 내림");
        assertEquals("minecraft:gold_block", blockAt(w, 5, -64, 5), "노이즈 훅이 돈다");
        assertTrue(manager.unload(w, true).get(60, TimeUnit.SECONDS));

        WorldCreator stored = manager.storedCreator("hooked");
        assertTrue(stored.needsGenerationHooksObject());
        assertEquals("test:recording", stored.generationHooksObjectName());
        var e = cause(assertThrows(ExecutionException.class, () -> manager.load("hooked").get(60, TimeUnit.SECONDS)));
        var n = assertInstanceOf(WorldManager.NeedsGenerationHooksException.class, e);
        assertEquals("hooked", n.world());
        assertEquals("test:recording", n.hooks());

        // 객체를 다시 주면 열린다 — 시드는 level.dat 의 것, 스폰은 다시 묻지 않는다
        Recording again = new Recording();
        World back = create(stored.toBuilder().generationHooksObject(again).build());
        assertEquals(41L, again.prepared.get(0).seed());
        assertEquals(List.of("minecraft:hooked"), again.attached);
        assertEquals(0, again.spawnAsks.get());
        assertEquals(new SpawnPoint(3, 70, -3, 90f), back.defaultSpawn());
        assertTrue(manager.unload(back, true).get(60, TimeUnit.SECONDS));

        // 객체를 지우고 열면 표시도 지워진다(Paper 처럼 새 청크는 바닐라)
        World plain = create(manager.storedCreator("hooked").toBuilder().generationHooksObject(null).build());
        assertTrue(manager.unload(plain, true).get(60, TimeUnit.SECONDS));
        assertFalse(manager.storedCreator("hooked").needsGenerationHooksObject());
    }

    @Test
    void fixedSpawnInAnotherWorldFailsLikePaperAndNullFallsBackToVanilla() throws Exception {
        Recording bad = new Recording();
        bad.spawn = new org.domi.furnace.api.world.Location("elsewhere", 0, 64, 0);
        var e = cause(assertThrows(ExecutionException.class, () -> create(WorldCreator.named("lost").generationHooksObject(bad).build())));
        assertInstanceOf(IllegalStateException.class, e);
        assertEquals("Cannot set spawn point for lost to be in another world (elsewhere)", e.getMessage());
        assertNull(core.world("minecraft:lost"), "실패한 월드는 목록에서 빠진다");

        Recording none = new Recording();
        World w = create(WorldCreator.named("plainspawn").seed(41L).generationHooksObject(none).build());
        assertEquals(1, none.spawnAsks.get());
        World ref = create(WorldCreator.named("refspawn").seed(41L).build());
        assertEquals(ref.defaultSpawn(), w.defaultSpawn(), "null 이면 바닐라 스폰 찾기");
    }

    @Test
    void hooksObjectAndKeyHooksCannotBeCombined() {
        assertThrows(IllegalArgumentException.class,
                () -> WorldCreator.named("both").generationHooks("test:k").generationHooksObject(new Recording()).build());
    }
}
