// 바닐라 ShootTongue: 개구리가 대상에게 다가가(1.75칸 이내) 혀를 쏴 끌어당긴 뒤 먹어 치운다. 닿을 수 없는 대상은 기억해 두었다가 100틱 동안 피한다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Frog;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.entity.ai.navigation.Path;

public class ShootTongue extends Behavior<Frog> {
    public static final int TIME_OUT_DURATION = 100;
    public static final int CATCH_ANIMATION_DURATION = 6;
    public static final int TONGUE_ANIMATION_DURATION = 10;
    private static final float EATING_DISTANCE = 1.75F;
    private static final float EATING_MOVEMENT_FACTOR = 0.75F;
    public static final int UNREACHABLE_TONGUE_TARGETS_COOLDOWN_DURATION = 100;
    public static final int MAX_UNREACHABLE_TONGUE_TARGETS_IN_MEMORY = 5;
    private int eatAnimationTimer;
    private int calculatePathCounter;
    private final String tongueSound;
    private final String eatSound;
    private double[] itemSpawnPos;
    private State state = State.DONE;

    private enum State { MOVE_TO_TARGET, CATCH_ANIMATION, EAT_ANIMATION, DONE }

    public ShootTongue(String tongueSound, String eatSound) {
        super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT), TIME_OUT_DURATION);
        this.tongueSound = tongueSound;
        this.eatSound = eatSound;
    }

    @Override
    protected boolean checkExtraStartConditions(ChunkLevel level, Frog frog) {
        Targetable target = frog.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
        boolean ok = canPathfindToTarget(frog, target);
        if (!ok) {
            frog.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            addUnreachableTargetToMemory(frog, target);
        }
        return ok && frog.getPose() != Entity.POSE_CROAKING && Frog.canEat(target);
    }

    @Override
    protected boolean canStillUse(ChunkLevel level, Frog frog, long gameTime) {
        return frog.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && state != State.DONE && !frog.getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING);
    }

    @Override
    protected void start(ChunkLevel level, Frog frog, long gameTime) {
        Targetable target = frog.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
        BehaviorUtils.lookAtEntity(frog, target);
        frog.setTongueTarget(target);
        frog.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new double[] {target.x(), target.y(), target.z()}, 2.0F, 0));
        calculatePathCounter = 10;
        state = State.MOVE_TO_TARGET;
    }

    @Override
    protected void stop(ChunkLevel level, Frog frog, long gameTime) {
        frog.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        frog.eraseTongueTarget();
        frog.setPose(Entity.POSE_STANDING);
    }

    private void eatEntity(ChunkLevel level, Frog frog) {
        level.playSound(null, frog.x(), frog.y(), frog.z(), eatSound, "neutral", 2.0F, 1.0F);
        Optional<Entity> target = frog.getTongueTarget();
        if (target.isPresent()) {
            Entity e = target.get();
            if (e.isAlive()) {
                frog.doHurtTarget((Targetable) e);
                if (!e.isAlive()) e.remove(Entity.RemovalReason.KILLED);
            }
        }
    }

    @Override
    protected void tick(ChunkLevel level, Frog frog, long gameTime) {
        Targetable target = frog.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
        frog.setTongueTarget(target);
        switch (state) {
            case MOVE_TO_TARGET:
                double dx = target.x() - frog.x(), dy = target.y() - frog.y(), dz = target.z() - frog.z();
                if (Math.sqrt(dx * dx + dy * dy + dz * dz) < EATING_DISTANCE) {
                    level.playSound(null, frog.x(), frog.y(), frog.z(), tongueSound, "neutral", 2.0F, 1.0F);
                    frog.setPose(Entity.POSE_USING_TONGUE);
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    // target.setDeltaMovement(target.position().vectorTo(frog.position()).normalize().scale(0.75))
                    double fx = frog.x() - target.x(), fy = frog.y() - target.y(), fz = frog.z() - target.z();
                    double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
                    double ux = fl < 1.0E-5F ? 0.0 : fx / fl, uy = fl < 1.0E-5F ? 0.0 : fy / fl, uz = fl < 1.0E-5F ? 0.0 : fz / fl;
                    if (target instanceof Entity e) e.setDeltaMovement(ux * EATING_MOVEMENT_FACTOR, uy * EATING_MOVEMENT_FACTOR, uz * EATING_MOVEMENT_FACTOR);
                    else target.addDeltaMovement(ux * EATING_MOVEMENT_FACTOR - 0, uy * EATING_MOVEMENT_FACTOR, uz * EATING_MOVEMENT_FACTOR);
                    itemSpawnPos = new double[] {target.x(), target.y(), target.z()};
                    eatAnimationTimer = 0;
                    state = State.CATCH_ANIMATION;
                } else if (calculatePathCounter <= 0) {
                    frog.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new double[] {target.x(), target.y(), target.z()}, 2.0F, 0));
                    calculatePathCounter = 10;
                } else {
                    calculatePathCounter--;
                }
                break;
            case CATCH_ANIMATION:
                if (eatAnimationTimer++ >= CATCH_ANIMATION_DURATION) {
                    state = State.EAT_ANIMATION;
                    eatEntity(level, frog);
                }
                break;
            case EAT_ANIMATION:
                if (eatAnimationTimer >= TONGUE_ANIMATION_DURATION) state = State.DONE;
                else eatAnimationTimer++;
                break;
            case DONE:
                break;
        }
    }

    private boolean canPathfindToTarget(Frog frog, Targetable target) {
        Path path = frog.getNavigation().createPath(target, 0);
        return path != null && path.getDistToTarget() < EATING_DISTANCE;
    }

    private void addUnreachableTargetToMemory(Frog frog, Targetable target) {
        if (!(target instanceof Entity e)) return;
        List<UUID> list = frog.getBrain().getMemory(MemoryModuleType.UNREACHABLE_TONGUE_TARGETS).orElseGet(ArrayList::new);
        boolean flag = !list.contains(e.uuid());
        if (list.size() == MAX_UNREACHABLE_TONGUE_TARGETS_IN_MEMORY && flag) list.remove(0);
        if (flag) list.add(e.uuid());
        frog.getBrain().setMemoryWithExpiry(MemoryModuleType.UNREACHABLE_TONGUE_TARGETS, list, UNREACHABLE_TONGUE_TARGETS_COOLDOWN_DURATION);
    }
}
