// Bukkit ChunkGenerator·BlockPopulator → Furnace 생성 훅·장식기(T-217) — CraftBukkit CustomChunkGenerator·CraftChunkData·OldCraftChunkData·
// CraftLimitedRegion·ChunkGenerator#applyBiomeDecoration(populators) 와 같은 일. 난수 씨앗과 부르는 순서는 Paper 1.21.1 그대로다.
package org.domi.furnace.compat.paper;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.domi.furnace.api.worldgen.ChunkGenerationHooks;
import org.domi.furnace.api.worldgen.DecorationRegion;
import org.domi.furnace.api.worldgen.GenerationChunk;
import org.domi.furnace.api.worldgen.GenerationHeightmap;
import org.domi.furnace.api.worldgen.GenerationStage;
import org.domi.furnace.compat.i18n.CompatLang;

public final class BukkitChunkGenerators {

    private static final Logger LOG = Logger.getLogger("Furnace-Compat");

    private BukkitChunkGenerators() {}

    // ------------------------------------------------------------------ 씨앗

    /** 바닐라 Mth.getSeed(x, y, z). */
    static long mthSeed(int x, int y, int z) {
        long l = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        l = l * l * 42317861L + l * 11L;
        return l >> 16;
    }

    /** CustomChunkGenerator 의 shouldGenerate* 씨앗: Mth.getSeed(x, salt.hashCode(), z) ^ 월드 시드. */
    static long shouldSeed(int chunkX, String salt, int chunkZ, long worldSeed) { return mthSeed(chunkX, salt.hashCode(), chunkZ) ^ worldSeed; }

    /** generate*·getBaseHeight·옛 generateChunkData 의 씨앗. */
    static long chunkSeed(int chunkX, int chunkZ) { return (long) chunkX * 341873128712L + (long) chunkZ * 132897987541L; }

    /**
     * Paper 가 생성기에 넘기는 난수 — {@code RandomSourceWrapper.RandomWrapper(new WorldgenRandom(new LegacyRandomSource(0)))} 와 같다.
     * 덮어쓴 메서드 묶음도 같게 둔다(나머지 java.util.Random 메서드는 RandomWrapper 처럼 Random 자체 상태를 쓴다 — 생성자의 setSeed 가 무시돼 상태 0).
     * 가우스 값은 바깥 WorldgenRandom 의 것이라 setSeed 로 지워지지 않는다(WorldgenRandom#setSeed 는 안쪽 원천만 다시 심는다 — 가정, 컨텍스트 노트).
     */
    static final class PaperRandom extends Random {
        private static final long MULTIPLIER = 0x5DEECE66DL;
        private static final long MASK = (1L << 48) - 1;
        private long lcg;   // 안쪽 LegacyRandomSource 상태(생성 직후는 시드 0)
        private boolean ready;
        private boolean haveNextNextGaussian;
        private double nextNextGaussian;

        PaperRandom() {
            super();
            ready = true;
            lcg = (0L ^ MULTIPLIER) & MASK;
        }

        @Override
        public void setSeed(long seed) {
            if (!ready) return;   // RandomWrapper: random == null 인 생성자 안 호출은 무시
            lcg = (seed ^ MULTIPLIER) & MASK;
        }

        private int bits(int bits) {
            lcg = lcg * MULTIPLIER + 0xBL & MASK;
            return (int) (lcg >> 48 - bits);
        }

        @Override
        public int nextInt() { return bits(32); }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("Bound must be positive");
            if ((bound & bound - 1) == 0) return (int) ((long) bound * (long) bits(31) >> 31);
            int i, j;
            do {
                i = bits(31);
                j = i % bound;
            } while (i - j + (bound - 1) < 0);
            return j;
        }

        @Override
        public long nextLong() {
            int hi = bits(32);
            int lo = bits(32);
            return ((long) hi << 32) + (long) lo;
        }

        @Override
        public boolean nextBoolean() { return bits(1) != 0; }

        @Override
        public float nextFloat() { return bits(24) * 5.9604645E-8F; }

        @Override
        public double nextDouble() {
            int i = bits(26);
            int j = bits(27);
            long l = ((long) i << 27) + (long) j;
            return (double) l * 1.1102230246251565E-16;
        }

        @Override
        public double nextGaussian() {
            if (haveNextNextGaussian) {
                haveNextNextGaussian = false;
                return nextNextGaussian;
            }
            while (true) {
                double d = 2.0 * nextDouble() - 1.0;
                double e = 2.0 * nextDouble() - 1.0;
                double f = d * d + e * e;
                if (!(f >= 1.0) && f != 0.0) {
                    double g = Math.sqrt(-2.0 * Math.log(f) / f);
                    nextNextGaussian = e * g;
                    haveNextNextGaussian = true;
                    return d * g;
                }
            }
        }

