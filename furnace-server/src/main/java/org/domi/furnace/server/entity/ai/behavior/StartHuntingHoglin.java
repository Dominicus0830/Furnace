// 바닐라 StartHuntingHoglin: 사냥할 수 있는 호글린이 보이고 최근 사냥하지 않았으면 그 호글린에게 분노하고 주변 피글린에게도 알린다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.List;
import java.util.Optional;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Piglin;
import org.domi.furnace.server.entity.PiglinAi;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class StartHuntingHoglin {
    private StartHuntingHoglin() {}

    public static OneShot<Piglin> create() {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Piglin piglin, long gameTime) {
                if (!present(piglin, MemoryModuleType.NEAREST_VISIBLE_HUNTABLE_HOGLIN) || !absent(piglin, MemoryModuleType.ANGRY_AT) || !absent(piglin, MemoryModuleType.HUNTED_RECENTLY)
                        || !registered(piglin, MemoryModuleType.NEARBY_ADULT_PIGLINS)) return false;
                Optional<List<Mob>> nearby = piglin.getBrain().getMemory(MemoryModuleType.NEARBY_ADULT_PIGLINS);
                // 바닐라 그대로: map(...).isPresent() 라서 근처 어른 피글린 목록이 기억에 있기만 해도 시작하지 않는다.
                if (!piglin.isBaby() && !nearby.map(l -> l.stream().anyMatch(StartHuntingHoglin::hasHuntedRecently)).isPresent()) {
                    Mob hoglin = piglin.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_HUNTABLE_HOGLIN).get();
                    PiglinAi.setAngerTarget(piglin, hoglin);
                    PiglinAi.dontKillAnyMoreHoglinsForAWhile(piglin);
                    PiglinAi.broadcastAngerTarget(piglin, hoglin);
                    nearby.ifPresent(l -> l.forEach(m -> PiglinAi.dontKillAnyMoreHoglinsForAWhile((org.domi.furnace.server.entity.AbstractPiglin) m)));
                    return true;
                }
                return false;
            }
        };
    }

    private static boolean hasHuntedRecently(Mob piglin) { return piglin.getBrain().hasMemoryValue(MemoryModuleType.HUNTED_RECENTLY); }
}
