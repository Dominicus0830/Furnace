// 월드 하나에 걸린 월드젠 단계 훅(불만 표 40번). 키만 쥐고 생성 때마다 저장소에서 훅을 찾는다 — 플러그인이 꺼지면 바닐라로 돌아간다.
// 플러그인 예외는 기록하고 그 훅만 건너뛴다. 훅이 없는 월드에는 이 객체가 없다(생성 경로가 바닐라 그대로).
package org.domi.furnace.server.world.gen;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.worldgen.ChunkGenerationHooks;
import org.domi.furnace.api.worldgen.DecorationRegion;
import org.domi.furnace.api.worldgen.GenerationChunk;
import org.domi.furnace.api.worldgen.GenerationStage;
import org.domi.furnace.server.plugin.ApiConvert;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;

public final class GenHooks {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(GenHooks.class);

    private final String key;
    private final Function<String, ChunkGenerationHooks> lookup;
    private final String world;
    private final long seed;
    private final BlockRegistry blocks;
    private final List<String> biomeNames;
    private final int seaLevel;
    private final ConcurrentHashMap<BlockData, Integer> stateCache = new ConcurrentHashMap<>();
    private final AtomicLong failures = new AtomicLong();
    private volatile boolean warnedMissing;

    public GenHooks(String key, Function<String, ChunkGenerationHooks> lookup, String world, long seed, BlockRegistry blocks,
                    List<String> biomeNames, int seaLevel) {
        this.key = key;
        this.lookup = lookup;
        this.world = world;
        this.seed = seed;
        this.blocks = blocks;
        this.biomeNames = biomeNames;
        this.seaLevel = seaLevel;
    }

    /**
     * 생성기에 훅을 건다(노이즈·평지 — 그 밖의 생성기는 무시). key 는 등록 키, lookup 은 키 → 지금 훅(꺼졌으면 null).
     * 돌려준 값은 건 바인딩(걸 수 없으면 null).
     */
    public static GenHooks attach(WorldSource gen, String key, Function<String, ChunkGenerationHooks> lookup, String world, long seed,
                                  BlockRegistry blocks, org.domi.furnace.server.net.registry.RegistrySet registries) {
        if (key == null || key.isEmpty()) return null;
        if (gen instanceof AnvilWorldSource a) gen = a.base();
        List<String> biomes = registries.entries("minecraft:worldgen/biome");
        if (gen instanceof NoiseWorldSource n) {
            GenHooks h = new GenHooks(key, lookup, world, n.seed(), blocks, biomes, n.seaLevel());
            n.generationHooks(h);
            return h;
        }
        if (gen instanceof SuperflatWorldSource f) {
            GenHooks h = new GenHooks(key, lookup, world, seed, blocks, biomes, -63);   // 바닐라 FlatLevelSource.getSeaLevel
            f.generationHooks(h);
            return h;
        }
        LOG.warn("[WORLDGEN] {} 의 생성기({})에는 월드젠 훅을 걸 수 없다 — '{}' 무시", world, gen.getClass().getSimpleName(), key);
        return null;
    }

    public String key() { return key; }

    public long seed() { return seed; }

    /** 캐시 무효화용 표식 — 이 바인딩과 지금 등록된 훅 객체가 같으면 같은 값. */
    Object stamp() {
        ChunkGenerationHooks h = lookup.apply(key);
        return h == null ? this : h;
    }

    /** 지금 걸린 훅(플러그인이 꺼졌으면 null — 처음 한 번 경고). */
    ChunkGenerationHooks current() {
        ChunkGenerationHooks h = lookup.apply(key);
        if (h == null && !warnedMissing) {
            warnedMissing = true;
            LOG.warn("[WORLDGEN] {} 의 월드젠 훅 '{}' 이 등록돼 있지 않다 — 새 청크는 바닐라로 생성한다", world, key);
        }
        return h;
    }

    private void failed(String what, int cx, int cz, RuntimeException e) {
        long n = failures.incrementAndGet();
        if (n <= 10 || n % 1000 == 0) {
            LOG.warn("[WORLDGEN] 훅 '{}' {} 실패 ({},{}) — 이 훅만 건너뛴다 (누적 {}회)", key, what, cx, cz, n, e);
        }
    }

