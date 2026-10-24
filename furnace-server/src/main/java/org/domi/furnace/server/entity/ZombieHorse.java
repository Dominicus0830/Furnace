// 바닐라 ZombieHorse: 좀비 말. 점프력만 무작위. 길들여지지 않았으면 상호작용하지 않는다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;

public class ZombieHorse extends AbstractHorse {
    public ZombieHorse(ChunkLevel level, int id) { super(EntityType.ZOMBIE_HORSE, level, id); }

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

    @Override public String getAmbientSound() { return "entity.zombie_horse.ambient"; }

    @Override protected String getDeathSound() { return "entity.zombie_horse.death"; }

    @Override protected String getHurtSound(Damage source) { return "entity.zombie_horse.hurt"; }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return new ZombieHorse(level, level.nextEntityId()); }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        return !isTamed() ? false : super.interact(p, hand, held, region);
    }
}
