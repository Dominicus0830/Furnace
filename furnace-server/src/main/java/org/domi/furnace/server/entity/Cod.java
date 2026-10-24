// 바닐라 Cod: 가장 흔한 떼 물고기. 체력 3.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;

public class Cod extends AbstractSchoolingFish {
    public Cod(ChunkLevel level, int id) { super(EntityType.COD, level, id); }

    @Override public String getAmbientSound() { return "entity.cod.ambient"; }

    @Override protected String getDeathSound() { return "entity.cod.death"; }

    @Override protected String getHurtSound(Damage source) { return "entity.cod.hurt"; }

    @Override protected String getFlopSound() { return "entity.cod.flop"; }
}
