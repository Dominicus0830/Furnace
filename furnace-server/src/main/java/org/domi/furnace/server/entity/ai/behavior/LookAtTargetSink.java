// 바닐라 LookAtTargetSink: LOOK_TARGET 기억 쪽을 시선 제어로 바라본다(보이지 않으면 멈춤).
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;

public class LookAtTargetSink extends Behavior<Mob> {
    public LookAtTargetSink(int minDuration, int maxDuration) {
        super(Map.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_PRESENT), minDuration, maxDuration);
    }

    @Override
    protected boolean canStillUse(ChunkLevel level, Mob mob, long gameTime) {
        return mob.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).filter(t -> t.isVisibleBy(mob)).isPresent();
    }

    @Override
    protected void stop(ChunkLevel level, Mob mob, long gameTime) { mob.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET); }

    @Override
    protected void tick(ChunkLevel level, Mob mob, long gameTime) {
        mob.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).ifPresent(t -> {
            double[] p = t.currentPosition();
            mob.getLookControl().setLookAt(p[0], p[1], p[2]);
        });
    }
}
