// 바닐라 JumpOnBed: 아기가 가까운 침대(NEAREST_BED)로 걸어가 3~6번 뛴다. 100틱 안에 못 오르거나 다 뛰면 끝.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import java.util.Optional;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;

public class JumpOnBed extends Behavior<Mob> {
    private static final int MAX_TIME_TO_REACH_BED = 100, MIN_JUMPS = 3, MAX_JUMPS = 6, COOLDOWN_BETWEEN_JUMPS = 5;
    private final float speedModifier;
    private Pos targetBed;
    private int remainingTimeToReachBed, remainingJumps, remainingCooldownUntilNextJump;

    public JumpOnBed(float speed) {
        super(Map.of(MemoryModuleType.NEAREST_BED, MemoryStatus.VALUE_PRESENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
        this.speedModifier = speed;
    }

    @Override
    protected boolean checkExtraStartConditions(ChunkLevel level, Mob mob) { return mob.isBaby() && nearBed(level, mob); }

    @Override
    protected void start(ChunkLevel level, Mob mob, long time) {
        getNearestBed(mob).ifPresent(p -> {
            targetBed = p;
            remainingTimeToReachBed = MAX_TIME_TO_REACH_BED;
            remainingJumps = MIN_JUMPS + level.random().nextInt(MAX_JUMPS - MIN_JUMPS + 1);
            remainingCooldownUntilNextJump = 0;
            mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(p, speedModifier, 0));
        });
    }

    @Override
    protected void stop(ChunkLevel level, Mob mob, long time) {
        targetBed = null;
        remainingTimeToReachBed = 0;
        remainingJumps = 0;
        remainingCooldownUntilNextJump = 0;
    }

    @Override
    protected boolean canStillUse(ChunkLevel level, Mob mob, long time) {
        return mob.isBaby() && targetBed != null && isBed(level, targetBed) && !tiredOfWalking(level, mob) && !tiredOfJumping(level, mob);
    }

    @Override protected boolean timedOut(long time) { return false; }

    @Override
    protected void tick(ChunkLevel level, Mob mob, long time) {
        if (!onOrOverBed(level, mob)) {
            remainingTimeToReachBed--;
        } else if (remainingCooldownUntilNextJump > 0) {
            remainingCooldownUntilNextJump--;
        } else if (onBedSurface(level, mob)) {
            mob.getJumpControl().jump();
            remainingJumps--;
            remainingCooldownUntilNextJump = COOLDOWN_BETWEEN_JUMPS;
        }
    }

    private boolean nearBed(ChunkLevel level, Mob mob) { return onOrOverBed(level, mob) || getNearestBed(mob).isPresent(); }

    private boolean onOrOverBed(ChunkLevel level, Mob mob) {
        Pos p = mob.blockPosition();
        return isBed(level, p) || isBed(level, p.below());
    }

    private boolean onBedSurface(ChunkLevel level, Mob mob) { return isBed(level, mob.blockPosition()); }

    private static boolean isBed(ChunkLevel level, Pos p) { return B.isTag(level.getBlockState(p), "minecraft:beds"); }

    private static Optional<Pos> getNearestBed(Mob mob) { return mob.getBrain().getMemory(MemoryModuleType.NEAREST_BED); }

    private boolean tiredOfWalking(ChunkLevel level, Mob mob) { return !onOrOverBed(level, mob) && remainingTimeToReachBed <= 0; }

    private boolean tiredOfJumping(ChunkLevel level, Mob mob) { return onOrOverBed(level, mob) && remainingJumps <= 0; }
}
