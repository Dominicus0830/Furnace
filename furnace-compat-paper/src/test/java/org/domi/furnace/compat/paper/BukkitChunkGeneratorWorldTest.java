package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.util.Vector;
import org.bukkit.generator.WorldInfo;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-217: Bukkit WorldCreator#generator 로 만드는 월드(CraftBukkit CustomChunkGenerator). 난수 기대값은 Paper 식을 시험 안에서 따로 계산한다 —
 * WorldgenRandom(LegacyRandomSource) 의 setSeed·nextInt·nextLong 은 java.util.Random 과 같은 LCG 라 그것으로 대조한다.
 */
class BukkitChunkGeneratorWorldTest {

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

    /** 그 열의 [y0, y1] 에 공기가 아닌 블록이 있나(바닐라 노이즈는 동굴 공기가 섞인다). */
    private static boolean solidBetween(World w, int x, int z, int y0, int y1) {
        for (int y = y0; y <= y1; y++) if (!w.getBlockAt(x, y, z).getType().isAir()) return true;
        return false;
    }

    /** 바닐라 Mth.getSeed — Paper shouldGenerate* 씨앗의 기대값을 따로 계산한다. */
    private static long mthSeed(int x, int y, int z) {
        long l = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        l = l * l * 42317861L + l * 11L;
        return l >> 16;
    }

    private static long chunkSeed(int x, int z) { return (long) x * 341873128712L + (long) z * 132897987541L; }

    /** WorldgenRandom#setDecorationSeed(seed, x, z) 의 값. */
    private static long decorationSeed(long seed, int x, int z) {
        Random r = new Random(seed);
        long a = r.nextLong() | 1L;
        long b = r.nextLong() | 1L;
        return (long) x * a + (long) z * b ^ seed;
    }

    /** 새 API: 돌 4층(-64..-61) + 맨 위 한 칸 잔디(0,0), 사막 바이옴 공급자. 받은 난수의 첫 값을 청크마다 적는다. */
    static final class Layers extends ChunkGenerator {
        final Map<String, Long> noiseFirst = new ConcurrentHashMap<>();
        final Map<String, Long> bedrockFirst = new ConcurrentHashMap<>();
        final Map<String, Integer> cavesFirst = new ConcurrentHashMap<>();
        final List<String> infos = new CopyOnWriteArrayList<>();
        final List<Object> noiseInfo = new CopyOnWriteArrayList<>();
        final Map<String, Biome> seenBiome = new ConcurrentHashMap<>();
        final java.util.Set<String> classes = ConcurrentHashMap.newKeySet();
        final BiomeProvider desert = new BiomeProvider() {
            @Override public Biome getBiome(WorldInfo w, int x, int y, int z) { return Biome.DESERT; }
            @Override public List<Biome> getBiomes(WorldInfo w) { return List.of(Biome.DESERT); }
        };

        @Override
        public BiomeProvider getDefaultBiomeProvider(WorldInfo w) {
            infos.add(w.getName() + " " + w.getSeed());
            classes.add("info " + w.getClass().getName());
            return desert;
        }

        @Override
        public void generateNoise(WorldInfo w, Random random, int x, int z, ChunkData data) {
            noiseFirst.put(x + "," + z, random.nextLong());
            noiseInfo.add(w);
            classes.add("data " + data.getClass().getName());
            seenBiome.put(x + "," + z, data.getBiome(3, 0, 3));
            assertEquals(-64, data.getMinHeight());
            assertEquals(320, data.getMaxHeight());
            data.setRegion(-5, -100, -5, 99, -60, 99, Material.STONE);   // 잘려서 x·z 0..15, y -64..-61
            data.setBlock(16, -60, 5, Material.GOLD_BLOCK);              // 청크 밖은 무시(감아서 0,5 에 쓰지 않는다)
            assertEquals(Material.AIR, data.getType(16, -61, 0));
            assertEquals(Material.STONE, data.getType(15, -61, 15));
        }

        @Override
        public void generateSurface(WorldInfo w, Random random, int x, int z, ChunkData data) {
            data.setBlock(0, -60, 0, Material.GRASS_BLOCK.createBlockData());
        }

        @Override
        public void generateBedrock(WorldInfo w, Random random, int x, int z, ChunkData data) {
            bedrockFirst.put(x + "," + z, random.nextLong());
            data.setBlock(0, -64, 0, Material.BEDROCK);
        }

        @Override
        public void generateCaves(WorldInfo w, Random random, int x, int z, ChunkData data) {
            cavesFirst.put(x + "," + z, random.nextInt());
        }
    }

