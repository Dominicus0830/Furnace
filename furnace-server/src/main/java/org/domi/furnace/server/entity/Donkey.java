// 바닐라 Donkey: 상자를 다는 말과. 말과 번식하면 노새.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;

public class Donkey extends AbstractChestedHorse {
    public Donkey(ChunkLevel level, int id) { super(EntityType.DONKEY, level, id); }

    @Override public String getAmbientSound() { return "entity.donkey.ambient"; }

    @Override protected String getAngrySound() { return "entity.donkey.angry"; }

    @Override protected String getDeathSound() { return "entity.donkey.death"; }

    @Override protected String getEatingSound() { return "entity.donkey.eat"; }

    @Override protected String getHurtSound(Damage source) { return "entity.donkey.hurt"; }

    @Override
    public boolean canMate(Animal other) {
        if (other == this) return false;
        return (other instanceof Donkey || other instanceof Horse) && canParent() && ((AbstractHorse) other).canParent();
    }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) {
        AbstractHorse child = partner instanceof Horse ? new Mule(level, level.nextEntityId()) : new Donkey(level, level.nextEntityId());
        setOffspringAttributes(partner, child);
        return child;
    }
}
