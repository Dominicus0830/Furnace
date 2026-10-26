// 바닐라 PolarBear: 새끼 곁에서는 적대적(NeutralMob), 맞으면 분노. 일어서서 경고하다 문다. TODO: 여우 사냥.
package org.domi.furnace.server.entity;

import java.util.UUID;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class PolarBear extends Animal implements NeutralMob {
    public static final int DATA_STANDING_ID = 17;
    private int warningSoundTicks;
    private int remainingPersistentAngerTime;
    private UUID persistentAngerTarget;

    public PolarBear(ChunkLevel level, int id) { super(EntityType.POLAR_BEAR, level, id); }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return new PolarBear(level, level.nextEntityId()); }

    @Override public boolean isFood(ItemStack s) { return false; }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_STANDING_ID, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 30.0).add(Attribute.FOLLOW_RANGE, 20.0).add(Attribute.MOVEMENT_SPEED, 0.25).add(Attribute.ATTACK_DAMAGE, 6.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PolarBearMeleeAttackGoal());
        goalSelector.addGoal(1, new PanicGoal(this, 2.0) {
            @Override
            protected boolean shouldPanic() {
                String t = getLastDamageType();
                return t != null && org.domi.furnace.server.entity.damage.DamageType.byKey(t).isInTag(isBaby() ? "minecraft:panic_causes" : "minecraft:panic_environmental_causes");
            }
        });
        goalSelector.addGoal(4, new FollowParentGoal(this, 1.25));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, 6.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new PolarBearHurtByTargetGoal());
        targetSelector.addGoal(2, new PolarBearAttackPlayersGoal());
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Player.class, 10, true, false, t -> isAngryAt(t)));
        // TODO(여우): targetSelector 4 = Fox(10, mustSee, mustReach)
        targetSelector.addGoal(5, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        addPersistentAngerSaveData(tag);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        readPersistentAngerSaveData(tag);
    }

    @Override public void startPersistentAngerTimer() { setRemainingPersistentAngerTime(20 + random.nextInt(20)); }

    @Override public void setRemainingPersistentAngerTime(int t) { remainingPersistentAngerTime = t; }

    @Override public int getRemainingPersistentAngerTime() { return remainingPersistentAngerTime; }

    @Override public void setPersistentAngerTarget(UUID u) { persistentAngerTarget = u; }

    @Override public UUID getPersistentAngerTarget() { return persistentAngerTarget; }

    @Override public boolean canAttackTarget(Targetable t) { return canAttackType(t); }

    @Override
    public String getAmbientSound() { return isBaby() ? "entity.polar_bear.ambient_baby" : "entity.polar_bear.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.polar_bear.hurt"; }

    @Override protected String getDeathSound() { return "entity.polar_bear.death"; }

    protected void playWarningSound() {
        if (warningSoundTicks <= 0) {
            playSound("entity.polar_bear.warning", 1.0F, 1.0F);
            warningSoundTicks = 40;
        }
    }

    public boolean isStanding() { return (Boolean) entityData.get(DATA_STANDING_ID); }

    public void setStanding(boolean v) { entityData.set(DATA_STANDING_ID, v); }

    @Override
    public void tick() {
        super.tick();
        if (warningSoundTicks > 0) warningSoundTicks--;
        updatePersistentAnger(level.gameRule("universalAnger"));
    }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        super.finalizeSpawn(r, difficulty);
    }

    @Override
    public GroupData finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, GroupData data) {
        if (data == null) data = new GroupData(1.0F);
        return super.finalizeSpawn(r, difficulty, data);
    }

    private final class PolarBearHurtByTargetGoal extends HurtByTargetGoal {
        PolarBearHurtByTargetGoal() { super(PolarBear.this); }

        @Override
        public void start() {
            super.start();
            if (PolarBear.this.isBaby()) {
                alertOthers();
                stop();
            }
        }

        @Override
        protected void alertOther(Mob other, Targetable target) {
            if (other instanceof PolarBear && !other.isBaby()) super.alertOther(other, target);
        }
    }

    private final class PolarBearAttackPlayersGoal extends NearestAttackableTargetGoal {
        PolarBearAttackPlayersGoal() { super(PolarBear.this, Player.class, 20, true, true, null); }

        @Override
        public boolean canUse() {
            if (PolarBear.this.isBaby()) return false;
            if (super.canUse()) {
                for (PolarBear b : PolarBear.this.level.getEntitiesOfClass(PolarBear.class, PolarBear.this.boundingBox().inflate(8.0, 4.0, 8.0), x -> true)) {
                    if (b.isBaby()) return true;
                }
            }
            return false;
        }

        @Override protected double getFollowDistance() { return super.getFollowDistance() * 0.5; }
    }

    private final class PolarBearMeleeAttackGoal extends MeleeAttackGoal {
        PolarBearMeleeAttackGoal() { super(PolarBear.this, 1.25, true); }

        @Override
        protected void checkAndPerformAttack(Targetable target) {
            if (canPerformAttack(target)) {
                resetAttackCooldown();
                mob.doHurtTarget(target);
                setStanding(false);
            } else {
                double dx = mob.x() - target.x(), dy = mob.y() - target.y(), dz = mob.z() - target.z();
                double w = target.bbWidth() + 3.0F;
                if (dx * dx + dy * dy + dz * dz < w * w) {
                    if (isTimeToAttack()) {
                        setStanding(false);
                        resetAttackCooldown();
                    }
                    if (getTicksUntilNextAttack() <= 10) {
                        setStanding(true);
                        playWarningSound();
                    }
                } else {
                    resetAttackCooldown();
                    setStanding(false);
                }
            }
        }

        @Override
        public void stop() {
            setStanding(false);
            super.stop();
        }
    }
}
