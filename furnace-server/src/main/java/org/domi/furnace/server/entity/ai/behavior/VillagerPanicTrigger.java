// 바닐라 VillagerPanicTrigger: 맞았거나 가까운 적이 있으면 공황 활동으로 바꾼다(처음이면 길·걷기·시선·번식·상호작용 기억을 지운다).
// 공황 중 100틱마다(gameTime % 100 == 0) 철 골렘 소환을 시도한다(주변 주민 3명 이상).
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public class VillagerPanicTrigger extends Behavior<Villager> {
    public VillagerPanicTrigger() { super(Map.of()); }

    @Override
    protected boolean canStillUse(ChunkLevel level, Villager v, long time) { return isHurt(v) || hasHostile(v); }

    @Override
    protected void start(ChunkLevel level, Villager v, long time) {
        if (isHurt(v) || hasHostile(v)) {
            var brain = v.getBrain();
            if (!brain.isActive(Activity.PANIC)) {
                brain.eraseMemory(MemoryModuleType.PATH);
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
                brain.eraseMemory(MemoryModuleType.BREED_TARGET);
                brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
            }
            brain.setActiveActivityIfPossible(Activity.PANIC);
        }
    }

    @Override
    protected void tick(ChunkLevel level, Villager v, long time) {
        if (time % 100L == 0L) v.spawnGolemIfNeeded(level, time, 3);
    }

    public static boolean hasHostile(Mob m) { return m.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE); }

    public static boolean isHurt(Mob m) { return m.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY); }
}