    /** 그 단계 바닐라 처리를 돌리는가(훅이 없거나 실패하면 돌린다). */
    public boolean vanilla(GenerationStage stage, int cx, int cz) {
        ChunkGenerationHooks h = current();
        if (h == null) return true;
        try {
            return h.runVanilla(stage, cx, cz);
        } catch (RuntimeException e) {
            failed("runVanilla(" + stage + ")", cx, cz, e);
            return true;
        }
    }

    /** NOISE·SURFACE·CARVERS 단계 훅. */
    public void chunkStage(GenerationStage stage, ChunkColumn col, int cx, int cz) {
        ChunkGenerationHooks h = current();
        if (h == null) return;
        ChunkView view = new ChunkView(col, cx, cz);
        try {
            switch (stage) {
                case NOISE -> h.noise(view);
                case SURFACE -> h.surface(view);
                case CARVERS -> h.carvers(view);
                default -> throw new IllegalArgumentException(stage.name());
            }
        } catch (RuntimeException e) {
            failed(stage.name().toLowerCase(java.util.Locale.ROOT), cx, cz, e);
        } finally {
            view.closed = true;
        }
    }

    public boolean structure(String structureId, int cx, int cz) {
        ChunkGenerationHooks h = current();
        if (h == null) return true;
        try {
            return h.structure(structureId, cx, cz);
        } catch (RuntimeException e) {
            failed("structure(" + structureId + ")", cx, cz, e);
            return true;
        }
    }

    public boolean feature(String placedFeatureId, int ox, int oz) {
        ChunkGenerationHooks h = current();
        if (h == null) return true;
        try {
            return h.feature(placedFeatureId, ox, oz);
        } catch (RuntimeException e) {
            failed("feature(" + placedFeatureId + ")", ox, oz, e);
            return true;
        }
    }

    /** 원점 청크의 바닐라 장식 뒤. view 의 쓰기 범위(원점 ±1 청크)를 그대로 따른다. */
    public void decorate(FeatureWorldView view, int ox, int oz, long decorationSeed) {
        ChunkGenerationHooks h = current();
        if (h == null) return;
        RegionView r = new RegionView(view, ox, oz, decorationSeed);
        try {
            h.decorate(r);
        } catch (RuntimeException e) {
            failed("decorate", ox, oz, e);
        } finally {
            r.closed = true;
        }
    }

    /** 바닐라 WorldgenRandom.setDecorationSeed(seed, x, z) 의 값(Xoroshiro 판 — 바닐라 장식과 같은 씨앗). */
    public static long decorationSeed(long seed, int ox, int oz) {
        return new org.domi.furnace.server.world.gen.carver.XoroshiroWorldgenRandom().setDecorationSeed(seed, ox * 16, oz * 16);
    }

    private int stateOf(BlockData data) {
        Integer id = stateCache.get(data);
        if (id != null) return id;
        int s = ApiConvert.stateId(data, blocks);   // 모르는 블록이면 IllegalArgumentException
        if (stateCache.size() < 4096) stateCache.put(data, s);
        return s;
    }

    private String biomeName(int id) {
        return id >= 0 && id < biomeNames.size() ? biomeNames.get(id) : "minecraft:plains";
    }

    // ------------------------------------------------------------------ 청크 하나(NOISE·SURFACE·CARVERS)

    private final class ChunkView implements GenerationChunk {
        final ChunkColumn col;
        final int cx, cz;
        volatile boolean closed;

        ChunkView(ChunkColumn col, int cx, int cz) {
            this.col = col;
            this.cx = cx;
            this.cz = cz;
        }

        private void check(int x, int z) {
            if (closed) throw new IllegalStateException("훅이 끝난 뒤에는 청크를 쓸 수 없다");
            if ((x & ~15) != 0 || (z & ~15) != 0) throw new IndexOutOfBoundsException("청크 안 좌표는 0..15: " + x + "," + z);
        }

        @Override public String world() { return world; }
        @Override public int chunkX() { return cx; }
        @Override public int chunkZ() { return cz; }
        @Override public long seed() { return seed; }
        @Override public int minY() { return col.minY(); }
        @Override public int maxY() { return col.maxY(); }
        @Override public int seaLevel() { return seaLevel; }

        @Override
        public BlockData block(int x, int y, int z) {
            check(x, z);
            if (y < col.minY() || y > col.maxY()) return BlockData.AIR;
            return ApiConvert.block(col.getBlock(x, y, z), blocks);
        }

