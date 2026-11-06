// 바닐라 NearestBedSensor(20틱): 아기만. 48칸 안 침대 POI 중 최근 실패하지 않은 것(한 번에 5개 미만)을 골라 길을 찾고, 닿으면 NEAREST_BED 로 둔다.
package org.domi.furnace.server.entity.ai.sensing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.behavior.AcquirePoi;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.Sensor;
import org.domi.furnace.server.entity.ai.navigation.Path;
import org.domi.furnace.server.world.poi.PoiManager;
import org.domi.furnace.server.world.poi.PoiRecord;
import org.domi.furnace.server.world.poi.PoiTypes;

public class NearestBedSensor extends Sensor<PathfinderMob> {
    private static final int CACHE_TIMEOUT = 40, BATCH_SIZE = 5;
    private final Map<Pos, Long> batchCache = new HashMap<>();
    private int triedCount;
    private long lastUpdate;

    public NearestBedSensor() { super(20); }

    @Override public Set<MemoryModuleType<?>> requires() { return Set.of(MemoryModuleType.NEAREST_BED); }

    @Override
    protected void doTick(ChunkLevel level, PathfinderMob mob) {
        if (!mob.isBaby()) return;
        triedCount = 0;
        lastUpdate = level.gameTime() + level.random().nextInt(20);
        PoiManager poi = level.poiManager();
        List<PoiRecord> found = new ArrayList<>();
        for (PoiRecord r : poi.getInRange(t -> t == PoiTypes.HOME, mob.blockPosition(), 48, PoiManager.Occupancy.ANY)) {
            if (batchCache.containsKey(r.pos())) continue;
            if (++triedCount >= BATCH_SIZE) continue;
            batchCache.put(r.pos(), lastUpdate + CACHE_TIMEOUT);
            found.add(r);
        }
        Path path = AcquirePoi.findPathToPois(mob, found);
        if (path != null && path.canReach()) {
            Pos target = path.getTarget();
            if (poi.getType(target) != null) mob.getBrain().setMemory(MemoryModuleType.NEAREST_BED, target);
        } else if (triedCount < BATCH_SIZE) {
            batchCache.entrySet().removeIf(e -> e.getValue() < lastUpdate);
        }
    }
}
