// 지형지물 배치 다리(T-211) 구현 — 호환 계층의 NMS ConfiguredFeature/PlacedFeature#place shim 이 부른다. 소유 검사는 WorldHandle.ownedRegion
// (전역 빌림 구간이면 그 region 을 빌린다)과 같고, 배치는 LiveFeatures.placeThrough(사본 격자 + 쓰기 통로)로 한다.
package org.domi.furnace.server.plugin;

import java.util.Objects;
import java.util.Random;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.domi.furnace.api.internal.FeatureInternals;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.JavaRandomWorldgen;
import org.domi.furnace.server.world.LiveStructures;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.structure.BoundingBox;
import org.domi.furnace.server.world.gen.structure.StructureStart;

final class FeatureInternalsImpl implements FeatureInternals {

    private static WorldHandle handle(org.domi.furnace.api.world.World w) {
        if (w instanceof WorldHandle wh) return wh;
        throw new IllegalArgumentException("not a world handle created by the Furnace server: " + w);
    }

    @Override
    public boolean placeNow(org.domi.furnace.api.world.World world, boolean placed, String id, int x, int y, int z, Random random, LevelSink sink) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(random, "random");
        WorldHandle h = handle(world);
        BlockPos pos = new BlockPos(x, y, z);
        Region r = h.ownedRegion(pos, (placed ? "PlacedFeature" : "ConfiguredFeature") + "#place(" + pos + ")");
        if (r == null || !r.chunkLoaded(new ChunkPos(x >> 4, z >> 4))) throw new IllegalStateException("the chunk is not loaded: " + pos);
        FeatureWorldView.WriteSink ws = writeSink(sink);
        Boolean result = r.level().placeFeatureThrough(id, placed, new Pos(x, y, z), new JavaRandomWorldgen(random), ws);
        if (result == null) throw new IllegalArgumentException("unknown " + (placed ? "placed" : "configured") + " feature: " + id);
        return result;
    }

    private static FeatureWorldView.WriteSink writeSink(LevelSink sink) {
        return sink == null ? null : new FeatureWorldView.WriteSink() {
            @Override
            public boolean setBlock(int bx, int by, int bz, int state, int flags) { return sink.setBlock(bx, by, bz, state, flags); }

            @Override
            public boolean setBlock(int bx, int by, int bz, int state, int flags, int depth) { return sink.setBlock(bx, by, bz, state, flags, depth); }

            @Override
            public String blockEntityType(int bx, int by, int bz) { return sink.blockEntityType(bx, by, bz); }

            @Override
            public long levelSeed(boolean throughGetLevel) { return sink.levelSeed(throughGetLevel); }

            @Override
            public void entityLevel() { sink.entityLevel(); }

            @Override
            public void addEntity(String type, double x, double y, double z) { sink.addEntity(type, x, y, z); }

            @Override
            public void blockUpdated(int bx, int by, int bz, int state) { sink.blockUpdated(bx, by, bz, state); }
        };
    }

    // ------------------------------------------------------------------ 구조물(T-211 2단계)

    /** 서버 구조물 시작 손잡이. */
    private record StartHandle(StructureStart start) implements StructureStartHandle {
        @Override public String structureId() { return start.structureId(); }
        @Override public int chunkX() { return start.chunkX(); }
        @Override public int chunkZ() { return start.chunkZ(); }

        @Override
        public int[] boundingBox() {
            BoundingBox b = start.boundingBox();
            return new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
        }
    }

    private static NoiseWorldSource noise(WorldHandle h) {
        NoiseWorldSource gen = NoiseWorldSource.of(h.handle().worldSource());
        if (gen == null) throw new IllegalArgumentException("the world has no noise generator (structures): " + h);
        return gen;
    }

    @Override
    public StructureStartHandle generateStructure(org.domi.furnace.api.world.World world, String id, int chunkX, int chunkZ, Predicate<String> biomeTest) {
        Objects.requireNonNull(id, "id");
        StructureStart start = LiveStructures.generate(noise(handle(world)), id, chunkX, chunkZ, biomeTest);
        return start == null ? null : new StartHandle(start);
    }

    @Override
    public Object newStructureSession() { return new LiveStructures.Session(); }

    @Override
    public void structureSessionWrite(Object session, int x, int y, int z, int stateId) {
        ((LiveStructures.Session) Objects.requireNonNull(session, "session")).accept(x, y, z, stateId);
    }

    @Override
    public void placeStructureNow(org.domi.furnace.api.world.World world, StructureStartHandle start, int[] box, int chunkX, int chunkZ, Random random,
                                  Supplier<Random> levelRandom, Object session, LevelSink sink) {
        Objects.requireNonNull(random, "random");
        if (!(start instanceof StartHandle sh)) throw new IllegalArgumentException("not a structure start made by the Furnace server: " + start);
        if (box == null || box.length != 6) throw new IllegalArgumentException("box must be minX, minY, minZ, maxX, maxY, maxZ");
        WorldHandle h = handle(world);
        NoiseWorldSource gen = noise(h);
        BlockPos pos = new BlockPos(chunkX << 4, 0, chunkZ << 4);
        Region r = h.ownedRegion(pos, "StructureStart#placeInChunk(" + chunkX + ", " + chunkZ + ")");
        if (r == null || !r.chunkLoaded(new ChunkPos(chunkX, chunkZ))) throw new IllegalStateException("the chunk is not loaded: " + chunkX + ", " + chunkZ);
        LiveStructures.Session s = sink == null ? null : (LiveStructures.Session) Objects.requireNonNull(session, "session");
        LiveStructures.placeInChunk(r.level(), gen, sh.start(), new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]), chunkX, chunkZ,
                new JavaRandomWorldgen(random), levelRandom, s, writeSink(sink));
    }
}
