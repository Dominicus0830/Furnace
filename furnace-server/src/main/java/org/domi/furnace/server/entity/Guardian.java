// 바닐라 Guardian: 물속 몬스터. 대상에게 80틱 동안 광선을 쏴(충전) 마법 피해와 일반 피해를 준다. 가시로 맞받아치고, 물 밖에서는 파닥인다.
// TODO(오징어·아홀로틀 표적): 선택기에 Axolotl 추가(아홀로틀 구현 때). 광선 입자/소리는 클라이언트 몫.
package org.domi.furnace.server.entity;

import java.util.EnumSet;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.control.MoveControl;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.entity.ai.navigation.WaterBoundPathNavigation;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Guardian extends Monster {
    public static final int DATA_MOVING = 16, DATA_ACTIVE_ATTACK_TARGET = 17;
    protected static final int ATTACK_TIME = 80;
    protected RandomStrollGoal randomStrollGoal;

    public Guardian(ChunkLevel level, int id) { this(EntityType.GUARDIAN, level, id); }

    protected Guardian(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        this.xpReward = 10;
        setPathfindingMalus(PathType.WATER, 0.0F);
        this.moveControl = new GuardianMoveControl(this);
        random.nextFloat();   // clientSideTailAnimation 초기값(클라이언트 전용이지만 난수 한 번을 쓴다)
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.ATTACK_DAMAGE, 6.0).add(Attribute.MOVEMENT_SPEED, 0.5).add(Attribute.FOLLOW_RANGE, 16.0).add(Attribute.MAX_HEALTH, 30.0);
    }

    @Override
    protected void registerGoals() {
        MoveTowardsRestrictionGoal restriction = new MoveTowardsRestrictionGoal(this, 1.0);
        randomStrollGoal = new RandomStrollGoal(this, 1.0, 80);
        goalSelector.addGoal(4, new GuardianAttackGoal(this));
        goalSelector.addGoal(5, restriction);
        goalSelector.addGoal(7, randomStrollGoal);
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, 8.0F));
        goalSelector.addGoal(8, new LookAtMobGoal(this, Guardian.class, 12.0F, 0.01F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        randomStrollGoal.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        restriction.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal(this, LivingEntity.class, 10, true, false, new GuardianAttackSelector(this)));
    }

    @Override
    protected PathNavigation createNavigation(ChunkLevel level) { return new WaterBoundPathNavigation(this, level); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_MOVING, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_ACTIVE_ATTACK_TARGET, SetEntityData.SERIALIZER_INT, 0);
    }

    public boolean isMoving() { return (Boolean) entityData.get(DATA_MOVING); }

    void setMoving(boolean v) { entityData.set(DATA_MOVING, v); }

    public int getAttackDuration() { return ATTACK_TIME; }

    void setActiveAttackTarget(int id) { entityData.set(DATA_ACTIVE_ATTACK_TARGET, id); }

    public boolean hasActiveAttackTarget() { return (Integer) entityData.get(DATA_ACTIVE_ATTACK_TARGET) != 0; }

    @Override
    public int getAmbientSoundInterval() { return 160; }

    @Override public String getAmbientSound() { return isInWaterOrBubble() ? "entity.guardian.ambient" : "entity.guardian.ambient_land"; }

    @Override protected String getHurtSound(Damage source) { return isInWaterOrBubble() ? "entity.guardian.hurt" : "entity.guardian.hurt_land"; }

    @Override protected String getDeathSound() { return isInWaterOrBubble() ? "entity.guardian.death" : "entity.guardian.death_land"; }

    protected String getFlopSound() { return "entity.guardian.flop"; }

    @Override
    public float getWalkTargetValue(Pos pos, ChunkLevel level) {
        if (!Fluids.isWater(level.getFluidState(pos))) return super.getWalkTargetValue(pos, level);
        float f = level.getMaxLocalRawBrightness(pos) / 15.0F;
        return 10.0F + f / (4.0F - 3.0F * f);   // 10 + getPathfindingCostFromLightLevels
    }

    @Override
    protected void aiStep() {
        if (isAlive()) {
            if (isInWaterOrBubble()) {
                setAirSupply(300);
            } else if (onGround()) {
                setDeltaMovement(vx() + (random.nextFloat() * 2.0F - 1.0F) * 0.4F, vy() + 0.5, vz() + (random.nextFloat() * 2.0F - 1.0F) * 0.4F);
                setYRot(random.nextFloat() * 360.0F);
                setOnGround(false);
                hasImpulse = true;
            }
            if (hasActiveAttackTarget()) setYRot(yHeadRot);
        }
        super.aiStep();
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (!isMoving() && !org.domi.furnace.server.entity.damage.DamageType.keyHasTag(source.type(), "minecraft:avoids_guardian_thorns")
                && !source.type().equals("thorns") && attacker != null && !org.domi.furnace.server.entity.damage.DamageType.keyHasTag(source.type(), "minecraft:is_projectile")) {
            attacker.hurtByMob(new Damage("thorns", false, false, false), 2.0F, this);
        }
        if (randomStrollGoal != null) randomStrollGoal.trigger();
        return super.hurtBy(source, amount, attacker);
    }

    @Override
    public int getMaxHeadXRot() { return 180; }

    @Override
    protected void travel(float inX, float inY, float inZ) {
        if (isEffectiveAi() && isInWater()) {
            moveRelative(0.1F, inX, inY, inZ);
            move(MOVER_SELF, vx(), vy(), vz());
            setDeltaMovement(vx() * 0.9, vy() * 0.9, vz() * 0.9);
            if (!isMoving() && getTarget() == null) setDeltaMovement(vx(), vy() - 0.005, vz());
        } else {
            super.travel(inX, inY, inZ);
        }
    }

    /** 먼저 고른 대상(플레이어·오징어·아홀로틀, 3칸 밖)만 노린다. */
    static final class GuardianAttackSelector implements java.util.function.Predicate<Targetable> {
        private final Guardian guardian;

        GuardianAttackSelector(Guardian guardian) { this.guardian = guardian; }

        @Override
        public boolean test(Targetable t) {
            if (!(t instanceof Player || t instanceof Squid || t instanceof Axolotl)) return false;
            double dx = t.x() - guardian.x(), dy = t.y() - guardian.y(), dz = t.z() - guardian.z();
            return dx * dx + dy * dy + dz * dz > 9.0;
        }
    }

    static final class GuardianMoveControl extends MoveControl {
        private final Guardian guardian;

        GuardianMoveControl(Guardian guardian) {
            super(guardian);
            this.guardian = guardian;
        }

        @Override
        public void tick() {
            if (operation == Operation.MOVE_TO && !guardian.getNavigation().isDone()) {
                double dx = wantedX - guardian.x(), dy = wantedY - guardian.y(), dz = wantedZ - guardian.z();
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double nx = dx / len, ny = dy / len, nz = dz / len;
                float target = (float) (Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
                guardian.setYRot(rotlerp(guardian.yRot(), target, 90.0F));
                guardian.setYBodyRot(guardian.yRot());
                float speed = (float) (speedModifier * guardian.attributeValue(Attribute.MOVEMENT_SPEED));
                float s = guardian.getSpeed() + 0.125F * (speed - guardian.getSpeed());
                guardian.setSpeed(s);
                double a = Math.sin((guardian.tickCount + guardian.id()) * 0.5) * 0.05;
                double cos = Math.cos(guardian.yRot() * (float) (Math.PI / 180.0));
                double sin = Math.sin(guardian.yRot() * (float) (Math.PI / 180.0));
                double b = Math.sin((guardian.tickCount + guardian.id()) * 0.75) * 0.05;
                guardian.setDeltaMovement(guardian.vx() + a * cos, guardian.vy() + b * (sin + cos) * 0.25 + s * ny * 0.1, guardian.vz() + a * sin);
                var look = guardian.getLookControl();
                double lx = guardian.x() + nx * 2.0, ly = guardian.eyeY() + ny / len, lz = guardian.z() + nz * 2.0;
                double cx = look.getWantedX(), cy = look.getWantedY(), cz = look.getWantedZ();
                if (!look.isLookingAtTarget()) {
                    cx = lx;
                    cy = ly;
                    cz = lz;
                }
                guardian.getLookControl().setLookAt(cx + 0.125 * (lx - cx), cy + 0.125 * (ly - cy), cz + 0.125 * (lz - cz), 10.0F, 40.0F);
                guardian.setMoving(true);
            } else {
                guardian.setSpeed(0.0F);
                guardian.setMoving(false);
            }
        }
    }

    static final class GuardianAttackGoal extends Goal {
        private final Guardian guardian;
        private int attackTime;
        private final boolean elder;

        GuardianAttackGoal(Guardian guardian) {
            this.guardian = guardian;
            this.elder = guardian instanceof ElderGuardian;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Targetable t = guardian.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && (elder || guardian.getTarget() != null && distSqr(guardian.getTarget()) > 9.0);
        }

        private double distSqr(Targetable t) {
            double dx = guardian.x() - t.x(), dy = guardian.y() - t.y(), dz = guardian.z() - t.z();
            return dx * dx + dy * dy + dz * dz;
        }

        @Override
        public void start() {
            attackTime = -10;
            guardian.getNavigation().stop();
            Targetable t = guardian.getTarget();
            if (t != null) guardian.getLookControl().setLookAtPlayerOrEntity(t, 90.0F, 90.0F);
            guardian.hasImpulse = true;
        }

        @Override
        public void stop() {
            guardian.setActiveAttackTarget(0);
            guardian.setTarget(null);
            guardian.randomStrollGoal.trigger();
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            Targetable t = guardian.getTarget();
            if (t == null) return;
            guardian.getNavigation().stop();
            guardian.getLookControl().setLookAtPlayerOrEntity(t, 90.0F, 90.0F);
            if (!guardian.hasLineOfSight(t)) {
                guardian.setTarget(null);
            } else {
                attackTime++;
                if (attackTime == 0) {
                    guardian.setActiveAttackTarget(t.id());
                    if (!guardian.isSilent()) guardian.broadcastEntityEvent((byte) 21);
                } else if (attackTime >= guardian.getAttackDuration()) {
                    float dmg = 1.0F;
                    if (guardian.level().difficulty() == 3) dmg += 2.0F;
                    if (elder) dmg += 2.0F;
                    t.hurtByMob(new Damage("indirect_magic", false, false, false), dmg, guardian);
                    guardian.doHurtTarget(t);
                    guardian.setTarget(null);
                }
            }
        }
    }

    @Override
    public boolean checkSpawnObstruction(ChunkLevel level) { return level.isUnobstructed(org.domi.furnace.server.entity.spawn.NaturalSpawner.shapeOf(boundingBox())); }
}
