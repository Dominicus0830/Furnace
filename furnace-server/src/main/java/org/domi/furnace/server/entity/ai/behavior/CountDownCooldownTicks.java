// 바닐라 CountDownCooldownTicks: 정수 기억(재사용 대기 틱)을 틱마다 1씩 줄이다가 0 이하면 지운다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import java.util.Optional;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;

public class CountDownCooldownTicks extends Behavior<Mob> {
    private final MemoryModuleType<Integer> cooldownTicks;

    public CountDownCooldownTicks(MemoryModuleType<Integer> type) {
        super(Map.of(type, MemoryStatus.VALUE_PRESENT));
        this.cooldownTicks = type;
    }

    private Optional<Integer> getCooldownTickMemory(Mob mob) { return mob.getBrain().getMemory(cooldownTicks); }

    @Override protected boolean timedOut(long gameTime) { return false; }

    @Override
    protected boolean canStillUse(ChunkLevel level, Mob mob, long gameTime) {
        Optional<Integer> v = getCooldownTickMemory(mob);
        return v.isPresent() && v.get() > 0;
    }

    @Override
    protected void tick(ChunkLevel level, Mob mob, long gameTime) {
        Optional<Integer> v = getCooldownTickMemory(mob);
        mob.getBrain().setMemory(cooldownTicks, v.get() - 1);
    }

    @Override protected void stop(ChunkLevel level, Mob mob, long gameTime) { mob.getBrain().eraseMemory(cooldownTicks); }
}
