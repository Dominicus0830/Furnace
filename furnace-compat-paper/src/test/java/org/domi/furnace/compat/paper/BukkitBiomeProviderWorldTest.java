package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeParameterPoint;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** T-210: Bukkit WorldCreator#biomeProvider 로 만드는 보통 월드(CraftBukkit CustomWorldChunkManager), 평지 generatorSettings, 다시 열기. */
class BukkitBiomeProviderWorldTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { RecorderPlugin.reset(); }

    private FurnaceTestServer start() throws Exception {
        Path jar = Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
        return FurnaceTestServer.builder().failFast(false).multiWorld(true).pluginJar(jar).start();
    }

    private static <T> T onRegion(World bw, int x, int z, java.util.function.Supplier<T> task) throws Exception {
        var fw = ((org.bukkit.craftbukkit.CraftWorld) bw).furnaceHandle();
        assertTrue(fw.loadChunkAsync(x >> 4, z >> 4, true).get(60, TimeUnit.SECONDS));
        return fw.scheduler(new org.domi.furnace.api.world.BlockPos(x, 0, z)).submit(task).get(60, TimeUnit.SECONDS);
    }

    /** x 가 음수면 사막, 아니면 눈 덮인 평원. 받은 WorldInfo 와 기후 점을 기록한다. */
    static final class Halves extends BiomeProvider {
        final List<String> infos = new CopyOnWriteArrayList<>();
        volatile double maxAbsTemperature;
        final java.util.Set<String> pointClasses = java.util.concurrent.ConcurrentHashMap.newKeySet();

        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) { return x < 0 ? Biome.DESERT : Biome.SNOWY_PLAINS; }

        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z, BiomeParameterPoint p) {
            maxAbsTemperature = Math.max(maxAbsTemperature, Math.abs(p.getTemperature()));
            pointClasses.add(p.getClass().getName());
            return getBiome(worldInfo, x, y, z);
        }

        @Override
        public List<Biome> getBiomes(WorldInfo w) {
            infos.add(w.getName() + " " + w.getEnvironment() + " " + w.getSeed() + " " + w.getMinHeight() + ".." + w.getMaxHeight() + " " + w.getUID());
            return List.of(Biome.DESERT, Biome.SNOWY_PLAINS);
        }
    }

    @Test
    void createWorldUsesTheBiomeProviderLikeCraftBukkit() throws Exception {
        try (FurnaceTestServer server = start()) {
            Halves p = new Halves();
            World w = Bukkit.createWorld(new WorldCreator("halves").seed(11L).biomeProvider(p));
            assertSame(p, w.getBiomeProvider(), "Paper: 만들 때 준 공급자");
            assertEquals(List.of("halves NORMAL 11 -64..320 " + w.getUID()), p.infos, "WorldInfo = 그 월드(UID 까지)");
            assertEquals(Biome.DESERT, onRegion(w, -40, 8, () -> w.getBiome(-40, 64, 8)));
            assertEquals(Biome.SNOWY_PLAINS, onRegion(w, 40, 8, () -> w.getBiome(40, 64, 8)));
            assertTrue(p.maxAbsTemperature > 0, "노이즈 월드는 바닐라 기후");
            assertEquals(java.util.Set.of("org.bukkit.craftbukkit.generator.CraftBiomeParameterPoint"), p.pointClasses, "T-264 P2c-3: Paper 와 같은 클래스");
            assertSame(p, new WorldCreator("copy").copy(w).biomeProvider(), "WorldCreator#copy 도 옮긴다");

            // 평지(오버월드)도 받는다 — 기후 0
            Halves q = new Halves();
            World flat = Bukkit.createWorld(new WorldCreator("flathalves").type(WorldType.FLAT).biomeProvider(q));
            assertEquals(Biome.DESERT, onRegion(flat, -40, 8, () -> flat.getBiome(-40, -61, 8)));
            assertEquals(Biome.SNOWY_PLAINS, onRegion(flat, 40, 8, () -> flat.getBiome(40, -61, 8)));
            assertEquals(0.0, q.maxAbsTemperature, "평지의 기후는 0(바닐라 평지 RandomState)");

            // 네더 환경도(CraftBukkit 은 모든 NoiseBasedChunkGenerator 를 감싼다)
            Halves r = new Halves();
            World nether = Bukkit.createWorld(new WorldCreator("netherhalves").environment(World.Environment.NETHER).seed(3L).biomeProvider(r));
            assertEquals(List.of("netherhalves NETHER 3 0..256 " + nether.getUID()), r.infos);
            assertEquals(Biome.DESERT, onRegion(nether, -40, 8, () -> nether.getBiome(-40, 40, 8)));
        }
    }

    /** 재시작 흉내: 내린 뒤 공급자와 함께 다시 만들면 그 공급자, 공급자 없이 만들면 Paper 처럼 바닐라 바이옴(경고 한 번)으로 열리고 표시를 지운다. */
    @Test
    void reopeningFollowsPaperForBukkitAndKeepsTheMarkerForAutoload() throws Exception {
        try (FurnaceTestServer server = start()) {
            Halves p = new Halves();
            World w = Bukkit.createWorld(new WorldCreator("kept").seed(5L).biomeProvider(p));
            var fw = ((org.bukkit.craftbukkit.CraftWorld) w).furnaceHandle();
            assertTrue(server.api().unloadWorld(fw, true).get(60, TimeUnit.SECONDS));
            var stored = server.api().storedWorldCreator("kept");
            assertTrue(stored.needsBiomeProvider());
            assertEquals(Halves.class.getName(), stored.biomeProviderName());

            Halves again = new Halves();
            World back = Bukkit.createWorld(new WorldCreator("kept").biomeProvider(again));
            assertSame(again, back.getBiomeProvider());
            assertEquals(List.of("kept NORMAL 5 -64..320 " + back.getUID()), again.infos, "시드는 level.dat 의 것");
            assertEquals(Biome.SNOWY_PLAINS, onRegion(back, 40, 40, () -> back.getBiome(40, 64, 40)));
            assertTrue(server.api().unloadWorld(((org.bukkit.craftbukkit.CraftWorld) back).furnaceHandle(), true).get(60, TimeUnit.SECONDS));

            World plain = Bukkit.createWorld(new WorldCreator("kept"));
            assertNull(plain.getBiomeProvider());
            assertFalse(server.api().storedWorldCreator("kept").needsBiomeProvider());
        }
    }

    /** generatorSettings 는 Paper 처럼 바닐라 generator-settings 규칙 그대로 서버에 간다(구조물 세트 값은 서버 시험 BiomeProviderWorldTest). */
    @Test
    void flatGeneratorSettingsFollowVanilla() throws Exception {
        try (FurnaceTestServer server = start()) {
            // 시드를 정한다 — 무작위 시드면 기본 프리셋의 마을 길(흙길)이 (3, 3)에 놓일 때가 있다
            World def = Bukkit.createWorld(new WorldCreator("deflat").seed(1L).type(WorldType.FLAT));
            assertEquals(List.of(org.bukkit.Material.GRASS_BLOCK, org.bukkit.Material.AIR, Biome.PLAINS), onRegion(def, 3, 3, () -> List.<Object>of(
                    def.getBlockAt(3, -61, 3).getType(), def.getBlockAt(3, -60, 3).getType(), def.getBiome(3, -61, 3))));
            World desert = Bukkit.createWorld(new WorldCreator("dflat").type(WorldType.FLAT)
                    .generatorSettings("{\"layers\":[{\"block\":\"minecraft:sand\",\"height\":3}],\"biome\":\"minecraft:desert\",\"features\":true}"));
            assertEquals(List.of(org.bukkit.Material.SAND, Biome.DESERT), onRegion(desert, 3, 3, () -> List.<Object>of(
                    desert.getBlockAt(3, -62, 3).getType(), desert.getBiome(3, -62, 3))));
        }
    }
}
