// 바닐라 SetClosestHomeAsWalkTarget: 집이 없으면 20틱(+0~19)마다 48칸 안 가장 가까운 침대가 4칸보다 멀 때, 최근 실패하지 않은 침대(5개 미만)로 길을 찾아 걷는다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.entity.ai.navigation.Path;
import org.domi.furnace.server.world.poi.PoiManager;
import org.domi.furnace.server.world.poi.PoiRecord;
import org.domi.furnace.server.world.poi.PoiTypes;

public final class SetClosestHomeAsWalkTarget {
    private static final int CACHE_TIMEOUT = 40, BATCH_SIZE = 5, RATE = 20, OK_DISTANCE_SQR = 4;

    private SetClosestHomeAsWalkTarget() {}

    public static OneShot<PathfinderMob> create(float speed) {
        Map<Pos, Long> batchCache = new HashMap<>();
        long[] lastUpdate = {0L};
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, PathfinderMob mob, long time) {
                if (!absent(mob, MemoryModuleType.WALK_TARGET) || !absent(mob, MemoryModuleType.HOME)) return false;
                if (level.gameTime() - lastUpdate[0] < RATE) return false;
                PoiManager poi = level.poiManager();
                Pos closest = poi.findClosest(t -> t == PoiTypes.HOME, mob.blockPosition(), 48, PoiManager.Occupancy.ANY);
                if (closest == null || distSqr(closest, mob.blockPosition()) <= OK_DISTANCE_SQR) return false;
                int[] tried = {0};
                lastUpdate[0] = level.gameTime() + level.random().nextInt(20);
                List<PoiRecord> found = new ArrayList<>();
                for (PoiRecord r : poi.getInRange(t -> t == PoiTypes.HOME, mob.blockPosition(), 48, PoiManager.Occupancy.ANY)) {
                    if (batchCache.containsKey(r.pos())) continue;
                    if (++tried[0] >= BATCH_SIZE) continue;
                    batchCache.put(r.pos(), lastUpdate[0] + CACHE_TIMEOUT);
                    found.add(r);
                }
                Path path = AcquirePoi.findPathToPois(mob, found);
                if (path != null && path.canReach()) {
                    Pos target = path.getTarget();
                    if (poi.getType(target) != null) mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, speed, 1));
                } else if (tried[0] < BATCH_SIZE) {
                    batchCache.entrySet().removeIf(e -> e.getValue() < lastUpdate[0]);
                }
                return true;
            }
        };
    }

    /** Vec3i.distSqr. */
    static double distSqr(Pos a, Pos b) {
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
