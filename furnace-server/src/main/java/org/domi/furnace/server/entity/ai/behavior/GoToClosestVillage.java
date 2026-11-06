// 바닐라 GoToClosestVillage: 마을 밖이면 마을까지 섹션 거리를 줄이는 무작위 땅 위치(15×7, 5번)로 걷는다.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;
import org.domi.furnace.server.world.poi.PoiManager;

public final class GoToClosestVillage {
    private GoToClosestVillage() {}

    static int sectionsToVillage(PoiManager poi, Pos p) { return poi.sectionsToVillage(p.x() >> 4, p.y() >> 4, p.z() >> 4); }

    public static OneShot<Villager> create(float speed, int closeEnough) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Villager v, long time) {
                if (!absent(v, MemoryModuleType.WALK_TARGET)) return false;
                if (level.isVillage(v.blockPosition())) return false;
                PoiManager poi = level.poiManager();
                int cur = sectionsToVillage(poi, v.blockPosition());
                double[] target = null;
                for (int i = 0; i < 5; i++) {
                    double[] vec = LandRandomPos.getPos(v, 15, 7, p -> -sectionsToVillage(poi, p));
                    if (vec != null) {
                        int d = sectionsToVillage(poi, new Pos((int) Math.floor(vec[0]), (int) Math.floor(vec[1]), (int) Math.floor(vec[2])));
                        if (d < cur) {
                            target = vec;
                            break;
                        }
                        if (d == cur) target = vec;
                    }
                }
                if (target != null) v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, speed, closeEnough));
                return true;
            }
        };
    }
}
