// 바닐라 Ghast: 네더의 거대한 날아다니는 몹. 64칸 안에서 대상이 보이면 큰 불덩이(폭발력 explosionPower)를 쏜다. 가스트가 쏜 불덩이를 플레이어가 되치면 즉사한다.
package org.domi.furnace.server.entity;

import java.util.EnumSet;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Clip;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.control.MoveControl;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.entity.ai.goal.NearestAttackableTargetGoal;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Ghast extends FlyingMob implements Enemy {

    @Override
    protected boolean shouldDespawnInPeaceful() { return true; }

    public static final int DATA_IS_CHARGING = 16;
    private boolean takingPlayerFireball;

    /** cjx.g: 직접 원인이 큰 불덩이이고 때린 쪽이 플레이어다(되친 불덩이). */
    private boolean isFireballFromPlayer(Targetable attacker) { return directCause() instanceof LargeFireball && attacker instanceof Player; }

    /** cjx.isInvulnerableTo: 되친 불덩이는 일반 면역을 건너뛴다(무적 플래그만 본다). */
    @Override
    public boolean isInvulnerableTo(Damage source) {
        if (takingPlayerFireball) return isRemoved() || isInvulnerable() && !source.bypassesInvulnerability();
        return super.isInvulnerableTo(source);
    }

    /** cjx.hurt: 되친 불덩이는 피해 1000(즉사), 그 밖에는 무적이면 거절. */
    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (isFireballFromPlayer(attacker)) {
            takingPlayerFireball = true;
            try {
                super.hurtBy(source, 1000.0F, attacker);
            } finally {
                takingPlayerFireball = false;
            }
            return true;
        }
        return isInvulnerableTo(source) ? false : super.hurtBy(source, amount, attacker);
    }

    private int explosionPower = 1;

    public Ghast(ChunkLevel level, int id) {
        super(EntityType.GHAST, level, id);
        this.xpReward = 5;
        this.moveControl = new GhastMoveControl(this);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 10.0).add(Attribute.FOLLOW_RANGE, 100.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(5, new RandomFloatAroundGoal(this));
        goalSelector.addGoal(7, new GhastLookGoal(this));
        goalSelector.addGoal(7, new GhastShootFireballGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal(this, Player.class, 10, true, false, t -> Math.abs(t.y() - y()) <= 4.0));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_IS_CHARGING, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    public boolean isCharging() { return (Boolean) entityData.get(DATA_IS_CHARGING); }

    void setCharging(boolean v) { entityData.set(DATA_IS_CHARGING, v); }

    public int getExplosionPower() { return explosionPower; }

    @Override public String getAmbientSound() { return "entity.ghast.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.ghast.hurt"; }

    @Override protected String getDeathSound() { return "entity.ghast.death"; }

    @Override
    protected float getSoundVolume() { return 5.0F; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("ExplosionPower", (byte) explosionPower);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ExplosionPower")) explosionPower = tag.getByte("ExplosionPower");
    }

    static final class GhastMoveControl extends MoveControl {
        private final Ghast ghast;
        private int floatDuration;

        GhastMoveControl(Ghast ghast) {
            super(ghast);
            this.ghast = ghast;
        }

        @Override
        public void tick() {
            if (operation == Operation.MOVE_TO) {
                if (floatDuration-- <= 0) {
                    floatDuration += ghast.random().nextInt(5) + 2;
                    double dx = wantedX - ghast.x(), dy = wantedY - ghast.y(), dz = wantedZ - ghast.z();
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double nx = len < 1.0E-5F ? 0.0 : dx / len, ny = len < 1.0E-5F ? 0.0 : dy / len, nz = len < 1.0E-5F ? 0.0 : dz / len;
                    if (canReach(nx, ny, nz, (int) Math.ceil(len))) {
                        ghast.setDeltaMovement(ghast.vx() + nx * 0.1, ghast.vy() + ny * 0.1, ghast.vz() + nz * 0.1);
                    } else {
                        operation = Operation.WAIT;
                    }
                }
            }
        }

        private boolean canReach(double nx, double ny, double nz, int steps) {
            AABB box = ghast.boundingBox();
            for (int i = 1; i < steps; i++) {
                box = box.move(nx, ny, nz);
                if (!ghast.level().noCollision(box)) return false;
            }
            return true;
        }
    }

    static final class RandomFloatAroundGoal extends Goal {
        private final Ghast ghast;

        RandomFloatAroundGoal(Ghast ghast) {
            this.ghast = ghast;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            MoveControl mc = ghast.getMoveControl();
            if (!mc.hasWanted()) return true;
            double dx = mc.getWantedX() - ghast.x(), dy = mc.getWantedY() - ghast.y(), dz = mc.getWantedZ() - ghast.z();
            double d = dx * dx + dy * dy + dz * dz;
            return d < 1.0 || d > 3600.0;
        }

        @Override public boolean canContinueToUse() { return false; }

        @Override
        public void start() {
            var r = ghast.random();
            double x = ghast.x() + (r.nextFloat() * 2.0F - 1.0F) * 16.0F;
            double y = ghast.y() + (r.nextFloat() * 2.0F - 1.0F) * 16.0F;
            double z = ghast.z() + (r.nextFloat() * 2.0F - 1.0F) * 16.0F;
            ghast.getMoveControl().setWantedPosition(x, y, z, 1.0);
        }
    }

    static final class GhastLookGoal extends Goal {
        private final Ghast ghast;

        GhastLookGoal(Ghast ghast) {
            this.ghast = ghast;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override public boolean canUse() { return true; }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            Targetable t = ghast.getTarget();
            if (t == null) {
                ghast.setYRot(-((float) Mth.atan2(ghast.vx(), ghast.vz())) * (180.0F / (float) Math.PI));
                ghast.setYBodyRot(ghast.yRot());
            } else {
                double dx0 = t.x() - ghast.x(), dy0 = t.y() - ghast.y(), dz0 = t.z() - ghast.z();
                if (dx0 * dx0 + dy0 * dy0 + dz0 * dz0 < 4096.0) {
                    ghast.setYRot(-((float) Mth.atan2(dx0, dz0)) * (180.0F / (float) Math.PI));
                    ghast.setYBodyRot(ghast.yRot());
                }
            }
        }
    }

    static final class GhastShootFireballGoal extends Goal {
        private final Ghast ghast;
        private int chargeTime;

        GhastShootFireballGoal(Ghast ghast) { this.ghast = ghast; }

        @Override public boolean canUse() { return ghast.getTarget() != null; }

        @Override public void start() { chargeTime = 0; }

        @Override public void stop() { ghast.setCharging(false); }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            Targetable t = ghast.getTarget();
            if (t != null) {
                double dx0 = t.x() - ghast.x(), dy0 = t.y() - ghast.y(), dz0 = t.z() - ghast.z();
                if (dx0 * dx0 + dy0 * dy0 + dz0 * dz0 < 4096.0 && ghast.hasLineOfSight(t)) {
                    ChunkLevel level = ghast.level();
                    chargeTime++;
                    if (chargeTime == 10 && !ghast.isSilent()) level.levelEvent(null, 1015, ghast.blockPosition(), 0);
                    if (chargeTime == 20) {
                        double[] v = Clip.viewVector(ghast.xRot(), ghast.yRot());
                        double dx = t.x() - (ghast.x() + v[0] * 4.0);
                        double dy = t.y() + t.bbHeight() * 0.5 - (0.5 + ghast.y() + ghast.bbHeight() * 0.5);
                        double dz = t.z() - (ghast.z() + v[2] * 4.0);
                        if (!ghast.isSilent()) level.levelEvent(null, 1016, ghast.blockPosition(), 0);
                        LargeFireball fireball = new LargeFireball(level, level.nextEntityId());
                        fireball.setExplosionPower(ghast.getExplosionPower());
                        fireball.launchFrom(ghast, dx, dy, dz);
                        fireball.setPos(ghast.x() + v[0] * 4.0, ghast.y() + ghast.bbHeight() * 0.5 + 0.5, fireball.z() + v[2] * 4.0);
                        level.addFreshEntity(fireball);
                        chargeTime = -40;
                    }
                } else if (chargeTime > 0) {
                    chargeTime--;
                }
                ghast.setCharging(chargeTime > 10);
            }
        }
    }
    @Override public int getMaxSpawnClusterSize() { return 1; }
}
