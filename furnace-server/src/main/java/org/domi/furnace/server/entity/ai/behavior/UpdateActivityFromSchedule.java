// 바닐라 UpdateActivityFromSchedule: 시간표(하루 시각)에 맞춰 활동을 바꾼다(브레인이 20틱마다만 실제로 확인).
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;

public final class UpdateActivityFromSchedule {
    private UpdateActivityFromSchedule() {}

    public static OneShot<Mob> create() {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long time) {
                mob.getBrain().updateActivityFromSchedule(level.dayTime(), level.gameTime());
                return true;
            }
        };
    }
}
