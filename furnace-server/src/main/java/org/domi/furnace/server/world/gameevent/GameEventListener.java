// 바닐라 GameEventListener: 위치·듣는 반경·처리. 전달 방식 BY_DISTANCE 는 가까운 순으로 모아 처리한다.
package org.domi.furnace.server.world.gameevent;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Vec3;

public interface GameEventListener {

    enum DeliveryMode { UNSPECIFIED, BY_DISTANCE }

    PositionSource getListenerSource();

    int getListenerRadius();

    boolean handleGameEvent(ChunkLevel level, GameEvent event, GameEventContext context, Vec3 pos);

    default DeliveryMode getDeliveryMode() { return DeliveryMode.UNSPECIFIED; }

    /** GameEventListener.Provider(블록 엔티티·엔티티가 가진 리스너). */
    interface Provider {
        GameEventListener getListener();
    }
}
