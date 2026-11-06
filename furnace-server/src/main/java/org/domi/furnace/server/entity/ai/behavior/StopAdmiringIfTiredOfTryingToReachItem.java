// 바닐라 StopAdmiringIfTiredOfTryingToReachItem: 아이템에 닿지 못한 채 maxTime 틱이 지나면 감상을 멈추고 한동안 찾아가지 않는다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Optional;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.Piglin;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class StopAdmiringIfTiredOfTryingToReachItem {
    private StopAdmiringIfTiredOfTryingToReachItem() {}

    public static OneShot<Piglin> create(int maxTimeToReachItem, int disableTime) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Piglin piglin, long gameTime) {
                if (!present(piglin, MemoryModuleType.ADMIRING_ITEM) || !present(piglin, MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM)
                        || !registered(piglin, MemoryModuleType.TIME_TRYING_TO_REACH_ADMIRE_ITEM) || !registered(piglin, MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM)) return false;
                if (!piglin.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) return false;
                var brain = piglin.getBrain();
                Optional<Integer> time = brain.getMemory(MemoryModuleType.TIME_TRYING_TO_REACH_ADMIRE_ITEM);
                if (time.isEmpty()) {
                    brain.setMemory(MemoryModuleType.TIME_TRYING_TO_REACH_ADMIRE_ITEM, 0);
                } else if (time.get() > maxTimeToReachItem) {
                    brain.eraseMemory(MemoryModuleType.ADMIRING_ITEM);
                    brain.eraseMemory(MemoryModuleType.TIME_TRYING_TO_REACH_ADMIRE_ITEM);
                    brain.setMemoryWithExpiry(MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM, true, disableTime);
                } else {
                    brain.setMemory(MemoryModuleType.TIME_TRYING_TO_REACH_ADMIRE_ITEM, time.get() + 1);
                }
                return true;
            }
        };
    }
}
