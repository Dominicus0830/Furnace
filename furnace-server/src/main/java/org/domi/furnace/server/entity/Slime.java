// 바닐라 Slime: 크기(1~127)에 따라 체력 size², 속도 0.2+0.1×size, 공격력 size. 통통 뛰어 다니며 닿으면 때리고, 죽으면 작게 2~4마리로 갈라진다.
// TODO: 철 골렘에게도 피해, 입자.
package org.domi.furnace.server.entity;

import java.util.EnumSet;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.control.MoveControl;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.entity.ai.goal.NearestAttackableTargetGoal;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Slime extends Mob implements Enemy {
    public static final int DATA_SIZE = 16;
    private boolean wasOnGround;

    public Slime(ChunkLevel level, int id) { this(EntityType.SLIME, level, id); }

    protected Slime(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        this.moveControl = new SlimeMoveControl(this);
        applySize();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SIZE, SetEntityData.SERIALIZER_INT, 1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new SlimeFloatGoal(this));
        goalSelector.addGoal(2, new SlimeAttackGoal(this));
        goalSelector.addGoal(3, new SlimeRandomDirectionGoal(this));
        goalSelector.addGoal(5, new SlimeKeepOnJumpingGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal(this, Player.class, 10, true, false, t -> Math.abs(t.y() - y()) <= 4.0));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, IronGolem.class, true));
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.ATTACK_DAMAGE, 2.0);   // Monster.createMonsterAttributes (Slime 은 Mob 이지만 Monster 속성 사용)
    }

    public int getSize() { return (Integer) entityData.get(DATA_SIZE); }

    @Override
    protected boolean shouldDespawnInPeaceful() { return getSize() > 0; }

    private void applySize() {
        int size = getSize();
        setDimensions(0.52F * size, 0.52F * size, 0.325F * size);
    }

    /** Slime.setSize(size, updateHealth). */
    public void setSize(int size, boolean updateHealth) {
        int s = Math.clamp(size, 1, 127);
        entityData.set(DATA_SIZE, s);
        applySize();
        attributes().get(Attribute.MAX_HEALTH).setBaseValue((double) s * s);
        attributes().get(Attribute.MOVEMENT_SPEED).setBaseValue(0.2F + 0.1F * s);
        attributes().get(Attribute.ATTACK_DAMAGE).setBaseValue(s);
        onSizeSet(s);
        if (updateHealth) setHealth(maxHealth());
        xpReward = s;
    }

    protected void onSizeSet(int size) {}

    public boolean isTiny() { return getSize() <= 1; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Size", getSize() - 1);
        tag.putBoolean("wasOnGround", wasOnGround);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        setSize(tag.getIntOr0("Size") + 1, false);
        super.readAdditionalSaveData(tag);
        wasOnGround = tag.getBoolean("wasOnGround");
    }

    @Override public String soundSource() { return "hostile"; }

    @Override
    public void tick() {
        super.tick();
        if (onGround() && !wasOnGround) {
            playSound(getSquishSound(), getSoundVolume(), ((random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F) / 0.8F);
        }
        wasOnGround = onGround();
    }

    protected int getJumpDelay() { return random.nextInt(20) + 10; }

    @Override
    public void remove(RemovalReason reason) {
        int size = getSize();
        if (level != null && size > 1 && isDeadOrDying()) {
            float w = bbWidth();
            float half = w / 2.0F;
            int newSize = size / 2;
            int count = 2 + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                float ox = (i % 2 - 0.5F) * half;
                float oz = (i / 2 - 0.5F) * half;
                Slime child = (Slime) Entities.create(level, type.name());
                if (child != null) {
                    if (persistenceRequired) child.persistenceRequired = true;
                    child.setSize(newSize, true);
                    child.moveTo(x() + ox, y() + 0.5, z() + oz, random.nextFloat() * 360.0F, 0.0F);
                    level.addFreshEntity(child.spawnReason("SLIME_SPLIT"));
                }
            }
        }
        super.remove(reason);
    }

    @Override
    public void playerTouch(Player p) {
        if (isDealsDamage()) dealDamage(p);
    }

    protected void dealDamage(Targetable t) {
        if (isAlive() && isWithinMeleeAttackRange(t) && hasLineOfSight(t)) {
            if (t.hurtByMob(new Entity.Damage("mob_attack", false, false, false), getAttackDamage(), this)) {
                playSound("entity.slime.attack", 1.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
            }
        }
    }

    protected boolean isDealsDamage() { return !isTiny() && isEffectiveAi(); }

    protected float getAttackDamage() { return (float) attributeValue(Attribute.ATTACK_DAMAGE); }

    @Override
    protected String getHurtSound(Damage source) { return isTiny() ? "entity.slime.hurt_small" : "entity.slime.hurt"; }

    @Override
    protected String getDeathSound() { return isTiny() ? "entity.slime.death_small" : "entity.slime.death"; }

    protected String getSquishSound() { return isTiny() ? "entity.slime.squish_small" : "entity.slime.squish"; }

    protected String getJumpSound() { return isTiny() ? "entity.slime.jump_small" : "entity.slime.jump"; }

    @Override protected float getSoundVolume() { return 0.4F * getSize(); }

    @Override public int getMaxHeadXRot() { return 0; }

    @Override public int getMaxHeadYRot() { return 0; }

    protected boolean doPlayJumpSound() { return getSize() > 0; }

    @Override
    public void jumpFromGround() {
        setDeltaMovement(vx(), getJumpPower(), vz());
        hasImpulse = true;
    }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        org.domi.furnace.server.block.RandomSource lr = level.random();
        int i = lr.nextInt(3);
        if (i < 2 && lr.nextFloat() < 0.5F * difficulty.specialMultiplier()) i++;
        setSize(1 << i, true);
        super.finalizeSpawn(r, difficulty);
    }

    // ------------------------------------------------------------------ 제어·목표

    static final class SlimeMoveControl extends MoveControl {
        private float yRot;
        private int jumpDelay;
        private final Slime slime;
        private boolean isAggressive;

        SlimeMoveControl(Slime slime) {
            super(slime);
            this.slime = slime;
            this.yRot = 180.0F * slime.yRot() / (float) Math.PI;
        }

        void setDirection(float yRot, boolean aggressive) {
            this.yRot = yRot;
            this.isAggressive = aggressive;
        }

        void setWantedMovement(double speed) {
            this.speedModifier = speed;
            this.operation = Operation.MOVE_TO;
        }

        @Override
        public void tick() {
            slime.setYRot(rotlerp(slime.yRot(), yRot, 90.0F));
            slime.setYHeadRot(slime.yRot());
            slime.setYBodyRot(slime.yRot());
            if (operation != Operation.MOVE_TO) {
                slime.setZza(0.0F);
            } else {
                operation = Operation.WAIT;
                if (slime.onGround()) {
                    slime.setSpeed((float) (speedModifier * slime.attributeValue(Attribute.MOVEMENT_SPEED)));
                    if (jumpDelay-- <= 0) {
                        jumpDelay = slime.getJumpDelay();
                        if (isAggressive) jumpDelay /= 3;
                        slime.getJumpControl().jump();
                        if (slime.doPlayJumpSound()) {
                            slime.playSound(slime.getJumpSound(), slime.getSoundVolume(), slime.slimePitch());
                        }
                    } else {
                        slime.setXxa(0.0F);
                        slime.setZza(0.0F);
                        slime.setSpeed(0.0F);
                    }
                } else {
                    slime.setSpeed((float) (speedModifier * slime.attributeValue(Attribute.MOVEMENT_SPEED)));
                }
            }
        }
    }

    float slimePitch() {
        float f = isTiny() ? 1.4F : 0.8F;
        return ((random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F) * f;
    }

    static final class SlimeAttackGoal extends Goal {
        private final Slime slime;
        private int growTiredTimer;

        SlimeAttackGoal(Slime slime) {
            this.slime = slime;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Targetable t = slime.getTarget();
            if (t == null) return false;
            return !slime.canAttackType(t) ? false : slime.getMoveControl() instanceof SlimeMoveControl;
        }

        @Override
        public void start() {
            growTiredTimer = reducedTickDelay(300);
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            Targetable t = slime.getTarget();
            if (t == null) return false;
            return !slime.canAttackType(t) ? false : --growTiredTimer > 0;
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            Targetable t = slime.getTarget();
            if (t != null) slime.faceTarget(t, 10.0F, 10.0F);
            if (slime.getMoveControl() instanceof SlimeMoveControl c) c.setDirection(slime.yRot(), slime.isDealsDamage());
        }
    }

    static final class SlimeFloatGoal extends Goal {
        private final Slime slime;

        SlimeFloatGoal(Slime slime) {
            this.slime = slime;
            setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
            slime.getNavigation().setCanFloat(true);
        }

        @Override
        public boolean canUse() { return (slime.isInWater() || slime.isInLava()) && slime.getMoveControl() instanceof SlimeMoveControl; }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            if (slime.random().nextFloat() < 0.8F) slime.getJumpControl().jump();
            if (slime.getMoveControl() instanceof SlimeMoveControl c) c.setWantedMovement(1.2);
        }
    }

    static final class SlimeRandomDirectionGoal extends Goal {
        private final Slime slime;
        private float chosenDegrees;
        private int nextRandomizeTime;

        SlimeRandomDirectionGoal(Slime slime) {
            this.slime = slime;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return slime.getTarget() == null && (slime.onGround() || slime.isInWater() || slime.isInLava() || slime.hasEffect(org.domi.furnace.server.effect.MobEffect.LEVITATION))
                    && slime.getMoveControl() instanceof SlimeMoveControl;
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            if (--nextRandomizeTime <= 0) {
                nextRandomizeTime = adjustedTickDelay(40 + slime.random().nextInt(60));
                chosenDegrees = slime.random().nextInt(360);
            }
            if (slime.getMoveControl() instanceof SlimeMoveControl c) c.setDirection(chosenDegrees, false);
        }
    }

    static final class SlimeKeepOnJumpingGoal extends Goal {
        private final Slime slime;

        SlimeKeepOnJumpingGoal(Slime slime) {
            this.slime = slime;
            setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
        }

        @Override public boolean canUse() { return !slime.isPassenger(); }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            if (slime.getMoveControl() instanceof SlimeMoveControl c) c.setWantedMovement(1.0);
        }
    }
}
