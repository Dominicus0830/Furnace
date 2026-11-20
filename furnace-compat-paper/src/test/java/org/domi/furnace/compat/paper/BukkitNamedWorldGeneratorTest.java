package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.domi.furnace.compat.paper.fixtures.DefaultGenProbe;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-222: 이름별 생성기 설정(bukkit.yml worlds.<이름>.generator·biome-provider 자리 — furnace.properties world-generator.name.<이름>).
 * Paper CraftServer#createWorld 는 같은 이름·키 검사 뒤, 생성기·공급자가 비어 있을 때 각각 getGenerator(이름)·getBiomeProvider(이름)로 채운다.
 */
class BukkitNamedWorldGeneratorTest {

    @TempDir
    Path tmp;

    private final List<String> severe = new CopyOnWriteArrayList<>();
    private final Handler capture = new Handler() {
        @Override public void publish(LogRecord r) { if (r.getMessage() != null) severe.add(r.getMessage()); }
        @Override public void flush() {}
        @Override public void close() {}
    };

    @BeforeEach
    void reset() {
        DefaultGenProbe.reset();
        Logger.getLogger("Minecraft").addHandler(capture);
    }

    @AfterEach
    void detach() { Logger.getLogger("Minecraft").removeHandler(capture); }

    private FurnaceTestServer start(String world, String generator, String provider) throws Exception {
        Path jar = Jars.bukkit(tmp.resolve("gen.jar"), "plugin.yml", "name: GenPlug\nversion: '1'\nmain: " + DefaultGenProbe.class.getName()
                + "\napi-version: '1.21'\n", DefaultGenProbe.class);
        return FurnaceTestServer.builder().failFast(false).spawnChunkRadius(0).namedWorldGeneration(world, generator, provider).pluginJar(jar).start();
    }

    private static <T> T onRegion(World bw, int x, int z, java.util.function.Supplier<T> task) throws Exception {
        var fw = ((org.bukkit.craftbukkit.CraftWorld) bw).furnaceHandle();
        assertTrue(fw.loadChunkAsync(x >> 4, z >> 4, true).get(60, TimeUnit.SECONDS));
        return fw.scheduler(new org.domi.furnace.api.world.BlockPos(x, 0, z)).submit(task).get(60, TimeUnit.SECONDS);
    }

    @Test
    void configuredGeneratorShapesCreatedWorld() throws Exception {
        var gen = new BukkitDefaultWorldGeneratorTest.Stone();
        DefaultGenProbe.GENERATOR = gen;
        try (FurnaceTestServer server = start("custom", "GenPlug:flat", "")) {
            assertEquals(List.of("GenPlug:enable"), DefaultGenProbe.LOG, "만들기 전에는 묻지 않는다");
            World w = Bukkit.createWorld(new WorldCreator("custom").seed(5L));
            assertEquals(List.of("GenPlug:enable", "GenPlug:generator custom flat"), DefaultGenProbe.LOG);
            assertSame(gen, w.getGenerator());
            assertSame(BukkitDefaultWorldGeneratorTest.DESERT, w.getBiomeProvider(), "공급자가 없으면 생성기의 기본 공급자");
            assertEquals(Material.STONE, onRegion(w, 40, 40, () -> w.getBlockAt(40, -61, 40).getType()));
            assertEquals(Biome.DESERT, onRegion(w, 40, 40, () -> w.getBiome(40, 0, 40)));
            assertTrue(severe.isEmpty(), severe.toString());
        }
    }

    @Test
    void otherNamesAndExistingWorldsDoNotAsk() throws Exception {
        DefaultGenProbe.GENERATOR = new BukkitDefaultWorldGeneratorTest.Stone();
        try (FurnaceTestServer server = start("custom", "GenPlug", "")) {
            World other = Bukkit.createWorld(new WorldCreator("Custom"));   // 이름은 정확히 같아야 한다(대소문자 구분)
            assertNull(other.getGenerator());
            assertEquals(List.of("GenPlug:enable"), DefaultGenProbe.LOG);
            assertSame(other, Bukkit.createWorld(new WorldCreator("Custom")), "이미 있는 월드는 묻지 않고 그 월드");
            assertEquals(List.of("GenPlug:enable"), DefaultGenProbe.LOG);
        }
    }

