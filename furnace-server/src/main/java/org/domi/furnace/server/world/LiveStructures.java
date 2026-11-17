// 플러그인이 부른 바닐라 Structure#generate·StructureStart#placeInChunk(T-211 2단계). 생성은 생성기만 쓰는 계산이고, 청크 배치는 1단계 LiveFeatures 처럼
// 실제 청크 사본에 월드젠 구조물 코드를 돌린다 — 통로가 있으면 쓰기를 플러그인 레벨로(Paper 의 populator·나무 기록), 없으면 /place structure 처럼 반영.
// 소유권: 사본은 이 region 이 가진 청크만 담는다. 닿지 못하면 결과를 꾸미지 않고 IllegalStateException.
package org.domi.furnace.server.world;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.domi.furnace.server.world.gen.noise.PositionalRandom;
import org.domi.furnace.server.world.gen.structure.BoundingBox;
import org.domi.furnace.server.world.gen.structure.StructureEngine;
import org.domi.furnace.server.world.gen.structure.StructureStart;

public final class LiveStructures {

    private LiveStructures() {}

    /**
     * 바닐라 {@code Structure#generate(…, chunkPos, references, heightAccessor, biomeTest)}: 시작 자리를 찾고 그 바이옴이 biomeTest 를 지나면 조각을 만든다.
     * 유효하지 않으면(자리 없음·바이옴 거절·조각 없음) null.
     *
     * @throws IllegalArgumentException 모르는 구조물
     * @throws UnsupportedOperationException 서버에 아직 없는 구조물 종류이거나 템플릿이 없다
     */
    public static StructureStart generate(NoiseWorldSource gen, String structureId, int chunkX, int chunkZ, Predicate<String> biomeTest) {
        StructureEngine engine = gen.structureEngine();
        StructureEngine.CommandStart cs = engine.generateFor(structureId, chunkX, chunkZ, gen, biomeTest);
        if (!cs.known()) throw new IllegalArgumentException("unknown structure: " + structureId);
        if (!cs.supported()) throw new UnsupportedOperationException("structure not implemented yet: " + structureId);
        return cs.start();
    }

    /**
     * 같은 시작을 같은 플러그인 레벨로 여러 청크에 놓는 동안 그 레벨이 받아들인 쓰기(칸마다 마지막 상태). Paper 는 그 레벨의 기록·표를 읽으므로 다음 청크의
     * 사본에도 얹는다(청크 경계 모양 갱신 등). 한 스레드에서 차례로 쓴다(전역 스레드 Bukkit 작업 또는 그 region 스레드).
     */
    public static final class Session {
        private final Map<Long, Map<Long, Integer>> byChunk = new HashMap<>();

        public void accept(int x, int y, int z, int state) {
            byChunk.computeIfAbsent(chunkKey(x >> 4, z >> 4), k -> new HashMap<>()).put(new Pos(x, y, z).asLong(), state);
        }

        void applyTo(int chunkX, int chunkZ, ChunkColumn copy) {
            Map<Long, Integer> m = byChunk.get(chunkKey(chunkX, chunkZ));
            if (m == null) return;
            for (Map.Entry<Long, Integer> e : m.entrySet()) {
                Pos p = Pos.of(e.getKey());
                copy.setBlock(p.x() & 15, p.y(), p.z() & 15, e.getValue());
            }
        }

        private static long chunkKey(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }
    }