    @Test
    void newApiGeneratesChunksLikeCustomChunkGenerator() throws Exception {
        try (FurnaceTestServer server = start()) {
            Layers g = new Layers();
            World w = Bukkit.createWorld(new WorldCreator("layers").seed(77L).generator(g));
            assertSame(g, w.getGenerator(), "Paper: 만들 때 준 생성기");
            assertSame(g.desert, w.getBiomeProvider(), "공급자가 없으면 생성기의 기본 공급자");
            assertEquals(List.of("layers 77"), g.infos);
            assertSame(g, new WorldCreator("copy").copy(w).generator(), "WorldCreator#copy 도 옮긴다");

            var top = onRegion(w, 0, 0, () -> List.<Object>of(w.getBlockAt(0, -60, 0).getType(), w.getBlockAt(1, -60, 0).getType(),
                    w.getBlockAt(1, -61, 1).getType(), w.getBlockAt(0, -64, 0).getType(), w.getBlockAt(5, -64, 5).getType(), w.getBiome(5, 0, 5)));
            assertEquals(List.of(Material.GRASS_BLOCK, Material.AIR, Material.STONE, Material.BEDROCK, Material.STONE, Biome.DESERT), top,
                    "바닐라 단계 없음(shouldGenerate* 기본 거짓) — 플러그인이 놓은 것뿐");
            assertEquals(List.of(Material.AIR, Material.AIR), onRegion(w, 16, 5, () -> List.of(w.getBlockAt(16, -60, 5).getType(), w.getBlockAt(0, -60, 5).getType())), "청크 밖 쓰기는 무시");
            assertEquals(Biome.DESERT, g.seenBiome.get("0,0"), "ChunkData#getBiome 은 그 청크의 바이옴");
            assertSame(w, g.noiseInfo.get(0), "Paper 는 WorldInfo 자리에 그 CraftWorld 를 넘긴다");
            // T-264 P2c-3: 플러그인이 보는 클래스가 Paper 와 같다(ChunkData 는 관측표 generator.callback.chunkData, 기본 공급자에 넘기는 WorldInfo 는
            // Paper createWorld 가 만드는 CraftWorldInfo — 관측표에 없는 가정)
            assertEquals(java.util.Set.of("info org.bukkit.craftbukkit.generator.CraftWorldInfo", "data org.bukkit.craftbukkit.generator.CraftChunkData"), g.classes);

            assertEquals(new Random(chunkSeed(0, 0)).nextLong(), g.noiseFirst.get("0,0"), "generateNoise 난수 = x*341873128712 + z*132897987541");
            assertEquals(new Random(chunkSeed(1, 0)).nextLong(), g.noiseFirst.get("1,0"));
            assertEquals(new Random(chunkSeed(0, 0)).nextLong(), g.bedrockFirst.get("0,0"), "generateBedrock 은 같은 씨앗의 새 난수");
            assertEquals(new Random(77L).nextInt(), g.cavesFirst.get("0,0"), "generateCaves 는 setDecorationSeed(시드, 0, 0) = 시드");
        }
    }

    /** shouldGenerate* 씨앗(Mth.getSeed(x, "should-…".hashCode(), z) ^ 시드)과 바닐라 단계 켜기. */
    static final class Vanilla extends ChunkGenerator {
        final Map<String, Integer> noiseAsk = new ConcurrentHashMap<>();
        final Map<String, Integer> decoAsk = new ConcurrentHashMap<>();
        final Map<String, Integer> mobsAsk = new ConcurrentHashMap<>();

        @Override
        public boolean shouldGenerateNoise(WorldInfo w, Random random, int x, int z) {
            noiseAsk.put(x + "," + z, random.nextInt());
            return true;
        }

        @Override public boolean shouldGenerateSurface() { return true; }
        @Override public boolean shouldGenerateCaves() { return true; }

        @Override
        public boolean shouldGenerateDecorations(WorldInfo w, Random random, int x, int z) {
            decoAsk.put(x + "," + z, random.nextInt(1000));
            return true;
        }

        @Override
        public boolean shouldGenerateMobs(WorldInfo w, Random random, int x, int z) {
            mobsAsk.put(x + "," + z, random.nextInt(1000));
            return true;
        }
    }

    @Test
    void shouldGenerateSeedsAndVanillaStagesFollowPaper() throws Exception {
        try (FurnaceTestServer server = start()) {
            Vanilla g = new Vanilla();
            World w = Bukkit.createWorld(new WorldCreator("vanillaish").seed(1234L).generator(g));
            assertTrue(onRegion(w, 8, 8, () -> solidBetween(w, 8, 8, -60, -40)), "바닐라 노이즈를 켰다");
            assertNull(w.getBiomeProvider(), "기본 공급자 없음 — 바닐라 바이옴");
            assertEquals(new Random(mthSeed(0, "should-noise".hashCode(), 0) ^ 1234L).nextInt(), g.noiseAsk.get("0,0"));
            assertEquals(new Random(mthSeed(0, "should-decoration".hashCode(), 0) ^ 1234L).nextInt(1000), g.decoAsk.get("0,0"));
            assertEquals(new Random(mthSeed(0, "should-mobs".hashCode(), 0) ^ 1234L).nextInt(1000), g.mobsAsk.get("0,0"));
        }
    }