    /** 검토: 설정에 이름이 있는 월드를 두 번째 createWorld 해도 다시 묻지 않고, 내렸다 level.dat 으로 다시 열 때는 설정을 또 읽는다. */
    @Test
    void secondCreateDoesNotAskButReopenReadsConfigAgain() throws Exception {
        DefaultGenProbe.GENERATOR = new BukkitDefaultWorldGeneratorTest.Stone();
        try (FurnaceTestServer server = start("again", "GenPlug:v", "")) {
            World first = Bukkit.createWorld(new WorldCreator("again").seed(3L));
            assertSame(first, Bukkit.createWorld(new WorldCreator("again")));
            assertEquals(List.of("GenPlug:enable", "GenPlug:generator again v"), DefaultGenProbe.LOG, "이미 있는 월드는 묻지 않는다");
            assertTrue(server.api().unloadWorld(((org.bukkit.craftbukkit.CraftWorld) first).furnaceHandle(), true).get(60, TimeUnit.SECONDS));
            var second = new BukkitDefaultWorldGeneratorTest.Stone();
            DefaultGenProbe.GENERATOR = second;
            World reopened = Bukkit.createWorld(new WorldCreator("again"));   // level.dat 이 있다 — Paper 는 그래도 설정을 먼저 읽는다
            assertEquals(List.of("GenPlug:enable", "GenPlug:generator again v", "GenPlug:generator again v"), DefaultGenProbe.LOG);
            assertSame(second, reopened.getGenerator());
            assertEquals(3L, reopened.getSeed(), "시드는 level.dat 의 것");
        }
    }

    @Test
    void explicitGeneratorKeepsConfigProviderIndependent() throws Exception {
        var gen = new BukkitDefaultWorldGeneratorTest.Stone();
        BiomeProvider configured = new BiomeProvider() {
            @Override public Biome getBiome(org.bukkit.generator.WorldInfo w, int x, int y, int z) { return Biome.SNOWY_PLAINS; }
            @Override public List<Biome> getBiomes(org.bukkit.generator.WorldInfo w) { return List.of(Biome.SNOWY_PLAINS); }
        };
        DefaultGenProbe.GENERATOR = new BukkitDefaultWorldGeneratorTest.Stone();   // 설정의 생성기는 쓰이지 않는다
        DefaultGenProbe.PROVIDER = configured;
        try (FurnaceTestServer server = start("mix", "GenPlug:g", "GenPlug:p")) {
            World w = Bukkit.createWorld(new WorldCreator("mix").generator(gen));
            assertEquals(List.of("GenPlug:enable", "GenPlug:provider mix p"), DefaultGenProbe.LOG, "준 생성기는 그대로, 비어 있는 공급자만 설정에서");
            assertSame(gen, w.getGenerator());
            assertSame(configured, w.getBiomeProvider(), "설정의 공급자가 생성기의 기본 공급자보다 앞선다");
            assertEquals(Biome.SNOWY_PLAINS, onRegion(w, 8, 8, () -> w.getBiome(8, 0, 8)));
        }
    }

    @Test
    void explicitBothAreNotOverridden() throws Exception {
        var gen = new BukkitDefaultWorldGeneratorTest.Stone();
        try (FurnaceTestServer server = start("both", "GenPlug:g", "GenPlug:p")) {
            World w = Bukkit.createWorld(new WorldCreator("both").generator(gen).biomeProvider(BukkitDefaultWorldGeneratorTest.DESERT));
            assertEquals(List.of("GenPlug:enable"), DefaultGenProbe.LOG);
            assertSame(gen, w.getGenerator());
        }
    }

    @Test
    void missingPluginAndNullAnswersUsePaperMessages() throws Exception {
        try (FurnaceTestServer server = start("gone", "NoSuchPlugin:x", "NoSuchPlugin")) {
            World w = Bukkit.createWorld(new WorldCreator("gone"));
            assertNull(w.getGenerator());
            assertEquals(List.of("Could not set generator for default world 'gone': Plugin 'NoSuchPlugin' does not exist",
                    "Could not set biome provider for default world 'gone': Plugin 'NoSuchPlugin' does not exist"), severe);
        }
        severe.clear();
        DefaultGenProbe.reset();
        try (FurnaceTestServer server = start("nothing", "GenPlug:a", "GenPlug:b")) {
            World w = Bukkit.createWorld(new WorldCreator("nothing"));
            assertNull(w.getGenerator());
            assertEquals(List.of("Could not set generator for default world 'nothing': Plugin 'GenPlug v1' lacks a default world generator",
                    "Could not set biome provider for default world 'nothing': Plugin 'GenPlug v1' lacks a default world biome provider"), severe);
            assertEquals(List.of("GenPlug:enable", "GenPlug:generator nothing a", "GenPlug:provider nothing b"), DefaultGenProbe.LOG);
            assertEquals(Material.BEDROCK, onRegion(w, 0, 0, () -> w.getBlockAt(0, -64, 0).getType()), "바닐라 생성기로 열린다");
        }
    }
}
