// 바닐라 Mule: 말과 당나귀의 자식. 번식하지 못한다(자식은 노새).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;

public class Mule extends AbstractChestedHorse {
    public Mule(ChunkLevel level, int id) { super(EntityType.MULE, level, id); }

    @Override public String getAmbientSound() { return "entity.mule.ambient"; }

    @Override protected String getAngrySound() { return "entity.mule.angry"; }

    @Override protected String getDeathSound() { return "entity.mule.death"; }

    @Override protected String getEatingSound() { return "entity.mule.eat"; }

    @Override protected String getHurtSound(Damage source) { return "entity.mule.hurt"; }

    @Override
    protected void playChestEquipsSound() { playSound("entity.mule.chest", 1.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F); }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return new Mule(level, level.nextEntityId()); }
}
