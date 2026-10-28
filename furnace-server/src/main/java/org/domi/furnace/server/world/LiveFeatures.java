// 블록 동작(묘목·뼛가루)이 월드 런타임에 지형 생성 feature 를 놓는 어댑터. 바닐라는 ServerLevel 에 직접 쓰지만,
// 여기선 그 칸 region 스레드에서 실제 청크의 복사본(처음 닿는 청크만 복사)에 월드젠 코드를 돌리고 쓴 칸만 순서대로 옮긴다.
// 난수: 레벨 난수(LegacyRandomSource)의 상태를 WorldgenRandom 으로 옮겨 돌린 뒤 되돌려 놓는다 — 소비 순서가 바닐라와 같다.
// 소유권: 복사본은 이 region 이 가진 청크만 담는다. 다른 region·미로드 청크에 닿으면 아무것도 옮기지 않는다(성장 실패로 본다).
package org.domi.furnace.server.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.LevelFeature;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.block.entity.BlockEntities;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.gen.WorldgenData;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BiomeFeatureData;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.ConfiguredFeature;
import org.domi.furnace.server.world.gen.feature.FeatureSupport;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.PlacedFeature;

public final class LiveFeatures {

    private LiveFeatures() {}

    /** configured 또는 placed 하나. supported 가 거짓이면 하위에 Noop 이 있다. */
    public record Handle(ConfiguredFeature configured, PlacedFeature placed, boolean supported) implements LevelFeature {}

    /** 쓰기 범위(청크): 월드젠 WorldGenRegion 의 writeRadius 와 같은 1(3×3). */
    static final int RADIUS = 1;

    private static volatile BiomeFeatureData fallback;
    private static final Map<BiomeFeatureData, Map<String, Handle>> CACHE = new ConcurrentHashMap<>();

    /** 노이즈 생성기가 없는 월드(평지 등)용: 1.21.1 내장 데이터의 feature 표. 바닐라도 feature 는 생성기와 무관한 registry 에서 온다. */
    public static BiomeFeatureData fallbackData() {
        BiomeFeatureData d = fallback;
        if (d == null) {
            synchronized (LiveFeatures.class) {
                d = fallback;
                if (d == null) {
                    fallback = d = BiomeFeatureData.load(WorldgenData.load("worldgen/1.21.1"), List.of(),
                            BlockRegistry.load("blocks/1.21.1.txt"));
                }
            }
        }
        return d;
    }

    private static Map<String, Handle> cache(BiomeFeatureData data) { return CACHE.computeIfAbsent(data, k -> new ConcurrentHashMap<>()); }

    public static Handle configured(BiomeFeatureData data, String id) {
        if (data == null) return null;
        String key = "c:" + id;
        Handle h = cache(data).get(key);
        if (h != null) return h;
        ConfiguredFeature f = data.configuredFeatureById(id);
        if (f == null) return null;
        h = new Handle(f, null, !FeatureSupport.containsNoop(f));
        cache(data).put(key, h);
        return h;
    }

    public static Handle placed(BiomeFeatureData data, String id) {
        if (data == null) return null;
        String key = "p:" + id;
        Handle h = cache(data).get(key);
        if (h != null) return h;
        PlacedFeature f;
        try {
            f = data.placedFeatureById(id);
        } catch (RuntimeException e) {
            return null;   // 데이터 없음
        }
        if (f == null) return null;
        h = new Handle(null, f, !FeatureSupport.containsNoop(f));
        cache(data).put(key, h);
        return h;
    }

    public static Handle flower(BiomeFeatureData data, String biome) {
        if (data == null || biome == null) return null;
        String key = "f:" + biome;
        Handle h = cache(data).get(key);
        if (h != null) return h;
        PlacedFeature f = data.flowerFeature(biome);
        if (f == null) return null;
        h = new Handle(null, f, !FeatureSupport.containsNoop(f));
        cache(data).put(key, h);
        return h;
    }

    /** 레벨 난수 상태를 지형 생성 난수로 옮긴다. LegacyRandomSource 가 아니면(가정: 시험용) 그 난수에서 시드 하나를 뽑는다. */
    static void bridgeIn(RandomSource src, WorldgenRandom wr) {
        if (src instanceof LegacyRandomSource l) wr.restoreState(l.rawState(), l.gaussianPending(), l.pendingGaussian());
        else wr.setSeed(src.nextLong());
    }

    static void bridgeOut(WorldgenRandom wr, RandomSource src) {
        if (src instanceof LegacyRandomSource l) l.restoreState(wr.debugState(), wr.gaussianPending(), wr.pendingGaussian());
    }

