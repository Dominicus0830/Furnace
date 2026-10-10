// 이벤트 버스. 상위 타입으로 등록해도 하위 이벤트를 받는다(예: PlayerEvent 로 모든 플레이어 이벤트).
// 처리기 배열은 등록이 바뀔 때만 다시 만들어 두므로 발행은 잠금·할당 없이 배열을 한 번 훑는다.
package org.domi.furnace.api.event;

import java.util.function.Consumer;
import org.domi.furnace.api.plugin.FurnacePlugin;

public interface EventBus {

    /** listener 의 @Subscribe 메서드를 모두 등록한다. */
    void register(FurnacePlugin plugin, Listener listener);

    /** 람다로 등록 — 리플렉션 없이 쓸 수 있다. */
    <E extends Event> EventSubscription subscribe(FurnacePlugin plugin, Class<E> type, EventPriority priority, boolean ignoreCancelled,
                                                  Consumer<? super E> handler);

    default <E extends Event> EventSubscription subscribe(FurnacePlugin plugin, Class<E> type, Consumer<? super E> handler) {
        return subscribe(plugin, type, EventPriority.NORMAL, false, handler);
    }

    void unregister(Listener listener);

    void unregisterAll(FurnacePlugin plugin);

    /** 이 이벤트(또는 상위 타입)를 받는 처리기가 있는가 — 없으면 서버는 이벤트 객체조차 만들지 않는다. */
    boolean hasSubscribers(Class<? extends Event> type);

    /** 처리기들을 순서대로 부르고 이벤트를 돌려준다. 처리기 예외는 로그만 남기고 다음 처리기로 간다. */
    <E extends Event> E call(E event);
}
