// 바닐라 WardenAi: 워든 브레인 — 활동(core/emerge/dig/idle/investigate/sniff/roar/fight)과 워든 전용 행동
// (Emerging·Digging·ForceUnmount·SetRoarTarget·TryToSniff·Sniffing·Roar·SonicBoom·SetWardenLookTarget)·WardenEntitySensor.
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.behavior.*;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.BlockPosTracker;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.brain.Unit;
import org.domi.furnace.server.entity.ai.sensing.NearestLivingEntitySensor;

public final class WardenAi {
    public static final int EMERGE_DURATION = (int) Math.ceil(133.59999F);
    public static final int ROAR_DURATION = (int) Math.ceil(84.0F);
    private static final int DIGGING_DURATION = (int) Math.ceil(100.0F);
    private static final int SNIFFING_DURATION = (int) Math.ceil(83.2F);
    public static final int DIGGING_COOLDOWN = 1200;
    private static final int DISTURBANCE_LOCATION_EXPIRY_TIME = 100;
    private static final float SPEED_MULTIPLIER_WHEN_IDLING = 0.5F, SPEED_MULTIPLIER_WHEN_INVESTIGATING = 0.7F, SPEED_MULTIPLIER_WHEN_FIGHTING = 1.2F;
    private static final int MELEE_ATTACK_COOLDOWN = 18;

    private WardenAi() {}

    /** updateActivity. */
    public static void updateActivity(Warden w) {
        w.getBrain().setActiveActivityToFirstValid(List.of(Activity.EMERGE, Activity.DIG, Activity.ROAR, Activity.FIGHT, Activity.INVESTIGATE,
                Activity.SNIFF, Activity.IDLE));
    }

    private static Map.Entry<Integer, BehaviorControl<? super Warden>> p(int prio, BehaviorControl<? super Warden> b) { return Map.entry(prio, b); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Brain<Warden> makeBrain(Warden w, Brain<Warden> brain) {
        brain.addActivity(Activity.CORE, 0, (List) List.of(new Swim(0.8F), new SetWardenLookTarget(), new LookAtTargetSink(45, 90), new MoveToTargetSink()));
        brain.addActivityAndRemoveMemoryWhenStopped(Activity.EMERGE, 5, List.of(new Emerging(EMERGE_DURATION)), MemoryModuleType.IS_EMERGING);
        brain.addActivityWithConditions(Activity.DIG, List.of(p(0, new ForceUnmount()), p(1, new Digging(DIGGING_DURATION))),
                Set.of(Map.entry(MemoryModuleType.ROAR_TARGET, MemoryStatus.VALUE_ABSENT), Map.entry(MemoryModuleType.DIG_COOLDOWN, MemoryStatus.VALUE_ABSENT)));
        brain.addActivity(Activity.IDLE, 10, (List) List.of(new SetRoarTarget(Warden::getEntityAngryAt), new TryToSniff(),
                new RunOne<Warden>(Map.of(MemoryModuleType.IS_SNIFFING, MemoryStatus.VALUE_ABSENT),
                        (List) List.of(Map.entry(RandomStroll.stroll(SPEED_MULTIPLIER_WHEN_IDLING), 2), Map.entry(new DoNothing(30, 60), 1)))));
        brain.addActivityAndRemoveMemoryWhenStopped(Activity.INVESTIGATE, 5, (List) List.of(new SetRoarTarget(Warden::getEntityAngryAt),
                GoToTargetLocation.create(MemoryModuleType.DISTURBANCE_LOCATION, 2, SPEED_MULTIPLIER_WHEN_INVESTIGATING)), MemoryModuleType.DISTURBANCE_LOCATION);
        brain.addActivityAndRemoveMemoryWhenStopped(Activity.SNIFF, 5, (List) List.of(new SetRoarTarget(Warden::getEntityAngryAt), new Sniffing(SNIFFING_DURATION)),
                MemoryModuleType.IS_SNIFFING);
        brain.addActivityAndRemoveMemoryWhenStopped(Activity.ROAR, 10, List.of(new Roar()), MemoryModuleType.ROAR_TARGET);
        brain.addActivityAndRemoveMemoryWhenStopped(Activity.FIGHT, 10, (List) List.of(new DigCooldownSetter(),
                StopAttackingIfTargetInvalid.<Warden>create(t -> !w.getAngerLevel().isAngry() || !w.canTargetEntity(t), WardenAi::onTargetInvalid, false),
                SetEntityLookTarget.create(t -> isTarget(w, t), (float) w.attributeValue(Attribute.FOLLOW_RANGE)),
                SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(SPEED_MULTIPLIER_WHEN_FIGHTING), new SonicBoom(), MeleeAttack.create(MELEE_ATTACK_COOLDOWN)),
                MemoryModuleType.ATTACK_TARGET);
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static boolean isTarget(Warden w, Targetable t) { return w.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).filter(x -> x == t).isPresent(); }

    private static void onTargetInvalid(Warden w, Targetable t) {
        if (!w.canTargetEntity(t)) w.clearAnger(t);
        setDigCooldown(w);
    }

    public static void setDigCooldown(Mob m) {
        if (m.getBrain().hasMemoryValue(MemoryModuleType.DIG_COOLDOWN)) m.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, DIGGING_COOLDOWN);
    }

