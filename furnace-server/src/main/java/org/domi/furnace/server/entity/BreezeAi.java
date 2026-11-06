// 바닐라 BreezeAi + BreezeUtil + Breeze 행동들(Shoot·LongJump·Slide·물/비행 중 사격 방지): 브리즈 브레인 구성 — 활동 core/idle/fight.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Clip;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.entity.ai.behavior.*;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.brain.Sensor;
import org.domi.furnace.server.entity.ai.brain.Unit;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;

public final class BreezeAi {
    public static final float SPEED_MULTIPLIER_WHEN_SLIDING = 0.6F;
    private static final MemoryStatus PRESENT = MemoryStatus.VALUE_PRESENT, ABSENT = MemoryStatus.VALUE_ABSENT, REGISTERED = MemoryStatus.REGISTERED;

    private BreezeAi() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Brain<Breeze> makeBrain(Breeze breeze, Brain<Breeze> brain) {
        brain.addActivity(Activity.CORE, 0, List.of(new Swim(0.8F), new LookAtTargetSink(45, 90)));
        brain.addActivity(Activity.IDLE, List.of(
                Map.entry(0, StartAttacking.create((Breeze b) -> b.getBrain().getMemory(MemoryModuleType.NEAREST_ATTACKABLE))),
                Map.entry(1, StartAttacking.create((Breeze b) -> b.getHurtByTarget())),
                Map.entry(2, new SlideBehavior(20, 40)),
                Map.entry(3, new RunOne<Breeze>(List.<Map.Entry<? extends BehaviorControl<? super Breeze>, Integer>>of(Map.entry(new DoNothing(20, 100), 1), Map.entry(RandomStroll.stroll(0.6F), 2))))));
        brain.addActivityWithConditions(Activity.FIGHT, List.of(
                Map.entry(0, StopAttackingIfTargetInvalid.<Breeze>create((Targetable t) -> !Sensor.isEntityAttackable(breeze, t))),
                Map.entry(1, new ShootBehavior()),
                Map.entry(2, new JumpBehavior()),
                Map.entry(3, new StuckBehavior()),
                Map.entry(4, new SlideAwayBehavior())),
                Set.of(Map.entry(MemoryModuleType.ATTACK_TARGET, PRESENT), Map.entry(MemoryModuleType.WALK_TARGET, ABSENT)));
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.FIGHT);
        brain.useDefaultActivity();
        return brain;
    }

    static void updateActivity(Breeze breeze) { breeze.getBrain().setActiveActivityToFirstValid(List.of(Activity.FIGHT, Activity.IDLE)); }

    // ------------------------------------------------------------------ BreezeUtil

    /** BreezeUtil.randomPointBehindTarget: 대상의 뒤쪽(고개 방향 반대 ±45°)으로 4~8칸. */
    static double[] randomPointBehindTarget(Targetable target, org.domi.furnace.server.block.RandomSource random) {
        float yaw = headYaw(target) + 180.0F + (float) random.nextGaussian() * 90.0F / 2.0F;
        float dist = 4.0F + random.nextFloat() * (8.0F - 4.0F);
        double[] v = Dir.viewVector(0.0F, yaw);
        return new double[] {target.x() + v[0] * dist, target.y() + v[1] * dist, target.z() + v[2] * dist};
    }

    static float headYaw(Targetable t) {
        return t instanceof LivingEntity le ? le.yHeadRot() : t instanceof Player p ? p.yaw() : 0.0F;
    }

    static boolean hasLineOfSight(Breeze breeze, double[] to) {
        double dx = to[0] - breeze.x(), dy = to[1] - breeze.y(), dz = to[2] - breeze.z();
        if (dx * dx + dy * dy + dz * dz > 50.0) return false;
        return Clip.clipCollider(breeze.level(), breeze.x(), breeze.y(), breeze.z(), to[0], to[1], to[2]).miss();
    }

    // ------------------------------------------------------------------ 행동

    /** 가만히 미끄러지다(자세 SLIDING) 끝나면 사격을 준비한다(DoNothing 변형). */
    static final class SlideBehavior extends Behavior<Breeze> {
        SlideBehavior(int min, int max) { super(Map.of(), min, max); }

        @Override protected boolean canStillUse(ChunkLevel level, Breeze b, long gameTime) { return true; }

        @Override
        protected void start(ChunkLevel level, Breeze b, long gameTime) {
            b.playSound("entity.breeze.slide", 1.0F, 1.0F);
            b.setPose(Entity.POSE_SLIDING);
        }

        @Override
        protected void stop(ChunkLevel level, Breeze b, long gameTime) {
            b.setPose(Entity.POSE_STANDING);
            if (b.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) b.getBrain().setMemoryWithExpiry(MemoryModuleType.BREEZE_SHOOT, Unit.INSTANCE, 60L);
        }
    }

    /** BreezeShoot: 대상이 4~16칸 사이에서 정면에 있으면 바람 폭탄을 쏜다. */
    static final class ShootBehavior extends Behavior<Breeze> {
        private static final int ATTACK_RANGE_MIN_SQRT = 4, ATTACK_RANGE_MAX_SQRT = 256, CHARGE = 15, RECOVER = 4, COOLDOWN = 10;

        ShootBehavior() {
            super(Map.of(MemoryModuleType.ATTACK_TARGET, PRESENT, MemoryModuleType.BREEZE_SHOOT_COOLDOWN, ABSENT, MemoryModuleType.BREEZE_SHOOT_CHARGING, ABSENT,
                    MemoryModuleType.BREEZE_SHOOT_RECOVER, ABSENT, MemoryModuleType.BREEZE_SHOOT, PRESENT, MemoryModuleType.WALK_TARGET, ABSENT, MemoryModuleType.BREEZE_JUMP_TARGET, ABSENT),
                    CHARGE + 1 + RECOVER);
        }

        @Override
        protected boolean checkExtraStartConditions(ChunkLevel level, Breeze b) {
            if (b.getPose() != Entity.POSE_STANDING) return false;
            Targetable t = b.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            if (t == null) return false;
            double dx = t.x() - b.x(), dy = t.y() - b.y(), dz = t.z() - b.z();
            double d = dx * dx + dy * dy + dz * dz;
            boolean inRange = d > ATTACK_RANGE_MIN_SQRT && d < ATTACK_RANGE_MAX_SQRT;
            if (!inRange) b.getBrain().eraseMemory(MemoryModuleType.BREEZE_SHOOT);
            return inRange;
        }

        @Override
        protected boolean canStillUse(ChunkLevel level, Breeze b, long gameTime) {
            return b.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && b.getBrain().hasMemoryValue(MemoryModuleType.BREEZE_SHOOT);
        }

        @Override
        protected void start(ChunkLevel level, Breeze b, long gameTime) {
            b.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent(t -> b.setPose(Entity.POSE_SHOOTING));
            b.getBrain().setMemoryWithExpiry(MemoryModuleType.BREEZE_SHOOT_CHARGING, Unit.INSTANCE, CHARGE);
            level.playSound(null, b.x(), b.y(), b.z(), "minecraft:entity.breeze.inhale", "hostile", 1.0F, 1.0F);
        }

        @Override
        protected void stop(ChunkLevel level, Breeze b, long gameTime) {
            if (b.getPose() == Entity.POSE_SHOOTING) b.setPose(Entity.POSE_STANDING);
            b.getBrain().setMemoryWithExpiry(MemoryModuleType.BREEZE_SHOOT_COOLDOWN, Unit.INSTANCE, COOLDOWN);
            b.getBrain().eraseMemory(MemoryModuleType.BREEZE_SHOOT);
        }

        @Override
        protected void tick(ChunkLevel level, Breeze b, long gameTime) {
            var brain = b.getBrain();
            Targetable t = brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            if (t == null) return;
            b.lookAtEyes(t.x(), t.y(), t.z());
            if (!brain.getMemory(MemoryModuleType.BREEZE_SHOOT_CHARGING).isPresent() && !brain.getMemory(MemoryModuleType.BREEZE_SHOOT_RECOVER).isPresent()) {
                brain.setMemoryWithExpiry(MemoryModuleType.BREEZE_SHOOT_RECOVER, Unit.INSTANCE, RECOVER);
                if (isFacing(b, t)) {
                    double dx = t.x() - b.x();
                    double dy = t.y() + t.bbHeight() * 0.3 - (b.y() + b.bbHeight() * 0.5);
                    double dz = t.z() - b.z();
                    BreezeWindCharge charge = new BreezeWindCharge(level, level.nextEntityId());
                    charge.setOwner(b);
                    charge.setPos(b.x(), b.eyeY() - 0.4, b.z());
                    b.playSound("entity.breeze.shoot", 1.5F, 1.0F);
                    charge.shoot(dx, dy, dz, 0.7F, 5 - level.difficulty() * 4);
                    level.addFreshEntity(charge);
                }
            }
        }

        static boolean isFacing(Breeze b, Targetable t) {
            double[] view = Dir.viewVector(b.xRot(), b.yRot());
            double dx = t.x() - b.x(), dy = t.y() - b.y(), dz = t.z() - b.z();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0E-5) return false;
            return (view[0] * dx + view[1] * dy + view[2] * dz) / len > 0.5;
        }
    }

    /** BreezeJump: 땅(또는 물)에서 숨을 들이쉬고 대상 뒤쪽으로 길게 뛴다. */
    static final class JumpBehavior extends Behavior<Breeze> {
        private static final int[] ANGLES = {40, 55, 60, 75, 80};
        private static final int INHALING = 10;

        JumpBehavior() {
            super(Map.of(MemoryModuleType.ATTACK_TARGET, PRESENT, MemoryModuleType.BREEZE_JUMP_COOLDOWN, ABSENT, MemoryModuleType.BREEZE_JUMP_INHALING, REGISTERED,
                    MemoryModuleType.BREEZE_JUMP_TARGET, REGISTERED, MemoryModuleType.BREEZE_SHOOT, ABSENT, MemoryModuleType.WALK_TARGET, ABSENT, MemoryModuleType.BREEZE_LEAVING_WATER, REGISTERED), 200);
        }

        static boolean shouldJump(ChunkLevel level, Breeze b) {
            if (!b.onGround() && !b.isInWater()) return false;
            if (Swim.shouldSwim(b)) return false;
            var brain = b.getBrain();
            if (brain.hasMemoryValue(MemoryModuleType.BREEZE_JUMP_TARGET)) return true;
            Targetable t = brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            if (t == null) return false;
            double dx = t.x() - b.x(), dy = t.y() - b.y(), dz = t.z() - b.z();
            if (dx * dx + dy * dy + dz * dz > 24.0 * 24.0) {
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
                return false;
            }
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) - 4.0F <= 0.0F) return false;
            if (!canJumpFromCurrentPosition(level, b)) return false;
            Pos jumpPos = snapToSurface(b, randomPointBehindTarget(t, b.random()));
            if (jumpPos == null) return false;
            int floor = level.getBlockState(jumpPos.below());
            if (isBlockDangerous(floor)) return false;
            if (!hasLineOfSight(b, center(jumpPos)) && !hasLineOfSight(b, center(jumpPos.above(4)))) return false;
            brain.setMemory(MemoryModuleType.BREEZE_JUMP_TARGET, jumpPos);
            return true;
        }

        private static double[] center(Pos p) { return new double[] {p.x() + 0.5, p.y() + 0.5, p.z() + 0.5}; }

        private static boolean isBlockDangerous(int state) {
            return org.domi.furnace.server.entity.ai.navigation.WalkNodeEvaluator.isBurningBlock(state)
                    || B.is(state, "minecraft:wither_rose") || B.is(state, "minecraft:sweet_berry_bush") || B.is(state, "minecraft:cactus") || B.is(state, "minecraft:pointed_dripstone");
        }

        private static boolean canJumpFromCurrentPosition(ChunkLevel level, Breeze b) {
            Pos p = b.blockPosition();
            for (int i = 1; i <= 4; i++) {
                Pos q = p.above(i);
                if (!B.isAir(level.getBlockState(q)) && !org.domi.furnace.server.block.Fluids.isWater(level.getFluidState(q))) return false;
            }
            return true;
        }

        private static Pos snapToSurface(Breeze b, double[] from) {
            var down = Clip.clipCollider(b.level(), from[0], from[1], from[2], from[0], from[1] - 10.0, from[2]);
            if (!down.miss()) return new Pos((int) Math.floor(down.x()), (int) Math.floor(down.y()), (int) Math.floor(down.z())).above();
            var up = Clip.clipCollider(b.level(), from[0], from[1], from[2], from[0], from[1] + 10.0, from[2]);
            return up.miss() ? null : new Pos((int) Math.floor(up.x()), (int) Math.floor(up.y()), (int) Math.floor(up.z())).above();
        }

        private static double[] randomPointBehindTarget(Targetable t, org.domi.furnace.server.block.RandomSource r) { return BreezeAi.randomPointBehindTarget(t, r); }

        @Override protected boolean checkExtraStartConditions(ChunkLevel level, Breeze b) { return shouldJump(level, b); }

        @Override
        protected boolean canStillUse(ChunkLevel level, Breeze b, long gameTime) {
            return b.getPose() != Entity.POSE_STANDING && !b.getBrain().hasMemoryValue(MemoryModuleType.BREEZE_JUMP_COOLDOWN);
        }

        @Override
        protected void start(ChunkLevel level, Breeze b, long gameTime) {
            var brain = b.getBrain();
            if (!brain.hasMemoryValue(MemoryModuleType.BREEZE_JUMP_INHALING)) brain.setMemoryWithExpiry(MemoryModuleType.BREEZE_JUMP_INHALING, Unit.INSTANCE, INHALING);
            b.setPose(Entity.POSE_INHALING);
            level.playSound(null, b, "minecraft:entity.breeze.charge", "hostile", 1.0F, 1.0F);
            brain.getMemory(MemoryModuleType.BREEZE_JUMP_TARGET).ifPresent(p -> b.lookAtEyes(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5));
        }

        @Override
        protected void tick(ChunkLevel level, Breeze b, long gameTime) {
            var brain = b.getBrain();
            boolean inWater = b.isInWater();
            if (!inWater && brain.hasMemoryValue(MemoryModuleType.BREEZE_LEAVING_WATER)) brain.eraseMemory(MemoryModuleType.BREEZE_LEAVING_WATER);
            if (b.getPose() == Entity.POSE_INHALING && !brain.hasMemoryValue(MemoryModuleType.BREEZE_JUMP_INHALING)) {
                double[] jump = brain.getMemory(MemoryModuleType.BREEZE_JUMP_TARGET).flatMap(p -> calculateOptimalJumpVector(b, new double[] {p.x() + 0.5, p.y(), p.z() + 0.5})).orElse(null);
                if (jump == null) {
                    b.setPose(Entity.POSE_STANDING);
                    return;
                }
                if (inWater) brain.setMemory(MemoryModuleType.BREEZE_LEAVING_WATER, Unit.INSTANCE);
                b.playSound("entity.breeze.jump", 1.0F, 1.0F);
                b.setPose(Entity.POSE_LONG_JUMPING);
                b.setYRot(b.yBodyRot());
                b.setDiscardFriction(true);
                b.setDeltaMovement(jump[0], jump[1], jump[2]);
            } else if (b.getPose() == Entity.POSE_LONG_JUMPING && (b.onGround() || inWater && !brain.hasMemoryValue(MemoryModuleType.BREEZE_LEAVING_WATER))) {
                b.playSound("entity.breeze.land", 1.0F, 1.0F);
                b.setPose(Entity.POSE_STANDING);
                b.setDiscardFriction(false);
                boolean hasTarget = brain.hasMemoryValue(MemoryModuleType.HURT_BY);
                brain.setMemoryWithExpiry(MemoryModuleType.BREEZE_JUMP_COOLDOWN, Unit.INSTANCE, hasTarget ? 2 : 10);
                brain.setMemoryWithExpiry(MemoryModuleType.BREEZE_SHOOT, Unit.INSTANCE, 100L);
            }
        }

        @Override
        protected void stop(ChunkLevel level, Breeze b, long gameTime) {
            if (b.getPose() == Entity.POSE_LONG_JUMPING || b.getPose() == Entity.POSE_INHALING) b.setPose(Entity.POSE_STANDING);
            b.getBrain().eraseMemory(MemoryModuleType.BREEZE_JUMP_TARGET);
            b.getBrain().eraseMemory(MemoryModuleType.BREEZE_JUMP_INHALING);
            b.getBrain().eraseMemory(MemoryModuleType.BREEZE_LEAVING_WATER);
        }

        private static Optional<double[]> calculateOptimalJumpVector(Breeze b, double[] target) {
            List<Integer> angles = new ArrayList<>();
            for (int a : ANGLES) angles.add(a);
            org.domi.furnace.server.block.RandomSource r = b.random();
            for (int i = angles.size(); i > 1; i--) {   // Util.shuffledCopy
                int j = r.nextInt(i);
                Integer tmp = angles.get(i - 1);
                angles.set(i - 1, angles.get(j));
                angles.set(j, tmp);
            }
            for (int angle : angles) {
                Optional<double[]> v = LongJumpUtil.calculateJumpVectorForAngle(b, target, 1.4F, angle, false);
                if (v.isPresent()) return v;
            }
            return Optional.empty();
        }
    }

    /** 물속·탈것·부양 효과 중에는 사격을 60틱 미룬다(BreezeShoot 방지). */
    static final class StuckBehavior extends Behavior<Breeze> {
        StuckBehavior() {
            super(Map.of(MemoryModuleType.ATTACK_TARGET, PRESENT, MemoryModuleType.BREEZE_JUMP_INHALING, ABSENT, MemoryModuleType.BREEZE_JUMP_TARGET, ABSENT,
                    MemoryModuleType.WALK_TARGET, ABSENT, MemoryModuleType.BREEZE_SHOOT, ABSENT));
        }

        @Override protected boolean checkExtraStartConditions(ChunkLevel level, Breeze b) { return b.isInWater() || b.hasEffect(MobEffect.LEVITATION); }

        @Override protected boolean canStillUse(ChunkLevel level, Breeze b, long gameTime) { return false; }

        @Override protected void start(ChunkLevel level, Breeze b, long gameTime) { b.getBrain().setMemoryWithExpiry(MemoryModuleType.BREEZE_SHOOT, Unit.INSTANCE, 60L); }
    }

    /** BreezeSlide: 땅에서 대상과의 거리를 맞추려 6/10 걸음 옆·뒤로 미끄러지며 걷는다. */
    static final class SlideAwayBehavior extends Behavior<Breeze> {
        SlideAwayBehavior() {
            super(Map.of(MemoryModuleType.ATTACK_TARGET, PRESENT, MemoryModuleType.WALK_TARGET, ABSENT, MemoryModuleType.BREEZE_JUMP_COOLDOWN, ABSENT, MemoryModuleType.BREEZE_SHOOT, ABSENT));
        }

        @Override
        protected boolean checkExtraStartConditions(ChunkLevel level, Breeze b) { return b.onGround() && !b.isInWater() && b.getPose() == Entity.POSE_STANDING; }

        @Override protected boolean canStillUse(ChunkLevel level, Breeze b, long gameTime) { return false; }

        @Override
        protected void start(ChunkLevel level, Breeze b, long gameTime) {
            Targetable t = b.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            if (t == null) return;
            boolean inner = b.withinInnerRing(t.x(), t.y(), t.z());
            double[] walk = null;
            if (inner) {
                double[] away = LandRandomPos.getPosAway(b, 5, 5, new double[] {t.x(), t.y(), t.z()});
                if (away != null && hasLineOfSight(b, away)) {
                    double dt = (away[0] - t.x()) * (away[0] - t.x()) + (away[1] - t.y()) * (away[1] - t.y()) + (away[2] - t.z()) * (away[2] - t.z());
                    double db = (b.x() - t.x()) * (b.x() - t.x()) + (b.y() - t.y()) * (b.y() - t.y()) + (b.z() - t.z()) * (b.z() - t.z());
                    if (dt > db) walk = away;
                }
            }
            if (walk == null) walk = b.random().nextBoolean() ? randomPointBehindTarget(t, b.random()) : randomPointInMiddleCircle(b, t);
            b.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new Pos((int) Math.floor(walk[0]), (int) Math.floor(walk[1]), (int) Math.floor(walk[2])), SPEED_MULTIPLIER_WHEN_SLIDING, 1));
        }

        private static double[] randomPointInMiddleCircle(Breeze b, Targetable t) {
            double dx = t.x() - b.x(), dy = t.y() - b.y(), dz = t.z() - b.z();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double d = len - (8.0 + b.random().nextDouble() * (4.0 - 8.0));
            double ux = len < 1.0E-5 ? 0 : dx / len, uy = len < 1.0E-5 ? 0 : dy / len, uz = len < 1.0E-5 ? 0 : dz / len;
            return new double[] {b.x() + ux * d, b.y() + uy * d, b.z() + uz * d};
        }
    }
}
