// 취소할 수 있는 이벤트 — 취소되면 서버는 그 동작을 하지 않는다.
package org.domi.furnace.api.event;

public interface Cancellable {

    boolean isCancelled();

    void setCancelled(boolean cancelled);
}