    /** 옛 API: 손댄 섹션(16칸)만 통째로 갈아 끼우고 바이옴 격자는 청크에 바로 쓴다. */
    static final class Old extends ChunkGenerator {
        final Map<String, Long> first = new ConcurrentHashMap<>();
        volatile int calls;

        @Override public boolean shouldGenerateNoise() { return true; }

        @Override
        @SuppressWarnings("deprecation")
        public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
            calls++;
            first.put(x + "," + z, random.nextLong());
            ChunkData d = createChunkData(world);
            assertEquals("org.bukkit.craftbukkit.generator.OldCraftChunkData", d.getClass().getName(), "T-264 P2c-3: Paper 와 같은 클래스");
            d.setRegion(0, -64, 0, 16, -62, 16, Material.GLASS);   // 맨 아래 섹션(-64..-49)만 생긴다
            assertEquals(Material.GLASS, d.getType(3, -63, 3));
            assertEquals(Material.AIR, d.getType(3, -40, 3));
            assertThrows(UnsupportedOperationException.class, () -> d.getBiome(0, 0, 0));
            for (int bx = 0; bx < 16; bx++) for (int bz = 0; bz < 16; bz++) biome.setBiome(bx, bz, Biome.BADLANDS);
            return d;
        }
    }

    @Test
    void oldApiReplacesTouchedSectionsOnly() throws Exception {
        try (FurnaceTestServer server = start()) {
            Old g = new Old();
            World w = Bukkit.createWorld(new WorldCreator("oldapi").seed(9L).generator(g));
            var got = onRegion(w, 4, 4, () -> List.<Object>of(w.getBlockAt(4, -63, 4).getType(), w.getBlockAt(4, -55, 4).getType(),
                    solidBetween(w, 4, 4, -48, -33), w.getBiome(4, 100, 4)));
            assertEquals(Material.GLASS, got.get(0));
            assertEquals(Material.AIR, got.get(1), "손댄 섹션의 나머지는 공기(섹션째 갈아 끼운다)");
            assertEquals(true, got.get(2), "손대지 않은 섹션은 바닐라 노이즈 그대로");
            assertEquals(Biome.BADLANDS, got.get(3), "BiomeGrid#setBiome(x, z) 는 모든 높이");
            assertEquals(new Random(chunkSeed(0, 0)).nextLong(), g.first.get("0,0"));
        }
    }

    /** 기본 BlockPopulator·월드 BlockPopulator: 바닐라 장식 뒤, 각자 새 난수, LimitedRegion 은 3×3 청크. */
    static final class Populated extends ChunkGenerator {
        final Marker marker = new Marker();

        @Override
        public void generateNoise(WorldInfo w, Random random, int x, int z, ChunkData data) {
            data.setRegion(0, -64, 0, 16, -60, 16, Material.STONE);   // 맨 위 돌 y=-61
        }

        @Override
        public List<BlockPopulator> getDefaultPopulators(World world) { return List.of(marker); }
    }

    static final class Marker extends BlockPopulator {
        final Map<String, Long> first = new ConcurrentHashMap<>();
        final List<String> checks = new CopyOnWriteArrayList<>();
        volatile LimitedRegion kept;

        @Override
        public void populate(WorldInfo w, Random random, int x, int z, LimitedRegion region) {
            first.put(x + "," + z, random.nextLong());
            int bx = region.getCenterBlockX(), bz = region.getCenterBlockZ();
            int top = region.getHighestBlockYAt(bx + 8, bz + 8);
            region.setType(bx + 8, top, bz + 8, Material.GOLD_BLOCK);
            if (x == 0 && z == 0) {
                kept = region;
                checks.add(region.getBuffer() + " " + top + " " + region.isInRegion(bx - 16, 0, bz) + " " + region.isInRegion(bx - 17, 0, bz)
                        + " " + region.isInRegion(bx + 31, 319, bz) + " " + region.isInRegion(bx + 32, 0, bz) + " " + region.isInRegion(bx, 320, bz)
                        + " " + region.getType(bx, -61, bz) + " " + region.getHighestBlockYAt(bx, bz, HeightMap.WORLD_SURFACE_WG));
                var e = assertThrows(IllegalArgumentException.class, () -> region.getType(bx + 40, 0, bz));
                checks.add(e.getMessage());
                // Paper 1.21.1 LimitedRegion 멤버: getWorld·getKey·Vector 판은 바로, 블록 틱·유체 상태는 UnsupportedFeature(T-218)
                checks.add((region.getWorld() == w) + " " + region.getKey() + " " + region.getBlockData(new Vector(bx, -61, bz)).getMaterial());
                assertThrows(UnsupportedFeature.class, () -> region.scheduleBlockUpdate(new Vector(bx, -61, bz)));
                assertThrows(UnsupportedFeature.class, () -> region.getFluidData(bx, -61, bz));
            }
        }
    }

    @Test
    void populatorsRunAfterDecorationWithPaperRandomAndRegion() throws Exception {
        try (FurnaceTestServer server = start()) {
            Populated g = new Populated();
            World w = Bukkit.createWorld(new WorldCreator("populated").seed(31L).generator(g));
            assertEquals(List.of(g.marker), w.getPopulators(), "기본 BlockPopulator 가 월드 목록에 든다");
            assertEquals(Material.GOLD_BLOCK, onRegion(w, 8, 8, () -> w.getBlockAt(8, -60, 8).getType()));
            assertEquals(List.of("16 -60 true false true false false STONE -60",
                    "Coordinates " + 40 + ", 0, 0 are not in the region", "true " + w.getKey() + " STONE"), g.marker.checks);
            assertEquals(new Random(decorationSeed(31L, 0, 0)).nextLong(), g.marker.first.get("0,0"),
                    "WorldgenRandom(seed).setDecorationSeed(seed, 청크 x, 청크 z)");
            assertThrows(IllegalStateException.class, () -> g.marker.kept.getType(0, -61, 0), "populate 가 끝나면 끊긴다");
            assertThrows(IllegalStateException.class, () -> g.marker.kept.getWorld(), "getWorld 도 연결을 거친다(CraftLimitedRegion)");

            // 생성기가 없는 월드도 목록을 돈다(Paper: 모든 월드의 getPopulators)
            World flat = Bukkit.createWorld(new WorldCreator("flatpop").type(WorldType.FLAT));
            Marker m = new Marker();
            flat.getPopulators().add(m);
            assertEquals(Material.GOLD_BLOCK, onRegion(flat, 400, 400, () -> flat.getBlockAt(408, -60, 408).getType()));
            assertTrue(m.first.containsKey("25,25"));
        }
    }

    /** getFixedSpawnLocation: 새 월드만, 난수는 new Random(시드), 다른 월드면 Paper 문구의 IllegalStateException. */
    static final class Spawn extends ChunkGenerator {
        final List<Integer> rolls = new CopyOnWriteArrayList<>();
        volatile World other;

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            rolls.add(random.nextInt());
            return new Location(other != null ? other : world, 3.7, 70, -2.2, 90f, 10f);
        }
    }

    @Test
    void fixedSpawnLocationAndRestartRules() throws Exception {
        try (FurnaceTestServer server = start()) {
            Spawn g = new Spawn();
            World w = Bukkit.createWorld(new WorldCreator("spawned").seed(5L).generator(g));
            Location s = w.getSpawnLocation();
            assertEquals(List.of(3, 70, -3), List.of(s.getBlockX(), s.getBlockY(), s.getBlockZ()));
            assertEquals(90f, s.getYaw());
            assertEquals(List.of(new Random(5L).nextInt()), g.rolls);

            // 재시작 흉내: 생성기 표시가 남고, 생성기와 함께 다시 만들면 그 생성기(고정 스폰은 다시 묻지 않는다), 없이 만들면 바닐라로 열고 표시를 지운다
            var fw = ((org.bukkit.craftbukkit.CraftWorld) w).furnaceHandle();
            assertTrue(server.api().unloadWorld(fw, true).get(60, TimeUnit.SECONDS));
            var stored = server.api().storedWorldCreator("spawned");
            assertTrue(stored.needsGenerationHooksObject());
            assertEquals(Spawn.class.getName(), stored.generationHooksObjectName());

            Spawn again = new Spawn();
            World back = Bukkit.createWorld(new WorldCreator("spawned").generator(again));
            assertSame(again, back.getGenerator());
            assertTrue(again.rolls.isEmpty(), "이미 있는 월드의 스폰은 level.dat 의 것");
            assertEquals(5L, back.getSeed());
            assertTrue(server.api().unloadWorld(((org.bukkit.craftbukkit.CraftWorld) back).furnaceHandle(), true).get(60, TimeUnit.SECONDS));

            World plain = Bukkit.createWorld(new WorldCreator("spawned"));
            assertNull(plain.getGenerator());
            assertFalse(server.api().storedWorldCreator("spawned").needsGenerationHooksObject());

            Spawn elsewhere = new Spawn();
            elsewhere.other = plain;
            var e = assertThrows(IllegalStateException.class, () -> Bukkit.createWorld(new WorldCreator("lost").generator(elsewhere)));
            assertEquals("Cannot set spawn point for lost to be in another world (spawned)", e.getMessage());
            assertNull(Bukkit.getWorld("lost"));
        }
    }
}
