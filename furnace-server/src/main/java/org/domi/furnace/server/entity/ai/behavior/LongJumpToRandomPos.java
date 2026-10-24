// 바닐라 LongJumpToRandomPos: 쿨다운이 끝나면 주변 후보 칸 중 무작위(거리 제곱 가중)로 골라 40틱 준비 뒤 포물선으로 뛴다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.brain.BlockPosTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;

public class LongJumpToRandomPos<E extends PathfinderMob> extends Behavior<E> {
    protected static final int FIND_JUMP_TRIES = 20;
    private static final int PREPARE_JUMP_DURATION = 40;
    protected static final int MIN_PATHFIND_DISTANCE_TO_VALID_JUMP = 8;
    private static final int TIME_OUT_DURATION = 200;
    private static final List<Integer> ALLOWED_ANGLES = List.of(65, 70, 75, 80);
    private final UniformInt timeBetweenLongJumps;
    protected final int maxLongJumpHeight;
    protected final int maxLongJumpWidth;
    protected final float maxJumpVelocity;
    protected List<PossibleJump> jumpCandidates = new ArrayList<>();
    protected Optional<double[]> initialPosition = Optional.empty();
    protected double[] chosenJump;
    protected int findJumpTries;
    protected long prepareJumpStart;
    private final Function<E, String> getJumpSound;
    private final BiPredicate<E, Pos> acceptableLandingSpot;

    public static class PossibleJump {
        final Pos pos;
        final int weight;

        public PossibleJump(Pos pos, int weight) {
            this.pos = pos;
            this.weight = weight;
        }

        public Pos pos() { return pos; }
    }

    public LongJumpToRandomPos(UniformInt time, int height, int width, float vel, Function<E, String> sound) {
        this(time, height, width, vel, sound, LongJumpToRandomPos::defaultAcceptableLandingSpot);
    }

    public static <E extends PathfinderMob> boolean defaultAcceptableLandingSpot(E mob, Pos pos) {
        ChunkLevel level = mob.level();
        Pos below = pos.below();
        return B.isCollisionShapeFullBlock(level.getBlockState(below))
                && mob.getPathfindingMalus(org.domi.furnace.server.entity.ai.navigation.WalkNodeEvaluator.getPathTypeStatic(new org.domi.furnace.server.entity.ai.navigation.PathfindingContext(level, mob), pos)) == 0.0F;
    }

