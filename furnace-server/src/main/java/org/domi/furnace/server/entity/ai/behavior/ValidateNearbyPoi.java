// 바닐라 ValidateNearbyPoi: 기억한 POI 가 16칸 안인데 그 종류가 더는 아니면 기억을 지우고, 남이 차지한 침대면 티켓도 돌려준다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.function.Predicate;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.world.poi.PoiType;

public final class ValidateNearbyPoi {
    private static final int MAX_DISTANCE = 16;

    private ValidateNearbyPoi() {}

    public static OneShot<Mob> create(Predicate<PoiType> poiPredicate, MemoryModuleType<GlobalPos> memory) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long time) {
                if (!present(mob, memory)) return false;
                GlobalPos g = mob.getBrain().getMemory(memory).get();
                Pos pos = g.pos();
                if (!level.dimension().equals(g.dimension()) || !closerToCenterThan(pos, mob, MAX_DISTANCE)) return false;
                if (!level.poiManager().exists(pos, poiPredicate)) {
                    mob.getBrain().eraseMemory(memory);
                } else if (bedIsOccupied(level, pos, mob)) {
                    mob.getBrain().eraseMemory(memory);
                    level.poiManager().release(pos);
                }
                return true;
            }
        };
    }

    private static boolean bedIsOccupied(ChunkLevel level, Pos pos, Mob mob) {
        int st = level.getBlockState(pos);
        return B.isTag(st, "minecraft:beds") && B.prop("occupied").is(st) && !(mob instanceof LivingEntity le && le.isSleeping());
    }

    /** Vec3i.closerToCenterThan(position, d): 칸 가운데까지 거리 < d. */
    public static boolean closerToCenterThan(Pos p, Mob mob, double d) {
        double dx = p.x() + 0.5 - mob.x(), dy = p.y() + 0.5 - mob.y(), dz = p.z() + 0.5 - mob.z();
        return dx * dx + dy * dy + dz * dz < d * d;
    }
}
