// 바닐라 SkeletonHorse: 해골 말. 번개 덫(SkeletonTrap) 상태면 18000틱 뒤 사라진다. 길들여지지 않았으면 상호작용하지 않는다.
// TODO(해골 덫): SkeletonTrapGoal(번개·해골 기수 소환)은 번개·탑승 구현 때.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class SkeletonHorse extends AbstractHorse {
    private boolean isTrap;
    private int trapTime;

    public SkeletonHorse(ChunkLevel level, int id) { super(EntityType.SKELETON_HORSE, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 15.0).add(Attribute.MOVEMENT_SPEED, 0.2F);
    }

    @Override
    protected void randomizeAttributes(org.domi.furnace.server.block.RandomSource r) {
        attributes().get(Attribute.JUMP_STRENGTH).setBaseValue(b(r::nextDouble));
    }

    @Override protected void addBehaviourGoals() { }

    @Override public String getAmbientSound() { return isEyeInWater() ? "entity.skeleton_horse.ambient_water" : "entity.skeleton_horse.ambient"; }

    @Override protected String getDeathSound() { return "entity.skeleton_horse.death"; }

    @Override protected String getHurtSound(Damage source) { return "entity.skeleton_horse.hurt"; }

    @Override
    public void tick() {
        super.tick();
        if (isTrap() && trapTime++ >= 18000) discard();
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("SkeletonTrap", isTrap());
        tag.putInt("SkeletonTrapTime", trapTime);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setTrap(tag.getBoolean("SkeletonTrap"));
        trapTime = tag.getIntOr0("SkeletonTrapTime");
    }

    public boolean isTrap() { return isTrap; }

    public void setTrap(boolean v) { isTrap = v; }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return new SkeletonHorse(level, level.nextEntityId()); }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        return !isTamed() ? false : super.interact(p, hand, held, region);
    }
}
