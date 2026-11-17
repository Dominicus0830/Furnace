// 바닐라 VillageBoundRandomStroll: 마을 안이면 무작위 땅 위치로, 밖이면 2섹션 안에서 마을에 더 가까운 섹션 가운데 쪽으로 걷는다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Optional;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.entity.ai.util.DefaultRandomPos;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;
import org.domi.furnace.server.world.poi.PoiManager;

public final class VillageBoundRandomStroll {
    private static final int MAX_XZ_DIST = 10, MAX_Y_DIST = 7;

    private VillageBoundRandomStroll() {}

    public static OneShot<PathfinderMob> create(float speed) { return create(speed, MAX_XZ_DIST, MAX_Y_DIST); }

    public static OneShot<PathfinderMob> create(float speed, int maxXz, int maxY) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, PathfinderMob mob, long time) {
                if (!absent(mob, MemoryModuleType.WALK_TARGET)) return false;
                Pos pos = mob.blockPosition();
                double[] vec;
                if (level.isVillage(pos)) {
                    vec = LandRandomPos.getPos(mob, maxXz, maxY);
                } else {
                    int[] sp = {pos.x() >> 4, pos.y() >> 4, pos.z() >> 4};
                    int[] best = findSectionClosestToVillage(level.poiManager(), sp, 2);
                    if (best[0] != sp[0] || best[1] != sp[1] || best[2] != sp[2]) {
                        vec = DefaultRandomPos.getPosTowards(mob, maxXz, maxY, new double[] {(best[0] << 4) + 8 + 0.5, (best[1] << 4) + 8, (best[2] << 4) + 8 + 0.5},
                                (float) (Math.PI / 2));
                    } else {
                        vec = LandRandomPos.getPos(mob, maxXz, maxY);
                    }
                }
                mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, Optional.ofNullable(vec).map(v -> new WalkTarget(v, speed, 0)));
                return true;
            }
        };
    }

    /** BehaviorUtils.findSectionClosestToVillage: 둘레 섹션(z → y → x 순) 중 지금보다 마을에 가까운 것의 최솟값(같으면 먼저 것). 창 한 번 읽기(PoiManager.closestVillageSection). */
    public static int[] findSectionClosestToVillage(PoiManager poi, int[] sp, int radius) { return poi.closestVillageSection(sp, radius); }
}
