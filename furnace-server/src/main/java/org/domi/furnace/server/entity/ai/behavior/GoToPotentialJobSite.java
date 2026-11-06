// 바닐라 GoToPotentialJobSite(최대 1200틱): 쉬는·일하는·노는 중이면 찾아 둔 직업 블록으로 걷는다. 끝나면 티켓을 돌려주고 기억을 지운다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;

public class GoToPotentialJobSite extends Behavior<Villager> {
    private static final int TICKS_UNTIL_TIMEOUT = 1200;
    final float speedModifier;

    public GoToPotentialJobSite(float speed) {
        super(Map.of(MemoryModuleType.POTENTIAL_JOB_SITE, MemoryStatus.VALUE_PRESENT), TICKS_UNTIL_TIMEOUT);
        this.speedModifier = speed;
    }

    @Override
    protected boolean checkExtraStartConditions(ChunkLevel level, Villager v) {
        return v.getBrain().getActiveNonCoreActivity().map(a -> a == Activity.IDLE || a == Activity.WORK || a == Activity.PLAY).orElse(true);
    }

    @Override
    protected boolean canStillUse(ChunkLevel level, Villager v, long time) { return v.getBrain().hasMemoryValue(MemoryModuleType.POTENTIAL_JOB_SITE); }

    @Override
    protected void tick(ChunkLevel level, Villager v, long time) {
        BehaviorUtils.setWalkAndLookTargetMemories(v, v.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).get().pos(), speedModifier, 1);
    }

    @Override
    protected void stop(ChunkLevel level, Villager v, long time) {
        v.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).ifPresent(g -> {
            if (level.dimension().equals(g.dimension()) && level.poiManager().exists(g.pos(), t -> true)) level.poiManager().release(g.pos());
        });
        v.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
    }
}
