// 바닐라 Drowned: 물에서 사는 좀비. 물속에서는 헤엄 길찾기·이동으로 바뀌고, 삼지창을 던진다. 해변(밤)으로 올라오고 낮엔 물을 찾는다.
// TODO(자세·스폰): 헤엄 자세 전환, 서식 스폰 규칙(checkDrownedSpawnRules)은 자세/스폰 틀이 생기면.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.control.DrownedMoveControl;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.navigation.GroundPathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.entity.ai.navigation.WaterBoundPathNavigation;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;
import org.domi.furnace.server.item.ItemTools;

public class Drowned extends Zombie implements RangedAttackMob {
    private boolean searchingForLand;
    protected final WaterBoundPathNavigation waterNavigation;
    protected final GroundPathNavigation groundNavigation;

    public Drowned(ChunkLevel level, int id) {
        super(EntityType.DROWNED, level, id);
        moveControl = new DrownedMoveControl(this);
        setPathfindingMalus(PathType.WATER, 0.0F);
        waterNavigation = new WaterBoundPathNavigation(this, level);
        groundNavigation = new GroundPathNavigation(this, level);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void addBehaviourGoals() {
        goalSelector.addGoal(1, new DrownedGoToWaterGoal(this, 1.0));
        goalSelector.addGoal(2, new DrownedTridentAttackGoal(this, 1.0, 40, 10.0F));
        goalSelector.addGoal(2, new DrownedAttackGoal(this, 1.0, false));
        goalSelector.addGoal(5, new DrownedGoToBeachGoal(this, 1.0));
        goalSelector.addGoal(6, new DrownedSwimUpGoal(this, 1.0, level.seaLevel()));
        goalSelector.addGoal(7, new RandomStrollGoal(this, 1.0));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Drowned.class).setAlertOthers(ZombifiedPiglin.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, 10, true, false, this::okTarget));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Villager.class, false));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, IronGolem.class, true));
        // TODO(아홀로틀): 3 = Axolotl(mustSee, !mustReach)
        targetSelector.addGoal(5, new NearestAttackableTargetGoal(this, Turtle.class, 10, true, false, t -> t instanceof Turtle tt && tt.isBaby() && !tt.isInWater()));
    }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        super.finalizeSpawn(r, difficulty);
        if (getItemBySlot(EquipmentSlot.OFFHAND).isEmpty() && r.nextFloat() < 0.03F) {
            setItemSlot(EquipmentSlot.OFFHAND, stackOf("minecraft:nautilus_shell"));
            setDropChance(EquipmentSlot.OFFHAND, 2.0F);
        }
    }

    @Override protected boolean isSunSensitive() { return false; }

    @Override protected boolean supportsBreakDoorGoal() { return false; }

    @Override protected boolean convertsInWater() { return false; }

    @Override public String getAmbientSound() { return isInWater() ? "entity.drowned.ambient_water" : "entity.drowned.ambient"; }

    @Override protected String getHurtSound(Damage source) { return isInWater() ? "entity.drowned.hurt_water" : "entity.drowned.hurt"; }

    @Override protected String getDeathSound() { return isInWater() ? "entity.drowned.death_water" : "entity.drowned.death"; }

    @Override
    protected void populateDefaultEquipmentSlots(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        if (random.nextFloat() > 0.9) {
            int i = random.nextInt(16);
            setItemSlot(EquipmentSlot.MAINHAND, stackOf(i < 10 ? "minecraft:trident" : "minecraft:fishing_rod"));
        }
    }

    @Override
    protected boolean canReplaceCurrentItem(ItemStack candidate, ItemStack existing) {
        String cur = ItemTools.itemName(existing);
        if (cur.equals("minecraft:nautilus_shell")) return false;
        if (cur.equals("minecraft:trident")) {
            return ItemTools.itemName(candidate).equals("minecraft:trident") ? ItemTools.damage(candidate) < ItemTools.damage(existing) : false;
        }
        return ItemTools.itemName(candidate).equals("minecraft:trident") ? true : super.canReplaceCurrentItem(candidate, existing);
    }

    public boolean okTarget(Targetable t) {
        if (t == null) return false;
        boolean inWater = t instanceof Entity e ? e.isInWater() : level.isWaterAt(t.blockPosition());
        return !level.isDay() || inWater;
    }

    @Override public boolean isPushedByFluid() { return !isSwimming(); }

    public boolean wantsToSwim() {
        if (searchingForLand) return true;
        Targetable t = getTarget();
        return t != null && (t instanceof Entity e ? e.isInWater() : level.isWaterAt(t.blockPosition()));
    }

    public boolean isSearchingForLand() { return searchingForLand; }

    public void setSearchingForLand(boolean v) { searchingForLand = v; }

    @Override
    protected void travel(float inX, float inY, float inZ) {
        if (isEffectiveAi() && isInWater() && wantsToSwim()) {
            moveRelative(0.01F, inX, inY, inZ);
            move(MOVER_SELF, vx(), vy(), vz());
            setDeltaMovement(vx() * 0.9, vy() * 0.9, vz() * 0.9);
        } else {
            super.travel(inX, inY, inZ);
        }
    }

    @Override
    protected void updateSwimming() {
        if (!isEffectiveAi()) return;
        if (isInWater() && wantsToSwim()) {
            navigation = waterNavigation;
            setSwimming(true);
        } else {
            navigation = groundNavigation;
            setSwimming(false);
        }
    }

    private boolean closeToNextPos() {
        var path = getNavigation().getPath();
        if (path != null) {
            Pos next = path.getNextNodePos();
            if (next != null) {
                double dx = next.x() + 0.5 - x(), dy = next.y() + 0.5 - y(), dz = next.z() + 0.5 - z();
                return dx * dx + dy * dy + dz * dz < 4.0;
            }
        }
        return false;
    }

    @Override
    public void performRangedAttack(Targetable target, float power) {
        ThrownTrident trident = new ThrownTrident(level, level.nextEntityId(), this, stackOf("minecraft:trident"));
        double dx = target.x() - x();
        double dy = target.y() + target.bbHeight() * 0.3333333333333333 - trident.y();
        double dz = target.z() - z();
        double h = Math.sqrt(dx * dx + dz * dz);
        trident.shoot(dx, dy + h * 0.2F, dz, 1.6F, 14 - level.difficulty() * 4);
        playSound("entity.drowned.shoot", 1.0F, 1.0F / (random.nextFloat() * 0.4F + 0.8F));
        level.addFreshEntity(trident);
    }

    // ------------------------------------------------------------------ 목표

    static class DrownedAttackGoal extends ZombieAttackGoal {
        private final Drowned drowned;

        DrownedAttackGoal(Drowned drowned, double speed, boolean followEvenIfNotSeen) {
            super(drowned, speed, followEvenIfNotSeen);
            this.drowned = drowned;
        }

        @Override public boolean canUse() { return super.canUse() && drowned.okTarget(drowned.getTarget()); }

        @Override public boolean canContinueToUse() { return super.canContinueToUse() && drowned.okTarget(drowned.getTarget()); }
    }

    static class DrownedGoToBeachGoal extends MoveToBlockGoal {
        private final Drowned drowned;

        DrownedGoToBeachGoal(Drowned drowned, double speed) {
            super(drowned, speed, 8, 2);
            this.drowned = drowned;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !drowned.level().isDay() && drowned.isInWater() && drowned.y() >= drowned.level().seaLevel() - 3;
        }

        @Override
        protected boolean isValidTarget(org.domi.furnace.server.block.BlockLevel level, Pos pos) {
            Pos above = pos.above();
            if (!level.isEmptyBlock(above) || !level.isEmptyBlock(above.above())) return false;
            int state = level.getBlockState(pos);
            return B.isFaceSturdy(state, level, pos, org.domi.furnace.server.block.Dir.UP);
        }

        @Override
        public void start() {
            drowned.setSearchingForLand(false);
            drowned.navigation = drowned.groundNavigation;
            super.start();
        }
    }

    static class DrownedGoToWaterGoal extends org.domi.furnace.server.entity.ai.Goal {
        private final PathfinderMob mob;
        private double wantedX, wantedY, wantedZ;
        private final double speedModifier;
        private final ChunkLevel level;

        DrownedGoToWaterGoal(PathfinderMob mob, double speed) {
            this.mob = mob;
            this.speedModifier = speed;
            this.level = mob.level();
            setFlags(java.util.EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!level.isDay()) return false;
            if (mob.isInWater()) return false;
            double[] p = getWaterPos();
            if (p == null) return false;
            wantedX = p[0];
            wantedY = p[1];
            wantedZ = p[2];
            return true;
        }

        @Override public boolean canContinueToUse() { return !mob.getNavigation().isDone(); }

        @Override public void start() { mob.getNavigation().moveTo(wantedX, wantedY, wantedZ, speedModifier); }

        private double[] getWaterPos() {
            var r = mob.random();
            Pos base = mob.blockPosition();
            for (int i = 0; i < 10; i++) {
                Pos p = new Pos(base.x() + r.nextInt(20) - 10, base.y() + 2 - r.nextInt(8), base.z() + r.nextInt(20) - 10);
                if (B.is(level.getBlockState(p), "minecraft:water")) return new double[] {p.x() + 0.5, p.y(), p.z() + 0.5};
            }
            return null;
        }
    }

    static class DrownedSwimUpGoal extends org.domi.furnace.server.entity.ai.Goal {
        private final Drowned drowned;
        private final double speedModifier;
        private final int seaLevel;
        private boolean stuck;

        DrownedSwimUpGoal(Drowned drowned, double speed, int seaLevel) {
            this.drowned = drowned;
            this.speedModifier = speed;
            this.seaLevel = seaLevel;
        }

        @Override
        public boolean canUse() { return !drowned.level().isDay() && drowned.isInWater() && drowned.y() < seaLevel - 2; }

        @Override public boolean canContinueToUse() { return canUse() && !stuck; }

        @Override
        public void tick() {
            if (drowned.y() < seaLevel - 1 && (drowned.getNavigation().isDone() || drowned.closeToNextPos())) {
                double[] v = LandRandomPos.getPosTowards(drowned, 4, 8, new double[] {drowned.x(), seaLevel - 1, drowned.z()}, (float) (Math.PI / 2));
                if (v == null) {
                    stuck = true;
                    return;
                }
                drowned.getNavigation().moveTo(v[0], v[1], v[2], speedModifier);
            }
        }

        @Override
        public void start() {
            drowned.setSearchingForLand(true);
            stuck = false;
        }

        @Override public void stop() { drowned.setSearchingForLand(false); }
    }

    static class DrownedTridentAttackGoal extends RangedAttackGoal {
        private final Drowned drowned;

        DrownedTridentAttackGoal(RangedAttackMob mob, double speed, int interval, float radius) {
            super(mob, speed, interval, radius);
            this.drowned = (Drowned) mob;
        }

        @Override public boolean canUse() { return super.canUse() && ItemTools.itemName(drowned.getItemBySlot(EquipmentSlot.MAINHAND)).equals("minecraft:trident"); }

        @Override
        public void start() {
            super.start();
            drowned.setAggressive(true);
            drowned.startUsingItem(0);
        }

        @Override
        public void stop() {
            super.stop();
            drowned.stopUsingItem();
            drowned.setAggressive(false);
        }
    }

    @Override
    public boolean checkSpawnObstruction(ChunkLevel level) { return level.isUnobstructed(org.domi.furnace.server.entity.spawn.NaturalSpawner.shapeOf(boundingBox())); }
}
