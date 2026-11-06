// 바닐라 GameEvent.Context: 일으킨 엔티티(Entity 또는 Player, 없으면 null)와 영향받은 블록 상태(-1 = 없음).
package org.domi.furnace.server.world.gameevent;

import org.domi.furnace.server.block.BlockActor;

public record GameEventContext(Object sourceEntity, int affectedState) {

    public static final GameEventContext EMPTY = new GameEventContext(null, -1);

    public static GameEventContext of(Object entity) { return new GameEventContext(unwrap(entity), -1); }

    public static GameEventContext ofState(int state) { return new GameEventContext(null, state); }

    public static GameEventContext of(Object entity, int state) { return new GameEventContext(unwrap(entity), state); }

    /** BlockActor(플레이어 감싸개)는 본체로 푼다. */
    public static Object unwrap(Object o) { return o instanceof BlockActor a ? a.handle() : o; }
}
