// 바닐라 DoNothing: 정해진 시간(min~max틱) 동안 아무것도 안 하는 행동(RunOne 에서 쉬는 칸).
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;

public class DoNothing implements BehaviorControl<Mob> {
    private final int minDuration, maxDuration;
    private Status status = Status.STOPPED;
    private long endTimestamp;

    public DoNothing(int min, int max) {
        this.minDuration = min;
        this.maxDuration = max;
    }

    @Override public Status getStatus() { return status; }

    @Override
    public final boolean tryStart(ChunkLevel level, Mob entity, long gameTime) {
        status = Status.RUNNING;
        int i = minDuration + level.random().nextInt(maxDuration + 1 - minDuration);
        endTimestamp = gameTime + i;
        return true;
    }

    @Override
    public final void tickOrStop(ChunkLevel level, Mob entity, long gameTime) {
        if (gameTime > endTimestamp) doStop(level, entity, gameTime);
    }

    @Override public final void doStop(ChunkLevel level, Mob entity, long gameTime) { status = Status.STOPPED; }

    @Override public String debugString() { return getClass().getSimpleName(); }
}
