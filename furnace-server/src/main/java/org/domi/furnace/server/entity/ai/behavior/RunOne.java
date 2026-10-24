// 바닐라 RunOne: 섞은 순서로 하위 행동 중 하나만 시작하는 GateBehavior.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;

public class RunOne<E extends Mob> extends GateBehavior<E> {
    public RunOne(List<Map.Entry<? extends BehaviorControl<? super E>, Integer>> behaviors) { this(Map.of(), behaviors); }

    public RunOne(Map<MemoryModuleType<?>, MemoryStatus> entryCondition, List<Map.Entry<? extends BehaviorControl<? super E>, Integer>> behaviors) {
        super(entryCondition, Set.of(), OrderPolicy.SHUFFLED, RunningPolicy.RUN_ONE, behaviors);
    }
}
