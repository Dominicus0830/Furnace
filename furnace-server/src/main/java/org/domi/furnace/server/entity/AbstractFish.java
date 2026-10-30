// 바닐라 AbstractFish: 물고기 공통 — 물 길찾기, 헤엄치는 이동 제어, 물 밖에서 파닥이기, 겁먹으면 도망(플레이어 8칸), 양동이 속 여부(FromBucket).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.control.MoveControl;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.entity.ai.goal.AvoidEntityGoal;
import org.domi.furnace.server.entity.ai.goal.PanicGoal;
import org.domi.furnace.server.entity.ai.goal.RandomSwimmingGoal;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.ai.navigation.WaterBoundPathNavigation;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class AbstractFish extends WaterAnimal implements Bucketable {
    public static final int DATA_FROM_BUCKET = 16;

    protected AbstractFish(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        this.moveControl = new FishMoveControl(this);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 3.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_FROM_BUCKET, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    @Override public boolean fromBucket() { return (Boolean) entityData.get(DATA_FROM_BUCKET); }

    @Override public void setFromBucket(boolean v) { entityData.set(DATA_FROM_BUCKET, v); }

    /** AbstractFish.requiresCustomPersistence: 양동이에서 나온 물고기는 사라지지 않는다. */
    @Override protected boolean requiresCustomPersistence() { return super.requiresCustomPersistence() || fromBucket(); }

    @Override public String getPickupSound() { return "minecraft:item.bucket.fill_fish"; }

    /** AbstractFish.mobInteract: 물 양동이면 잡고, 아니면 기본. */
    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        return Bucketable.bucketMobPickup(p, hand, held, this, region) || super.interact(p, hand, held, region);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) { return !fromBucket() && !hasCustomName(); }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new PanicGoal(this, 1.25));
        goalSelector.addGoal(2, new AvoidEntityGoal(this, Player.class, t -> true, 8.0F, 1.6, 1.4,
                t -> !(t instanceof Player p) || p.gameMode() != org.domi.furnace.server.world.GameMode.SPECTATOR));
        goalSelector.addGoal(4, new FishSwimGoal(this));
    }

    @Override
    protected PathNavigation createNavigation(ChunkLevel level) { return new WaterBoundPathNavigation(this, level); }

    @Override
    protected void travel(float inX, float inY, float inZ) {
        if (isEffectiveAi() && isInWater()) {
            moveRelative(0.01F, inX, inY, inZ);
            move(MOVER_SELF, vx(), vy(), vz());
            setDeltaMovement(vx() * 0.9, vy() * 0.9, vz() * 0.9);
            if (getTarget() == null) setDeltaMovement(vx(), vy() - 0.005, vz());
        } else {
            super.travel(inX, inY, inZ);
        }
    }

    @Override
    protected void aiStep() {
        if (!isInWater() && onGround() && verticalCollision) {
            setDeltaMovement(vx() + (random.nextFloat() * 2.0F - 1.0F) * 0.05F, vy() + 0.4F, vz() + (random.nextFloat() * 2.0F - 1.0F) * 0.05F);
            setOnGround(false);
            hasImpulse = true;
            playSound(getFlopSound(), getSoundVolume(), getVoicePitch());
        }
        super.aiStep();
    }

    protected boolean canRandomSwim() { return true; }

    protected abstract String getFlopSound();

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("FromBucket", fromBucket());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setFromBucket(tag.getBoolean("FromBucket"));
    }

    static final class FishMoveControl extends MoveControl {
        private final AbstractFish fish;

        FishMoveControl(AbstractFish fish) {
            super(fish);
            this.fish = fish;
        }

        @Override
        public void tick() {
            if (fish.isEyeInWater()) fish.setDeltaMovement(fish.vx(), fish.vy() + 0.005, fish.vz());
            if (operation == Operation.MOVE_TO && !fish.getNavigation().isDone()) {
                float speed = (float) (speedModifier * fish.attributeValue(Attribute.MOVEMENT_SPEED));
                fish.setSpeed(fish.getSpeed() + 0.125F * (speed - fish.getSpeed()));
                double dx = wantedX - fish.x(), dy = wantedY - fish.y(), dz = wantedZ - fish.z();
                if (dy != 0.0) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    fish.setDeltaMovement(fish.vx(), fish.vy() + fish.getSpeed() * (dy / d) * 0.1, fish.vz());
                }
                if (dx != 0.0 || dz != 0.0) {
                    float target = (float) (Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
                    fish.setYRot(rotlerp(fish.yRot(), target, 90.0F));
                    fish.yBodyRot = fish.yRot();
                }
            } else {
                fish.setSpeed(0.0F);
            }
        }
    }

    static final class FishSwimGoal extends RandomSwimmingGoal {
        private final AbstractFish fish;

        FishSwimGoal(AbstractFish fish) {
            super(fish, 1.0, 40);
            this.fish = fish;
        }

        @Override
        public boolean canUse() { return fish.canRandomSwim() && super.canUse(); }
    }
    @Override public int getMaxSpawnClusterSize() { return 8; }
}
