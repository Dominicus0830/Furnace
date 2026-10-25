// 바닐라 StopHoldingItemIfNoLongerAdmiring: 감상이 끝났는데 반대손에 물건이 있으면 내려놓는다(방패 제외).
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.Piglin;
import org.domi.furnace.server.entity.PiglinAi;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.item.ItemTools;

public final class StopHoldingItemIfNoLongerAdmiring {
    private StopHoldingItemIfNoLongerAdmiring() {}

    public static OneShot<Piglin> create() {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Piglin piglin, long gameTime) {
                if (!absent(piglin, MemoryModuleType.ADMIRING_ITEM)) return false;
                var off = piglin.getItemBySlot(EquipmentSlot.OFFHAND);
                if (!off.isEmpty() && !ItemTools.itemName(off).equals("minecraft:shield")) {
                    PiglinAi.stopHoldingOffHandItem(piglin, true);
                    return true;
                }
                return false;
            }
        };
    }
}
