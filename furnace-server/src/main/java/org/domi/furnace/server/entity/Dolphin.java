// 바닐라 Dolphin: 물속을 부드럽게 헤엄치고, 물 위로 뛰어오르며, 숨이 모자라면 수면으로 올라온다. 마르면(moistness) 피해를 입는다.
// TODO(보물): 가장 가까운 보물 구조물을 못 찾는 것으로 처리(구조물 위치 탐색 필요). TODO(플레이어와 헤엄): 플레이어 수영 자세 필요.
// TODO(아이템 가지고 놀기·보트 따라가기): 몹 아이템 줍기와 보트가 아직 없다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.entity.ai.control.SmoothSwimmingLookControl;
import org.domi.furnace.server.entity.ai.control.SmoothSwimmingMoveControl;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.ai.navigation.WaterBoundPathNavigation;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Dolphin extends WaterAnimal {
    public static final int DATA_TREASURE_POS = 16, DATA_GOT_FISH = 17, DATA_MOISTNESS = 18;
    public static final int TOTAL_AIR_SUPPLY = 4800;
    private static final int TOTAL_MOISTNESS_LEVEL = 2400;

    public Dolphin(ChunkLevel level, int id) {
        super(EntityType.DOLPHIN, level, id);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
        // setCanPickUpLoot(true) — 줍기는 TODO
    }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        setAirSupply(getMaxAirSupply());
        setXRot(0.0F);
        super.finalizeSpawn(r, difficulty);
    }

    @Override
    protected void handleAirSupply(int previousAir) { }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_TREASURE_POS, SetEntityData.SERIALIZER_BLOCK_POS, new int[] {0, 0, 0});
        entityData.define(DATA_GOT_FISH, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_MOISTNESS, SetEntityData.SERIALIZER_INT, TOTAL_MOISTNESS_LEVEL);
    }

    public void setTreasurePos(Pos p) { entityData.set(DATA_TREASURE_POS, new int[] {p.x(), p.y(), p.z()}); }

    public Pos getTreasurePos() {
        int[] p = (int[]) entityData.get(DATA_TREASURE_POS);
        return new Pos(p[0], p[1], p[2]);
    }

    public boolean gotFish() { return (Boolean) entityData.get(DATA_GOT_FISH); }

    public void setGotFish(boolean v) { entityData.set(DATA_GOT_FISH, v); }

    public int getMoistnessLevel() { return (Integer) entityData.get(DATA_MOISTNESS); }

    public void setMoisntessLevel(int v) { entityData.set(DATA_MOISTNESS, v); }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        Pos p = getTreasurePos();
        tag.putInt("TreasurePosX", p.x());
        tag.putInt("TreasurePosY", p.y());
        tag.putInt("TreasurePosZ", p.z());
        tag.putBoolean("GotFish", gotFish());
        tag.putInt("Moistness", getMoistnessLevel());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        int x = tag.getIntOr0("TreasurePosX"), y = tag.getIntOr0("TreasurePosY"), z = tag.getIntOr0("TreasurePosZ");
        setTreasurePos(new Pos(x, y, z));
        super.readAdditionalSaveData(tag);
        setGotFish(tag.getBoolean("GotFish"));
        setMoisntessLevel(tag.getIntOr0("Moistness"));
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 10.0).add(Attribute.MOVEMENT_SPEED, 1.2F).add(Attribute.ATTACK_DAMAGE, 3.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new BreathAirGoal(this));
        goalSelector.addGoal(0, new TryFindWaterGoal(this));
        goalSelector.addGoal(1, new DolphinSwimToTreasureGoal(this));
        goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0, 10));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, 6.0F));
        goalSelector.addGoal(5, new DolphinJumpGoal(this, 10));
        goalSelector.addGoal(6, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(9, new AvoidEntityGoal(this, Guardian.class, 8.0F, 1.0, 1.0));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Guardian.class).setAlertOthers());
    }

    @Override
    protected PathNavigation createNavigation(ChunkLevel level) { return new WaterBoundPathNavigation(this, level); }

    @Override
    public boolean doHurtTarget(Targetable t) {
        boolean hit = t.hurtByMob(new Damage("mob_attack", false, false, false), (int) attributeValue(Attribute.ATTACK_DAMAGE), this);
        if (hit) {
            playSound("entity.dolphin.attack", 1.0F, 1.0F);
            setLastHurtMob(t);
        }
        return hit;
    }

    @Override
    public int getMaxAirSupply() { return TOTAL_AIR_SUPPLY; }

    @Override
    protected int increaseAirSupply(int air) { return getMaxAirSupply(); }

    @Override
    public int getMaxHeadXRot() { return 1; }

    @Override
    public int getMaxHeadYRot() { return 1; }

    @Override
    public void tick() {
        super.tick();
        if (isNoAi()) {
            setAirSupply(getMaxAirSupply());
        } else if (isInWaterRainOrBubble()) {
            setMoisntessLevel(TOTAL_MOISTNESS_LEVEL);
        } else {
            setMoisntessLevel(getMoistnessLevel() - 1);
            if (getMoistnessLevel() <= 0) hurt(new Damage("dry_out", false, false, false), 1.0F);
            if (onGround()) {
                setDeltaMovement(vx() + (random.nextFloat() * 2.0F - 1.0F) * 0.2F, vy() + 0.5, vz() + (random.nextFloat() * 2.0F - 1.0F) * 0.2F);
                setYRot(random.nextFloat() * 360.0F);
                setOnGround(false);
                hasImpulse = true;
            }
        }
    }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if (!held.isEmpty() && ItemTags.is(held, "minecraft:fishes")) {
            playSound("entity.dolphin.eat", 1.0F, 1.0F);
            setGotFish(true);
            usePlayerItem(p, hand, held);
            return true;
        }
        return super.interact(p, hand, held, region);
    }

    @Override
    protected void travel(float inX, float inY, float inZ) {
        if (isEffectiveAi() && isInWater()) {
            moveRelative(getSpeed(), inX, inY, inZ);
            move(MOVER_SELF, vx(), vy(), vz());
            setDeltaMovement(vx() * 0.9, vy() * 0.9, vz() * 0.9);
            if (getTarget() == null) setDeltaMovement(vx(), vy() - 0.005, vz());
        } else {
            super.travel(inX, inY, inZ);
        }
    }

    @Override protected String swimSplashSound() { return "entity.dolphin.splash"; }

    @Override public String getAmbientSound() { return isInWater() ? "entity.dolphin.ambient_water" : "entity.dolphin.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.dolphin.hurt"; }

    @Override protected String getDeathSound() { return "entity.dolphin.death"; }

    /** 보물 구조물을 가까이서 찾지 못하는 것으로 처리하는 DolphinSwimToTreasureGoal. */
    static final class DolphinSwimToTreasureGoal extends Goal {
        private final Dolphin dolphin;
        private boolean stuck;

        DolphinSwimToTreasureGoal(Dolphin dolphin) {
            this.dolphin = dolphin;
            setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override public boolean isInterruptable() { return false; }

        @Override public boolean canUse() { return dolphin.gotFish() && dolphin.getAirSupply() >= 100; }

        @Override
        public boolean canContinueToUse() {
            Pos p = dolphin.getTreasurePos();
            double dx = p.x() + 0.5 - dolphin.x(), dy = p.y() + 0.5 - dolphin.y(), dz = p.z() + 0.5 - dolphin.z();
            boolean near = dx * dx + dy * dy + dz * dz < 16.0;
            return !near && !stuck && dolphin.getAirSupply() >= 100;
        }

        @Override
        public void start() {
            stuck = false;
            dolphin.getNavigation().stop();
            // TODO(구조물): findNearestMapStructure(ON_TREASURE_MAPS, pos, 50) — 아직 못 찾는 것으로 처리
            stuck = true;
        }

        @Override
        public void stop() {
            Pos p = dolphin.getTreasurePos();
            double dx = p.x() + 0.5 - dolphin.x(), dy = p.y() - dolphin.y(), dz = p.z() + 0.5 - dolphin.z();
            if (dx * dx + dy * dy + dz * dz < 16.0 || stuck) dolphin.setGotFish(false);
        }
    }

    /** DolphinJumpGoal: 앞쪽 물이 충분히 트여 있으면 물 위로 뛰어오른다. */
    static final class DolphinJumpGoal extends Goal {
        private static final int[] STEPS_TO_CHECK = {0, 1, 4, 5, 6, 7};
        private final Dolphin dolphin;
        private final int interval;
        private boolean breached;

        DolphinJumpGoal(Dolphin dolphin, int interval) {
            this.dolphin = dolphin;
            this.interval = reducedTickDelay(interval);
            setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (dolphin.random().nextInt(interval) != 0) return false;
            int dir = Dir.fromYRot(dolphin.yRot());
            int dx = Dir.DX[dir], dz = Dir.DZ[dir];
            Pos pos = dolphin.blockPosition();
            for (int step : STEPS_TO_CHECK) {
                if (!waterIsClear(pos, dx, dz, step) || !surfaceIsClear(pos, dx, dz, step)) return false;
            }
            return true;
        }

        private boolean waterIsClear(Pos pos, int dx, int dz, int i) {
            Pos p = pos.offset(dx * i, 0, dz * i);
            return Fluids.isWater(dolphin.level().getFluidState(p)) && !B.INFO.blocksMotion(dolphin.level().getBlockState(p));
        }

        private boolean surfaceIsClear(Pos pos, int dx, int dz, int i) {
            return B.isAir(dolphin.level().getBlockState(pos.offset(dx * i, 1, dz * i))) && B.isAir(dolphin.level().getBlockState(pos.offset(dx * i, 2, dz * i)));
        }

        @Override
        public boolean canContinueToUse() {
            double d = dolphin.vy();
            return (!(d * d < 0.03F) || dolphin.xRot() == 0.0F || !(Math.abs(dolphin.xRot()) < 10.0F) || !dolphin.isInWater()) && !dolphin.onGround();
        }

        @Override public boolean isInterruptable() { return false; }

        @Override
        public void start() {
            int dir = Dir.fromYRot(dolphin.yRot());
            dolphin.setDeltaMovement(dolphin.vx() + Dir.DX[dir] * 0.6, dolphin.vy() + 0.7, dolphin.vz() + Dir.DZ[dir] * 0.6);
            dolphin.getNavigation().stop();
        }

        @Override public void stop() { dolphin.setXRot(0.0F); }

        @Override
        public void tick() {
            boolean was = breached;
            if (!was) breached = Fluids.isWater(dolphin.level().getFluidState(dolphin.blockPosition()));
            if (breached && !was) dolphin.playSound("entity.dolphin.jump", 1.0F, 1.0F);
            double vx = dolphin.vx(), vy = dolphin.vy(), vz = dolphin.vz();
            if (vy * vy < 0.03F && dolphin.xRot() != 0.0F) {
                dolphin.setXRot(dolphin.xRot() + 0.2F * Mth.wrapDegrees(0.0F - dolphin.xRot()));
            } else if (vx * vx + vy * vy + vz * vz > 1.0E-5F) {
                double h = Math.sqrt(vx * vx + vz * vz);
                double d = Math.atan2(-vy, h) * 180.0F / (float) Math.PI;
                dolphin.setXRot((float) d);
            }
        }
    }
}
