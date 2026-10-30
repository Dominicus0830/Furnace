// 바닐라 Turtle: 물과 땅을 오간다. 바다에서 짝짓기 해 알을 품고, 태어난 해변(집)으로 돌아가 모래에 알을 낳는다. 아기가 자라면 스트라이프 조각(스쿠트)을 떨군다.
// TODO(발걸음 소리): 걷는 소리.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.types.TurtleEggBlock;
import org.domi.furnace.server.entity.ai.control.MoveControl;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.navigation.AmphibiousPathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Turtle extends Animal {
    /** Turtle.BABY_ON_LAND_SELECTOR: 물 밖의 아기. */
    public static final java.util.function.Predicate<Targetable> BABY_ON_LAND_SELECTOR = t -> t instanceof LivingEntity e && e.isBaby() && !e.isInWater();

    /** Turtle.thunderHit: 번개에 맞으면 즉사(최대 피해). */
    @Override
    public void thunderHit(org.domi.furnace.server.block.ChunkLevel level, LightningBolt bolt) {
        hurt(new Damage("lightning_bolt", false, false, false), Float.MAX_VALUE);
    }

    public static final int DATA_HOME_POS = 17, DATA_HAS_EGG = 18, DATA_LAYING_EGG = 19, DATA_TRAVEL_POS = 20, DATA_GOING_HOME = 21, DATA_TRAVELLING = 22;
    private static final Prop EGGS = B.prop("eggs");
    int layEggCounter;

    public Turtle(ChunkLevel level, int id) {
        super(EntityType.TURTLE, level, id);
        setPathfindingMalus(PathType.WATER, 0.0F);
        setPathfindingMalus(PathType.DOOR_IRON_CLOSED, -1.0F);
        setPathfindingMalus(PathType.DOOR_WOOD_CLOSED, -1.0F);
        setPathfindingMalus(PathType.DOOR_OPEN, -1.0F);
        this.moveControl = new TurtleMoveControl(this);
    }

    public void setHomePos(Pos p) { entityData.set(DATA_HOME_POS, new int[] {p.x(), p.y(), p.z()}); }

    Pos getHomePos() { return pos(DATA_HOME_POS); }

    void setTravelPos(Pos p) { entityData.set(DATA_TRAVEL_POS, new int[] {p.x(), p.y(), p.z()}); }

    Pos getTravelPos() { return pos(DATA_TRAVEL_POS); }

    private Pos pos(int id) {
        int[] p = (int[]) entityData.get(id);
        return new Pos(p[0], p[1], p[2]);
    }

    public boolean hasEgg() { return (Boolean) entityData.get(DATA_HAS_EGG); }

    void setHasEgg(boolean v) { entityData.set(DATA_HAS_EGG, v); }

    public boolean isLayingEgg() { return (Boolean) entityData.get(DATA_LAYING_EGG); }

    void setLayingEgg(boolean v) {
        layEggCounter = v ? 1 : 0;
        entityData.set(DATA_LAYING_EGG, v);
    }

    boolean isGoingHome() { return (Boolean) entityData.get(DATA_GOING_HOME); }

    void setGoingHome(boolean v) { entityData.set(DATA_GOING_HOME, v); }

    boolean isTravelling() { return (Boolean) entityData.get(DATA_TRAVELLING); }

    void setTravelling(boolean v) { entityData.set(DATA_TRAVELLING, v); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_HOME_POS, SetEntityData.SERIALIZER_BLOCK_POS, new int[] {0, 0, 0});
        entityData.define(DATA_HAS_EGG, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_LAYING_EGG, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_TRAVEL_POS, SetEntityData.SERIALIZER_BLOCK_POS, new int[] {0, 0, 0});
        entityData.define(DATA_GOING_HOME, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_TRAVELLING, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        Pos h = getHomePos();
        tag.putInt("HomePosX", h.x());
        tag.putInt("HomePosY", h.y());
        tag.putInt("HomePosZ", h.z());
        tag.putBoolean("HasEgg", hasEgg());
        Pos t = getTravelPos();
        tag.putInt("TravelPosX", t.x());
        tag.putInt("TravelPosY", t.y());
        tag.putInt("TravelPosZ", t.z());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        setHomePos(new Pos(tag.getIntOr0("HomePosX"), tag.getIntOr0("HomePosY"), tag.getIntOr0("HomePosZ")));
        super.readAdditionalSaveData(tag);
        setHasEgg(tag.getBoolean("HasEgg"));
        setTravelPos(new Pos(tag.getIntOr0("TravelPosX"), tag.getIntOr0("TravelPosY"), tag.getIntOr0("TravelPosZ")));
    }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        setHomePos(blockPosition());
        setTravelPos(new Pos(0, 0, 0));
        super.finalizeSpawn(r, difficulty);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 30.0).add(Attribute.MOVEMENT_SPEED, 0.25).add(Attribute.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new TurtlePanicGoal(this, 1.2));
        goalSelector.addGoal(1, new TurtleBreedGoal(this, 1.0));
        goalSelector.addGoal(1, new TurtleLayEggGoal(this, 1.0));
        goalSelector.addGoal(2, new TemptGoal(this, 1.1, s -> ItemTags.is(s, "minecraft:turtle_food"), false));
        goalSelector.addGoal(3, new TurtleGoToWaterGoal(this, 1.0));
        goalSelector.addGoal(4, new TurtleGoHomeGoal(this, 1.0));
        goalSelector.addGoal(7, new TurtleTravelGoal(this, 1.0));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, 8.0F));
        goalSelector.addGoal(9, new TurtleRandomStrollGoal(this, 1.0, 100));
    }

    @Override
    public boolean isPushedByFluid() { return false; }

    @Override
    public int getAmbientSoundInterval() { return 200; }

    @Override
    public String getAmbientSound() { return !isInWater() && onGround() && !isBaby() ? "entity.turtle.ambient_land" : null; }

    @Override protected String getHurtSound(Damage source) { return isBaby() ? "entity.turtle.hurt_baby" : "entity.turtle.hurt"; }

    @Override protected String getDeathSound() { return isBaby() ? "entity.turtle.death_baby" : "entity.turtle.death"; }

    @Override
    public boolean canFallInLove() { return super.canFallInLove() && !hasEgg(); }

    @Override
    public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:turtle_food"); }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return new Turtle(level, level.nextEntityId()); }

    @Override
    protected float babyScale() { return 0.3F; }

    @Override
    protected PathNavigation createNavigation(ChunkLevel level) { return new TurtleNavigation(this, level); }

    @Override
    public float getWalkTargetValue(Pos pos, ChunkLevel level) {
        if (!isGoingHome() && Fluids.isWater(level.getFluidState(pos))) return 10.0F;
        if (TurtleEggBlock.onSand(level, pos)) return 10.0F;
        float f = level.getMaxLocalRawBrightness(pos) / 15.0F;
        return f / (4.0F - 3.0F * f);   // getPathfindingCostFromLightLevels
    }

    @Override
    protected void aiStep() {
        super.aiStep();
        if (isAlive() && isLayingEgg() && layEggCounter >= 1 && layEggCounter % 5 == 0) {
            Pos pos = blockPosition();
            if (TurtleEggBlock.onSand(level, pos)) level.levelEvent(null, 2001, pos, level.getBlockState(pos.below()));
        }
    }

    @Override
    protected void ageBoundaryReached() {
        super.ageBoundaryReached();
        if (!isBaby() && level.gameRule("doMobLoot")) spawnAtLocation(new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "turtle_scute"), 1));
    }

    @Override
    protected void travel(float inX, float inY, float inZ) {
        if (isEffectiveAi() && isInWater()) {
            moveRelative(0.1F, inX, inY, inZ);
            move(MOVER_SELF, vx(), vy(), vz());
            setDeltaMovement(vx() * 0.9, vy() * 0.9, vz() * 0.9);
            if (getTarget() == null && (!isGoingHome() || !closerToCenter(getHomePos(), 20.0))) setDeltaMovement(vx(), vy() - 0.005, vz());
        } else {
            super.travel(inX, inY, inZ);
        }
    }

    boolean closerToCenter(Pos p, double dist) {
        double dx = p.x() + 0.5 - x(), dy = p.y() + 0.5 - y(), dz = p.z() + 0.5 - z();
        return dx * dx + dy * dy + dz * dz < dist * dist;
    }

    // ------------------------------------------------------------------ 이동·길찾기

    static final class TurtleMoveControl extends MoveControl {
        private final Turtle turtle;

        TurtleMoveControl(Turtle turtle) {
            super(turtle);
            this.turtle = turtle;
        }

        private void updateSpeed() {
            if (turtle.isInWater()) {
                turtle.setDeltaMovement(turtle.vx(), turtle.vy() + 0.005, turtle.vz());
                if (!turtle.closerToCenter(turtle.getHomePos(), 16.0)) turtle.setSpeed(Math.max(turtle.getSpeed() / 2.0F, 0.08F));
                if (turtle.isBaby()) turtle.setSpeed(Math.max(turtle.getSpeed() / 3.0F, 0.06F));
            } else if (turtle.onGround()) {
                turtle.setSpeed(Math.max(turtle.getSpeed() / 2.0F, 0.06F));
            }
        }

        @Override
        public void tick() {
            updateSpeed();
            if (operation == Operation.MOVE_TO && !turtle.getNavigation().isDone()) {
                double dx = wantedX - turtle.x(), dy = wantedY - turtle.y(), dz = wantedZ - turtle.z();
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d < 1.0E-5F) {
                    mob.setSpeed(0.0F);
                } else {
                    dy /= d;
                    float target = (float) (Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
                    turtle.setYRot(rotlerp(turtle.yRot(), target, 90.0F));
                    turtle.setYBodyRot(turtle.yRot());
                    float speed = (float) (speedModifier * turtle.attributeValue(Attribute.MOVEMENT_SPEED));
                    turtle.setSpeed(turtle.getSpeed() + 0.125F * (speed - turtle.getSpeed()));
                    turtle.setDeltaMovement(turtle.vx(), turtle.vy() + turtle.getSpeed() * dy * 0.1, turtle.vz());
                }
            } else {
                turtle.setSpeed(0.0F);
            }
        }
    }

    static final class TurtleNavigation extends AmphibiousPathNavigation {
        private final Turtle turtle;

        TurtleNavigation(Turtle turtle, ChunkLevel level) {
            super(turtle, level);
            this.turtle = turtle;
        }

        @Override
        public boolean isStableDestination(Pos pos) {
            return turtle.isGoingHome() ? B.is(level.getBlockState(pos), "minecraft:water") : !B.isAir(level.getBlockState(pos.below()));
        }
    }

    // ------------------------------------------------------------------ 목표

    static final class TurtlePanicGoal extends PanicGoal {
        TurtlePanicGoal(Turtle turtle, double speed) { super(turtle, speed); }

        @Override
        public boolean canUse() {
            if (!shouldPanic()) return false;
            Pos water = lookForWater(7);
            if (water != null) {
                posX = water.x();
                posY = water.y();
                posZ = water.z();
                return true;
            }
            return findRandomPosition();
        }
    }

    static final class TurtleBreedGoal extends BreedGoal {
        private final Turtle turtle;

        TurtleBreedGoal(Turtle turtle, double speed) {
            super(turtle, speed);
            this.turtle = turtle;
        }

        @Override
        public boolean canUse() { return super.canUse() && !turtle.hasEgg(); }

        @Override
        protected void breed() {
            turtle.setHasEgg(true);
            turtle.setAge(6000);
            partner.setAge(6000);
            turtle.resetLove();
            partner.resetLove();
            ChunkLevel level = turtle.level();
            if (level.gameRule("doMobLoot")) {
                level.addFreshEntity(new ExperienceOrb(level, level.nextEntityId(), turtle.x(), turtle.y(), turtle.z(), turtle.random().nextInt(7) + 1));
            }
        }
    }

    static final class TurtleGoHomeGoal extends org.domi.furnace.server.entity.ai.Goal {
        private final Turtle turtle;
        private final double speedModifier;
        private boolean stuck;
        private int closeToHomeTryTicks;

        TurtleGoHomeGoal(Turtle turtle, double speed) {
            this.turtle = turtle;
            this.speedModifier = speed;
        }

        @Override
        public boolean canUse() {
            if (turtle.isBaby()) return false;
            if (turtle.hasEgg()) return true;
            if (turtle.random().nextInt(reducedTickDelay(700)) != 0) return false;
            return !turtle.closerToCenter(turtle.getHomePos(), 64.0);
        }

        @Override
        public void start() {
            turtle.setGoingHome(true);
            stuck = false;
            closeToHomeTryTicks = 0;
        }

        @Override public void stop() { turtle.setGoingHome(false); }

        @Override
        public boolean canContinueToUse() {
            return !turtle.closerToCenter(turtle.getHomePos(), 7.0) && !stuck && closeToHomeTryTicks <= adjustedTickDelay(600);
        }

        @Override
        public void tick() {
            Pos home = turtle.getHomePos();
            boolean near = turtle.closerToCenter(home, 16.0);
            if (near) closeToHomeTryTicks++;
            if (turtle.getNavigation().isDone()) {
                double[] towards = {home.x() + 0.5, home.y(), home.z() + 0.5};
                double[] p = LandRandomPos.getPosTowards(turtle, 16, 3, towards, (float) (Math.PI / 10));
                if (p == null) p = LandRandomPos.getPosTowards(turtle, 8, 7, towards, (float) (Math.PI / 2));
                if (p != null && !near && !B.is(turtle.level().getBlockState(new Pos((int) Math.floor(p[0]), (int) Math.floor(p[1]), (int) Math.floor(p[2]))), "minecraft:water")) {
                    p = LandRandomPos.getPosTowards(turtle, 16, 5, towards, (float) (Math.PI / 2));
                }
                if (p == null) {
                    stuck = true;
                    return;
                }
                turtle.getNavigation().moveTo(p[0], p[1], p[2], speedModifier);
            }
        }
    }

    static final class TurtleGoToWaterGoal extends MoveToBlockGoal {
        private final Turtle turtle;

        TurtleGoToWaterGoal(Turtle turtle, double speed) {
            super(turtle, turtle.isBaby() ? 2.0 : speed, 24);
            this.turtle = turtle;
            this.verticalSearchStart = -1;
        }

        @Override
        public boolean canContinueToUse() { return !turtle.isInWater() && tryTicks <= 1200 && isValidTarget(turtle.level(), blockPos); }

        @Override
        public boolean canUse() {
            if (turtle.isBaby() && !turtle.isInWater()) return super.canUse();
            return !turtle.isGoingHome() && !turtle.isInWater() && !turtle.hasEgg() && super.canUse();
        }

        @Override
        public boolean shouldRecalculatePath() { return tryTicks % 160 == 0; }

        @Override
        protected boolean isValidTarget(BlockLevel level, Pos pos) { return B.is(level.getBlockState(pos), "minecraft:water"); }
    }

    static final class TurtleLayEggGoal extends MoveToBlockGoal {
        private final Turtle turtle;

        TurtleLayEggGoal(Turtle turtle, double speed) {
            super(turtle, speed, 16);
            this.turtle = turtle;
        }

        @Override
        public boolean canUse() { return turtle.hasEgg() && turtle.closerToCenter(turtle.getHomePos(), 9.0) && super.canUse(); }

        @Override
        public boolean canContinueToUse() { return super.canContinueToUse() && turtle.hasEgg() && turtle.closerToCenter(turtle.getHomePos(), 9.0); }

        @Override
        public void tick() {
            super.tick();
            Pos pos = turtle.blockPosition();
            if (!turtle.isInWater() && isReachedTarget()) {
                if (turtle.layEggCounter < 1) {
                    turtle.setLayingEgg(true);
                } else if (turtle.layEggCounter > adjustedTickDelay(200)) {
                    ChunkLevel level = turtle.level();
                    level.playSound(null, pos, "minecraft:entity.turtle.lay_egg", "blocks", 0.3F, 0.9F + level.random().nextFloat() * 0.2F);
                    Pos eggPos = blockPos.above();
                    int state = EGGS.withInt(B.defaultState(B.id("minecraft:turtle_egg")), turtle.random().nextInt(4) + 1);
                    level.setBlock(eggPos, state, 3);
                    turtle.setHasEgg(false);
                    turtle.setLayingEgg(false);
                    turtle.setInLoveTime(600);
                }
                if (turtle.isLayingEgg()) turtle.layEggCounter++;
            }
        }

        @Override
        protected boolean isValidTarget(BlockLevel level, Pos pos) {
            return B.isAir(level.getBlockState(pos.above())) && TurtleEggBlock.isSand(level, pos);
        }
    }

    static final class TurtleRandomStrollGoal extends RandomStrollGoal {
        private final Turtle turtle;

        TurtleRandomStrollGoal(Turtle turtle, double speed, int interval) {
            super(turtle, speed, interval);
            this.turtle = turtle;
        }

        @Override
        public boolean canUse() { return !turtle.isInWater() && !turtle.isGoingHome() && !turtle.hasEgg() && super.canUse(); }
    }

    static final class TurtleTravelGoal extends org.domi.furnace.server.entity.ai.Goal {
        private final Turtle turtle;
        private final double speedModifier;
        private boolean stuck;

        TurtleTravelGoal(Turtle turtle, double speed) {
            this.turtle = turtle;
            this.speedModifier = speed;
        }

        @Override
        public boolean canUse() { return !turtle.isGoingHome() && !turtle.hasEgg() && turtle.isInWater(); }

        @Override
        public void start() {
            var r = turtle.random();
            int x = r.nextInt(1025) - 512;
            int y = r.nextInt(9) - 4;
            int z = r.nextInt(1025) - 512;
            if (y + turtle.y() > turtle.level().seaLevel() - 1) y = 0;
            turtle.setTravelPos(new Pos((int) Math.floor(x + turtle.x()), (int) Math.floor(y + turtle.y()), (int) Math.floor(z + turtle.z())));
            turtle.setTravelling(true);
            stuck = false;
        }

        @Override
        public void tick() {
            if (turtle.getNavigation().isDone()) {
                Pos t = turtle.getTravelPos();
                double[] towards = {t.x() + 0.5, t.y(), t.z() + 0.5};
                double[] p = LandRandomPos.getPosTowards(turtle, 16, 3, towards, (float) (Math.PI / 10));
                if (p == null) p = LandRandomPos.getPosTowards(turtle, 8, 7, towards, (float) (Math.PI / 2));
                if (p != null) {
                    int i = (int) Math.floor(p[0]), j = (int) Math.floor(p[2]);
                    if (!turtle.level().hasChunksAt(i - 34, j - 34, i + 34, j + 34)) p = null;
                }
                if (p == null) {
                    stuck = true;
                    return;
                }
                turtle.getNavigation().moveTo(p[0], p[1], p[2], speedModifier);
            }
        }

        @Override
        public boolean canContinueToUse() { return !turtle.getNavigation().isDone() && !stuck && !turtle.isGoingHome() && !turtle.isInLove() && !turtle.hasEgg(); }

        @Override
        public void stop() {
            turtle.setTravelling(false);
            super.stop();
        }
    }
}
