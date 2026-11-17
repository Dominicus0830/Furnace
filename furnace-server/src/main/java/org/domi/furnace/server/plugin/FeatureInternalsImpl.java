// 지형지물 배치 다리(T-211) 구현 — 호환 계층의 NMS ConfiguredFeature/PlacedFeature#place shim 이 부른다. 소유 검사는 WorldHandle.ownedRegion
// (전역 빌림 구간이면 그 region 을 빌린다)과 같고, 배치는 LiveFeatures.placeThrough(사본 격자 + 쓰기 통로)로 한다.
package org.domi.furnace.server.plugin;

import java.util.Objects;
import java.util.Random;
import org.domi.furnace.api.internal.FeatureInternals;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.JavaRandomWorldgen;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;

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
        FeatureWorldView.WriteSink ws = sink == null ? null : new FeatureWorldView.WriteSink() {
            @Override
            public boolean setBlock(int bx, int by, int bz, int state, int flags) { return sink.setBlock(bx, by, bz, state, flags); }

            @Override
            public String blockEntityType(int bx, int by, int bz) { return sink.blockEntityType(bx, by, bz); }
        };
        Boolean result = r.level().placeFeatureThrough(id, placed, new Pos(x, y, z), new JavaRandomWorldgen(random), ws);
        if (result == null) throw new IllegalArgumentException("unknown " + (placed ? "placed" : "configured") + " feature: " + id);
        return result;
    }
}
