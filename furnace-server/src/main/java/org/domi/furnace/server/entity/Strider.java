// 바닐라 Strider: 용암 위를 걷는 네더 동물. 따뜻한 곳(용암·특정 블록)에서 벗어나면 떨며(suffocating) 느려진다. 뒤틀린 균류로 번식·유인.
// TODO(탑승): 안장·뒤틀린 균류 낚싯대 조종(플레이어 탑승 시스템 필요), 스폰 때의 좀비 피글린 기수·새끼 기수.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pathfinding;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.navigation.GroundPathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Strider extends Animal {
    public static final int DATA_BOOST_TIME = 17, DATA_SUFFOCATING = 18, DATA_SADDLE_ID = 19;
    private static final String SUFFOCATING_MODIFIER_ID = "minecraft:suffocating";
    private static final AttributeModifier SUFFOCATING_MODIFIER = new AttributeModifier(SUFFOCATING_MODIFIER_ID, -0.34F, AttributeModifier.ADD_MULTIPLIED_BASE);

    public Strider(ChunkLevel level, int id) {
        super(EntityType.STRIDER, level, id);
        setPathfindingMalus(PathType.WATER, -1.0F);
        setPathfindingMalus(PathType.LAVA, 0.0F);
        setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
        setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.175F).add(Attribute.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_BOOST_TIME, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_SUFFOCATING, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_SADDLE_ID, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    @Override
    protected void addAdditionalSaveData(org.domi.furnace.server.net.io.nbt.NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Saddle", isSaddled());
    }

    @Override
    protected void readAdditionalSaveData(org.domi.furnace.server.net.io.nbt.NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_SADDLE_ID, tag.getBoolean("Saddle"));
    }

    public boolean isSaddled() { return (Boolean) entityData.get(DATA_SADDLE_ID); }

    public boolean isSuffocating() { return (Boolean) entityData.get(DATA_SUFFOCATING); }

    public void setSuffocating(boolean v) {
        entityData.set(DATA_SUFFOCATING, v);
        var inst = attributes().get(Attribute.MOVEMENT_SPEED);
        if (inst != null) {
            if (v) inst.addOrReplaceModifier(SUFFOCATING_MODIFIER);
            else inst.removeModifier(SUFFOCATING_MODIFIER_ID);
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new PanicGoal(this, 1.65));
        goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        goalSelector.addGoal(3, new TemptGoal(this, 1.4, s -> ItemTags.is(s, "minecraft:strider_tempt_items"), false));
        goalSelector.addGoal(4, new StriderGoToLavaGoal(1.0));
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.0));
        goalSelector.addGoal(7, new RandomStrollGoal(this, 1.0, 60));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        goalSelector.addGoal(9, new LookAtMobGoal(this, Strider.class, 8.0F, 0.02F));
    }

    @Override
    public boolean canStandOnFluid(int fluid) { return Fluids.isLava(fluid); }

    @Override
    protected PathNavigation createNavigation(ChunkLevel level) {
        return new GroundPathNavigation(this, level) {
            @Override
            public boolean isStableDestination(Pos pos) { return Fluids.isLava(level.getFluidState(pos)) || super.isStableDestination(pos); }
        };
    }

    @Override
    public boolean isSensitiveToWater() { return true; }

    @Override
    public boolean isOnFire() { return false; }

    @Override
    public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:strider_food"); }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return new Strider(level, level.nextEntityId()); }

    @Override
    public float getWalkTargetValue(Pos pos, ChunkLevel level) {
        if (Fluids.isLava(level.getFluidState(pos))) return 10.0F;
        return isInLava() ? Float.NEGATIVE_INFINITY : 0.0F;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, Damage source) { return !isInLava() && super.causeFallDamage(distance, multiplier, source); }

    @Override
    public void tick() {
        if (isNoAi() ? false : true) {
            int here = level.getBlockState(blockPosition());
            int below = level.getBlockState(getOnPosLegacy());
            boolean warm = B.isTag(here, "minecraft:strider_warm_blocks") || B.isTag(below, "minecraft:strider_warm_blocks") || fluidHeight(false) > 0.0;
            setSuffocating(!warm);
        }
        super.tick();
        floatStrider();
    }

    private void floatStrider() {
        if (isInLava()) {
            Pos p = blockPosition();
            if (y() > p.y() + 0.5 - 1.0E-5F && !Fluids.isLava(level.getFluidState(p.above()))) {
                setOnGround(true);
            } else {
                setDeltaMovement(vx() * 0.5, vy() * 0.5 + 0.05, vz() * 0.5);
            }
        }
    }

    @Override public String getAmbientSound() { return !isPanicking() && !isSuffocating() ? "entity.strider.ambient" : null; }

    @Override protected String getHurtSound(Damage source) { return "entity.strider.hurt"; }

    @Override protected String getDeathSound() { return "entity.strider.death"; }

    /** StriderGoToLavaGoal: 용암 위(위가 뚫린 용암)로 걸어간다. */
    private final class StriderGoToLavaGoal extends MoveToBlockGoal {
        StriderGoToLavaGoal(double speed) { super(Strider.this, speed, 8, 2); }

        @Override protected Pos getMoveToTarget() { return blockPos; }

        @Override public boolean canContinueToUse() { return !isInLava() && isValidTarget(level, blockPos); }

        @Override public boolean canUse() { return !isInLava() && super.canUse(); }

        @Override public boolean shouldRecalculatePath() { return tryTicks % 20 == 0; }

        @Override
        protected boolean isValidTarget(BlockLevel lv, Pos pos) {
            return B.is(lv.getBlockState(pos), "minecraft:lava") && Pathfinding.isPathfindable(lv.getBlockState(pos.above()), Pathfinding.LAND);
        }
    }

    @Override
    public boolean checkSpawnObstruction(ChunkLevel level) { return level.isUnobstructed(org.domi.furnace.server.entity.spawn.NaturalSpawner.shapeOf(boundingBox())); }
}