    /**
     * 바닐라 {@code StructureStart#placeInChunk(level, structureManager, generator, random, chunkBox, chunkPos)}.
     *
     * @param random      배치 난수(조각에 넘기는 RandomSource)
     * @param levelRandom 그 레벨의 {@code getRandom()} — 뽑을 때마다 부른다(조각이 {@code world.getRandom()} 으로 뽑는 자리). 플러그인 레벨이 던지면 그대로
     * @param session     통로 모드의 세션(sink 가 null 이면 쓰지 않는다)
     * @param sink        null 이면 이 레벨에 바로 놓는다(/place structure 와 같은 반영, flag 2)
     * @throws IllegalStateException 닿은 청크가 로드돼 있지 않거나 다른 region·범위 밖이었다
     * @throws UnsupportedOperationException 몹 finalizeSpawn 자리, 플러그인 레벨 블록 엔티티 내용 바꾸기 등 같은 결과를 낼 수 없는 자리
     */
    public static void placeInChunk(ChunkLevel level, NoiseWorldSource gen, StructureStart start, BoundingBox chunkBox, int chunkX, int chunkZ,
                                    WorldgenRandom random, Supplier<java.util.Random> levelRandom, Session session, FeatureWorldView.WriteSink sink) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(chunkBox, "chunkBox");
        Objects.requireNonNull(levelRandom, "levelRandom");
        StructureEngine engine = gen.structureEngine();
        NoiseRandom lr = new LevelRandom(levelRandom);
        Runnable touch = levelRandom::get;
        int radius = gridRadius(chunkBox, chunkX, chunkZ);
        if (sink == null) {
            if (!(level instanceof RegionLevel rl)) throw new IllegalStateException("structures are placed into a region level");
            LiveGrid grid = new LiveGrid(rl, chunkX, chunkZ, radius, gen.seed());
            FeatureWorldView view = grid.view;
            view.setPluginPlacement(true);
            view.setLevelRandomTouch(touch);
            synchronized (start) {
                start.placeInChunk(engine, view, random, lr, chunkBox, chunkX, chunkZ);
            }
            if (view.missingAccess() > 0) throw unreachable(chunkX, chunkZ, radius);
            grid.commit(2);   // 구조물 조각 flag(2) — 모양 갱신은 사본 안에서 이미 했다(/place structure 와 같다)
            return;
        }
        Objects.requireNonNull(session, "session");
        int size = 2 * radius + 1;
        ChunkColumn[] live = new ChunkColumn[size * size], copy = new ChunkColumn[size * size];
        boolean[] tried = new boolean[size * size];
        int[][] biomes = new int[size * size][];
        java.util.function.IntConsumer fill = i -> {
            if (tried[i]) return;
            tried[i] = true;
            int x = chunkX + (i % size) - radius, z = chunkZ + (i / size) - radius;
            ChunkColumn col = level.listenerChunk(x, z);
            if (col == null) return;
            live[i] = col;
            ChunkColumn cp = LiveFeatures.copyOnWrite ? col.cowCopy() : col.copy();
            session.applyTo(x, z, cp);
            copy[i] = cp;
            biomes[i] = LiveFeatures.biomeIds(col);
        };
        FeatureWorldView view = FeatureWorldView.lazyLiveGrid(copy, biomes, chunkX, chunkZ, radius, gen.seed(), fill, new java.util.ArrayList<>(),
                new java.util.ArrayList<>());
        view.setLiveWriteFlags(2);
        view.setSinkDepth(512);   // Paper TransformerGeneratorAccess: 조각 쓰기는 레벨의 setBlock(pos, state, flags, 512)
        view.setPluginPlacement(true);
        view.setLevelRandomTouch(touch);
        SinkShapeLevel shapeLevel = new SinkShapeLevel(view, (x, y, z) -> {
            int i = (Math.floorDiv(z, 16) - chunkZ + radius) * size + (Math.floorDiv(x, 16) - chunkX + radius);
            ChunkColumn l = i >= 0 && i < live.length ? live[i] : null;
            return l == null ? view.getBlock(x, y, z) : l.getBlock(x & 15, y, z & 15);
        });
        view.useSink(new Recording(sink, session), live, LiveFeatures.sinkEdges(view, shapeLevel));
        synchronized (start) {
            start.placeInChunk(engine, view, random, lr, chunkBox, chunkX, chunkZ);
        }
        if (view.missingAccess() > 0) throw unreachable(chunkX, chunkZ, radius);
    }

    /** 청크 상자를 덮고 한 칸 여유(모양 갱신이 이웃을 읽는다), 적어도 1단계 통로와 같은 2. 상한은 /place 의 격자 상한. */
    private static int gridRadius(BoundingBox box, int chunkX, int chunkZ) {
        int r = Math.max(Math.max(Math.abs((box.minX() >> 4) - chunkX), Math.abs((box.maxX() >> 4) - chunkX)),
                Math.max(Math.abs((box.minZ() >> 4) - chunkZ), Math.abs((box.maxZ() >> 4) - chunkZ))) + 1;
        r = Math.max(r, LiveFeatures.SINK_RADIUS);
        if (r > LiveGrid.MAX_GRID_RADIUS) {
            throw new IllegalStateException("the chunk box spans more than " + (2 * LiveGrid.MAX_GRID_RADIUS - 1) + " chunks around chunk " + chunkX + ", " + chunkZ);
        }
        return r;
    }

    private static IllegalStateException unreachable(int chunkX, int chunkZ, int radius) {
        return new IllegalStateException("placing the structure in chunk " + chunkX + ", " + chunkZ
                + " reached a chunk that is not loaded, belongs to another region or is more than " + radius + " chunks away");
    }

    /** 받아들인 쓰기를 세션에 남기는 통로. */
    private record Recording(FeatureWorldView.WriteSink sink, Session session) implements FeatureWorldView.WriteSink {
        @Override
        public boolean setBlock(int x, int y, int z, int state, int flags) {
            boolean ok = sink.setBlock(x, y, z, state, flags);
            if (ok) session.accept(x, y, z, state);
            return ok;
        }

        @Override
        public boolean setBlock(int x, int y, int z, int state, int flags, int maxUpdateDepth) {
            boolean ok = sink.setBlock(x, y, z, state, flags, maxUpdateDepth);
            if (ok) session.accept(x, y, z, state);
            return ok;
        }

        @Override public String blockEntityType(int x, int y, int z) { return sink.blockEntityType(x, y, z); }
        @Override public long levelSeed(boolean throughGetLevel) { return sink.levelSeed(throughGetLevel); }
        @Override public void entityLevel() { sink.entityLevel(); }
        @Override public void addEntity(String id, double x, double y, double z) { sink.addEntity(id, x, y, z); }
        @Override public void blockUpdated(int x, int y, int z, int state) { sink.blockUpdated(x, y, z, state); }
    }

    /** 레벨의 getRandom() 을 뽑을 때마다 불러 그 원천에서 뽑는다(Paper 조각의 world.getRandom()). 위치별 공장은 Paper 와 같이 지원하지 않는다. */
    private record LevelRandom(Supplier<java.util.Random> source) implements NoiseRandom {
        @Override public int nextInt() { return source.get().nextInt(); }
        @Override public int nextInt(int bound) { return source.get().nextInt(bound); }
        @Override public long nextLong() { return source.get().nextLong(); }
        @Override public float nextFloat() { return source.get().nextFloat(); }
        @Override public double nextDouble() { return source.get().nextDouble(); }
        @Override public boolean nextBoolean() { return source.get().nextBoolean(); }

        @Override
        public void consumeCount(int count) {
            for (int i = 0; i < count; i++) source.get().nextInt();
        }

        @Override
        public PositionalRandom forkPositional() { throw new UnsupportedOperationException("Not supported yet."); }
    }
}
