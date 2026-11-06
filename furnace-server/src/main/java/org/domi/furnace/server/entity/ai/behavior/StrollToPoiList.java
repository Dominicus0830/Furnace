// 바닐라 StrollToPoiList: 기준 POI(직업 블록) maxDist 안이면 목록(보조 작업 블록) 중 하나를 레벨 난수로 골라 100틱마다 거기로 걷는다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.List;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;

public final class StrollToPoiList {
    private StrollToPoiList() {}

    public static OneShot<Villager> create(MemoryModuleType<List<GlobalPos>> poiListMemory, float speed, int closeEnough, int maxDist, MemoryModuleType<GlobalPos> mustBeCloseTo) {
        long[] nextOk = {0L};
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Villager v, long time) {
                if (!registered(v, MemoryModuleType.WALK_TARGET) || !present(v, poiListMemory) || !present(v, mustBeCloseTo)) return false;
                List<GlobalPos> list = v.getBrain().getMemory(poiListMemory).get();
                GlobalPos close = v.getBrain().getMemory(mustBeCloseTo).get();
                if (list.isEmpty()) return false;
                GlobalPos g = list.get(level.random().nextInt(list.size()));
                if (g == null || !level.dimension().equals(g.dimension()) || !ValidateNearbyPoi.closerToCenterThan(close.pos(), v, maxDist)) return false;
                if (time > nextOk[0]) {
                    v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(g.pos(), speed, closeEnough));
                    nextOk[0] = time + 100L;
                }
                return true;
            }
        };
    }
}
