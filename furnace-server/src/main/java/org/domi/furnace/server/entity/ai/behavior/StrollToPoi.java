// 바닐라 StrollToPoi: 기억한 관심 위치가 maxDist 안이면 80틱마다 거기로 걷는 목표를 둔다.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;

public final class StrollToPoi {
    private StrollToPoi() {}

    public static OneShot<PathfinderMob> create(MemoryModuleType<GlobalPos> poiMemory, float speed, int closeEnoughDist, int maxDistFromPoi) {
        long[] nextOk = {0L};
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, PathfinderMob mob, long gameTime) {
                if (!registered(mob, MemoryModuleType.WALK_TARGET) || !present(mob, poiMemory)) return false;
                GlobalPos g = mob.getBrain().getMemory(poiMemory).get();
                double dx = g.pos().x() + 0.5 - mob.x(), dy = g.pos().y() + 0.5 - mob.y(), dz = g.pos().z() + 0.5 - mob.z();
                if (!level.dimension().equals(g.dimension()) || !(dx * dx + dy * dy + dz * dz < (double) maxDistFromPoi * maxDistFromPoi)) return false;
                if (gameTime <= nextOk[0]) return true;
                mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(g.pos(), speed, closeEnoughDist));
                nextOk[0] = gameTime + 80L;
                return true;
            }
        };
    }
}
