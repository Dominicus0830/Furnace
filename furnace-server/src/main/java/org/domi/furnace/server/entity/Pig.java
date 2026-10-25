// 바닐라 Pig: 당근·감자·사탕무를 좋아하고 안장을 얹을 수 있다. TODO(타기): 안장을 얹은 돼지 타기·당근 낚싯대 가속.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Pig extends Animal {
    /** 17 안장, 18 가속 시간(Pig.DATA_SADDLE_ID, DATA_BOOST_TIME). */
    public static final int DATA_SADDLE = 17, DATA_BOOST_TIME = 18;

    public Pig(ChunkLevel level, int id) { super(EntityType.PIG, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 10.0).add(Attribute.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SADDLE, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_BOOST_TIME, SetEntityData.SERIALIZER_INT, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.25));
        goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        goalSelector.addGoal(4, new TemptGoal(this, 1.2, s -> ItemTools.itemName(s).equals("minecraft:carrot_on_a_stick"), false));
        goalSelector.addGoal(4, new TemptGoal(this, 1.2, s -> ItemTags.is(s, "minecraft:pig_food"), false));
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, 6.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:pig_food"); }

    @Override
    public void thunderHit(org.domi.furnace.server.block.ChunkLevel level, LightningBolt bolt) {
        if (level.difficulty() != 0) {
            ZombifiedPiglin piglin = new ZombifiedPiglin(level, level.nextEntityId());
            piglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "minecraft:golden_sword"), 1));
            piglin.persistenceRequired = true;
            convertTo(piglin, false);
        } else {
            super.thunderHit(level, bolt);
        }
    }

    public boolean isSaddled() { return (Boolean) entityData.get(DATA_SADDLE); }

    public void setSaddled(boolean v) { entityData.set(DATA_SADDLE, v); }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if (super.interact(p, hand, held, region)) return true;
        if (!held.isEmpty() && ItemTools.itemName(held).equals("minecraft:saddle") && isAlive() && !isBaby() && !isSaddled()) {
            level.playSound(null, x(), y(), z(), "entity.pig.saddle", "neutral", 0.5F, 1.0F);
            setSaddled(true);
            if (!p.vitals().isCreative()) {
                var inv = p.inventory();
                inv.set(hand == 0 ? inv.heldWindowSlot() : 45, held.withCount(held.count() - 1));
            }
            return true;
        }
        return false;
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        if (isSaddled()) spawnAtLocation(new ItemStack(ItemContext.id("minecraft:item", "saddle"), 1));
    }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return new Pig(level, level.nextEntityId()); }

    @Override public String getAmbientSound() { return "entity.pig.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.pig.hurt"; }

    @Override protected String getDeathSound() { return "entity.pig.death"; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Saddle", isSaddled());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setSaddled(tag.getBoolean("Saddle"));
    }
}
