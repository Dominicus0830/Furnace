// 바닐라 AcquirePoi: 20틱(+0~19)마다 48칸 안 빈자리 있는 POI 가까운 순 5개로 길을 찾아, 닿을 수 있으면 티켓을 잡고 기억에 둔다.
// 실패한 칸은 JitteredLinearRetry(40~80틱씩 늘어 최대 400틱) 동안 다시 보지 않는다. 난수는 모두 레벨 난수.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.navigation.Path;
import org.domi.furnace.server.world.poi.PoiManager;
import org.domi.furnace.server.world.poi.PoiRecord;
import org.domi.furnace.server.world.poi.PoiType;

public final class AcquirePoi {
    public static final int SCAN_RANGE = 48;

    private AcquirePoi() {}

    public static OneShot<PathfinderMob> create(Predicate<PoiType> poiType, MemoryModuleType<GlobalPos> memory, boolean onlyIfAdult, Optional<Byte> entityEvent) {
        return create(poiType, memory, memory, onlyIfAdult, entityEvent);
    }

    /** memoryToAcquire·memoryToValidate 둘 다 비었을 때만. 찾으면 memoryToValidate 에 둔다. */
    public static OneShot<PathfinderMob> create(Predicate<PoiType> poiType, MemoryModuleType<GlobalPos> memoryToAcquire, MemoryModuleType<GlobalPos> memoryToValidate,
            boolean onlyIfAdult, Optional<Byte> entityEvent) {
        long[] nextScheduledStart = {0L};
        Map<Pos, JitteredLinearRetry> batchCache = new HashMap<>();
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, PathfinderMob mob, long time) {
                if (!absent(mob, memoryToAcquire) || !absent(mob, memoryToValidate)) return false;
                if (onlyIfAdult && mob.isBaby()) return false;
                if (nextScheduledStart[0] == 0L) {
                    nextScheduledStart[0] = level.gameTime() + level.random().nextInt(20);
                    return false;
                }
                if (level.gameTime() < nextScheduledStart[0]) return false;
                nextScheduledStart[0] = time + 20L + level.random().nextInt(20);
                PoiManager poi = level.poiManager();
                batchCache.entrySet().removeIf(e -> !e.getValue().isStillValid(time));
                Predicate<Pos> posPred = p -> {
                    JitteredLinearRetry r = batchCache.get(p);
                    if (r == null) return true;
                    if (!r.shouldRetry(time)) return false;
                    r.markAttempt(time);
                    return true;
                };
                List<PoiRecord> all = poi.findAllClosestFirstWithType(poiType, posPred, mob.blockPosition(), SCAN_RANGE, PoiManager.Occupancy.HAS_SPACE);
                List<PoiRecord> set = all.size() > 5 ? new ArrayList<>(all.subList(0, 5)) : all;
                Path path = findPathToPois(mob, set);
                if (path != null && path.canReach()) {
                    Pos target = path.getTarget();
                    if (poi.getType(target) != null) {
                        poi.take(poiType, (t, p) -> p.equals(target), target, 1);
                        mob.getBrain().setMemory(memoryToValidate, new GlobalPos(level.dimension(), target));
                        entityEvent.ifPresent(b -> mob.broadcastEntityEvent(b));
                        batchCache.clear();
                    }
                } else {
                    for (PoiRecord r : set) batchCache.computeIfAbsent(r.pos(), k -> new JitteredLinearRetry(level.random(), time));
                }
                return true;
            }
        };
    }

    /** findPathToPois: 후보 칸 모두를 목표로 한 길(정확도는 후보 종류의 유효 거리 중 최대, 최소 1). */
    public static Path findPathToPois(PathfinderMob mob, List<PoiRecord> pois) {
        if (pois.isEmpty()) return null;
        int accuracy = 1;
        List<Pos> targets = new ArrayList<>();
        for (PoiRecord r : pois) {
            accuracy = Math.max(accuracy, r.type().validRange());
            if (!targets.contains(r.pos())) targets.add(r.pos());
        }
        return mob.getNavigation().createPath(vanillaHashOrder(targets), accuracy);
    }

    /** 바닐라 HashSet<BlockPos> 의 순회 순서(작은 집합: 16칸 버킷, Vec3i.hashCode 의 spread). 같은 버킷은 넣은 순서. */
    static LinkedHashSet<Pos> vanillaHashOrder(List<Pos> list) {
        List<Pos> sorted = new ArrayList<>(list);
        int cap = 16;
        while (sorted.size() > cap * 3 / 4) cap <<= 1;
        int mask = cap - 1;
        sorted.sort(Comparator.comparingInt(p -> {
            int h = (p.y() + p.z() * 31) * 31 + p.x();
            return (h ^ (h >>> 16)) & mask;
        }));
        return new LinkedHashSet<>(sorted);
    }

    /** AcquirePoi.JitteredLinearRetry. */
    static final class JitteredLinearRetry {
        private static final int MIN_INTERVAL_INCREASE = 40, MAX_INTERVAL_INCREASE = 80, MAX_RETRY_PATHFINDING_INTERVAL = 400;
        private final RandomSource random;
        private long previousAttemptTimestamp, nextScheduledAttemptTimestamp;
        private int currentDelay;

        JitteredLinearRetry(RandomSource random, long time) {
            this.random = random;
            markAttempt(time);
        }

        void markAttempt(long time) {
            previousAttemptTimestamp = time;
            int d = currentDelay + random.nextInt(MIN_INTERVAL_INCREASE) + MIN_INTERVAL_INCREASE;
            currentDelay = Math.min(d, MAX_RETRY_PATHFINDING_INTERVAL);
            nextScheduledAttemptTimestamp = time + currentDelay;
        }

        boolean isStillValid(long time) { return time - previousAttemptTimestamp < 400L; }

        boolean shouldRetry(long time) { return time >= nextScheduledAttemptTimestamp; }
    }
}
