// 바닐라 EntityTracker 중 살아 있는 생물이 아닌 엔티티(떨어진 아이템 등)를 가리키는 판.
package org.domi.furnace.server.entity.ai.brain;

import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Mob;

public final class ItemEntityTracker implements PositionTracker {
    private final Entity entity;
    private final boolean trackEyeHeight;

    public ItemEntityTracker(Entity entity, boolean trackEyeHeight) {
        this.entity = entity;
        this.trackEyeHeight = trackEyeHeight;
    }

    public Entity getEntity() { return entity; }

    @Override public double[] currentPosition() { return new double[] {entity.x(), trackEyeHeight ? entity.y() + entity.bbHeight() * 0.5 : entity.y(), entity.z()}; }

    @Override public Pos currentBlockPosition() { return entity.blockPosition(); }

    @Override public boolean isVisibleBy(Mob looker) { return !entity.isRemoved(); }

    @Override public String toString() { return "ItemEntityTracker for " + entity; }
}