    public static void setDisturbanceLocation(Warden w, Pos pos) {
        if (w.level().withinBorder(pos) && w.getEntityAngryAt().isEmpty() && w.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty()) {
            setDigCooldown(w);
            var b = w.getBrain();
            b.setMemoryWithExpiry(MemoryModuleType.SNIFF_COOLDOWN, Unit.INSTANCE, 100L);
            b.setMemoryWithExpiry(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos), 100L);
            b.setMemoryWithExpiry(MemoryModuleType.DISTURBANCE_LOCATION, pos, 100L);
            b.eraseMemory(MemoryModuleType.WALK_TARGET);
        }
    }

    // ------------------------------------------------------------------ 행동

    /** DIG_COOLDOWN 을 이미 가지고 있으면 1200 으로 되감는다(언제나 성공). */
    static final class DigCooldownSetter extends OneShot<Warden> {
        @Override
        protected boolean trigger(ChunkLevel level, Warden w, long t) {
            if (!registered(w, MemoryModuleType.DIG_COOLDOWN)) return false;
            if (w.getBrain().hasMemoryValue(MemoryModuleType.DIG_COOLDOWN)) w.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, 1200L);
            return true;
        }
    }

    /** SetWardenLookTarget: 포효 대상 위치, 없으면 소란 위치를 본다. */
    static final class SetWardenLookTarget extends OneShot<Warden> {
        @Override
        protected boolean trigger(ChunkLevel level, Warden w, long t) {
            if (!absent(w, MemoryModuleType.ATTACK_TARGET)) return false;
            var b = w.getBrain();
            Optional<Pos> p = b.getMemory(MemoryModuleType.ROAR_TARGET).map(Targetable::blockPosition).or(() -> b.getMemory(MemoryModuleType.DISTURBANCE_LOCATION));
            if (p.isEmpty()) return false;
            b.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(p.get()));
            return true;
        }
    }

    static final class SetRoarTarget extends OneShot<Warden> {
        private final Function<Warden, Optional<Targetable>> getter;

        SetRoarTarget(Function<Warden, Optional<Targetable>> getter) { this.getter = getter; }

        @Override
        protected boolean trigger(ChunkLevel level, Warden w, long t) {
            if (!absent(w, MemoryModuleType.ROAR_TARGET) || !absent(w, MemoryModuleType.ATTACK_TARGET)) return false;
            Optional<Targetable> e = getter.apply(w);
            if (e.filter(w::canTargetEntity).isEmpty()) return false;
            w.getBrain().setMemory(MemoryModuleType.ROAR_TARGET, e.get());
            w.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            return true;
        }
    }

    static final class TryToSniff extends OneShot<Warden> {
        private static final UniformInt SNIFF_COOLDOWN = UniformInt.of(100, 200);

        @Override
        protected boolean trigger(ChunkLevel level, Warden w, long t) {
            if (!absent(w, MemoryModuleType.SNIFF_COOLDOWN) || !present(w, MemoryModuleType.NEAREST_ATTACKABLE) || !absent(w, MemoryModuleType.DISTURBANCE_LOCATION)) return false;
            var b = w.getBrain();
            b.setMemory(MemoryModuleType.IS_SNIFFING, Unit.INSTANCE);
            b.setMemoryWithExpiry(MemoryModuleType.SNIFF_COOLDOWN, Unit.INSTANCE, SNIFF_COOLDOWN.sample(level.random()));
            b.eraseMemory(MemoryModuleType.WALK_TARGET);
            w.setPose(Entity.POSE_SNIFFING);
            return true;
        }
    }

    static final class Emerging extends Behavior<Warden> {
        Emerging(int duration) {
            super(Map.of(MemoryModuleType.IS_EMERGING, MemoryStatus.VALUE_PRESENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                    MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED), duration);
        }

        @Override protected boolean canStillUse(ChunkLevel level, Warden w, long t) { return true; }

        @Override
        protected void start(ChunkLevel level, Warden w, long t) {
            w.setPose(Entity.POSE_EMERGING);
            w.playSound("minecraft:entity.warden.emerge", 5.0F, 1.0F);
        }

        @Override
        protected void stop(ChunkLevel level, Warden w, long t) {
            if (w.getPose() == Entity.POSE_EMERGING) w.setPose(Entity.POSE_STANDING);
        }
    }

    static final class ForceUnmount extends Behavior<Warden> {
        ForceUnmount() { super(Map.of()); }

        @Override protected boolean checkExtraStartConditions(ChunkLevel level, Warden w) { return w.isPassenger(); }

        @Override protected void start(ChunkLevel level, Warden w, long t) { w.stopRiding(); }
    }

    static final class Digging extends Behavior<Warden> {
        Digging(int duration) {
            super(Map.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT), duration);
        }

        @Override protected boolean canStillUse(ChunkLevel level, Warden w, long t) { return w.removalReason() == null; }

        @Override protected boolean checkExtraStartConditions(ChunkLevel level, Warden w) { return w.onGround() || w.isInWater() || w.isInLava(); }

        @Override
        protected void start(ChunkLevel level, Warden w, long t) {
            if (w.onGround()) {
                w.setPose(Entity.POSE_DIGGING);
                w.playSound("minecraft:entity.warden.dig", 5.0F, 1.0F);
            } else {
                w.playSound("minecraft:entity.warden.agitated", 5.0F, 1.0F);
                stop(level, w, t);
            }
        }

        @Override
        protected void stop(ChunkLevel level, Warden w, long t) {
            if (w.removalReason() == null) w.remove(Entity.RemovalReason.DISCARDED);
        }
    }

    static final class Sniffing extends Behavior<Warden> {
        Sniffing(int duration) {
            super(Map.of(MemoryModuleType.IS_SNIFFING, MemoryStatus.VALUE_PRESENT, MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT,
                    MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                    MemoryModuleType.NEAREST_ATTACKABLE, MemoryStatus.REGISTERED, MemoryModuleType.DISTURBANCE_LOCATION, MemoryStatus.REGISTERED,
                    MemoryModuleType.SNIFF_COOLDOWN, MemoryStatus.REGISTERED), duration);
        }

        @Override protected boolean canStillUse(ChunkLevel level, Warden w, long t) { return true; }

        @Override protected void start(ChunkLevel level, Warden w, long t) { w.playSound("minecraft:entity.warden.sniff", 5.0F, 1.0F); }

        @Override
        protected void stop(ChunkLevel level, Warden w, long t) {
            if (w.getPose() == Entity.POSE_SNIFFING) w.setPose(Entity.POSE_STANDING);
            w.getBrain().eraseMemory(MemoryModuleType.IS_SNIFFING);
            w.getBrain().getMemory(MemoryModuleType.NEAREST_ATTACKABLE).filter(w::canTargetEntity).ifPresent(e -> {
                if (Warden.closerThan(w, e, 6.0, 20.0)) w.increaseAngerAt(e);
                if (!w.getBrain().hasMemoryValue(MemoryModuleType.DISTURBANCE_LOCATION)) setDisturbanceLocation(w, e.blockPosition());
            });
        }
    }

    static final class Roar extends Behavior<Warden> {
        private static final int TICKS_BEFORE_PLAYING_ROAR_SOUND = 25, ROAR_ANGER_INCREASE = 20;

        Roar() {
            super(Map.of(MemoryModuleType.ROAR_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT,
                    MemoryModuleType.ROAR_SOUND_COOLDOWN, MemoryStatus.REGISTERED, MemoryModuleType.ROAR_SOUND_DELAY, MemoryStatus.REGISTERED), ROAR_DURATION);
        }

        @Override
        protected void start(ChunkLevel level, Warden w, long t) {
            var b = w.getBrain();
            b.setMemoryWithExpiry(MemoryModuleType.ROAR_SOUND_DELAY, Unit.INSTANCE, TICKS_BEFORE_PLAYING_ROAR_SOUND);
            b.eraseMemory(MemoryModuleType.WALK_TARGET);
            Targetable target = b.getMemory(MemoryModuleType.ROAR_TARGET).get();
            BehaviorUtils.lookAtEntity(w, target);
            w.setPose(Entity.POSE_ROARING);
            w.increaseAngerAt(target, ROAR_ANGER_INCREASE, false);
        }

        @Override protected boolean canStillUse(ChunkLevel level, Warden w, long t) { return true; }

        @Override
        protected void tick(ChunkLevel level, Warden w, long t) {
            var b = w.getBrain();
            if (!b.hasMemoryValue(MemoryModuleType.ROAR_SOUND_DELAY) && !b.hasMemoryValue(MemoryModuleType.ROAR_SOUND_COOLDOWN)) {
                b.setMemoryWithExpiry(MemoryModuleType.ROAR_SOUND_COOLDOWN, Unit.INSTANCE, ROAR_DURATION - TICKS_BEFORE_PLAYING_ROAR_SOUND);
                w.playSound("minecraft:entity.warden.roar", 3.0F, 1.0F);
            }
        }

        @Override
        protected void stop(ChunkLevel level, Warden w, long t) {
            if (w.getPose() == Entity.POSE_ROARING) w.setPose(Entity.POSE_STANDING);
            w.getBrain().getMemory(MemoryModuleType.ROAR_TARGET).ifPresent(w::setAttackTarget);
            w.getBrain().eraseMemory(MemoryModuleType.ROAR_TARGET);
        }
    }

    /** SonicBoom: 60틱. 34틱째 15×20 안의 대상에게 10 피해(방어구 무시 종류)와 밀치기. 입자: 가슴에서 대상 눈까지 sonic_boom. */
    public static final class SonicBoom extends Behavior<Warden> {
        public static final int COOLDOWN = 40;
        private static final int DURATION = (int) Math.ceil(60.0F), TICKS_BEFORE_PLAYING_SOUND = (int) Math.ceil(34.0);

        SonicBoom() {
            super(Map.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.SONIC_BOOM_COOLDOWN, MemoryStatus.VALUE_ABSENT,
                    MemoryModuleType.SONIC_BOOM_SOUND_COOLDOWN, MemoryStatus.REGISTERED, MemoryModuleType.SONIC_BOOM_SOUND_DELAY, MemoryStatus.REGISTERED), DURATION);
        }

        @Override
        protected boolean checkExtraStartConditions(ChunkLevel level, Warden w) {
            return Warden.closerThan(w, w.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get(), 15.0, 20.0);
        }

        @Override protected boolean canStillUse(ChunkLevel level, Warden w, long t) { return true; }

        @Override
        protected void start(ChunkLevel level, Warden w, long t) {
            w.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, DURATION);
            w.getBrain().setMemoryWithExpiry(MemoryModuleType.SONIC_BOOM_SOUND_DELAY, Unit.INSTANCE, TICKS_BEFORE_PLAYING_SOUND);
            w.broadcastEntityEvent((byte) 62);
            w.playSound("minecraft:entity.warden.sonic_charge", 3.0F, 1.0F);
        }

        @Override
        protected void tick(ChunkLevel level, Warden w, long t) {
            var b = w.getBrain();
            b.getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent(e -> w.getLookControl().setLookAt(e.x(), e.y(), e.z()));
            if (!b.hasMemoryValue(MemoryModuleType.SONIC_BOOM_SOUND_DELAY) && !b.hasMemoryValue(MemoryModuleType.SONIC_BOOM_SOUND_COOLDOWN)) {
                b.setMemoryWithExpiry(MemoryModuleType.SONIC_BOOM_SOUND_COOLDOWN, Unit.INSTANCE, DURATION - TICKS_BEFORE_PLAYING_SOUND);
                b.getMemory(MemoryModuleType.ATTACK_TARGET).filter(w::canTargetEntity).filter(e -> Warden.closerThan(w, e, 15.0, 20.0)).ifPresent(e -> {
                    double ox = w.x(), oy = w.y() + 1.6F, oz = w.z();   // WARDEN_CHEST 부착점
                    double dx = e.x() - ox, dy = e.eyeY() - oy, dz = e.z() - oz;
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double nx = len < 1.0E-4 ? 0 : dx / len, ny = len < 1.0E-4 ? 0 : dy / len, nz = len < 1.0E-4 ? 0 : dz / len;
                    double dlen = Math.sqrt(dx * dx + dy * dy + dz * dz);   // 입자: i = 1 .. floor(길이)+6, 방향은 정규화(길이 0 이면 0)
                    for (int i = 1; i < (int) Math.floor(dlen) + 7; i++) {
                        level.sendParticles(org.domi.furnace.server.net.protocol.play.ParticleOptions.simple("sonic_boom"),
                                ox + nx * i, oy + ny * i, oz + nz * i, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                    w.playSound("minecraft:entity.warden.sonic_boom", 3.0F, 1.0F);
                    if (e.hurtByMob(new Entity.Damage("sonic_boom", false, false, false), 10.0F, w)) {
                        double kr = e instanceof LivingEntity le ? le.attributeValue(Attribute.KNOCKBACK_RESISTANCE) : 0.0;
                        double v = 0.5 * (1.0 - kr), h = 2.5 * (1.0 - kr);
                        e.addDeltaMovement(nx * h, ny * v, nz * h);   // Entity.push
                    }
                });
            }
        }

        @Override protected void stop(ChunkLevel level, Warden w, long t) { setCooldown(w, COOLDOWN); }

        public static void setCooldown(Mob m, int ticks) { m.getBrain().setMemoryWithExpiry(MemoryModuleType.SONIC_BOOM_COOLDOWN, Unit.INSTANCE, ticks); }
    }

    /** WardenEntitySensor: 24칸 안 생물 중 노릴 수 있는 플레이어, 없으면 그 밖의 것을 nearest_attackable 로. */
    public static final class WardenEntitySensor extends NearestLivingEntitySensor<Warden> {
        @Override protected int radiusXZ() { return 24; }

        @Override protected int radiusY() { return 24; }

        @Override
        protected void doTick(ChunkLevel level, Warden w) {
            super.doTick(level, w);
            Optional<Targetable> t = find(w, Targetable::isPlayer).or(() -> find(w, e -> !e.isPlayer()));
            if (t.isPresent()) w.getBrain().setMemory(MemoryModuleType.NEAREST_ATTACKABLE, t.get());
            else w.getBrain().eraseMemory(MemoryModuleType.NEAREST_ATTACKABLE);
        }

        private static Optional<Targetable> find(Warden w, java.util.function.Predicate<Targetable> p) {
            return w.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).stream().flatMap(List::stream).filter(w::canTargetEntity).filter(p).findFirst();
        }

        @Override
        public Set<MemoryModuleType<?>> requires() {
            return Set.of(MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.NEAREST_ATTACKABLE);
        }
    }
}
