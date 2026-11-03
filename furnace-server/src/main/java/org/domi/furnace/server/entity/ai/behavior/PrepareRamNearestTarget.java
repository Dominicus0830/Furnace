// 바닐라 PrepareRamNearestTarget: 염소가 보이는 가장 가까운 대상 맞은편으로 물러서 준비한 뒤 RAM_TARGET 을 정한다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.BlockPosTracker;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.entity.ai.navigation.PathfindingContext;
import org.domi.furnace.server.entity.ai.navigation.WalkNodeEvaluator;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;

public class PrepareRamNearestTarget<E extends PathfinderMob> extends Behavior<E> {
    public static final int TIME_OUT_DURATION = 160;
    private static final int[] HORIZONTAL = {Dir.NORTH, Dir.EAST, Dir.SOUTH, Dir.WEST};
    private final ToIntFunction<E> getCooldownOnFail;
    private final int minRamDistance;
    private final int maxRamDistance;
    private final float walkSpeed;
    private final TargetingConditions ramTargeting;
    private final int ramPrepareTime;
    private final Function<E, String> getPrepareRamSound;
    private Optional<Long> reachedRamPositionTimestamp = Optional.empty();
    private Optional<RamCandidate> ramCandidate = Optional.empty();

    public PrepareRamNearestTarget(ToIntFunction<E> cooldownOnFail, int minRamDistance, int maxRamDistance, float walkSpeed, TargetingConditions ramTargeting, int ramPrepareTime,
            Function<E, String> prepareRamSound) {
        super(Map.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.RAM_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT, MemoryModuleType.RAM_TARGET, MemoryStatus.VALUE_ABSENT), TIME_OUT_DURATION);
        this.getCooldownOnFail = cooldownOnFail;
        this.minRamDistance = minRamDistance;
        this.maxRamDistance = maxRamDistance;
        this.walkSpeed = walkSpeed;
        this.ramTargeting = ramTargeting;
        this.ramPrepareTime = ramPrepareTime;
        this.getPrepareRamSound = prepareRamSound;
    }

    @Override
    protected void start(ChunkLevel level, E mob, long gameTime) {
        mob.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).flatMap(v -> v.findClosest(t -> ramTargeting.test(mob, t))).ifPresent(t -> chooseRamPosition(mob, t));
    }

    @Override
    protected void stop(ChunkLevel level, E mob, long gameTime) {
        if (!mob.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET)) {
            mob.broadcastEntityEvent((byte) 59);
            mob.getBrain().setMemory(MemoryModuleType.RAM_COOLDOWN_TICKS, getCooldownOnFail.applyAsInt(mob));
        }
    }

    @Override protected boolean canStillUse(ChunkLevel level, E mob, long gameTime) { return ramCandidate.isPresent() && ramCandidate.get().target().isAlive(); }

    @Override
    protected void tick(ChunkLevel level, E mob, long gameTime) {
        if (ramCandidate.isEmpty()) return;
        var brain = mob.getBrain();
        RamCandidate c = ramCandidate.get();
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(c.startPosition()), walkSpeed, 0));
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(c.target(), true));
        boolean moved = !c.target().blockPosition().equals(c.targetPosition());
        if (moved) {
            mob.broadcastEntityEvent((byte) 59);
            mob.getNavigation().stop();
            chooseRamPosition(mob, c.target());
        } else {
            Pos pos = mob.blockPosition();
            if (pos.equals(c.startPosition())) {
                mob.broadcastEntityEvent((byte) 58);
                if (reachedRamPositionTimestamp.isEmpty()) reachedRamPositionTimestamp = Optional.of(gameTime);
                if (gameTime - reachedRamPositionTimestamp.get() >= ramPrepareTime) {
                    brain.setMemory(MemoryModuleType.RAM_TARGET, getEdgeOfBlock(pos, c.targetPosition()));
                    level.playSound(null, mob, getPrepareRamSound.apply(mob), "neutral", 1.0F, mob.getVoicePitch());
                    ramCandidate = Optional.empty();
                }
            }
        }
    }

    private static double[] getEdgeOfBlock(Pos start, Pos target) {
        int sx = Integer.compare(target.x() - start.x(), 0), sz = Integer.compare(target.z() - start.z(), 0);
        return new double[] {target.x() + 0.5 + 0.5 * sx, target.y() + 0.5, target.z() + 0.5 + 0.5 * sz};
    }

    private Optional<Pos> calculateRammingStartPosition(PathfinderMob mob, Targetable target) {
        Pos targetPos = target.blockPosition();
        if (!isWalkableBlock(mob, targetPos)) return Optional.empty();
        List<Pos> candidates = new ArrayList<>();
        for (int dir : HORIZONTAL) {
            Pos p = targetPos;
            for (int i = 0; i < maxRamDistance; i++) {
                Pos next = p.relative(dir);
                if (!isWalkableBlock(mob, next)) break;
                p = next;
            }
            if (Math.abs(p.x() - targetPos.x()) + Math.abs(p.y() - targetPos.y()) + Math.abs(p.z() - targetPos.z()) >= minRamDistance) candidates.add(p);
        }
        var nav = mob.getNavigation();
        Pos here = mob.blockPosition();
        return candidates.stream().sorted(Comparator.comparingDouble(p -> {
            double dx = p.x() - here.x(), dy = p.y() - here.y(), dz = p.z() - here.z();
            return dx * dx + dy * dy + dz * dz;
        })).filter(p -> {
            var path = nav.createPath(p, 0);
            return path != null && path.canReach();
        }).findFirst();
    }

    private boolean isWalkableBlock(PathfinderMob mob, Pos pos) {
        return mob.getNavigation().isStableDestination(pos)
                && mob.getPathfindingMalus(WalkNodeEvaluator.getPathTypeStatic(new PathfindingContext(mob.level(), mob), pos)) == 0.0F;
    }

    private void chooseRamPosition(PathfinderMob mob, Targetable target) {
        reachedRamPositionTimestamp = Optional.empty();
        ramCandidate = calculateRammingStartPosition(mob, target).map(p -> new RamCandidate(p, target.blockPosition(), target));
    }

    public record RamCandidate(Pos startPosition, Pos targetPosition, Targetable target) {}
}
