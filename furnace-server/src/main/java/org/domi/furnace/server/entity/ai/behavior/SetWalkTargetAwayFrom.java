// 바닐라 SetWalkTargetAwayFrom: 기억한 위치/대상이 desiredDistance 안이면 반대쪽 무작위 점으로 걷는 목표를 둔다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.function.Function;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;

public final class SetWalkTargetAwayFrom {
    private SetWalkTargetAwayFrom() {}

    public static OneShot<PathfinderMob> pos(MemoryModuleType<Pos> memory, float speed, int desiredDistance, boolean interrupt) {
        return create(memory, speed, desiredDistance, interrupt, p -> new double[] {p.x() + 0.5, p.y() + 0.5, p.z() + 0.5});
    }

    public static OneShot<PathfinderMob> entity(MemoryModuleType<? extends Targetable> memory, float speed, int desiredDistance, boolean interrupt) {
        return create(memory, speed, desiredDistance, interrupt, t -> new double[] {t.x(), t.y(), t.z()});
    }

    private static <T> OneShot<PathfinderMob> create(MemoryModuleType<T> memory, float speed, int desiredDistance, boolean interrupt, Function<T, double[]> position) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, PathfinderMob mob, long gameTime) {
                if (!registered(mob, MemoryModuleType.WALK_TARGET) || !present(mob, memory)) return false;
                var brain = mob.getBrain();
                var walk = brain.getMemory(MemoryModuleType.WALK_TARGET);
                if (walk.isPresent() && !interrupt) return false;
                double[] from = position.apply(brain.getMemory(memory).get());
                double dx = from[0] - mob.x(), dy = from[1] - mob.y(), dz = from[2] - mob.z();
                if (!(dx * dx + dy * dy + dz * dz < (double) desiredDistance * desiredDistance)) return false;
                if (walk.isPresent() && walk.get().getSpeedModifier() == speed) {
                    double[] cur = walk.get().getTarget().currentPosition();
                    double ax = cur[0] - mob.x(), ay = cur[1] - mob.y(), az = cur[2] - mob.z();
                    if (ax * dx + ay * dy + az * dz < 0.0) return false;
                }
                for (int i = 0; i < 10; i++) {
                    double[] v = LandRandomPos.getPosAway(mob, 16, 7, from);
                    if (v != null) {
                        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(v, speed, 0));
                        break;
                    }
                }
                return true;
            }
        };
    }
}
