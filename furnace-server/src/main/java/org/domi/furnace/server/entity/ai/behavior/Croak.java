// 바닐라 Croak(개구리 울음): 서 있고 걷는 목표가 없으면 물 밖에서 울음 자세로 60틱 동안 서 있는다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Frog;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;

public class Croak extends Behavior<Frog> {
    private static final int CROAK_TICKS = 60;
    private static final int TIME_OUT_DURATION = 100;
    private int croakCounter;

    public Croak() { super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT), TIME_OUT_DURATION); }

    @Override protected boolean checkExtraStartConditions(ChunkLevel level, Frog frog) { return frog.getPose() == Entity.POSE_STANDING; }

    @Override protected boolean canStillUse(ChunkLevel level, Frog frog, long gameTime) { return croakCounter < CROAK_TICKS; }

    @Override
    protected void start(ChunkLevel level, Frog frog, long gameTime) {
        if (!frog.isInWaterOrBubble()) {
            frog.setPose(Entity.POSE_CROAKING);
            croakCounter = 0;
        }
    }

    @Override protected void stop(ChunkLevel level, Frog frog, long gameTime) { frog.setPose(Entity.POSE_STANDING); }

    @Override protected void tick(ChunkLevel level, Frog frog, long gameTime) { croakCounter++; }
}
