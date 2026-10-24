// 바닐라 Behavior: 기억 조건 → (시작 시 무작위 지속시간) → 유지 검사 → 종료. 하위 클래스가 훅을 재정의한다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;

public abstract class Behavior<E extends Mob> implements BehaviorControl<E> {
    public static final int DEFAULT_DURATION = 60;
    protected final Map<MemoryModuleType<?>, MemoryStatus> entryCondition;
    private Status status = Status.STOPPED;
    private long endTimestamp;
    private final int minDuration, maxDuration;

    public Behavior(Map<MemoryModuleType<?>, MemoryStatus> entryCondition) { this(entryCondition, DEFAULT_DURATION); }

    public Behavior(Map<MemoryModuleType<?>, MemoryStatus> entryCondition, int duration) { this(entryCondition, duration, duration); }

    public Behavior(Map<MemoryModuleType<?>, MemoryStatus> entryCondition, int minDuration, int maxDuration) {
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
        this.entryCondition = entryCondition;
    }

    @Override public Status getStatus() { return status; }

    @Override
    public final boolean tryStart(ChunkLevel level, E entity, long gameTime) {
        if (hasRequiredMemories(entity) && checkExtraStartConditions(level, entity)) {
            status = Status.RUNNING;
            int i = minDuration + level.random().nextInt(maxDuration + 1 - minDuration);
            endTimestamp = gameTime + i;
            start(level, entity, gameTime);
            return true;
        }
        return false;
    }

    protected void start(ChunkLevel level, E entity, long gameTime) { }

    @Override
    public final void tickOrStop(ChunkLevel level, E entity, long gameTime) {
        if (!timedOut(gameTime) && canStillUse(level, entity, gameTime)) tick(level, entity, gameTime);
        else doStop(level, entity, gameTime);
    }

    protected void tick(ChunkLevel level, E entity, long gameTime) { }

    @Override
    public final void doStop(ChunkLevel level, E entity, long gameTime) {
        status = Status.STOPPED;
        stop(level, entity, gameTime);
    }

    protected void stop(ChunkLevel level, E entity, long gameTime) { }

    protected boolean canStillUse(ChunkLevel level, E entity, long gameTime) { return false; }

    protected boolean timedOut(long gameTime) { return gameTime > endTimestamp; }

    protected boolean checkExtraStartConditions(ChunkLevel level, E entity) { return true; }

    @Override public String debugString() { return getClass().getSimpleName(); }

    protected boolean hasRequiredMemories(E entity) {
        for (Map.Entry<MemoryModuleType<?>, MemoryStatus> e : entryCondition.entrySet()) {
            if (!entity.getBrain().checkMemory(e.getKey(), e.getValue())) return false;
        }
        return true;
    }
}
