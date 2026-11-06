// 플레이어 명령 실행 직전 — "/명령 인자" 전체. 취소하거나 바꿀 수 있다(바꾸면 서명 없는 명령으로 실행). 연결 스레드에서 불린다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;

public final class PlayerCommandPreprocessEvent extends PlayerEvent implements Cancellable {

    private String message;
    private boolean cancelled;

    public PlayerCommandPreprocessEvent(Player player, String message) {
        super(player);
        this.message = message;
    }

    /** '/' 로 시작하는 명령 줄. */
    public String message() { return message; }

    public void message(String message) { this.message = message.startsWith("/") ? message : "/" + message; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
