// 블록 동작(묘목·뼛가루)이 월드 런타임에 지형 생성 feature 를 놓는 어댑터. 바닐라는 ServerLevel 에 직접 쓰지만,
// 여기선 그 칸 region 스레드에서 실제 청크의 복사본(처음 닿는 청크만 복사)에 월드젠 코드를 돌리고 쓴 칸만 순서대로 옮긴다.
// 난수: 레벨 난수(LegacyRandomSource)의 상태를 WorldgenRandom 으로 옮겨 돌린 뒤 되돌려 놓는다 — 소비 순서가 바닐라와 같다.
// 소유권: 복사본은 이 region 이 가진 청크만 담는다. 다른 region·미로드 청크에 닿으면 아무것도 옮기지 않는다(성장 실패로 본다).
package org.domi.furnace.server.world;

import org.domi.furnace.api.GameVersion;
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
import org.domi.furnace.server.world.gen.feature.ChorusPlantFeature;
import org.domi.furnace.server.world.gen.feature.ConfiguredFeature;
import org.domi.furnace.server.world.gen.feature.FeatureSupport;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.PlacedFeature;
import org.domi.furnace.server.world.gen.feature.TreeFeature;

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
                    fallback = d = BiomeFeatureData.load(WorldgenData.load("worldgen/" + GameVersion.RESOURCE_TAG), List.of(),
                            BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt"));
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
        WorldgenRandom wr = new WorldgenRandom();
        Placement r = run(level, pos, worldSeed, true, liveFlags(h), wr,
                view -> {
                    bridgeIn(random, wr);
                    try {
                        return placeIn(h, view, wr, pos);
                    } finally {
                        bridgeOut(wr, random);
                    }
                });
        return r.placed();
    }

    /**
     * 놓은 결과. placed 는 feature 의 반환값(닿지 못한 청크가 있으면 false), writes 는 {x, y, z, 마지막 상태}를 칸마다 처음 쓴 차례로
     * (바뀌지 않은 쓰기도 남는다 — CraftBukkit captureTreeGeneration 의 capturedBlockStates 와 같은 모양).
     */
    public record Placement(boolean placed, List<long[]> writes) {
        static final Placement NONE = new Placement(false, List.of());
    }

    /**
     * 플러그인용(Paper CraftRegionAccessor#generateTree): 주어진 난수를 feature 난수로 그대로 쓴다(레벨 난수와 잇지 않는다).
     * apply 가 거짓이면 월드를 바꾸지 않고 쓴 칸만 돌려준다(가장자리 모양 갱신·블록 엔티티는 빠진다).
     */
    public static Placement placeWith(ChunkLevel level, Handle h, Pos pos, WorldgenRandom random, long worldSeed, boolean apply) {
        if (h == null || !h.supported()) return Placement.NONE;
        return run(level, pos, worldSeed, apply, liveFlags(h), random, view -> placeIn(h, view, random, pos));
    }

    /** ChorusFlowerBlock.generatePlant(level, pos, random, 8) 를 flag 2 로(Paper generateTree CHORUS_PLANT). 닿지 못하면 placed=false. */
    public static Placement chorusPlant(ChunkLevel level, BiomeFeatureData data, Pos pos, WorldgenRandom random, long worldSeed, boolean apply) {
        Handle h = configured(data, "minecraft:chorus_plant");
        if (h == null || !(h.configured() instanceof ChorusPlantFeature chorus)) return Placement.NONE;
        return run(level, pos, worldSeed, apply, 2, random, view -> {
            chorus.generatePlant(view, pos.x(), pos.y(), pos.z(), random, 8);
            return true;
        });
    }

    /**
     * 바닐라 TreeFeature: 통나무·잎·장식·잎 거리 모두 flag 19(UPDATE_KNOWN_SHAPE), 가장자리만 따로 모양 갱신. 그 밖은 Feature.setBlock 기본 3.
     * placed feature 는 가리키는 configured feature 로 본다.
     */
    private static int liveFlags(Handle h) {
        ConfiguredFeature c = h.configured() != null ? h.configured() : h.placed().feature();
        return c instanceof TreeFeature ? 19 : 3;
    }

    // ------------------------------------------------------------------ 플러그인 레벨 통로(T-211)

    /** 통로 모드 사본 범위(청크): 런타임 배치는 월드젠의 3×3 제한이 없다 — 처음 닿을 때만 복사하므로 넓혀도 닿은 만큼만 든다. */
    static final int SINK_RADIUS = 2;

    /** 플러그인 배치가 닿지 못한 청크(로드 안 됨·다른 region·범위 밖)가 있었다 — 결과가 반쪽이라 성공·실패로 꾸미지 않는다. */
    public static final class Unreachable extends IllegalStateException {
        Unreachable(Pos pos) {
            super("the feature at " + pos.x() + ", " + pos.y() + ", " + pos.z() + " reached a chunk that is not loaded, belongs to another region or is more than "
                    + SINK_RADIUS + " chunks away");
        }
    }

    /**
     * 플러그인이 부른 바닐라 {@code ConfiguredFeature/PlacedFeature#place(WorldGenLevel, …)}. placed feature 는 top feature 없이 놓는다 —
     * 바이옴 거르기에서 바닐라처럼 IllegalStateException.
     * <ul>
     *   <li>sink 가 null: 서버 레벨 자신에 놓는다(사본에 돌리고 쓴 칸을 flag 대로 실제 월드에 옮긴다 — 블록 엔티티·생성 NBT 포함, 묘목 성장과 같은 길).</li>
     *   <li>sink 가 있으면 통로 모드: 사본 격자에 지형 생성 코드를 돌리되 쓰기는 차례대로 sink 에 넘긴다({@link FeatureWorldView.WriteSink}).
     *       실제 월드는 바꾸지 않고, 생성 NBT·엔티티·유체 예약 틱은 버린다(Paper 의 populator·나무 기록과 같다).</li>
     * </ul>
     *
     * @return feature 의 반환값
     * @throws Unreachable 원점 청크나 닿은 청크가 로드돼 있지 않거나 다른 region·범위 밖이었다(통로 모드는 그 전까지의 쓰기가 이미 sink 에 갔다,
     *         직접 놓는 길은 아무것도 바꾸지 않았다)
     */
    public static boolean placeThrough(ChunkLevel level, Handle h, Pos pos, WorldgenRandom random, long worldSeed, FeatureWorldView.WriteSink sink) {
        java.util.Objects.requireNonNull(h, "feature");
        if (!h.supported()) throw new UnsupportedOperationException("feature not implemented yet");
        Placement r = run(level, pos, worldSeed, sink == null, liveFlags(h), random, SINK_RADIUS, sink, view -> placeIn(h, view, random, pos, true));
        if (r == UNREACHABLE || r == Placement.NONE) throw new Unreachable(pos);
        return r.placed();
    }

    /** 통로 모드에서 닿지 못했다는 표시(Placement.NONE 과 구별). */
    private static final Placement UNREACHABLE = new Placement(false, List.of());

    /** 통로 모드 모양 갱신: 면 처리와 updateFromNeighbourShapes 를 실제 블록 동작으로(읽기는 SinkShapeLevel). */
    static FeatureWorldView.EdgeUpdater sinkEdges(FeatureWorldView view, SinkShapeLevel level) {
        return new FeatureWorldView.EdgeUpdater() {
            @Override
            public void update(int x, int y, int z, int dx, int dy, int dz, int writeFlags) { sinkEdge(view, level, x, y, z, dx, dy, dz, writeFlags); }

            @Override
            public int neighbourShapes(int state, int x, int y, int z) {
                return org.domi.furnace.server.block.B.updateFromNeighbourShapes(state, level, new Pos(x, y, z));
            }
        };
    }

    /** 통로 모드 가장자리 한 면: StructureTemplate.updateShapeAtEdge(level, flags, …) 의 면 처리 — 쓰기는 writeFlags(flags & ~1; 나무는 2). */
    private static void sinkEdge(FeatureWorldView view, SinkShapeLevel level, int x, int y, int z, int dx, int dy, int dz, int writeFlags) {
        Pos pos = new Pos(x, y, z), npos = new Pos(x + dx, y + dy, z + dz);
        int dir = fromDelta(dx, dy, dz);
        int s = view.getBlock(x, y, z), n = view.getBlock(npos.x(), npos.y(), npos.z());
        int saved = view.liveWriteFlags();
        view.setLiveWriteFlags(writeFlags);
        try {
            int s2 = org.domi.furnace.server.block.B.behavior(s).updateShape(s, dir, n, level, pos, npos);
            if (s2 != s) view.trySetBlock(x, y, z, s2);
            int n2 = org.domi.furnace.server.block.B.behavior(n).updateShape(n, org.domi.furnace.server.block.Dir.opposite(dir), s2, level, npos, pos);
            if (n2 != n) view.trySetBlock(npos.x(), npos.y(), npos.z(), n2);
        } finally {
            view.setLiveWriteFlags(saved);
        }
    }

    private static boolean placeIn(Handle h, FeatureWorldView view, WorldgenRandom wr, Pos pos) { return placeIn(h, view, wr, pos, false); }

    private static boolean placeIn(Handle h, FeatureWorldView view, WorldgenRandom wr, Pos pos, boolean noTopFeature) {
        if (h.configured() != null) return h.configured().place(view, wr, pos.x(), pos.y(), pos.z());
        BlockPos origin = new BlockPos(pos.x(), pos.y(), pos.z());
        return noTopFeature ? h.placed().placeWithoutTopFeature(view, wr, origin) : h.placed().placeAt(view, wr, origin);
    }

    /** 사본 격자에 body 를 돌리고, 닿지 못한 청크가 없으면 apply 일 때 실제 월드에 옮긴다. */
    static Placement run(ChunkLevel level, Pos pos, long worldSeed, boolean apply, int flags, WorldgenRandom wr,
                         java.util.function.Predicate<FeatureWorldView> body) {
        return run(level, pos, worldSeed, apply, flags, wr, RADIUS, null, body);
    }

    /** sink 가 있으면 통로 모드(apply 는 거짓이어야 한다): 닿지 못하면 {@link #UNREACHABLE}, 아니면 placed 와 빈 쓰기 목록. */
    private static Placement run(ChunkLevel level, Pos pos, long worldSeed, boolean apply, int flags, WorldgenRandom wr, int radius,
                                 FeatureWorldView.WriteSink sink, java.util.function.Predicate<FeatureWorldView> body) {
        final int RADIUS = radius;
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
            copy[i] = copyOnWrite ? col.cowCopy() : col.copy();   // 블록 엔티티는 얕은 복사 — 월드젠 코드는 생성 NBT 만 남긴다
            biomes[i] = biomeIds(col);
        };
        int center = RADIUS * size + RADIUS;
        fill.accept(center);
        if (copy[center] == null) return Placement.NONE;
        List<long[]> writes = new ArrayList<>();
        List<int[]> edges = new ArrayList<>();
        FeatureWorldView view = FeatureWorldView.lazyLiveGrid(copy, biomes, cx, cz, RADIUS, worldSeed, fill, writes, edges);
        view.setLiveWriteFlags(flags);
        if (sink != null) {
            if (apply) throw new IllegalArgumentException("a write sink does not apply to the world");
            SinkShapeLevel shapeLevel = new SinkShapeLevel(view, (x, y, z) -> {
                int i = (Math.floorDiv(z, 16) - cz + RADIUS) * size + (Math.floorDiv(x, 16) - cx + RADIUS);
                ChunkColumn l = i >= 0 && i < live.length ? live[i] : null;
                return l == null ? view.getBlock(x, y, z) : l.getBlock(x & 15, y, z & 15);   // 없는 칸은 view 가 공기로 답하고 닿지 못함을 센다
            });
            view.useSink(sink, live, sinkEdges(view, shapeLevel));
            boolean placed = body.test(view);
            return view.missingAccess() > 0 ? UNREACHABLE : new Placement(placed, List.of());
        }
        boolean placed = body.test(view);
        if (view.missingAccess() > 0) return Placement.NONE;
        if (apply) {
            commit(level, cx, cz, size, RADIUS, live, copy, writes);
            updateShapeAtEdge(level, edges, 3);
        }
        return new Placement(placed, apply ? List.of() : captured(cx, cz, size, RADIUS, live, copy, writes));
    }

    /**
     * 쓴 칸마다 처음 쓴 차례로 {x, y, z, 사본의 마지막 상태}. Paper 의 나무 생성 기록(captureTreeGeneration)처럼 부술 수 없는 블록
     * (Protect-Bedrock 패치, allow-permanent-block-break-exploits 기본 false) 칸은 기록하지 않는다 — 나무 feature 는 그 블록을 스스로
     * 놓지 않으므로 쓰기 때 검사와 옛 상태로 거르기는 같다.
     */
    private static List<long[]> captured(int cx, int cz, int size, int radius, ChunkColumn[] live, ChunkColumn[] copy, List<long[]> writes) {
        Map<Long, long[]> order = new LinkedHashMap<>();
        for (long[] w : writes) {
            int x = (int) w[0], y = (int) w[1], z = (int) w[2];
            order.computeIfAbsent(new Pos(x, y, z).asLong(), k -> new long[]{x, y, z, 0});
        }
        List<long[]> out = new ArrayList<>(order.size());
        for (long[] w : order.values()) {
            int x = (int) w[0], y = (int) w[1], z = (int) w[2];
            int i = index(cx, cz, size, radius, x >> 4, z >> 4);
            if (!isDestroyable(live[i].getBlock(x & 15, y, z & 15))) continue;
            w[3] = copy[i].getBlock(x & 15, y, z & 15);
            out.add(w);
        }
        return out;
    }

    /** Paper Block#isDestroyable(allowPermanentBlockBreakExploits 꺼짐): 이 10종이 아니면 부술 수 있다. */
    private static final class Indestructible {
        static final java.util.Set<Integer> BLOCKS = java.util.stream.Stream.of("barrier", "bedrock", "end_portal_frame", "end_portal",
                        "end_gateway", "command_block", "repeating_command_block", "chain_command_block", "structure_block", "jigsaw")
                .map(n -> {
                    int b = org.domi.furnace.server.block.Blocks.get().block("minecraft:" + n);
                    if (b < 0) throw new IllegalStateException("no such block: " + n);
                    return b;
                })
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static boolean isDestroyable(int state) { return !Indestructible.BLOCKS.contains(org.domi.furnace.server.block.B.block(state)); }

    /** 쓴 칸을 쓴 순서대로(칸마다 처음 쓴 차례) 실제 월드에 놓는다(flag 3 = Feature.setBlock 기본). 생성 NBT·엔티티도 옮긴다. */
    private static void commit(ChunkLevel level, int cx, int cz, int size, int radius, ChunkColumn[] live, ChunkColumn[] copy, List<long[]> writes) {
        Map<Long, Pos> order = new LinkedHashMap<>();
        Map<Long, Integer> flags = new java.util.HashMap<>();
        for (long[] w : writes) {
            Pos p = new Pos((int) w[0], (int) w[1], (int) w[2]);
            order.putIfAbsent(p.asLong(), p);
            flags.putIfAbsent(p.asLong(), (int) w[3]);
        }
        for (Pos p : order.values()) {
            int i = index(cx, cz, size, radius, p.x() >> 4, p.z() >> 4);
            ChunkColumn cp = copy[i];
            if (cp == null) continue;
            int s = cp.getBlock(p.x() & 15, p.y(), p.z() & 15);
            if (s == live[i].getBlock(p.x() & 15, p.y(), p.z() & 15)) continue;
            level.setBlock(p, s, flags.get(p.asLong()));
        }
        for (int i = 0; i < copy.length; i++) {
            ChunkColumn l = live[i], cp = copy[i];
            if (l == null) continue;
            int ox = (cx + (i % size) - radius) * 16, oz = (cz + (i / size) - radius) * 16;
            cp.forEachGeneratedBlockEntity((lx, y, lz, nbt) -> adoptBlockEntity(level, l, ox + lx, y, oz + lz, nbt));
            for (NbtCompound e : cp.generatedEntities()) {
                Entity ent = Entities.load(level, e.copy());
                if (ent != null) level.addFreshEntityWithPassengers(ent);
            }
        }
    }

    /** StructureTemplate.updateShapeAtEdge(level, flags, shape, …): 면마다 안쪽 → 바깥쪽 순으로 updateShape(flag & ~1). */
    private static void updateShapeAtEdge(ChunkLevel level, List<int[]> edges, int flags) {
        for (int[] e : edges) {
            Pos pos = new Pos(e[0], e[1], e[2]), npos = new Pos(e[0] + e[3], e[1] + e[4], e[2] + e[5]);
            int dir = fromDelta(e[3], e[4], e[5]);
            int s = level.getBlockState(pos), n = level.getBlockState(npos);
            int s2 = org.domi.furnace.server.block.B.behavior(s).updateShape(s, dir, n, level, pos, npos);
            if (s2 != s) level.setBlock(pos, s2, flags & ~1);
            int n2 = org.domi.furnace.server.block.B.behavior(n).updateShape(n, org.domi.furnace.server.block.Dir.opposite(dir), s2, level, npos, pos);
            if (n2 != n) level.setBlock(npos, n2, flags & ~1);
        }
    }

    private static int fromDelta(int dx, int dy, int dz) {
        for (int d = 0; d < 6; d++) {
            if (org.domi.furnace.server.block.Dir.DX[d] == dx && org.domi.furnace.server.block.Dir.DY[d] == dy && org.domi.furnace.server.block.Dir.DZ[d] == dz) return d;
        }
        throw new IllegalArgumentException("not a direction");
    }

    /** 측정·비교용: false 면 예전처럼 청크 기둥 전체를 복사한다. */
    public static volatile boolean copyOnWrite = true;

    private static int index(int cx, int cz, int size, int radius, int chunkX, int chunkZ) {
        return (chunkZ - cz + radius) * size + (chunkX - cx + radius);
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
            if (be != null) {
                level.setBlockEntity(be);
                be.afterLoad(level);   // 바닐라는 실제 레벨에 놓은 블록 엔티티에 loadWithComponents(레벨 있음)
            }
            return;
        }
        col.removeBlockEntityAt(x, y, z);
        col.putGeneratedBlockEntity(x & 15, y, z & 15, tag);
    }

    /** FeatureWorldView 의 biome 배열 형식: (quartY * 4 + quartZ) * 4 + quartX. */
    static int[] biomeIds(ChunkColumn col) {
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
