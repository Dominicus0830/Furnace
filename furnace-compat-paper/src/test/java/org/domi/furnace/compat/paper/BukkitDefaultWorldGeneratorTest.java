package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.domi.furnace.compat.paper.fixtures.DefaultGenProbe;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-219: 기본 월드의 생성기·바이옴 공급자 플러그인(bukkit.yml worlds.world.generator·biome-provider 자리 — furnace.properties
 * world-generator.overworld·world-biome-provider.overworld). Paper 처럼 load: STARTUP 플러그인을 켠 뒤 묻고, 고정 스폰·기본 BlockPopulator·
 * CraftWorld#getGenerator 가 그 생성기를 따른다. 못 쓰는 경우는 CraftServer#getGenerator 와 같은 문구를 남기고 바닐라로 연다.
 */
class BukkitDefaultWorldGeneratorTest {

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

    private Path jar(String load) throws Exception {
        return Jars.bukkit(tmp.resolve("gen.jar"), "plugin.yml", "name: GenPlug\nversion: '1'\nmain: " + DefaultGenProbe.class.getName()
                + "\napi-version: '1.21'\n" + (load == null ? "" : "load: " + load + "\n"), DefaultGenProbe.class);
    }

    private FurnaceTestServer start(String load, String generator, String provider) throws Exception {
        return FurnaceTestServer.builder().failFast(false).spawnChunkRadius(0)
                .generator((blocks, registries) -> NoiseWorldSource.overworld(1234L, blocks, registries), 64)
                .defaultWorldGeneration(generator, provider).pluginJar(jar(load)).start();
    }

    private static <T> T onRegion(World bw, int x, int z, java.util.function.Supplier<T> task) throws Exception {
        var fw = ((org.bukkit.craftbukkit.CraftWorld) bw).furnaceHandle();
        assertTrue(fw.loadChunkAsync(x >> 4, z >> 4, true).get(60, TimeUnit.SECONDS));
        return fw.scheduler(new org.domi.furnace.api.world.BlockPos(x, 0, z)).submit(task).get(60, TimeUnit.SECONDS);
    }

    static final BiomeProvider DESERT = new BiomeProvider() {
        @Override public Biome getBiome(WorldInfo w, int x, int y, int z) { return Biome.DESERT; }
        @Override public List<Biome> getBiomes(WorldInfo w) { return List.of(Biome.DESERT); }
    };

    /** 돌 4층(-64..-61), 사막 공급자, 고정 스폰(8, -60, 8), 기본 BlockPopulator 하나. */
    static final class Stone extends ChunkGenerator {
        final List<String> populated = new CopyOnWriteArrayList<>();
        final List<String> spawnAsked = new CopyOnWriteArrayList<>();
        final BlockPopulator populator = new BlockPopulator() {
            @Override
            public void populate(WorldInfo w, Random random, int x, int z, LimitedRegion region) { populated.add(w.getName() + " " + x + "," + z); }
        };

        @Override
        public BiomeProvider getDefaultBiomeProvider(WorldInfo w) { return DESERT; }

        @Override
        public void generateNoise(WorldInfo w, Random random, int x, int z, ChunkData data) {
            data.setRegion(0, -64, 0, 16, -60, 16, Material.STONE);
        }

