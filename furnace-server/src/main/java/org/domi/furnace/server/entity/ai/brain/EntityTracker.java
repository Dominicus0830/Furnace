// 바닐라 EntityTracker: 엔티티를 따라간다(눈높이 옵션). 그 몹이 보이는 목록에 있어야 "보인다".
package org.domi.furnace.server.entity.ai.brain;

import java.util.Optional;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;

public final class EntityTracker implements PositionTracker {
    private final Targetable entity;
    private final boolean trackEyeHeight;

    public EntityTracker(Targetable entity, boolean trackEyeHeight) {
        this.entity = entity;
        this.trackEyeHeight = trackEyeHeight;
    }

    public Targetable getEntity() { return entity; }

    @Override
    public double[] currentPosition() { return new double[] {entity.x(), trackEyeHeight ? entity.eyeY() : entity.y(), entity.z()}; }

    @Override public Pos currentBlockPosition() { return entity.blockPosition(); }

    @Override
    public boolean isVisibleBy(Mob looker) {
        if (!entity.isAlive()) return false;
        Optional<NearestVisibleLivingEntities> v = looker.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        return v.isPresent() && v.get().contains(entity);
    }

    @Override public String toString() { return "EntityTracker for " + entity; }
}
