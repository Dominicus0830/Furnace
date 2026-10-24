// 바닐라 GateBehavior(RunOne/RunSometimes): 조건이 맞으면 하위 행동들 중 하나(또는 가능한 전부)를 시작하고, 모두 끝나면 같이 끝난다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;

public class GateBehavior<E extends Mob> implements BehaviorControl<E> {
    public enum OrderPolicy { ORDERED, SHUFFLED }

    public enum RunningPolicy { RUN_ONE, TRY_ALL }

    private final Map<MemoryModuleType<?>, MemoryStatus> entryCondition;
    private final Set<MemoryModuleType<?>> exitErasedMemories;
    private final OrderPolicy orderPolicy;
    private final RunningPolicy runningPolicy;
    private final ShufflingList<BehaviorControl<? super E>> behaviors = new ShufflingList<>();
    private Status status = Status.STOPPED;

    public GateBehavior(Map<MemoryModuleType<?>, MemoryStatus> entryCondition, Set<MemoryModuleType<?>> exitErasedMemories, OrderPolicy order, RunningPolicy running,
            List<Map.Entry<? extends BehaviorControl<? super E>, Integer>> behaviors) {
        this.entryCondition = entryCondition;
        this.exitErasedMemories = exitErasedMemories;
        this.orderPolicy = order;
        this.runningPolicy = running;
        behaviors.forEach(e -> this.behaviors.add(e.getKey(), e.getValue()));
    }

    @Override public Status getStatus() { return status; }

    private boolean hasRequiredMemories(E entity) {
        for (Map.Entry<MemoryModuleType<?>, MemoryStatus> e : entryCondition.entrySet()) if (!entity.getBrain().checkMemory(e.getKey(), e.getValue())) return false;
        return true;
    }

    @Override
    public final boolean tryStart(ChunkLevel level, E entity, long gameTime) {
        if (hasRequiredMemories(entity)) {
            status = Status.RUNNING;
            if (orderPolicy == OrderPolicy.SHUFFLED) behaviors.shuffle();
            if (runningPolicy == RunningPolicy.RUN_ONE) {
                for (BehaviorControl<? super E> b : behaviors.asList()) {
                    if (b.getStatus() == Status.STOPPED && b.tryStart(level, entity, gameTime)) break;
                }
            } else {
                for (BehaviorControl<? super E> b : behaviors.asList()) if (b.getStatus() == Status.STOPPED) b.tryStart(level, entity, gameTime);
            }
            return true;
        }
        return false;
    }

    @Override
    public final void tickOrStop(ChunkLevel level, E entity, long gameTime) {
        for (BehaviorControl<? super E> b : behaviors.asList()) if (b.getStatus() == Status.RUNNING) b.tickOrStop(level, entity, gameTime);
        boolean any = false;
        for (BehaviorControl<? super E> b : behaviors.asList()) if (b.getStatus() == Status.RUNNING) any = true;
        if (!any) doStop(level, entity, gameTime);
    }

    @Override
    public final void doStop(ChunkLevel level, E entity, long gameTime) {
        status = Status.STOPPED;
        for (BehaviorControl<? super E> b : behaviors.asList()) if (b.getStatus() == Status.RUNNING) b.doStop(level, entity, gameTime);
        for (MemoryModuleType<?> m : exitErasedMemories) entity.getBrain().eraseMemory(m);
    }

    @Override public String debugString() { return getClass().getSimpleName(); }
}
