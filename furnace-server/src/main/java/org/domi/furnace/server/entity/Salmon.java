// 바닐라 Salmon: 떼 물고기, 무리 크기 상한 5.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;

public class Salmon extends AbstractSchoolingFish {
    public Salmon(ChunkLevel level, int id) { super(EntityType.SALMON, level, id); }

    @Override public int getMaxSchoolSize() { return 5; }

    @Override public String getAmbientSound() { return "entity.salmon.ambient"; }

    @Override protected String getDeathSound() { return "entity.salmon.death"; }

    @Override protected String getHurtSound(Damage source) { return "entity.salmon.hurt"; }

    @Override protected String getFlopSound() { return "entity.salmon.flop"; }
}
