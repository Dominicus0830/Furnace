// 바닐라 Chicken: 알을 낳고(6000~11999틱), 느리게 떨어지고, 씨앗을 좋아한다. 닭 기수면 알을 낳지 않고 멀어지면 사라지며 태운 것의 몸 방향을 맞춘다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class Chicken extends Animal {
    private boolean chickenJockey;
    private int eggTime = random.nextInt(6000) + 6000;

    public Chicken(ChunkLevel level, int id) { super(EntityType.CHICKEN, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 4.0).add(Attribute.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.4));
        goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        goalSelector.addGoal(3, new TemptGoal(this, 1.0, s -> ItemTags.is(s, "minecraft:chicken_food"), false));
        goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, 6.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected void aiStep() {
        super.aiStep();
        if (!onGround() && vy() < 0.0) setDeltaMovement(vx(), vy() * 0.6, vz());
        if (isAlive() && !isBaby() && !chickenJockey && --eggTime <= 0) {
            playSound("entity.chicken.egg", 1.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
            spawnAtLocation(new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "egg"), 1));
            eggTime = random.nextInt(6000) + 6000;
        }
    }

    public boolean isChickenJockey() { return chickenJockey; }

    public void setChickenJockey(boolean v) { chickenJockey = v; }

    /** Chicken.removeWhenFarAway: 기수를 태운 닭만 멀어지면 사라진다. */
    @Override
    public boolean removeWhenFarAway(double distSqr) { return chickenJockey; }

    /** Chicken.positionRider: 승객의 몸 방향을 내 몸 방향으로. */
    @Override
    protected void placeRider(Rider r) {
        super.placeRider(r);
        if (r instanceof LivingEntity le) le.yBodyRot = yBodyRot;
    }

    @Override
    public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:chicken_food"); }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return new Chicken(level, level.nextEntityId()); }

    @Override
    protected int getExperienceReward() { return chickenJockey ? 10 : super.getExperienceReward(); }

    @Override public String getAmbientSound() { return "entity.chicken.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.chicken.hurt"; }

    @Override protected String getDeathSound() { return "entity.chicken.death"; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IsChickenJockey", chickenJockey);
        tag.putInt("EggLayTime", eggTime);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        chickenJockey = tag.getBoolean("IsChickenJockey");
        if (tag.contains("EggLayTime")) eggTime = tag.getInt("EggLayTime");
    }
}
