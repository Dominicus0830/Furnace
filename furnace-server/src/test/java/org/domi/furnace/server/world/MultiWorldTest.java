// 다중 월드(W1): 추가 월드 만들기·저장·내리기·다시 불러오기, Paper 폴더 배치, 검사, 차원 타입 값으로 정해지는 동작, 설정·설명서 읽기.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.world.WorldCreator;
import org.domi.furnace.api.world.WorldEnvironment;
import org.domi.furnace.api.world.WorldGenerator;
import org.domi.furnace.api.worldgen.DimensionTypeDefinition;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.BedBlock;
import org.domi.furnace.server.block.types.RespawnAnchorBlock;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MultiWorldTest {

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
        reg = BlockRegistry.load("blocks/1.21.1.txt");
        // 플러그인 차원 타입(W2) 하나가 클라이언트 레지스트리에 있는 상태를 흉내 낸다
        registries = RegistrySet.load("registries/1.21.1.txt")
                .withExtras(List.of(new RegistrySet.Extra("minecraft:dimension_type", "test:low", new org.domi.furnace.server.net.io.nbt.NbtCompound())));
        var io = TestServers.ioPool();
        manager = new WorldManager(new WorldManager.Parts(tmp, reg, ItemRegistry.load("items/1.21.1.txt"), registries,
                registries.indexOf("minecraft:worldgen/biome", "minecraft:plains"), 7, new ChunkWorkerPool(1), sched, null,
                Config.DEFAULT, false, io, io), dims, core.levelData(), "world");
        core.worldManager(manager);
    }

    @AfterEach
    void tearDown() {
        manager.closeAllForShutdown();
        sched.close();
    }

    private World create(WorldCreator c) throws Exception { return manager.create(c).get(60, TimeUnit.SECONDS); }

    private static Throwable cause(Throwable t) { return t instanceof ExecutionException && t.getCause() != null ? t.getCause() : t; }

    private int stoneId() { return reg.defaultStateId("minecraft:stone"); }

    private void placeStone(World w, int x, int y, int z) {
        ChunkPos cp = ChunkPos.ofBlock(x, z);
        w.awaitChunk(cp);
        assertTrue(w.submitAtChunk(cp, r -> r.level().setBlock(new Pos(x, y, z), stoneId(), 3)).join());
    }

    private int blockAt(World w, int x, int y, int z) {
        ChunkPos cp = ChunkPos.ofBlock(x, z);
        w.awaitChunk(cp);
        return w.submitAtChunk(cp, r -> r.level().getBlockState(new Pos(x, y, z))).join();
    }

    @Test
    void voidWorldIsRegisteredSavedUnloadedAndReloaded() throws Exception {
        World w = create(WorldCreator.named("void1").generator(WorldGenerator.VOID).build());
        assertSame(w, core.world("minecraft:void1"), "기본 키 = minecraft:<소문자 이름>(Paper)");
        assertTrue(w.isExtraWorld());
        assertEquals("void1", w.levelName());
        assertEquals(core.levelData().seed, w.seed(), "시드를 정하지 않으면 주 월드 시드(바닐라)");
        assertSame(core.world().clock(), w.clock(), "시계는 오버월드와 공유");
        assertTrue(Files.isRegularFile(tmp.resolve("void1/level.dat")));
        assertEquals(List.of("minecraft:overworld", "minecraft:void1"), dims.levelKeys());
        assertEquals(core.world().worldDir(), w.worldDir(), "playerdata·stats 등 서버 공용 데이터는 주 월드 폴더(그 월드 폴더에 두면 재접속 때 못 찾는다)");
        assertEquals(tmp.resolve("void1"), w.levelRoot(), "청크·data 는 <이름>/");

        assertEquals(0, blockAt(w, 5, 70, 5), "빈 월드는 공기");
        placeStone(w, 5, 70, 5);
        assertTrue(manager.unload(w, true).get(60, TimeUnit.SECONDS));
        assertNull(core.world("minecraft:void1"));
        assertTrue(w.isUnloaded());
        assertTrue(Files.isRegularFile(tmp.resolve("void1/region/r.0.0.mca")), "NORMAL 환경은 <이름>/region");
        assertThrows(java.util.concurrent.RejectedExecutionException.class, () -> w.runAtChunk(new ChunkPos(0, 0), r -> { }));
        assertTrue(w.submitAtChunk(new ChunkPos(0, 0), r -> 1).isCompletedExceptionally());

        // 저장된 설명서로 다시(폴더 잠금이 풀렸어야 한다)
        World again = manager.load("void1").get(60, TimeUnit.SECONDS);
        assertNotSame(w, again);
        assertEquals(stoneId(), blockAt(again, 5, 70, 5), "내리기 전에 놓은 블록이 남는다");
        assertEquals(WorldGenerator.VOID, manager.storedCreator("void1").generator());
    }

    @Test
    void paperMadeNetherFolderWithoutDescriptorLoadsAsNether() throws Exception {
        // Paper/Multiverse 가 만든 네더 월드: 설명서(furnace:world) 없이 level.dat 와 DIM-1/region 만 있다
        Path dir = tmp.resolve("pnether");
        Files.createDirectories(dir.resolve("DIM-1/region"));
        var wgs = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        wgs.putLong("seed", 123L);
        var data = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        data.putCompound("WorldGenSettings", wgs);
        data.putInt("DataVersion", 3955);
        var root = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        root.putCompound("Data", data);
        try (var out = Files.newOutputStream(dir.resolve("level.dat"))) {
            org.domi.furnace.server.net.io.nbt.NbtIo.writeGzip(out, root, "");
        }
        assertEquals(WorldEnvironment.NETHER, manager.storedCreator("pnether").environment(), "폴더 배치로 환경을 추론한다");
        World w = manager.load("pnether").get(60, TimeUnit.SECONDS);
        assertEquals("minecraft:the_nether", w.dimensionType().typeKey());
        assertEquals(123L, w.seed(), "level.dat 의 시드가 이긴다");
    }

    @Test
    void unloadWithoutSaveDropsChanges() throws Exception {
        World w = create(WorldCreator.named("scratch").generator(WorldGenerator.VOID).build());
        placeStone(w, 1, 64, 1);
        assertTrue(manager.unload(w, false).get(60, TimeUnit.SECONDS));
        World again = manager.load("scratch").get(60, TimeUnit.SECONDS);
        assertEquals(0, blockAt(again, 1, 64, 1));
    }

    @Test
    void netherEnvironmentUsesPaperLayoutAndNetherTypeRules() throws Exception {
        World w = create(WorldCreator.named("nether2").environment(WorldEnvironment.NETHER).generator(WorldGenerator.VOID).seed(42L).build());
        assertEquals("minecraft:the_nether", w.dimensionType().typeKey());
        assertEquals("minecraft:nether2", w.dimension());
        assertEquals(42L, w.seed());
        assertEquals(42L, core.seedOf(w));
        assertEquals(tmp.resolve("nether2/DIM-1"), w.dimensionType().storageDir(w.levelRoot()), "습격·forceload data 는 <이름>/DIM-1/data");
        ChunkPos cp = new ChunkPos(0, 0);
        w.awaitChunk(cp);
        // 바닐라는 키가 아니라 차원 타입 값으로 정한다: 네더 타입이면 침대는 터지고 리스폰 앵커가 된다, 피글린은 안전, 습격 없음
        assertFalse(w.submitAtChunk(cp, r -> BedBlock.canSetSpawn(r.level())).join());
        assertTrue(w.submitAtChunk(cp, r -> RespawnAnchorBlock.canSetSpawn(r.level())).join());
        assertTrue(w.submitAtChunk(cp, r -> r.level().piglinSafe()).join());
        placeStone(w, 0, 10, 0);
        assertTrue(manager.unload(w, true).get(60, TimeUnit.SECONDS));
        assertTrue(Files.isRegularFile(tmp.resolve("nether2/DIM-1/region/r.0.0.mca")), "NETHER 환경은 <이름>/DIM-1/region(Paper)");
        assertEquals(42L, manager.storedCreator("nether2").seed().isPresent() ? 0 : 42L, "설명서는 시드를 비운다(level.dat 시드가 이긴다)");
        World again = manager.load("nether2").get(60, TimeUnit.SECONDS);
        assertEquals(42L, again.seed(), "다시 불러오면 level.dat 의 시드");
    }

    @Test
    void pluginDimensionTypeWithOtherHeightWorksForFlatButNotNoise() throws Exception {
        var def = DimensionTypeDefinition.overworld().heightRange(0, 128).bedWorks(false).build();
        manager.hooks(new WorldManager.Hooks() {
            @Override public void attach(World w) { core.attachWorld(w); }
            @Override public DimensionTypeDefinition pluginDimensionType(String key) { return "test:low".equals(key) ? def : null; }
        });
        var noise = manager.create(WorldCreator.named("lownoise").dimensionType("test:low").build());
        var ex = assertThrows(ExecutionException.class, () -> noise.get(60, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, cause(ex));
        assertFalse(Files.exists(tmp.resolve("lownoise/session.lock")) && core.world("minecraft:lownoise") != null);

        World w = create(WorldCreator.named("low").dimensionType("test:low").generator(WorldGenerator.FLAT)
                .flatLayers(List.of(new WorldCreator.FlatLayer("minecraft:stone", 3))).build());
        assertEquals(0, w.dimensionType().minY());
        assertEquals(128, w.dimensionType().height());
        assertEquals(stoneId(), blockAt(w, 0, 2, 0), "층은 그 차원 최저 높이(0)부터");
        assertEquals(0, blockAt(w, 0, 3, 0));
        ChunkPos cp = new ChunkPos(0, 0);
        assertFalse(w.submitAtChunk(cp, r -> BedBlock.canSetSpawn(r.level())).join(), "플러그인 타입의 bedWorks=false");
        assertEquals(3, w.defaultSpawn().y(), "평지 스폰은 맨 위 층 위");
    }

    @Test
    void validationRejectsBadSpecs() throws Exception {
        var reserved = manager.create(WorldCreator.named("world").build());
        assertInstanceOf(IllegalArgumentException.class, cause(assertThrows(ExecutionException.class, () -> reserved.get(5, TimeUnit.SECONDS))));
        var nether = manager.create(WorldCreator.named("world_nether").build());
        assertInstanceOf(IllegalArgumentException.class, cause(assertThrows(ExecutionException.class, () -> nether.get(5, TimeUnit.SECONDS))));
        var unknownType = manager.create(WorldCreator.named("x1").dimensionType("nope:nope").generator(WorldGenerator.VOID).build());
        assertInstanceOf(IllegalArgumentException.class, cause(assertThrows(ExecutionException.class, () -> unknownType.get(60, TimeUnit.SECONDS))));
        var vanillaKey = manager.create(WorldCreator.named("x2").key("minecraft:overworld").generator(WorldGenerator.VOID).build());
        assertInstanceOf(IllegalArgumentException.class, cause(assertThrows(ExecutionException.class, () -> vanillaKey.get(60, TimeUnit.SECONDS))));
        assertThrows(IllegalArgumentException.class, () -> WorldCreator.named("x3").environment(WorldEnvironment.NETHER).generator(WorldGenerator.AMPLIFIED).build());
        assertThrows(IllegalArgumentException.class, () -> WorldCreator.named("../x").build());
        // 기본 차원은 내릴 수 없다
        var base = manager.unload(core.world(), true);
        assertInstanceOf(IllegalArgumentException.class, cause(assertThrows(ExecutionException.class, () -> base.get(5, TimeUnit.SECONDS))));
        // 같은 이름을 같은 설명서로 다시 만들면 같은 월드, 다른 설명서면 거절
        World a = create(WorldCreator.named("same").generator(WorldGenerator.VOID).build());
        assertSame(a, create(WorldCreator.named("same").generator(WorldGenerator.VOID).build()));
        var other = manager.create(WorldCreator.named("same").generator(WorldGenerator.FLAT).build());
        assertInstanceOf(IllegalArgumentException.class, cause(assertThrows(ExecutionException.class, () -> other.get(60, TimeUnit.SECONDS))));
    }

    @Test
    void unloadIsRefusedWhenHookCancels() throws Exception {
        World w = create(WorldCreator.named("keep").generator(WorldGenerator.VOID).build());
        manager.hooks(new WorldManager.Hooks() {
            @Override public boolean beforeUnload(World x, boolean save) { return false; }
        });
        assertFalse(manager.unload(w, true).get(60, TimeUnit.SECONDS));
        assertSame(w, core.world("minecraft:keep"));
        assertFalse(w.isUnloaded());
    }

    private org.domi.furnace.api.plugin.FurnacePlugin testPlugin() {
        var host = core.plugins();
        var plugin = new org.domi.furnace.api.plugin.FurnacePlugin() {};
        plugin.init(new org.domi.furnace.api.plugin.PluginContext() {
            final org.domi.furnace.api.plugin.PluginDescription d = new org.domi.furnace.api.plugin.PluginDescription("mw", "1", "x", "",
                    List.of(), null, null, List.of(), List.of(), List.of(), null, java.util.Map.of(), List.of(), null);
            public org.domi.furnace.api.plugin.PluginDescription description() { return d; }
            public org.domi.furnace.api.Furnace server() { return host; }
            public java.util.logging.Logger logger() { return java.util.logging.Logger.getLogger("mw"); }
            public Path configFolder() { return tmp.resolve("mw"); }
            public Path dataFolder() { return tmp.resolve("mw"); }
            public Path file() { return tmp.resolve("mw.jar"); }
            public ClassLoader classLoader() { return getClass().getClassLoader(); }
            public org.domi.furnace.api.registry.PluginRegistries registries() { return null; }
        });
        plugin.setEnabled(true);
        return plugin;
    }

    @Test
    void unloadEventCancelKeepsWorldLoaded() throws Exception {
        var host = core.plugins();
        var plugin = testPlugin();
        boolean[] cancel = {true};
        host.eventsImpl().subscribe(plugin, org.domi.furnace.api.event.world.WorldUnloadEvent.class, e -> e.setCancelled(cancel[0]));
        var api = host.createWorld(WorldCreator.named("held").generator(WorldGenerator.VOID).build()).get(60, TimeUnit.SECONDS);
        assertFalse(host.unloadWorld(api, true).get(60, TimeUnit.SECONDS), "취소되면 false");
        assertTrue(api.isLoaded());
        assertNotNull(host.world("minecraft:held"));
        cancel[0] = false;
        assertTrue(host.unloadWorld(api, true).get(60, TimeUnit.SECONDS));
        assertFalse(api.isLoaded());
    }

    // ----- T-071: PluginHost.handleFor 빠른 길(get)이 옛 compute 와 같은 결과를 내는지 -----

    @Test
    void handleForKeepsIdentityAndReplacesStaleHandle() throws Exception {
        var host = core.plugins();
        var main = host.worldHandle("minecraft:overworld");
        assertSame(main, host.worldHandle("minecraft:overworld"), "같은 월드 = 같은 손잡이(여러 번)");
        assertSame(main, host.world("minecraft:overworld"));
        assertSame(core.world(), main.handle());

        var a = host.createWorld(WorldCreator.named("hf").generator(WorldGenerator.VOID).build()).get(60, TimeUnit.SECONDS);
        assertSame(a, host.worldHandle("minecraft:hf"));
        assertSame(a, host.worldHandle("minecraft:hf"));
        assertSame(a, host.worldNamed("hf"));
        assertSame(main, host.worldHandle("minecraft:overworld"), "다른 차원의 손잡이는 그대로");

        assertTrue(host.unloadWorld(a, false).get(60, TimeUnit.SECONDS));
        assertNull(host.worldHandle("minecraft:hf"), "내린 월드는 조회가 null");
        assertFalse(a.isLoaded(), "내린 월드의 손잡이는 isLoaded()=false 로 남는다");

        var b = host.createWorld(WorldCreator.named("hf").generator(WorldGenerator.VOID).build()).get(60, TimeUnit.SECONDS);
        assertNotSame(a, b, "같은 키로 다시 올리면 새 손잡이(낡은 손잡이를 돌려주지 않는다)");
        assertTrue(b.isLoaded());
        assertSame(b, host.worldHandle("minecraft:hf"));
        assertSame(b, host.worldHandle("minecraft:hf"));
        assertFalse(a.isLoaded());
    }

    @Test
    void handleForReplacesHandleAfterUnloadAndReload() throws Exception {
        // 내림(manager 훅 → worldDetached 로 손잡이가 걷힘) 뒤 같은 키로 다시 올리면 새 손잡이. 걷히기 전 낡은 항목이 남은 경우는 plugin.HandleForStaleEntryTest
        var host = core.plugins();
        World w1 = create(WorldCreator.named("stale").generator(WorldGenerator.VOID).build());
        var h1 = host.worldHandle("minecraft:stale");
        assertSame(w1, h1.handle());
        assertTrue(manager.unload(w1, false).get(60, TimeUnit.SECONDS));
        World w2 = create(WorldCreator.named("stale").generator(WorldGenerator.VOID).build());
        assertNotSame(w1, w2);
        var h2 = host.worldHandle("minecraft:stale");
        assertSame(w2, h2.handle(), "손잡이는 늘 지금 서버 월드를 가리킨다");
        assertSame(h2, host.worldHandle("minecraft:stale"));
        assertNotSame(h1, h2);
    }

    @Test
    void handleForConvergesUnderConcurrentFirstLookup() throws Exception {
        // 월드를 manager 로 직접 만들어 손잡이가 아직 없는 상태에서 여러 스레드가 동시에 처음 조회 → 모두 같은 손잡이 하나
        var host = core.plugins();
        for (int round = 0; round < 20; round++) {
            String name = "race" + round;
            World w = create(WorldCreator.named(name).generator(WorldGenerator.VOID).build());
            int n = 8;
            var go = new java.util.concurrent.CountDownLatch(1);
            var ex = java.util.concurrent.Executors.newFixedThreadPool(n);
            try {
                List<java.util.concurrent.Future<Object>> fs = new java.util.ArrayList<>();
                for (int i = 0; i < n; i++) fs.add(ex.submit(() -> { go.await(); return host.worldHandle("minecraft:" + name); }));
                go.countDown();
                Object first = fs.get(0).get(30, TimeUnit.SECONDS);
                assertNotNull(first);
                for (var f : fs) assertSame(first, f.get(30, TimeUnit.SECONDS), "동시 첫 조회도 하나로 수렴");
                assertSame(first, host.worldHandle("minecraft:" + name));
                assertSame(w, ((org.domi.furnace.server.plugin.WorldHandle) first).handle());
            } finally {
                ex.shutdownNow();
            }
        }
    }

    @Test
    void pluginApiCreatesAndUnloadsWithEvents() throws Exception {
        var host = core.plugins();
        var plugin = testPlugin();
        List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
        host.eventsImpl().subscribe(plugin, org.domi.furnace.api.event.world.WorldLoadEvent.class, e -> seen.add("load " + e.world() + " " + e.name()));
        host.eventsImpl().subscribe(plugin, org.domi.furnace.api.event.world.WorldUnloadEvent.class, e -> seen.add("unload " + e.name() + " " + e.save()));
        var api = host.createWorld(WorldCreator.named("api").generator(WorldGenerator.VOID).build()).get(60, TimeUnit.SECONDS);
        assertEquals("api", api.name());
        assertEquals("minecraft:overworld", api.dimensionType());
        assertEquals(WorldEnvironment.NORMAL, api.environment());
        assertTrue(api.isLoaded());
        assertSame(api, host.worldNamed("api"));
        assertTrue(host.unloadWorld(api, false).get(60, TimeUnit.SECONDS));
        assertFalse(api.isLoaded());
        assertNull(host.world("minecraft:api"));
        assertFalse(host.unloadWorld(api, false).get(5, TimeUnit.SECONDS), "이미 내린 손잡이");
        assertEquals(List.of("load minecraft:api api", "unload api false"), seen);
    }

    @Test
    void settingsAndStoredSpecRoundTrip() {
        Properties p = new Properties();
        assertEquals(WorldSettings.DEFAULT, WorldSettings.from(p));
        p.setProperty("dimension-seed.the_nether", "123");
        p.setProperty("dimension-seed.the_end", "glacier");
        p.setProperty("worlds.autoload", " a , b,a,");
        WorldSettings s = WorldSettings.from(p);
        assertEquals(123L, s.seedFor("minecraft:the_nether", 9L));
        assertEquals("glacier".hashCode(), s.seedFor("minecraft:the_end", 9L));
        assertEquals(9L, s.seedFor("minecraft:overworld", 9L));
        assertEquals(List.of("a", "b"), s.autoload());

        var spec = WorldCreator.named("Flat1").key("myplugin:flat").generator(WorldGenerator.FLAT).generateStructures(false)
                .flatLayers(List.of(new WorldCreator.FlatLayer("bedrock", 1), new WorldCreator.FlatLayer("minecraft:sand", 4)))
                .flatBiome("desert").seed(5L).build();
        var back = WorldManager.specFrom("Flat1", WorldManager.specNbt(spec));
        assertEquals(spec.toBuilder().sameSeedAsMainWorld().build(), back);
    }

    @Test
    void vanillaTypeValuesMatchDimensionTypeJson() {
        // data/minecraft/dimension_type/*.json 의 값(1.21.1)
        assertEquals("minecraft:infiniburn_nether", DimensionType.NETHER.infiniburn());
        assertFalse(DimensionType.NETHER.hasRaids());
        assertEquals(7, DimensionType.NETHER.monsterSpawnLightMin());
        assertEquals(15, DimensionType.NETHER.monsterSpawnBlockLightLimit());
        assertEquals("minecraft:infiniburn_end", DimensionType.END.infiniburn());
        assertTrue(DimensionType.END.hasRaids());
        assertEquals("raids_end", DimensionType.END.raidsFileId());
        assertEquals("raids", DimensionType.OVERWORLD.raidsFileId());
        var caves = DimensionType.vanillaType("minecraft:overworld_caves");
        assertTrue(caves.hasCeiling());
        assertTrue(caves.hasSkyLight());
        // 범위가 한 값이면 난수를 쓰지 않는다(ConstantInt), 아니면 0..7 균등
        assertEquals(7, DimensionType.NETHER.sampleMonsterSpawnLight(b -> { throw new AssertionError("난수 쓰면 안 됨"); }));
        assertEquals(5, DimensionType.OVERWORLD.sampleMonsterSpawnLight(b -> { assertEquals(8, b); return 5; }));
    }
}
