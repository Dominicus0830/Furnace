// 바닐라 Vex: 소환사가 부르는 날아다니는 작은 몹. 벽을 통과하고(noPhysics), 수명이 다하면 굶주림 피해를 받는다. 소환주와 같은 목표를 쫓는다.
package org.domi.furnace.server.entity;

import java.util.EnumSet;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.control.VexMoveControl;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Vex extends Monster {
    public static final int DATA_FLAGS_ID = 16;
    private static final int FLAG_IS_CHARGING = 1;
    private Mob owner;
    private Pos boundOrigin;
    private boolean hasLimitedLife;
    private int limitedLifeTicks;

    public Vex(ChunkLevel level, int id) {
        super(EntityType.VEX, level, id);
        moveControl = new VexMoveControl(this);
        xpReward = 3;
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 14.0).add(Attribute.ATTACK_DAMAGE, 4.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_FLAGS_ID, SetEntityData.SERIALIZER_BYTE, (byte) 0);
    }

    @Override
    public void move(int mover, double dx, double dy, double dz) {
        super.move(mover, dx, dy, dz);
        checkInsideBlocks();
    }

    @Override
    public void tick() {
        noPhysics = true;
        super.tick();
        noPhysics = false;
        setNoGravity(true);
        if (hasLimitedLife && --limitedLifeTicks <= 0) {
            limitedLifeTicks = 20;
            hurt(new Damage("starve", false, false, false), 1.0F);
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(4, new VexChargeAttackGoal());
        goalSelector.addGoal(8, new VexRandomMoveGoal());
        goalSelector.addGoal(9, new InteractGoal(this, 3.0F, 1.0F));
        goalSelector.addGoal(10, new LookAtMobGoal(this, Mob.class, 8.0F, 0.02F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        targetSelector.addGoal(2, new VexCopyOwnerTargetGoal(this));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Player.class, true));
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.tags().containsKey("BoundX")) boundOrigin = new Pos(tag.getIntOr0("BoundX"), tag.getIntOr0("BoundY"), tag.getIntOr0("BoundZ"));
        if (tag.tags().containsKey("LifeTicks")) setLimitedLife(tag.getIntOr0("LifeTicks"));
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        if (boundOrigin != null) {
            tag.putInt("BoundX", boundOrigin.x());
            tag.putInt("BoundY", boundOrigin.y());
            tag.putInt("BoundZ", boundOrigin.z());
        }
        if (hasLimitedLife) tag.putInt("LifeTicks", limitedLifeTicks);
    }

    public Mob getOwner() { return owner; }

    public void setOwner(Mob m) { owner = m; }

    public Pos getBoundOrigin() { return boundOrigin; }

    public void setBoundOrigin(Pos p) { boundOrigin = p; }

    public void setLimitedLife(int ticks) {
        hasLimitedLife = true;
        limitedLifeTicks = ticks;
    }

    private boolean getVexFlag(int f) { return ((Byte) entityData.get(DATA_FLAGS_ID) & f) != 0; }

    private void setVexFlag(int f, boolean on) {
        int b = (Byte) entityData.get(DATA_FLAGS_ID);
        entityData.set(DATA_FLAGS_ID, (byte) ((on ? b | f : b & ~f) & 0xFF));
    }

    public boolean isCharging() { return getVexFlag(FLAG_IS_CHARGING); }

    public void setIsCharging(boolean v) { setVexFlag(FLAG_IS_CHARGING, v); }

    @Override public float getLightLevelDependentMagicValue() { return 1.0F; }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        populateDefaultEquipmentSlots(r, difficulty);
        populateDefaultEquipmentEnchantments(r, difficulty);
        super.finalizeSpawn(r, difficulty);
    }

    @Override
    protected void populateDefaultEquipmentSlots(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, stackOf("minecraft:iron_sword"));
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    @Override public String getAmbientSound() { return "entity.vex.ambient"; }

    @Override protected String getDeathSound() { return "entity.vex.death"; }

    @Override protected String getHurtSound(Damage source) { return "entity.vex.hurt"; }

    private static double distSqr(Entity a, Targetable b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
        return dx * dx + dy * dy + dz * dz;
    }

    class VexChargeAttackGoal extends Goal {
        VexChargeAttackGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            Targetable t = getTarget();
            return t != null && t.isAlive() && !getMoveControl().hasWanted() && random.nextInt(reducedTickDelay(7)) == 0 && distSqr(Vex.this, t) > 4.0;
        }

        @Override
        public boolean canContinueToUse() {
            return getMoveControl().hasWanted() && isCharging() && getTarget() != null && getTarget().isAlive();
        }

        @Override
        public void start() {
            Targetable t = getTarget();
            if (t != null) getMoveControl().setWantedPosition(t.x(), t.eyeY(), t.z(), 1.0);
            setIsCharging(true);
            playSound("entity.vex.charge", 1.0F, 1.0F);
        }

        @Override public void stop() { setIsCharging(false); }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            Targetable t = getTarget();
            if (t != null) {
                if (boundingBox().intersects(t.boundingBox())) {
                    doHurtTarget(t);
                    setIsCharging(false);
                } else if (distSqr(Vex.this, t) < 9.0) {
                    getMoveControl().setWantedPosition(t.x(), t.eyeY(), t.z(), 1.0);
                }
            }
        }
    }

    class VexRandomMoveGoal extends Goal {
        VexRandomMoveGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override public boolean canUse() { return !getMoveControl().hasWanted() && random.nextInt(reducedTickDelay(7)) == 0; }

        @Override public boolean canContinueToUse() { return false; }

        @Override
        public void tick() {
            Pos origin = getBoundOrigin() != null ? getBoundOrigin() : blockPosition();
            for (int i = 0; i < 3; i++) {
                Pos p = new Pos(origin.x() + random.nextInt(15) - 7, origin.y() + random.nextInt(11) - 5, origin.z() + random.nextInt(15) - 7);
                if (level.isEmptyBlock(p)) {
                    getMoveControl().setWantedPosition(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5, 0.25);
                    if (getTarget() == null) getLookControl().setLookAt(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5, 180.0F, 20.0F);
                    break;
                }
            }
        }
    }

    static class VexCopyOwnerTargetGoal extends TargetGoal {
        private final TargetingConditions copyOwnerTargeting = TargetingConditions.forNonCombat().ignoreLineOfSight().ignoreInvisibilityTesting();
        private final Vex vex;

        VexCopyOwnerTargetGoal(Vex vex) {
            super(vex, false);
            this.vex = vex;
        }

        @Override
        public boolean canUse() {
            return vex.owner != null && vex.owner.getTarget() != null && canAttack(vex.owner.getTarget(), copyOwnerTargeting);
        }

        @Override
        public void start() {
            vex.setTarget(vex.owner.getTarget());
            super.start();
        }
    }
}
