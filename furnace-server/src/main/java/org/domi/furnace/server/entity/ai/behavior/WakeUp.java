// 바닐라 WakeUp: 쉬는 활동이 아닌데 자고 있으면 일어난다.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.Activity;

public final class WakeUp {
    private WakeUp() {}

    public static OneShot<Mob> create() {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long time) {
                if (!mob.getBrain().isActive(Activity.REST) && mob.isSleeping()) {
                    mob.stopSleeping();
                    return true;
                }
                return false;
            }
        };
    }
}
