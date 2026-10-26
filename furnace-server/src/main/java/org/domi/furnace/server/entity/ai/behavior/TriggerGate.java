// 바닐라 TriggerGate: 한 번짜리 행동들을 (섞어서) 차례로 trigger 하고, RUN_ONE 이면 처음 성공한 것에서 멈춘다. 자신은 언제나 성공.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;

public final class TriggerGate {
    private TriggerGate() {}

    public static <E extends Mob> OneShot<E> triggerOneShuffled(List<Map.Entry<? extends OneShot<? super E>, Integer>> triggers) {
        return triggerGate(triggers, GateBehavior.OrderPolicy.SHUFFLED, GateBehavior.RunningPolicy.RUN_ONE);
    }

    @SuppressWarnings("unchecked")
    public static <E extends Mob> OneShot<E> triggerGate(List<Map.Entry<? extends OneShot<? super E>, Integer>> triggers, GateBehavior.OrderPolicy order,
            GateBehavior.RunningPolicy running) {
        ShufflingList<OneShot<? super E>> list = new ShufflingList<>();
        for (var e : triggers) list.add(e.getKey(), e.getValue());
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, E mob, long time) {
                if (order == GateBehavior.OrderPolicy.SHUFFLED) list.shuffle();
                for (OneShot<? super E> t : list.asList()) {
                    if (((OneShot<E>) t).triggerOnly(level, mob, time) && running == GateBehavior.RunningPolicy.RUN_ONE) break;
                }
                return true;
            }
        };
    }
}
