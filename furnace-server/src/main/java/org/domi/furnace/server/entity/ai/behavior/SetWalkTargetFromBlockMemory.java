// 바닐라 SetWalkTargetFromBlockMemory: 기억한 블록(집·직업·모임)으로 걷는 목표. tooFar 보다 멀면 그쪽으로 15×7 무작위 점(최대 1000번),
// tooLong 틱 넘게 못 닿았거나 다른 차원이면 기억을 버리고 villager.releasePoi 를 부른다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Optional;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.entity.ai.util.DefaultRandomPos;

public final class SetWalkTargetFromBlockMemory {
    private SetWalkTargetFromBlockMemory() {}

    /** Vec3i.distManhattan. */
    static int distManhattan(Pos a, Pos b) { return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z()); }

    public static OneShot<Villager> create(MemoryModuleType<GlobalPos> memory, float speed, int closeEnough, int tooFar, int tooLong) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Villager v, long time) {
                if (!registered(v, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE) || !absent(v, MemoryModuleType.WALK_TARGET) || !present(v, memory)) return false;
                var brain = v.getBrain();
                GlobalPos g = brain.getMemory(memory).get();
                Optional<Long> since = brain.getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
                if (g.dimension().equals(level.dimension()) && (since.isEmpty() || level.gameTime() - since.get() <= tooLong)) {
                    if (distManhattan(g.pos(), v.blockPosition()) > tooFar) {
                        double[] vec = null;
                        int tries = 0;
                        while (vec == null || distManhattan(new Pos((int) Math.floor(vec[0]), (int) Math.floor(vec[1]), (int) Math.floor(vec[2])), v.blockPosition()) > tooFar) {
                            vec = DefaultRandomPos.getPosTowards(v, 15, 7, new double[] {g.pos().x() + 0.5, g.pos().y(), g.pos().z() + 0.5}, (float) (Math.PI / 2));
                            if (++tries == 1000) {
                                v.releasePoi(memory);
                                brain.eraseMemory(memory);
                                brain.setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, time);
                                return true;
                            }
                        }
                        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(vec, speed, closeEnough));
                    } else if (distManhattan(g.pos(), v.blockPosition()) > closeEnough) {
                        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(g.pos(), speed, closeEnough));
                    }
                } else {
                    v.releasePoi(memory);
                    brain.eraseMemory(memory);
                    brain.setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, time);
                }
                return true;
            }
        };
    }
}
