// 바닐라 StrollAroundPoi: 기억한 관심 위치 근처에 있으면 180틱마다 주변을 무작위로 걷는다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Optional;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;

public final class StrollAroundPoi {
    private StrollAroundPoi() {}

    public static OneShot<PathfinderMob> create(MemoryModuleType<GlobalPos> poiMemory, float speed, int maxDistFromPoi) {
        long[] nextOk = {0L};
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, PathfinderMob mob, long gameTime) {
                if (!registered(mob, MemoryModuleType.WALK_TARGET) || !present(mob, poiMemory)) return false;
                GlobalPos g = mob.getBrain().getMemory(poiMemory).get();
                double dx = g.pos().x() + 0.5 - mob.x(), dy = g.pos().y() + 0.5 - mob.y(), dz = g.pos().z() + 0.5 - mob.z();
                if (!level.dimension().equals(g.dimension()) || !(dx * dx + dy * dy + dz * dz < (double) maxDistFromPoi * maxDistFromPoi)) return false;
                if (gameTime <= nextOk[0]) return true;
                double[] v = LandRandomPos.getPos(mob, 8, 6);
                mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, v == null ? Optional.<WalkTarget>empty() : Optional.of(new WalkTarget(v, speed, 1)));
                nextOk[0] = gameTime + 180L;
                return true;
            }
        };
    }
}
