// 바닐라 MoveToTargetSink: WALK_TARGET 으로 길을 찾아 걸어간다. 못 닿으면 CANT_REACH 시각을 기록하고 근처 무작위 점으로 대신 간다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import java.util.Optional;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.entity.ai.navigation.Path;
import org.domi.furnace.server.entity.ai.util.DefaultRandomPos;

public class MoveToTargetSink extends Behavior<Mob> {
    private static final int MAX_COOLDOWN_BEFORE_RETRYING = 40;
    private int remainingCooldown;
    private Path path;
    private Pos lastTargetPos;
    private float speedModifier;

    public MoveToTargetSink() { this(150, 250); }

    public MoveToTargetSink(int minDuration, int maxDuration) {
        super(Map.of(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED, MemoryModuleType.PATH, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_PRESENT), minDuration, maxDuration);
    }

    @Override
    protected boolean checkExtraStartConditions(ChunkLevel level, Mob mob) {
        if (remainingCooldown > 0) {
            remainingCooldown--;
            return false;
        }
        var brain = mob.getBrain();
        WalkTarget target = brain.getMemory(MemoryModuleType.WALK_TARGET).get();
        boolean reached = reachedTarget(mob, target);
        if (!reached && tryComputePath(mob, target, level.gameTime())) {
            lastTargetPos = target.getTarget().currentBlockPosition();
            return true;
        }
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        if (reached) brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        return false;
    }

    @Override
    protected boolean canStillUse(ChunkLevel level, Mob mob, long gameTime) {
        if (path != null && lastTargetPos != null) {
            Optional<WalkTarget> target = mob.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
            boolean spectator = target.map(MoveToTargetSink::isWalkTargetSpectator).orElse(false);
            return !mob.getNavigation().isDone() && target.isPresent() && !reachedTarget(mob, target.get()) && !spectator;
        }
        return false;
    }

    @Override
    protected void stop(ChunkLevel level, Mob mob, long gameTime) {
        var brain = mob.getBrain();
        if (brain.hasMemoryValue(MemoryModuleType.WALK_TARGET) && !reachedTarget(mob, brain.getMemory(MemoryModuleType.WALK_TARGET).get()) && mob.getNavigation().isStuck()) {
            remainingCooldown = level.random().nextInt(MAX_COOLDOWN_BEFORE_RETRYING);
        }
        mob.getNavigation().stop();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
        path = null;
    }

    @Override
    protected void start(ChunkLevel level, Mob mob, long gameTime) {
        mob.getBrain().setMemory(MemoryModuleType.PATH, path);
        mob.getNavigation().moveTo(path, speedModifier);
    }

    @Override
    protected void tick(ChunkLevel level, Mob mob, long gameTime) {
        Path current = mob.getNavigation().getPath();
        var brain = mob.getBrain();
        if (path != current) {
            path = current;
            brain.setMemory(MemoryModuleType.PATH, current);
        }
        if (current != null && lastTargetPos != null) {
            WalkTarget target = brain.getMemory(MemoryModuleType.WALK_TARGET).get();
            Pos tp = target.getTarget().currentBlockPosition();
            int dx = tp.x() - lastTargetPos.x(), dy = tp.y() - lastTargetPos.y(), dz = tp.z() - lastTargetPos.z();
            if ((double) (dx * dx + dy * dy + dz * dz) > 4.0 && tryComputePath(mob, target, level.gameTime())) {
                lastTargetPos = tp;
                start(level, mob, gameTime);
            }
        }
    }

    private boolean tryComputePath(Mob mob, WalkTarget target, long gameTime) {
        Pos pos = target.getTarget().currentBlockPosition();
        path = mob.getNavigation().createPath(pos, 0);
        speedModifier = target.getSpeedModifier();
        var brain = mob.getBrain();
        if (reachedTarget(mob, target)) {
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        } else {
            boolean canReach = path != null && path.canReach();
            if (canReach) brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            else if (!brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) brain.setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, gameTime);
            if (path != null) return true;
            double[] v = DefaultRandomPos.getPosTowards((PathfinderMob) mob, 10, 7, new double[] {pos.x() + 0.5, pos.y(), pos.z() + 0.5}, (float) (Math.PI / 2));
            if (v != null) {
                path = mob.getNavigation().createPath(v[0], v[1], v[2], 0);
                return path != null;
            }
        }
        return false;
    }

    private boolean reachedTarget(Mob mob, WalkTarget target) {
        Pos t = target.getTarget().currentBlockPosition();
        Pos m = mob.blockPosition();
        return Math.abs(t.x() - m.x()) + Math.abs(t.y() - m.y()) + Math.abs(t.z() - m.z()) <= target.getCloseEnoughDist();
    }

    private static boolean isWalkTargetSpectator(WalkTarget target) {
        return target.getTarget() instanceof EntityTracker t && t.getEntity() instanceof org.domi.furnace.server.entity.Entity e && e.isSpectator()
                || target.getTarget() instanceof EntityTracker t2 && t2.getEntity() instanceof org.domi.furnace.server.entity.Player p
                        && p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR;
    }
}
