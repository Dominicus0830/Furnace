// 바닐라 ThrownExperienceBottle: 던진 경험치 병. 맞으면 유리 깨지는 효과와 함께 경험치 3~10 을 떨군다. 중력 0.07.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;

public class ThrownExperienceBottle extends ThrowableProjectile {
    public ThrownExperienceBottle(ChunkLevel level, int id) { super(EntityType.EXPERIENCE_BOTTLE, level, id); }

    @Override protected String defaultItem() { return "minecraft:experience_bottle"; }

    @Override protected double defaultGravity() { return 0.07; }

    @Override
    protected void onHit(Hit hit) {
        super.onHit(hit);
        level.levelEvent(null, 2002, blockPosition(), 0x385DC6);
        int amount = 3 + level.random().nextInt(5) + level.random().nextInt(5);
        ExperienceOrb.award(level, x(), y(), z(), amount);
        discard();
    }
}
