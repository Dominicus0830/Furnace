// 바닐라 SleepInBed: 집(HOME) 침대 2칸 안이고 비어 있으면, 지나온 문을 닫고 눕는다. 쉬는 활동이 끝나거나 침대에서 벗어나면 일어나고 40틱 쉰다.
// 깬 지 0~100틱이면 다시 눕지 않는다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import java.util.Optional;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;

public class SleepInBed extends Behavior<Mob> {
    public static final int COOLDOWN_AFTER_BEING_WOKEN = 100;
    private long nextOkStartTime;

    public SleepInBed() { super(Map.of(MemoryModuleType.HOME, MemoryStatus.VALUE_PRESENT, MemoryModuleType.LAST_WOKEN, MemoryStatus.REGISTERED)); }

    @Override
    protected boolean checkExtraStartConditions(ChunkLevel level, Mob mob) {
        if (mob.isPassenger()) return false;
        var brain = mob.getBrain();
        GlobalPos g = brain.getMemory(MemoryModuleType.HOME).get();
        if (!level.dimension().equals(g.dimension())) return false;
        Optional<Long> woken = brain.getMemory(MemoryModuleType.LAST_WOKEN);
        if (woken.isPresent()) {
            long d = level.gameTime() - woken.get();
            if (d > 0L && d < COOLDOWN_AFTER_BEING_WOKEN) return false;
        }
        int st = level.getBlockState(g.pos());
        return ValidateNearbyPoi.closerToCenterThan(g.pos(), mob, 2.0) && B.isTag(st, "minecraft:beds") && !B.prop("occupied").is(st);
    }

    @Override
    protected boolean canStillUse(ChunkLevel level, Mob mob, long time) {
        var home = mob.getBrain().getMemory(MemoryModuleType.HOME);
        if (home.isEmpty()) return false;
        Pos p = home.get().pos();
        return mob.getBrain().isActive(Activity.REST) && mob.y() > p.y() + 0.4 && ValidateNearbyPoi.closerToCenterThan(p, mob, 1.14);
    }

    @Override
    protected void start(ChunkLevel level, Mob mob, long time) {
        if (time > nextOkStartTime) {
            var brain = mob.getBrain();
            if (brain.hasMemoryValue(MemoryModuleType.DOORS_TO_CLOSE)) {
                var doors = brain.getMemory(MemoryModuleType.DOORS_TO_CLOSE).get();
                var mobs = brain.hasMemoryValue(MemoryModuleType.NEAREST_LIVING_ENTITIES) ? brain.getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES)
                        : Optional.<java.util.List<org.domi.furnace.server.entity.Targetable>>empty();
                InteractWithDoor.closeDoorsThatIHaveOpenedOrPassedThrough(level, mob, null, null, doors, mobs);
            }
            mob.startSleeping(brain.getMemory(MemoryModuleType.HOME).get().pos());
        }
    }

    @Override protected boolean timedOut(long time) { return false; }

    @Override
    protected void stop(ChunkLevel level, Mob mob, long time) {
        if (mob.isSleeping()) {
            mob.stopSleeping();
            nextOkStartTime = time + 40L;
        }
    }
}