        @Override
        public int nextInt(int origin, int bound) {
            if (origin >= bound) throw new IllegalArgumentException("bound - origin is non positive");
            return origin + nextInt(bound - origin);
        }

        /** WorldgenRandom#setDecorationSeed. */
        long setDecorationSeed(long worldSeed, int x, int z) {
            setSeed(worldSeed);
            long l = nextLong() | 1L;
            long m = nextLong() | 1L;
            long n = (long) x * l + (long) z * m ^ worldSeed;
            setSeed(n);
            return n;
        }
    }

    // ------------------------------------------------------------------ 바이옴

    public static Biome biomeOf(String key) {
        NamespacedKey k = key == null ? null : NamespacedKey.fromString(key);
        Biome b = k == null ? null : Biome.byKey(k);
        return b != null ? b : Biome.CUSTOM;   // CraftBiome: 레지스트리에 없는 바이옴은 CUSTOM
    }

    public static org.domi.furnace.api.world.BlockData furnaceData(BlockData data) {
        return ((CompatBlockData) data).furnace();   // CraftBukkit 의 (CraftBlockData) 캐스트와 같다 — 다른 구현이면 ClassCastException
    }

    // ------------------------------------------------------------------ ChunkData

    /** CraftChunkData·OldCraftChunkData#setRegion 의 자르기(끝은 제외). 빈 상자면 null. */
    public static int[] clamp(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, int minHeight, int maxHeight) {
        if (xMin > 0xf || yMin >= maxHeight || zMin > 0xf) return null;
        if (xMin < 0) xMin = 0;
        if (yMin < minHeight) yMin = minHeight;
        if (zMin < 0) zMin = 0;
        if (xMax > 0x10) xMax = 0x10;
        if (yMax > maxHeight) yMax = maxHeight;
        if (zMax > 0x10) zMax = 0x10;
        if (xMin >= xMax || yMin >= yMax || zMin >= zMax) return null;
        return new int[] {xMin, yMin, zMin, xMax, yMax, zMax};
    }

    /** {@code Server#createChunkData} 의 OldCraftChunkData(생성자는 패키지 전용). */
    public static ChunkGenerator.ChunkData createOldChunkData(int minHeight, int maxHeight) { return CraftAccess.newOldChunkData(minHeight, maxHeight); }

    /** CustomBiomeGrid — 옛 generateChunkData 의 바이옴 격자(청크에 바로 쓴다). */
    @SuppressWarnings("deprecation")
    static final class Grid implements ChunkGenerator.BiomeGrid {
        private final GenerationChunk chunk;
        private final int minHeight, maxHeight;

        Grid(GenerationChunk chunk) {
            this.chunk = chunk;
            this.minHeight = chunk.minY();
            this.maxHeight = chunk.maxY() + 1;
        }

        @Override public Biome getBiome(int x, int z) { return getBiome(x, 0, z); }

        @Override
        public void setBiome(int x, int z, Biome bio) {
            for (int y = minHeight; y < maxHeight; y += 4) setBiome(x, y, z, bio);
        }

        @Override
        public Biome getBiome(int x, int y, int z) { return biomeOf(chunk.biome(x & 15, Math.clamp(y, minHeight, maxHeight - 1), z & 15)); }

        @Override
        public void setBiome(int x, int y, int z, Biome bio) {
            if (bio == Biome.CUSTOM) throw new IllegalArgumentException("Cannot set the biome to " + bio);
            chunk.setBiome(x & 15, y, z & 15, bio.getKey().toString());
        }
    }

    // ------------------------------------------------------------------ 생성기 → 훅

    /** Bukkit 생성기를 Furnace 생성 훅 객체로. info 는 월드가 목록에 오르기 전 생성기에 넘길 월드 정보(서버가 prepare 로 채운다). */
    public static Adapted adapt(ChunkGenerator generator, org.bukkit.craftbukkit.generator.CraftWorldInfo info) {
        return new Adapted(java.util.Objects.requireNonNull(generator, "generator"), java.util.Objects.requireNonNull(info, "info"));
    }

    /** CustomChunkGenerator 와 같은 자리의 훅. {@link #bukkit()} 은 원래 생성기(CraftWorld#getGenerator). */
    public static final class Adapted implements ChunkGenerationHooks {
        private final ChunkGenerator generator;
        private final org.bukkit.craftbukkit.generator.CraftWorldInfo info;
        private volatile World world;   // 목록에 오른 뒤 그 CraftWorld — Paper 는 생성기에 늘 그 CraftWorld 를 넘긴다
        private volatile boolean newApi;

        private Adapted(ChunkGenerator generator, org.bukkit.craftbukkit.generator.CraftWorldInfo info) {
            this.generator = generator;
            this.info = info;
        }

        public ChunkGenerator bukkit() { return generator; }

        /** 생성기에 넘기는 월드 정보 — 목록에 오른 뒤엔 그 월드. */
        WorldInfo worldInfo() {
            World w = world;
            return w != null ? w : info;
        }

        private World world() {
            World w = world;
            if (w == null) throw new IllegalStateException("the world of this chunk generator is not up yet");
            return w;
        }

        @Override
        public String name() { return generator.getClass().getName(); }

        @Override
        public void prepare(org.domi.furnace.api.worldgen.BiomeProvider.Context ctx) { CraftAccess.worldInfoPrepare(info, ctx); }

        @Override
        public void attached(String levelKey) {
            World w = Compat.world(levelKey);
            if (w == null) throw new IllegalStateException("world " + levelKey + " is not registered");
            world = w;
            CraftAccess.worldInfoBind(info, w);
            w.getPopulators().addAll(generator.getDefaultPopulators(w));   // CraftBukkit initWorld
        }

        @Override
        public org.domi.furnace.api.world.Location fixedSpawn(String levelKey) {
            World w = world();
            Location spawn = generator.getFixedSpawnLocation(w, new Random(w.getSeed()));   // CraftBukkit setInitialSpawn
            if (spawn == null) return null;
            if (spawn.getWorld() != w) {
                throw new IllegalStateException("Cannot set spawn point for " + w.getName() + " to be in another world (" + spawn.getWorld().getName() + ")");
            }
            return new org.domi.furnace.api.world.Location(w.getName(), spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ(), spawn.getYaw(), 0f);
        }

        @Override
        public boolean runVanilla(GenerationStage stage, int chunkX, int chunkZ) {
            String salt = switch (stage) {
                case NOISE -> "should-noise";
                case SURFACE -> "should-surface";
                case CARVERS -> "should-caves";
                case STRUCTURES -> "should-structures";
                case DECORATION -> "should-decoration";
                case MOBS -> "should-mobs";
                case FEATURES -> null;   // Bukkit 은 지형지물만 따로 끄지 않는다(DECORATION 이 통째로)
            };
            if (salt == null) return true;
            WorldInfo wi = worldInfo();
            PaperRandom r = new PaperRandom();
            r.setSeed(shouldSeed(chunkX, salt, chunkZ, wi.getSeed()));
            return switch (stage) {
                case NOISE -> generator.shouldGenerateNoise(wi, r, chunkX, chunkZ);
                case SURFACE -> generator.shouldGenerateSurface(wi, r, chunkX, chunkZ);
                case CARVERS -> generator.shouldGenerateCaves(wi, r, chunkX, chunkZ);
                case STRUCTURES -> generator.shouldGenerateStructures(wi, r, chunkX, chunkZ);
                case DECORATION -> generator.shouldGenerateDecorations(wi, r, chunkX, chunkZ);
                case MOBS -> generator.shouldGenerateMobs(wi, r, chunkX, chunkZ);
                case FEATURES -> true;
            };
        }

        @Override
        public void noise(GenerationChunk chunk) {
            var data = CraftAccess.newChunkData(chunk);
            PaperRandom r = new PaperRandom();
            r.setSeed(chunkSeed(chunk.chunkX(), chunk.chunkZ()));
            try {
                generator.generateNoise(worldInfo(), r, chunk.chunkX(), chunk.chunkZ(), data);
            } finally {
                data.breakLink();
            }
        }

        @Override
        public void surface(GenerationChunk chunk) {
            int x = chunk.chunkX(), z = chunk.chunkZ();
            WorldInfo wi = worldInfo();
            var data = CraftAccess.newChunkData(chunk);
            try {
                PaperRandom r = new PaperRandom();
                r.setSeed(chunkSeed(x, z));
                generator.generateSurface(wi, r, x, z, data);
                r = new PaperRandom();   // generateBedrock 은 새 난수(같은 씨앗)
                r.setSeed(chunkSeed(x, z));
                generator.generateBedrock(wi, r, x, z, data);
            } finally {
                data.breakLink();
            }
            if (newApi) return;
            oldApi(chunk, x, z);
        }

        /** 옛 generateChunkData — 표면 단계 끝(구조물 전)에 부른다. 처음 UnsupportedOperationException 이면 다시 부르지 않는다. */
        @SuppressWarnings("deprecation")
        private void oldApi(GenerationChunk chunk, int x, int z) {
            Random random = new Random(chunkSeed(x, z));   // Paper 는 생성기 하나에 Random 하나를 다시 심어 쓴다 — 새로 만든 것과 같은 값
            Grid grid = new Grid(chunk);
            ChunkGenerator.ChunkData data;
            try {
                if (generator.isParallelCapable()) {
                    data = generator.generateChunkData(world(), random, x, z, grid);
                } else {
                    synchronized (this) {
                        data = generator.generateChunkData(world(), random, x, z, grid);
                    }
                }
            } catch (UnsupportedOperationException e) {
                newApi = true;
                return;
            }
            if (!(data instanceof org.bukkit.craftbukkit.generator.OldCraftChunkData old)) {
                throw new IllegalArgumentException("Plugins must use createChunkData(World) rather than implementing ChunkData: " + data);
            }
            CraftAccess.oldChunkDataApply(old, chunk);
        }

        @Override
        public void carvers(GenerationChunk chunk) {
            var data = CraftAccess.newChunkData(chunk);
            PaperRandom r = new PaperRandom();
            r.setDecorationSeed(chunk.seed(), 0, 0);
            try {
                generator.generateCaves(worldInfo(), r, chunk.chunkX(), chunk.chunkZ(), data);
            } finally {
                data.breakLink();
            }
        }

        @Override
        public java.util.OptionalInt baseHeight(GenerationHeightmap heightmap, int blockX, int blockZ) {
            PaperRandom r = new PaperRandom();
            r.setSeed(chunkSeed(blockX >> 4, blockZ >> 4));
            // 기본 구현의 UnsupportedOperationException 은 그대로 서버로 — 서버가 이 훅에 다시 묻지 않고 바닐라 높이를 쓴다(Paper 와 같다)
            return java.util.OptionalInt.of(generator.getBaseHeight(worldInfo(), r, blockX, blockZ, HeightMap.valueOf(heightmap.name())));
        }
    }

    // ------------------------------------------------------------------ BlockPopulator

    /** CraftWorld#getPopulators 의 목록 — 월드 장식기 하나로 서버에 걸린다(빈 목록이면 아무 일도 없다). */
    public static List<BlockPopulator> populators(World world, org.domi.furnace.api.world.World handle) {
        Populators list = new Populators(world);
        try {
            handle.addChunkDecorator(list::run);
        } catch (UnsupportedOperationException e) {
            // 장식 단계가 없는 생성기(디버그 월드) — Paper 도 그 생성기는 BlockPopulator 를 부르지 않는다
        }
        return list;
    }

    private static final Set<Class<?>> LEGACY_WARNED = ConcurrentHashMap.newKeySet();

    static final class Populators extends CopyOnWriteArrayList<BlockPopulator> {
        private final transient World world;

        Populators(World world) { this.world = world; }

        /** ChunkGenerator#applyBiomeDecoration 끝: 목록 순서대로, 각자 새 난수(WorldgenRandom(LegacyRandomSource(seed)).setDecorationSeed(seed, 청크 x, 청크 z)). */
        void run(DecorationRegion region) {
            if (isEmpty()) return;
            int x = region.originChunkX(), z = region.originChunkZ();
            long seed = region.seed();
            org.bukkit.craftbukkit.generator.CraftLimitedRegion limited = CraftAccess.newLimitedRegion(world, region);
            try {
                for (BlockPopulator p : this) {
                    warnLegacy(p);
                    PaperRandom r = new PaperRandom();
                    r.setDecorationSeed(seed, x, z);
                    p.populate(world, r, x, z, limited);
                }
            } finally {
                limited.breakLink();
            }
        }

        @Override
        public boolean equals(Object o) { return this == o; }

        @Override
        public int hashCode() { return System.identityHashCode(this); }
    }

    /** 옛 populate(World, Random, Chunk) 만 덮어쓴 BlockPopulator 는 아직 불리지 않는다(T-218) — 클래스마다 한 번 알린다. */
    private static void warnLegacy(BlockPopulator p) {
        Class<?> c = p.getClass();
        if (LEGACY_WARNED.contains(c)) return;
        boolean legacy = declares(c, "populate", World.class, Random.class, org.bukkit.Chunk.class);
        boolean modern = declares(c, "populate", WorldInfo.class, Random.class, int.class, int.class, LimitedRegion.class);
        if (legacy && !modern && LEGACY_WARNED.add(c)) LOG.warning(CompatLang.t("compat.populator-legacy-not-called", c.getName()));
        else LEGACY_WARNED.add(c);
    }

    private static boolean declares(Class<?> c, String name, Class<?>... params) {
        for (Class<?> k = c; k != null && k != BlockPopulator.class; k = k.getSuperclass()) {
            try {
                k.getDeclaredMethod(name, params);
                return true;
            } catch (NoSuchMethodException e) {
                // 위 클래스로
            }
        }
        return false;
    }
}
