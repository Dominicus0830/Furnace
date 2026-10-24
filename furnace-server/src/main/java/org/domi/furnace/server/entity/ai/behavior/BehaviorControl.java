// 바닐라 BehaviorControl: 브레인이 시작·유지·종료시키는 행동 하나.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;

public interface BehaviorControl<E extends Mob> {
    enum Status { STOPPED, RUNNING }

    Status getStatus();

    boolean tryStart(ChunkLevel level, E entity, long gameTime);

    void tickOrStop(ChunkLevel level, E entity, long gameTime);

    void doStop(ChunkLevel level, E entity, long gameTime);

    String debugString();
}
