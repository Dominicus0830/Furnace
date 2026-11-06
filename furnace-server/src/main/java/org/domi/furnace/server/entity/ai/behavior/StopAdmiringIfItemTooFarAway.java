// 바닐라 StopAdmiringIfItemTooFarAway: 반대손이 비었는데 원하는 아이템이 maxDist 밖이면 감상을 멈춘다.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.Piglin;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class StopAdmiringIfItemTooFarAway {
    private StopAdmiringIfItemTooFarAway() {}

    public static OneShot<Piglin> create(int maxDistanceToItem) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Piglin piglin, long gameTime) {
                if (!present(piglin, MemoryModuleType.ADMIRING_ITEM) || !registered(piglin, MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM)) return false;
                if (!piglin.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) return false;
                var item = piglin.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
                if (item.isPresent()) {
                    double dx = item.get().x() - piglin.x(), dy = item.get().y() - piglin.y(), dz = item.get().z() - piglin.z();
                    if (dx * dx + dy * dy + dz * dz < (double) maxDistanceToItem * maxDistanceToItem) return false;
                }
                piglin.getBrain().eraseMemory(MemoryModuleType.ADMIRING_ITEM);
                return true;
            }
        };
    }
}
