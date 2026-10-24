// 바닐라 LongJumpMidJump: 공중에 있는 동안 마찰 무시·점프 자세를 유지하고, 착지하면 속도를 줄이고 소리를 내며 쿨다운을 건다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;

public class LongJumpMidJump extends Behavior<PathfinderMob> {
    public static final int TIME_OUT_DURATION = 100;
    private final UniformInt timeBetweenLongJumps;
    private final String landingSound;

    public LongJumpMidJump(UniformInt time, String landingSound) {
        super(Map.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LONG_JUMP_MID_JUMP, MemoryStatus.VALUE_PRESENT), TIME_OUT_DURATION);
        this.timeBetweenLongJumps = time;
        this.landingSound = landingSound;
    }

    @Override protected boolean canStillUse(ChunkLevel level, PathfinderMob mob, long gameTime) { return !mob.onGround(); }

    @Override
    protected void start(ChunkLevel level, PathfinderMob mob, long gameTime) {
        mob.setDiscardFriction(true);
        mob.setPose(Entity.POSE_LONG_JUMPING);
    }

    @Override
    protected void stop(ChunkLevel level, PathfinderMob mob, long gameTime) {
        if (mob.onGround()) {
            mob.setDeltaMovement(mob.vx() * 0.1F, mob.vy(), mob.vz() * 0.1F);
            level.playSound(null, mob.x(), mob.y(), mob.z(), landingSound, "neutral", 2.0F, 1.0F);
        }
        mob.setDiscardFriction(false);
        mob.setPose(Entity.POSE_STANDING);
        mob.getBrain().eraseMemory(MemoryModuleType.LONG_JUMP_MID_JUMP);
        mob.getBrain().setMemory(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, timeBetweenLongJumps.sample(level.random()));
    }
}
