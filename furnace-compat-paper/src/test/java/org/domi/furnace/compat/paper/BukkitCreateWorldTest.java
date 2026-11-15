package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** T-141 E4d: Bukkit createWorld·WorldCreator·WorldType(Paper 1.21.1 CraftServer#createWorld). */
class BukkitCreateWorldTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { RecorderPlugin.reset(); }

    private Path recorderJar() throws Exception {
        return Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
    }

    /** 그 칸의 청크를 올리고 그 region 스레드에서 읽는다(추가 월드는 실제 시계로 돈다). */
    private static <T> T onRegion(World bw, int x, int z, java.util.function.Supplier<T> task) throws Exception {
        var fw = ((org.bukkit.craftbukkit.CraftWorld) bw).furnaceHandle();
        assertTrue(fw.loadChunkAsync(x >> 4, z >> 4, true).get(60, java.util.concurrent.TimeUnit.SECONDS));
        return fw.scheduler(new org.domi.furnace.api.world.BlockPos(x, 0, z)).submit(task).get(60, java.util.concurrent.TimeUnit.SECONDS);
    }

    private FurnaceTestServer start() throws Exception {
        return FurnaceTestServer.builder().failFast(false).multiWorld(true).pluginJar(recorderJar()).start();
    }

    /** 돌아올 때 월드가 돌고 Init → Load 가 지나갔다. 같은 이름은 이벤트 없이 같은 월드(설정이 달라도 — Paper 는 이름만 본다). */
    @Test
    void createWorldReturnsRunningWorldAfterInitAndLoad() throws Exception {
        try (FurnaceTestServer server = start()) {
            List<String> log = new CopyOnWriteArrayList<>();
            RecorderPlugin.ACTIONS.put("WorldInitEvent", e -> log.add("init " + ((org.bukkit.event.world.WorldInitEvent) e).getWorld().getName()
                    + " listed=" + (Bukkit.getWorld("mine") != null)));
            RecorderPlugin.ACTIONS.put("WorldLoadEvent", e -> log.add("load " + ((org.bukkit.event.world.WorldLoadEvent) e).getWorld().getName()));
            World w = new WorldCreator("Mine").seed(42L).type(WorldType.FLAT).createWorld();
            assertNotNull(w);
            assertEquals(List.of("init Mine listed=true", "load Mine"), log, "Init·Load 가 돌아오기 전에, Init 이 먼저");
            assertEquals("Mine", w.getName());
            assertEquals(new NamespacedKey("minecraft", "mine"), w.getKey(), "키 = minecraft:<소문자 이름>");
            assertEquals(42L, w.getSeed());
            assertEquals(World.Environment.NORMAL, w.getEnvironment());
            assertEquals(WorldType.FLAT, w.getWorldType());
            assertTrue(w.canGenerateStructures());
            assertFalse(w.isHardcore());
            assertSame(w, Bukkit.getWorld("Mine"));
            assertTrue(Bukkit.getWorlds().contains(w));
            // 바닐라 기본 평지(층 JSON 없음): 기반암 1 + 흙 2 + 잔디 → y -64..-61
            assertEquals(List.of(Material.GRASS_BLOCK, Material.AIR),
                    onRegion(w, 0, 0, () -> List.of(w.getBlockAt(0, -61, 0).getType(), w.getBlockAt(0, -60, 0).getType())));

            assertSame(w, Bukkit.createWorld(new WorldCreator("Mine").environment(World.Environment.NETHER)), "같은 이름이면 그 월드");
            assertEquals(2, log.size(), "이미 있는 월드는 다시 알리지 않는다");
            assertSame(server.api().world("minecraft:overworld"), ((org.bukkit.craftbukkit.CraftWorld) Bukkit.createWorld(new WorldCreator("world"))).furnaceHandle(),
                    "주 월드 이름은 그 월드");
        }
    }

    /** FLAT 의 generatorSettings 는 바닐라 평지 JSON. type 은 오버월드에만 쓰인다(네더 환경이면 네더 생성기 — Paper 와 같다). */
    @Test
    void flatSettingsAndTypeOnlyForOverworld() throws Exception {
        try (FurnaceTestServer server = start()) {
            World flat = Bukkit.createWorld(new WorldCreator("layers").type(WorldType.FLAT).generateStructures(false)
                    .generatorSettings("{\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:stone\",\"height\":3}],\"biome\":\"minecraft:desert\"}"));
            assertEquals(List.of(Material.STONE, Material.AIR, org.bukkit.block.Biome.DESERT), onRegion(flat, 5, 5, () -> List.<Object>of(
                    flat.getBlockAt(5, -61, 5).getType(), flat.getBlockAt(5, -60, 5).getType(), flat.getBlockAt(5, -61, 5).getBiome())));
            assertFalse(flat.canGenerateStructures());

            World nether = Bukkit.createWorld(new WorldCreator("nflat").environment(World.Environment.NETHER).type(WorldType.FLAT));
            assertEquals(World.Environment.NETHER, nether.getEnvironment());
            // 지형은 바닐라 네더(평지라면 y5 는 공기). 평지 표시(getWorldType)는 Paper 가 FLAT 이라 다르다 — context note "다른 점"
            assertNotEquals(Material.AIR, onRegion(nether, 0, 0, () -> nether.getBlockAt(0, 5, 0).getType()));

            var copy = new WorldCreator("copy").copy(flat);
            assertEquals(flat.getSeed(), copy.seed());
            assertEquals(WorldType.FLAT, copy.type());
            assertFalse(copy.generateStructures());
            assertNull(copy.generator());
        }
    }

    /** 지원하지 않는 것은 Paper 처럼 예외 또는 개발자에게 이유를 알려 주는 예외. */
    @Test
    void rejectionsAreExplicit() throws Exception {
        try (FurnaceTestServer server = start()) {
            var custom = assertThrows(IllegalArgumentException.class, () -> Bukkit.createWorld(new WorldCreator("c1").environment(World.Environment.CUSTOM)));
            assertEquals("Illegal dimension (CUSTOM)", custom.getMessage());
            var gen = new org.bukkit.generator.ChunkGenerator() {};
            var e = assertThrows(UnsupportedFeature.class, () -> Bukkit.createWorld(new WorldCreator("c2").generator(gen)));
            assertTrue(e.getMessage().contains("generationHooks"), e.getMessage());
            assertThrows(IllegalArgumentException.class, () -> Bukkit.createWorld(new WorldCreator("c3").type(WorldType.FLAT).generatorSettings("{layers")));
            assertThrows(IllegalArgumentException.class, () -> Bukkit.createWorld(null));
            assertNull(Bukkit.getWorld("c1"));
            assertNull(Bukkit.getWorld("c2"));
            // 없는 플러그인 이름의 생성기는 null(콘솔에 알림) — 그러면 바닐라 생성기로 만든다
            assertNull(new WorldCreator("c4").generator("NoSuchPlugin").generator());
        }
    }

    /** WorldCreator 기본값·키 규칙·WorldType 이름(Paper 와 같다). */
    @Test
    void creatorDefaultsMatchPaper() throws Exception {
        try (FurnaceTestServer server = start()) {
            var c = new WorldCreator("My World");
            assertEquals(new NamespacedKey("minecraft", "my_world"), c.key());
            assertEquals(new NamespacedKey("minecraft", "overworld"), new WorldCreator("world").key());
            assertEquals(new NamespacedKey("minecraft", "the_nether"), new WorldCreator("world_nether").key());
            assertEquals(new NamespacedKey("minecraft", "the_end"), new WorldCreator("world_the_end").key());
            assertEquals(World.Environment.NORMAL, c.environment());
            assertEquals(WorldType.NORMAL, c.type());
            assertTrue(c.generateStructures());
            assertFalse(c.hardcore());
            assertFalse(c.keepSpawnInMemory());
            assertEquals("", c.generatorSettings());
            assertEquals(WorldType.LARGE_BIOMES, WorldType.getByName("largebiomes"));
            assertEquals("DEFAULT", WorldType.NORMAL.getName());
            assertNull(WorldType.getByName("normal"));
            var k = WorldCreator.ofKey(new NamespacedKey("demo", "arena"));
            assertEquals("arena", k.name());
        }
    }
}