    public LongJumpToRandomPos(UniformInt time, int height, int width, float vel, Function<E, String> sound, BiPredicate<E, Pos> acceptable) {
        super(Map.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LONG_JUMP_MID_JUMP, MemoryStatus.VALUE_ABSENT), TIME_OUT_DURATION);
        this.timeBetweenLongJumps = time;
        this.maxLongJumpHeight = height;
        this.maxLongJumpWidth = width;
        this.maxJumpVelocity = vel;
        this.getJumpSound = sound;
        this.acceptableLandingSpot = acceptable;
    }

    @Override
    protected boolean checkExtraStartConditions(ChunkLevel level, PathfinderMob mob) {
        boolean ok = mob.onGround() && !mob.isInWater() && !mob.isInLava() && !B.is(level.getBlockState(mob.blockPosition()), "minecraft:honey_block");
        if (!ok) mob.getBrain().setMemory(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, timeBetweenLongJumps.sample(level.random()) / 2);
        return ok;
    }

    @Override
    protected boolean canStillUse(ChunkLevel level, E mob, long gameTime) {
        boolean ok = initialPosition.isPresent() && java.util.Arrays.equals(initialPosition.get(), new double[] {mob.x(), mob.y(), mob.z()}) && findJumpTries > 0
                && !mob.isInWaterOrBubble() && (chosenJump != null || !jumpCandidates.isEmpty());
        if (!ok && mob.getBrain().getMemory(MemoryModuleType.LONG_JUMP_MID_JUMP).isEmpty()) {
            mob.getBrain().setMemory(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, timeBetweenLongJumps.sample(level.random()) / 2);
            mob.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        }
        return ok;
    }

    @Override
    protected void start(ChunkLevel level, E mob, long gameTime) {
        chosenJump = null;
        findJumpTries = FIND_JUMP_TRIES;
        initialPosition = Optional.of(new double[] {mob.x(), mob.y(), mob.z()});
        Pos pos = mob.blockPosition();
        jumpCandidates = new ArrayList<>();
        for (int z = pos.z() - maxLongJumpWidth; z <= pos.z() + maxLongJumpWidth; z++) {
            for (int y = pos.y() - maxLongJumpHeight; y <= pos.y() + maxLongJumpHeight; y++) {
                for (int x = pos.x() - maxLongJumpWidth; x <= pos.x() + maxLongJumpWidth; x++) {
                    if (x == pos.x() && y == pos.y() && z == pos.z()) continue;
                    int dx = pos.x() - x, dy = pos.y() - y, dz = pos.z() - z;
                    jumpCandidates.add(new PossibleJump(new Pos(x, y, z), (int) Math.ceil((double) (dx * dx + dy * dy + dz * dz))));
                }
            }
        }
    }

    @Override
    protected void tick(ChunkLevel level, E mob, long gameTime) {
        if (chosenJump != null) {
            if (gameTime - prepareJumpStart >= PREPARE_JUMP_DURATION) {
                mob.setYRot(mob.yBodyRot());
                mob.setDiscardFriction(true);
                double len = Math.sqrt(chosenJump[0] * chosenJump[0] + chosenJump[1] * chosenJump[1] + chosenJump[2] * chosenJump[2]);
                double d = len + mob.getJumpBoostPower();
                mob.setDeltaMovement(chosenJump[0] * (d / len), chosenJump[1] * (d / len), chosenJump[2] * (d / len));
                mob.getBrain().setMemory(MemoryModuleType.LONG_JUMP_MID_JUMP, true);
                level.playSound(null, mob.x(), mob.y(), mob.z(), getJumpSound.apply(mob), "neutral", 1.0F, 1.0F);
            }
        } else {
            findJumpTries--;
            pickCandidate(level, mob, gameTime);
        }
    }

    protected void pickCandidate(ChunkLevel level, E mob, long gameTime) {
        while (!jumpCandidates.isEmpty()) {
            Optional<PossibleJump> c = getJumpCandidate(level);
            if (c.isEmpty()) continue;
            Pos pos = c.get().pos();
            if (isAcceptableLandingPosition(level, mob, pos)) {
                double[] center = {pos.x() + 0.5, pos.y(), pos.z() + 0.5};
                double[] jump = calculateOptimalJumpVector(mob, center);
                if (jump != null) {
                    mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
                    var path = mob.getNavigation().createPath(pos, 8, 0);
                    if (path == null || !path.canReach()) {
                        chosenJump = jump;
                        prepareJumpStart = gameTime;
                        return;
                    }
                }
            }
        }
    }

    protected Optional<PossibleJump> getJumpCandidate(ChunkLevel level) {
        long total = 0;
        for (PossibleJump j : jumpCandidates) total += j.weight;
        Optional<PossibleJump> r = Optional.empty();
        if (total > 0) {
            int roll = level.random().nextInt((int) total);
            for (PossibleJump j : jumpCandidates) {
                roll -= j.weight;
                if (roll < 0) {
                    r = Optional.of(j);
                    break;
                }
            }
        }
        r.ifPresent(jumpCandidates::remove);
        return r;
    }

    private boolean isAcceptableLandingPosition(ChunkLevel level, E mob, Pos pos) {
        Pos here = mob.blockPosition();
        if (here.x() == pos.x() && here.z() == pos.z()) return false;
        return acceptableLandingSpot.test(mob, pos);
    }

    protected double[] calculateOptimalJumpVector(PathfinderMob mob, double[] target) {
        List<Integer> angles = new ArrayList<>(ALLOWED_ANGLES);
        Collections.shuffle(angles);
        float f = (float) (mob.attributeValue(Attribute.JUMP_STRENGTH) * maxJumpVelocity);
        for (int angle : angles) {
            Optional<double[]> v = LongJumpUtil.calculateJumpVectorForAngle(mob, target, f, angle, true);
            if (v.isPresent()) return v.get();
        }
        return null;
    }
}
