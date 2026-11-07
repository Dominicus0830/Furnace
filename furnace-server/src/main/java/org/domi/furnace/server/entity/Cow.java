// 바닐라 Cow: 밀을 좋아하고, 빈 양동이로 우유를 짠다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class Cow extends Animal {

    public Cow(ChunkLevel level, int id) { this(EntityType.COW, level, id); }

    protected Cow(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 10.0).add(Attribute.MOVEMENT_SPEED, 0.2F);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 2.0));
        goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        goalSelector.addGoal(3, new TemptGoal(this, 1.25, s -> ItemTags.is(s, "minecraft:cow_food"), false));
        goalSelector.addGoal(4, new FollowParentGoal(this, 1.25));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, 6.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:cow_food"); }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if (!held.isEmpty() && org.domi.furnace.server.item.ItemTools.itemName(held).equals("minecraft:bucket") && !isBaby()) {
            level.playSound(null, p.x(), p.y(), p.z(), "entity.cow.milk", "players", 1.0F, 1.0F);
            fillResult(p, hand, held, "minecraft:milk_bucket", region);
            return true;
        }
        return super.interact(p, hand, held, region);
    }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return new Cow(level, level.nextEntityId()); }

    @Override public String getAmbientSound() { return "entity.cow.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.cow.hurt"; }

    @Override protected String getDeathSound() { return "entity.cow.death"; }

    /** Cow.playStepSound: cow.step 크기 0.15(블록 소리를 쓰지 않는다). */
    @Override
    protected void playStepSound(org.domi.furnace.server.block.Pos pos, int state) { playSound("minecraft:entity.cow.step", 0.15F, 1.0F); }
}