        @Override
        public String blockType(int x, int y, int z) {
            check(x, z);
            if (y < col.minY() || y > col.maxY()) return "minecraft:air";
            return blocks.nameOf(col.getBlock(x, y, z));
        }

        @Override
        public void setBlock(int x, int y, int z, BlockData data) {
            check(x, z);
            int s = stateOf(data);
            if (y < col.minY() || y > col.maxY()) return;
            col.setBlock(x, y, z, s);
        }

        @Override
        public void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockData data) {
            int ax = Math.min(x0, x1), bx = Math.max(x0, x1), az = Math.min(z0, z1), bz = Math.max(z0, z1);
            check(ax, az);
            check(bx, bz);
            int s = stateOf(data);
            int ay = Math.max(Math.min(y0, y1), col.minY()), by = Math.min(Math.max(y0, y1), col.maxY());
            for (int y = ay; y <= by; y++)
                for (int z = az; z <= bz; z++)
                    for (int x = ax; x <= bx; x++) col.setBlock(x, y, z, s);
        }

        @Override
        public String biome(int x, int y, int z) {
            check(x, z);
            int ly = Math.clamp(y, col.minY(), col.maxY()) - col.minY();
            return biomeName(col.sections()[ly >> 4].getBiome(x >> 2, (ly >> 2) & 3, z >> 2));
        }

        @Override
        public int highestBlockY(int x, int z) {
            check(x, z);
            for (int y = col.maxY(); y >= col.minY(); y--) {
                if (!ChunkSection.isAir(col.getBlock(x, y, z))) return y;
            }
            return col.minY() - 1;
        }
    }

    // ------------------------------------------------------------------ 장식 영역(원점 둘레 5×5 읽기, 3×3 쓰기)

    private final class RegionView implements DecorationRegion {
        final FeatureWorldView view;
        final int ox, oz;
        final long decoSeed;
        volatile boolean closed;

        RegionView(FeatureWorldView view, int ox, int oz, long decoSeed) {
            this.view = view;
            this.ox = ox;
            this.oz = oz;
            this.decoSeed = decoSeed;
        }

        private void open() {
            if (closed) throw new IllegalStateException("훅이 끝난 뒤에는 영역을 쓸 수 없다");
        }

        private boolean inY(int y) { return y >= view.minY() && y <= view.maxY(); }

        @Override public String world() { return world; }
        @Override public int originChunkX() { return ox; }
        @Override public int originChunkZ() { return oz; }
        @Override public long seed() { return seed; }
        @Override public long decorationSeed() { return decoSeed; }
        @Override public int minY() { return view.minY(); }
        @Override public int maxY() { return view.maxY(); }

        @Override
        public boolean canRead(int x, int y, int z) {
            int nx = x >> 4, nz = z >> 4;
            return inY(y) && Math.abs(nx - ox) <= 2 && Math.abs(nz - oz) <= 2 && view.hasCell(nx, nz);
        }

        @Override
        public boolean canWrite(int x, int y, int z) {
            int nx = x >> 4, nz = z >> 4;
            return inY(y) && Math.abs(nx - ox) <= 1 && Math.abs(nz - oz) <= 1 && view.hasCell(nx, nz);
        }

        @Override
        public BlockData block(int x, int y, int z) {
            open();
            return canRead(x, y, z) ? ApiConvert.block(view.getBlock(x, y, z), blocks) : BlockData.AIR;
        }

        @Override
        public String blockType(int x, int y, int z) {
            open();
            return canRead(x, y, z) ? blocks.nameOf(view.getBlock(x, y, z)) : "minecraft:air";
        }

        @Override
        public boolean setBlock(int x, int y, int z, BlockData data) {
            open();
            int s = stateOf(data);
            if (!canWrite(x, y, z)) return false;
            return view.trySetBlock(x, y, z, s);
        }

        @Override
        public String biome(int x, int y, int z) {
            open();
            if (!canRead(x, view.minY(), z)) return null;
            return biomeName(view.getBiomeId(x, Math.clamp(y, view.minY(), view.maxY()), z));
        }

        @Override
        public int highestBlockY(int x, int z) {
            open();
            if (!canRead(x, view.minY(), z)) return view.minY() - 1;
            for (int y = view.maxY(); y >= view.minY(); y--) {
                if (!ChunkSection.isAir(view.getBlock(x, y, z))) return y;
            }
            return view.minY() - 1;
        }
    }
}
