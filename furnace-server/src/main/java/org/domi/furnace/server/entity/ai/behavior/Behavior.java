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
    // 바닐라는 endTimestamp = gameTime + 지속시간 으로 끈다. Furnace 는 region 틱과 전역 gameTime 이 따로 가서
    // (기억 TTL 은 브레인 틱마다 줄고 gameTime 은 전역 스레드가 올림) 둘이 어긋나면 SonicBoom 처럼 기억과 지속시간이
    // 1틱 차이로 맞물린 행동이 두 번 발동한다. 그래서 시작 뒤 지난 브레인 틱 수로 잰다(엔티티가 매 gameTime 틱마다 도는 바닐라와 같은 값).
    private long duration, elapsed;
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
            duration = i;
            elapsed = -1;
            start(level, entity, gameTime);
            return true;
        }
        return false;
    }

    protected void start(ChunkLevel level, E entity, long gameTime) { }

    @Override
    public final void tickOrStop(ChunkLevel level, E entity, long gameTime) {
        elapsed++;
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

    /** 바닐라 gameTime > endTimestamp 와 같은 뜻: 시작 틱부터 지속시간을 넘긴 브레인 틱. */
    protected boolean timedOut(long gameTime) { return elapsed > duration; }

    protected boolean checkExtraStartConditions(ChunkLevel level, E entity) { return true; }

    @Override public String debugString() { return getClass().getSimpleName(); }

    protected boolean hasRequiredMemories(E entity) {
        for (Map.Entry<MemoryModuleType<?>, MemoryStatus> e : entryCondition.entrySet()) {
            if (!entity.getBrain().checkMemory(e.getKey(), e.getValue())) return false;
        }
        return true;
    }
}
