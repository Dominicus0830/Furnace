// 바닐라 Phantom: 밤에 하늘에서 나타나는 날아다니는 몹. 기준점 위를 맴돌다가 플레이어를 향해 급강하해 공격한다. 크기(size)에 따라 몸집과 공격력이 커진다. 낮에 불탄다. 고양이를 무서워한다.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.control.BodyRotationControl;
import org.domi.furnace.server.entity.ai.control.LookControl;
import org.domi.furnace.server.entity.ai.control.MoveControl;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Phantom extends FlyingMob implements Enemy {

    @Override
    protected boolean shouldDespawnInPeaceful() { return true; }

    public static final int DATA_ID_SIZE = 16;
    private static final TargetingConditions DEFAULT_TARGETING = TargetingConditions.forCombat();
    private double[] moveTargetPoint = {0.0, 0.0, 0.0};
    private Pos anchorPoint = new Pos(0, 0, 0);
    AttackPhase attackPhase = AttackPhase.CIRCLE;

    enum AttackPhase { CIRCLE, SWOOP }

    public Phantom(ChunkLevel level, int id) {
        super(EntityType.PHANTOM, level, id);
        this.xpReward = 5;
        this.moveControl = new PhantomMoveControl(this);
        this.lookControl = new PhantomLookControl(this);
        this.bodyRotationControl = new PhantomBodyRotationControl(this);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new PhantomAttackStrategyGoal());
        goalSelector.addGoal(2, new PhantomSweepAttackGoal());
        goalSelector.addGoal(3, new PhantomCircleAroundAnchorGoal());
        targetSelector.addGoal(1, new PhantomAttackPlayerTargetGoal());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ID_SIZE, SetEntityData.SERIALIZER_INT, 0);
    }

    public void setPhantomSize(int size) {
        entityData.set(DATA_ID_SIZE, Math.clamp(size, 0, 64));
        updateAttributes();
    }

    private void updateAttributes() {
        float s = 1.0F + 0.15F * getPhantomSize();
        EntityType t = type();
        setDimensions(t.width() * s, t.height() * s, t.eyeHeight() * s);
        attributes().get(Attribute.ATTACK_DAMAGE).setBaseValue(6 + getPhantomSize());
    }

    public int getPhantomSize() { return (Integer) entityData.get(DATA_ID_SIZE); }

    @Override
    protected void aiStep() {
        if (isAlive() && isSunBurnTick()) igniteForSeconds(8.0F);
        super.aiStep();
    }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        anchorPoint = blockPosition().relative(org.domi.furnace.server.block.Dir.UP, 5);
        setPhantomSize(0);
        super.finalizeSpawn(r, difficulty);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("AX")) anchorPoint = new Pos(tag.getIntOr0("AX"), tag.getIntOr0("AY"), tag.getIntOr0("AZ"));
        setPhantomSize(tag.getIntOr0("Size"));
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("AX", anchorPoint.x());
        tag.putInt("AY", anchorPoint.y());
        tag.putInt("AZ", anchorPoint.z());
        tag.putInt("Size", getPhantomSize());
    }

    @Override
    protected float getSoundVolume() { return 1.0F; }

    @Override public String getAmbientSound() { return "entity.phantom.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.phantom.hurt"; }

    @Override protected String getDeathSound() { return "entity.phantom.death"; }

    private boolean canAttack(Targetable t, TargetingConditions c) { return c.test(this, t); }

    // ------------------------------------------------------------------ 제어

    static final class PhantomMoveControl extends MoveControl {
        private final Phantom phantom;
        private float speed = 0.1F;

        PhantomMoveControl(Phantom phantom) {
            super(phantom);
            this.phantom = phantom;
        }

        @Override
        public void tick() {
            if (phantom.horizontalCollision) {
                phantom.setYRot(phantom.yRot() + 180.0F);
                speed = 0.1F;
            }
            double dx = phantom.moveTargetPoint[0] - phantom.x();
            double dy = phantom.moveTargetPoint[1] - phantom.y();
            double dz = phantom.moveTargetPoint[2] - phantom.z();
            double h = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(h) > 1.0E-5F) {
                double d = 1.0 - Math.abs(dy * 0.7F) / h;
                dx *= d;
                dz *= d;
                h = Math.sqrt(dx * dx + dz * dz);
                double len = Math.sqrt(dx * dx + dz * dz + dy * dy);
                float oldYRot = phantom.yRot();
                float angle = (float) Mth.atan2(dz, dx);
                float cur = Mth.wrapDegrees(phantom.yRot() + 90.0F);
                float want = Mth.wrapDegrees(angle * (180.0F / (float) Math.PI));
                phantom.setYRot(Mth.approachDegrees(cur, want, 4.0F) - 90.0F);
                phantom.setYBodyRot(phantom.yRot());
                if (Mth.degreesDifferenceAbs(oldYRot, phantom.yRot()) < 3.0F) speed = Mth.approach(speed, 1.8F, 0.005F * (1.8F / speed));
                else speed = Mth.approach(speed, 0.2F, 0.025F);
                float xr = (float) (-(Mth.atan2(-dy, h) * 180.0F / (float) Math.PI));
                phantom.setXRot(xr);
                float yaw = phantom.yRot() + 90.0F;
                double mx = speed * Mth.cos(yaw * (float) (Math.PI / 180.0)) * Math.abs(dx / len);
                double mz = speed * Mth.sin(yaw * (float) (Math.PI / 180.0)) * Math.abs(dz / len);
                double my = speed * Mth.sin(xr * (float) (Math.PI / 180.0)) * Math.abs(dy / len);
                double vx = phantom.vx(), vy = phantom.vy(), vz = phantom.vz();
                double ax = mx - vx, ay = my - vy, az = mz - vz;
                phantom.setDeltaMovement(vx + ax * 0.2, vy + ay * 0.2, vz + az * 0.2);
            }
        }
    }

    static final class PhantomLookControl extends LookControl {
        PhantomLookControl(Mob mob) { super(mob); }

        @Override public void tick() { }
    }

    static final class PhantomBodyRotationControl extends BodyRotationControl {
        private final Phantom phantom;

        PhantomBodyRotationControl(Phantom phantom) {
            super(phantom);
            this.phantom = phantom;
        }

        @Override
        public void clientTick() {
            phantom.setYHeadRot(phantom.yBodyRot());
            phantom.setYBodyRot(phantom.yRot());
        }
    }

    // ------------------------------------------------------------------ 목표

    private abstract class PhantomMoveTargetGoal extends Goal {
        PhantomMoveTargetGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        protected boolean touchingTarget() {
            double dx = moveTargetPoint[0] - x(), dy = moveTargetPoint[1] - y(), dz = moveTargetPoint[2] - z();
            return dx * dx + dy * dy + dz * dz < 4.0;
        }
    }

    private final class PhantomAttackStrategyGoal extends Goal {
        private int nextSweepTick;

        @Override
        public boolean canUse() {
            Targetable t = getTarget();
            return t != null && canAttack(t, DEFAULT_TARGETING);
        }

        @Override
        public void start() {
            nextSweepTick = adjustedTickDelay(10);
            attackPhase = AttackPhase.CIRCLE;
            setAnchorAboveTarget();
        }

        @Override
        public void stop() {
            int hx = anchorPoint.x(), hz = anchorPoint.z();
            Pos top = new Pos(hx, level.getMotionBlockingHeight(hx, hz), hz);
            anchorPoint = top.offset(0, 10 + random.nextInt(20), 0);
        }

        @Override
        public void tick() {
            if (attackPhase == AttackPhase.CIRCLE) {
                nextSweepTick--;
                if (nextSweepTick <= 0) {
                    attackPhase = AttackPhase.SWOOP;
                    setAnchorAboveTarget();
                    nextSweepTick = adjustedTickDelay((8 + random.nextInt(4)) * 20);
                    playSound("entity.phantom.swoop", 10.0F, 0.95F + random.nextFloat() * 0.1F);
                }
            }
        }

        private void setAnchorAboveTarget() {
            Pos tp = getTarget().blockPosition();
            anchorPoint = tp.offset(0, 20 + random.nextInt(20), 0);
            if (anchorPoint.y() < level.seaLevel()) anchorPoint = new Pos(anchorPoint.x(), level.seaLevel() + 1, anchorPoint.z());
        }
    }

    private final class PhantomSweepAttackGoal extends PhantomMoveTargetGoal {
        private boolean isScaredOfCat;
        private int catSearchTick;

        PhantomSweepAttackGoal() { }

        @Override
        public boolean canUse() { return getTarget() != null && attackPhase == AttackPhase.SWOOP; }

        @Override
        public boolean canContinueToUse() {
            Targetable t = getTarget();
            if (t == null) return false;
            if (!t.isAlive()) return false;
            if (t instanceof Player p && (p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR || p.gameMode() == org.domi.furnace.server.world.GameMode.CREATIVE)) return false;
            if (!canUse()) return false;
            if (tickCount > catSearchTick) {
                catSearchTick = tickCount + 20;
                List<Cat> cats = level.getEntitiesOfClass(Cat.class, boundingBox().inflate(16.0), c -> c.isAlive());
                for (Cat c : cats) c.hiss();
                isScaredOfCat = !cats.isEmpty();
            }
            return !isScaredOfCat;
        }

        @Override
        public void stop() {
            setTarget(null);
            attackPhase = AttackPhase.CIRCLE;
        }

        @Override
        public void tick() {
            Targetable t = getTarget();
            if (t != null) {
                moveTargetPoint = new double[] {t.x(), t.y() + t.bbHeight() * 0.5, t.z()};
                if (boundingBox().inflate(0.2F).intersects(t.boundingBox())) {
                    doHurtTarget(t);
                    attackPhase = AttackPhase.CIRCLE;
                    if (!isSilent()) level.levelEvent(null, 1039, blockPosition(), 0);
                } else if (horizontalCollision || hurtTime > 0) {
                    attackPhase = AttackPhase.CIRCLE;
                }
            }
        }
    }

    private final class PhantomCircleAroundAnchorGoal extends PhantomMoveTargetGoal {
        private float angle;
        private float distance;
        private float height;
        private float clockwise;

        @Override
        public boolean canUse() { return getTarget() == null || attackPhase == AttackPhase.CIRCLE; }

        @Override
        public void start() {
            distance = 5.0F + random.nextFloat() * 10.0F;
            height = -4.0F + random.nextFloat() * 9.0F;
            clockwise = random.nextBoolean() ? 1.0F : -1.0F;
            selectNext();
        }

        @Override
        public void tick() {
            if (random.nextInt(adjustedTickDelay(350)) == 0) height = -4.0F + random.nextFloat() * 9.0F;
            if (random.nextInt(adjustedTickDelay(250)) == 0) {
                distance++;
                if (distance > 15.0F) {
                    distance = 5.0F;
                    clockwise = -clockwise;
                }
            }
            if (random.nextInt(adjustedTickDelay(450)) == 0) {
                angle = random.nextFloat() * 2.0F * (float) Math.PI;
                selectNext();
            }
            if (touchingTarget()) selectNext();
            if (moveTargetPoint[1] < y() && !level.isEmptyBlock(blockPosition().below())) {
                height = Math.max(1.0F, height);
                selectNext();
            }
            if (moveTargetPoint[1] > y() && !level.isEmptyBlock(blockPosition().above())) {
                height = Math.min(-1.0F, height);
                selectNext();
            }
        }

        private void selectNext() {
            if (anchorPoint.x() == 0 && anchorPoint.y() == 0 && anchorPoint.z() == 0) anchorPoint = blockPosition();
            angle += clockwise * 15.0F * (float) (Math.PI / 180.0);
            moveTargetPoint = new double[] {anchorPoint.x() + distance * Mth.cos(angle), anchorPoint.y() + (-4.0F + height), anchorPoint.z() + distance * Mth.sin(angle)};
        }
    }

    private final class PhantomAttackPlayerTargetGoal extends Goal {
        private final TargetingConditions attackTargeting = TargetingConditions.forCombat().range(64.0);
        private int nextScanTick = reducedTickDelay(20);

        @Override
        public boolean canUse() {
            if (nextScanTick > 0) {
                nextScanTick--;
                return false;
            }
            nextScanTick = reducedTickDelay(60);
            List<Player> players = new ArrayList<>();
            level.forEachPlayerIn(boundingBox().inflate(16.0, 64.0, 16.0), p -> {
                if (attackTargeting.test(Phantom.this, p)) players.add(p);
            });
            if (!players.isEmpty()) {
                players.sort(Comparator.comparingDouble(Player::y).reversed());
                for (Player p : players) {
                    if (canAttack(p, DEFAULT_TARGETING)) {
                        setTarget(p);
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            Targetable t = getTarget();
            return t != null && canAttack(t, DEFAULT_TARGETING);
        }
    }
}