        @Override
        public List<BlockPopulator> getDefaultPopulators(World world) { return List.of(populator); }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            spawnAsked.add(world.getName());
            return new Location(world, 8.5, -60, 8.5, 90f, 0f);
        }
    }

    @Test
    void startupPluginGeneratorShapesDefaultWorld() throws Exception {
        Stone gen = new Stone();
        DefaultGenProbe.GENERATOR = gen;
        try (FurnaceTestServer server = start("STARTUP", "GenPlug:flat", "")) {
            // 켠 뒤 묻는다(Paper: STARTUP onEnable → loadWorld0 getGenerator). 공급자를 따로 정하지 않았으니 공급자는 묻지 않는다
            assertEquals(List.of("GenPlug:enable", "GenPlug:generator world flat"), DefaultGenProbe.LOG);
            World w = server.onGlobal(() -> Bukkit.getWorld("world"));
            assertNotNull(w);
            assertSame(gen, w.getGenerator());
            assertSame(DESERT, w.getBiomeProvider(), "공급자가 없으면 생성기의 getDefaultBiomeProvider(loadWorld0)");
            assertTrue(w.getPopulators().contains(gen.populator), "initWorld: 기본 BlockPopulator");
            assertEquals(List.of("world"), gen.spawnAsked);
            Location spawn = server.onGlobal(w::getSpawnLocation);
            assertEquals(8, spawn.getBlockX());
            assertEquals(-60, spawn.getBlockY());
            assertEquals(8, spawn.getBlockZ());
            assertEquals(90f, spawn.getYaw());

            assertEquals(Material.STONE, onRegion(w, 100, 100, () -> w.getBlockAt(100, -61, 100).getType()));
            assertEquals(Material.AIR, onRegion(w, 100, 100, () -> w.getBlockAt(100, -60, 100).getType()));
            assertEquals(Biome.DESERT, onRegion(w, 100, 100, () -> w.getBiome(100, 0, 100)));
            assertTrue(severe.isEmpty(), severe.toString());
        }
    }

    @Test
    void providerOnlyKeepsVanillaTerrain() throws Exception {
        DefaultGenProbe.PROVIDER = DESERT;
        try (FurnaceTestServer server = start("STARTUP", "", "GenPlug")) {
            assertEquals(List.of("GenPlug:enable", "GenPlug:provider world null"), DefaultGenProbe.LOG, "id 가 없으면 null(CraftServer split)");
            World w = server.onGlobal(() -> Bukkit.getWorld("world"));
            assertNull(w.getGenerator());
            assertSame(DESERT, w.getBiomeProvider());
            assertEquals(Biome.DESERT, onRegion(w, 0, 0, () -> w.getBiome(0, 64, 0)));
            assertEquals(Material.BEDROCK, onRegion(w, 0, 0, () -> w.getBlockAt(0, -64, 0).getType()), "지형은 바닐라 노이즈");
        }
    }

    @Test
    void postWorldPluginIsNotAskedAndWorldStaysVanilla() throws Exception {
        DefaultGenProbe.GENERATOR = new Stone();
        try (FurnaceTestServer server = start(null, "GenPlug", "")) {
            assertEquals(List.of("Could not set generator for default world 'world': Plugin 'GenPlug v1' is not enabled yet (is it load:STARTUP?)"), severe);
            assertEquals(List.of("GenPlug:enable"), DefaultGenProbe.LOG);
            World w = server.onGlobal(() -> Bukkit.getWorld("world"));
            assertNull(w.getGenerator());
            assertEquals(Material.BEDROCK, onRegion(w, 0, 0, () -> w.getBlockAt(0, -64, 0).getType()));
        }
    }

    @Test
    void nullGeneratorAndProviderUsePaperMessages() throws Exception {
        try (FurnaceTestServer server = start("STARTUP", "GenPlug:x", "GenPlug:y")) {
            assertEquals(List.of("Could not set generator for default world 'world': Plugin 'GenPlug v1' lacks a default world generator",
                    "Could not set biome provider for default world 'world': Plugin 'GenPlug v1' lacks a default world biome provider"), severe);
            assertEquals(List.of("GenPlug:enable", "GenPlug:generator world x", "GenPlug:provider world y"), DefaultGenProbe.LOG);
            World w = server.onGlobal(() -> Bukkit.getWorld("world"));
            assertNull(w.getGenerator());
            assertNull(w.getBiomeProvider());
        }
    }

    @Test
    void missingPluginLeavesVanilla() throws Exception {
        try (FurnaceTestServer server = start("STARTUP", "NoSuchPlugin", "")) {
            assertTrue(severe.isEmpty(), "Bukkit 쪽은 답하지 않는다 — 서버가 알린다: " + severe);
            assertEquals(List.of("GenPlug:enable"), DefaultGenProbe.LOG);
            World w = server.onGlobal(() -> Bukkit.getWorld("world"));
            assertNull(w.getGenerator());
        }
    }
}
