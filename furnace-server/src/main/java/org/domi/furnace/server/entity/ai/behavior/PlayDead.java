// 바닐라 PlayDead(아홀로틀): 물속에서 맞으면 죽은 척한다 — 걷기·공격 목표를 지우고 재생 효과를 10초간 받는다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;

public class PlayDead extends Behavior<Mob> {
    public PlayDead() { super(Map.of(MemoryModuleType.PLAY_DEAD_TICKS, MemoryStatus.VALUE_PRESENT, MemoryModuleType.HURT_BY_ENTITY, MemoryStatus.VALUE_PRESENT), 200); }

    @Override protected boolean checkExtraStartConditions(ChunkLevel level, Mob mob) { return mob.isInWater(); }

    @Override protected boolean canStillUse(ChunkLevel level, Mob mob, long gameTime) { return mob.isInWater() && mob.getBrain().hasMemoryValue(MemoryModuleType.PLAY_DEAD_TICKS); }

    @Override
    protected void start(ChunkLevel level, Mob mob, long gameTime) {
        var brain = mob.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        mob.addEffect(new MobEffectInstance(MobEffect.REGENERATION, 200, 0));
    }
}
