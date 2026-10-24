// 바닐라 Blaze: 불에 타지 않는 네더 몬스터. 대상이 보이면 작은 불덩이를 3발 연속(충전 60틱 후) 쏘고, 가까우면 직접 때린다. 물에 닿으면 피해를 입는다.
package org.domi.furnace.server.entity;

import java.util.EnumSet;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Blaze extends Monster {
    public static final int DATA_FLAGS_ID = 16;
    private float allowedHeightOffset = 0.5F;
    private int nextHeightOffsetChangeTick;

    public Blaze(ChunkLevel level, int id) {
        super(EntityType.BLAZE, level, id);
        setPathfindingMalus(PathType.WATER, -1.0F);
        setPathfindingMalus(PathType.LAVA, 8.0F);
        setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
        setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
        this.xpReward = 10;
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.ATTACK_DAMAGE, 6.0).add(Attribute.MOVEMENT_SPEED, 0.23F).add(Attribute.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(4, new BlazeAttackGoal(this));
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.0));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0, 0.0F));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_FLAGS_ID, SetEntityData.SERIALIZER_BYTE, (byte) 0);
    }

    @Override public String getAmbientSound() { return "entity.blaze.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.blaze.hurt"; }

    @Override protected String getDeathSound() { return "entity.blaze.death"; }

    @Override
    public float getLightLevelDependentMagicValue() { return 1.0F; }

    @Override
    protected void aiStep() {
        if (!onGround() && vy() < 0.0) setDeltaMovement(vx(), vy() * 0.6, vz());
        super.aiStep();
    }

    @Override
    public boolean isSensitiveToWater() { return true; }

    @Override
    protected void customServerAiStep() {
        nextHeightOffsetChangeTick--;
        if (nextHeightOffsetChangeTick <= 0) {
            nextHeightOffsetChangeTick = 100;
            allowedHeightOffset = (float) random.triangle(0.5, 6.891);
        }
        Targetable target = getTarget();
        if (target != null && target.eyeY() > eyeY() + allowedHeightOffset && target.canBeSeenAsEnemy() && canAttackType(target)) {
            setDeltaMovement(vx(), vy() + (0.3F - vy()) * 0.3F, vz());
            hasImpulse = true;
        }
        super.customServerAiStep();
    }

    @Override
    public boolean isOnFire() { return isCharged(); }

    private boolean isCharged() { return ((Byte) entityData.get(DATA_FLAGS_ID) & 1) != 0; }

    void setCharged(boolean charged) {
        byte b = (Byte) entityData.get(DATA_FLAGS_ID);
        entityData.set(DATA_FLAGS_ID, (byte) (charged ? b | 1 : b & -2));
    }

    static final class BlazeAttackGoal extends Goal {
        private final Blaze blaze;
        private int attackStep;
        private int attackTime;
        private int lastSeen;

        BlazeAttackGoal(Blaze blaze) {
            this.blaze = blaze;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Targetable t = blaze.getTarget();
            return t != null && t.isAlive() && t.canBeSeenAsEnemy() && blaze.canAttackType(t);
        }

        @Override public void start() { attackStep = 0; }

        @Override
        public void stop() {
            blaze.setCharged(false);
            lastSeen = 0;
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            attackTime--;
            Targetable target = blaze.getTarget();
            if (target == null) return;
            boolean seen = blaze.hasLineOfSight(target);
            if (seen) lastSeen = 0;
            else lastSeen++;
            double dx0 = blaze.x() - target.x(), dy0 = blaze.y() - target.y(), dz0 = blaze.z() - target.z();
            double distSqr = dx0 * dx0 + dy0 * dy0 + dz0 * dz0;
            if (distSqr < 4.0) {
                if (!seen) return;
                if (attackTime <= 0) {
                    attackTime = 20;
                    blaze.doHurtTarget(target);
                }
                blaze.getMoveControl().setWantedPosition(target.x(), target.y(), target.z(), 1.0);
            } else if (distSqr < followRange() * followRange() && seen) {
                double dx = target.x() - blaze.x();
                double dy = blaze.yAt(target, 0.5) - blaze.yAt(blaze, 0.5);
                double dz = target.z() - blaze.z();
                if (attackTime <= 0) {
                    attackStep++;
                    if (attackStep == 1) {
                        attackTime = 60;
                        blaze.setCharged(true);
                    } else if (attackStep <= 4) {
                        attackTime = 6;
                    } else {
                        attackTime = 100;
                        attackStep = 0;
                        blaze.setCharged(false);
                    }
                    if (attackStep > 1) {
                        double spread = Math.sqrt(Math.sqrt(distSqr)) * 0.5;
                        if (!blaze.isSilent()) blaze.level().levelEvent(null, 1018, blaze.blockPosition(), 0);
                        var r = blaze.random();
                        double mx = r.triangle(dx, 2.297 * spread);
                        double mz = r.triangle(dz, 2.297 * spread);
                        double len = Math.sqrt(mx * mx + dy * dy + mz * mz);
                        SmallFireball fireball = new SmallFireball(blaze.level(), blaze.level().nextEntityId());
                        fireball.launchFrom(blaze, mx / len, dy / len, mz / len);
                        fireball.setPos(fireball.x(), blaze.yAt(blaze, 0.5) + 0.5, fireball.z());
                        blaze.level().addFreshEntity(fireball);
                    }
                }
                blaze.getLookControl().setLookAtPlayerOrEntity(target, 10.0F, 10.0F);
            } else if (lastSeen < 5) {
                blaze.getMoveControl().setWantedPosition(target.x(), target.y(), target.z(), 1.0);
            }
        }

        private double followRange() { return blaze.attributeValue(Attribute.FOLLOW_RANGE); }
    }

    /** Entity.getY(scale): 발 위치 + 키 × scale. */
    double yAt(Targetable t, double scale) { return t.y() + t.bbHeight() * scale; }
}