    /** feature 를 pos 에 놓는다. 바닐라 place 의 반환값. 다른 region·미로드 청크에 닿았으면 아무것도 바꾸지 않고 false. */
    public static boolean place(ChunkLevel level, Handle h, Pos pos, RandomSource random, long worldSeed) {
        if (h == null || !h.supported()) return false;
        int cx = pos.x() >> 4, cz = pos.z() >> 4, size = 2 * RADIUS + 1;
        ChunkColumn[] live = new ChunkColumn[size * size], copy = new ChunkColumn[size * size];
        boolean[] tried = new boolean[size * size];
        int[][] biomes = new int[size * size][];
        java.util.function.IntConsumer fill = i -> {
            if (tried[i]) return;
            tried[i] = true;
            ChunkColumn col = level.listenerChunk(cx + (i % size) - RADIUS, cz + (i / size) - RADIUS);
            if (col == null) return;
            live[i] = col;
            copy[i] = col.copy();   // 블록 엔티티는 얕은 복사 — 월드젠 코드는 생성 NBT 만 남긴다
            biomes[i] = biomeIds(col);
        };
        int center = RADIUS * size + RADIUS;
        fill.accept(center);
        if (copy[center] == null) return false;
        List<long[]> writes = new ArrayList<>();
        FeatureWorldView view = FeatureWorldView.lazyLiveGrid(copy, biomes, cx, cz, RADIUS, worldSeed, fill, writes);
        WorldgenRandom wr = new WorldgenRandom();
        bridgeIn(random, wr);
        boolean placed;
        try {
            placed = h.configured() != null
                    ? h.configured().place(view, wr, pos.x(), pos.y(), pos.z())
                    : h.placed().placeAt(view, wr, new BlockPos(pos.x(), pos.y(), pos.z()));
        } finally {
            bridgeOut(wr, random);
        }
        if (view.missingAccess() > 0) return false;
        commit(level, cx, cz, size, live, copy, writes);
        return placed;
    }

    /** 쓴 칸을 쓴 순서대로(칸마다 처음 쓴 차례) 실제 월드에 놓는다(flag 3 = Feature.setBlock 기본). 생성 NBT·엔티티도 옮긴다. */
    private static void commit(ChunkLevel level, int cx, int cz, int size, ChunkColumn[] live, ChunkColumn[] copy, List<long[]> writes) {
        Map<Long, Pos> order = new LinkedHashMap<>();
        for (long[] w : writes) {
            Pos p = new Pos((int) w[0], (int) w[1], (int) w[2]);
            order.putIfAbsent(p.asLong(), p);
        }
        for (Pos p : order.values()) {
            int i = index(cx, cz, size, p.x() >> 4, p.z() >> 4);
            ChunkColumn cp = copy[i];
            if (cp == null) continue;
            int s = cp.getBlock(p.x() & 15, p.y(), p.z() & 15);
            if (s == live[i].getBlock(p.x() & 15, p.y(), p.z() & 15)) continue;
            level.setBlock(p, s, 3);
        }
        for (int i = 0; i < copy.length; i++) {
            ChunkColumn l = live[i], cp = copy[i];
            if (l == null) continue;
            int ox = (cx + (i % size) - RADIUS) * 16, oz = (cz + (i / size) - RADIUS) * 16;
            cp.forEachGeneratedBlockEntity((lx, y, lz, nbt) -> adoptBlockEntity(level, l, ox + lx, y, oz + lz, nbt));
            for (NbtCompound e : cp.generatedEntities()) {
                Entity ent = Entities.load(level, e.copy());
                if (ent != null) level.addFreshEntityWithPassengers(ent);
            }
        }
    }

    private static int index(int cx, int cz, int size, int chunkX, int chunkZ) {
        return (chunkZ - cz + RADIUS) * size + (chunkX - cx + RADIUS);
    }

    private static void adoptBlockEntity(ChunkLevel level, ChunkColumn col, int x, int y, int z, NbtCompound nbt) {
        Pos pos = new Pos(x, y, z);
        NbtCompound tag = nbt.copy();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        String id = tag.tags().get("id") instanceof String s ? s : null;
        if (id != null && BlockEntities.known(id)) {
            var be = BlockEntities.loadStatic(pos, level.getBlockState(pos), tag);
            if (be != null) level.setBlockEntity(be);
            return;
        }
        col.removeBlockEntityAt(x, y, z);
        col.putGeneratedBlockEntity(x & 15, y, z & 15, tag);
    }

    /** FeatureWorldView 의 biome 배열 형식: (quartY * 4 + quartZ) * 4 + quartX. */
    private static int[] biomeIds(ChunkColumn col) {
        ChunkSection[] sections = col.sections();
        int[] ids = new int[sections.length * 64];
        for (int s = 0; s < sections.length; s++) {
            for (int qy = 0; qy < 4; qy++) {
                for (int qz = 0; qz < 4; qz++) {
                    for (int qx = 0; qx < 4; qx++) ids[((s * 4 + qy) * 4 + qz) * 4 + qx] = sections[s].getBiome(qx, qy, qz);
                }
            }
        }
        return ids;
    }
}
