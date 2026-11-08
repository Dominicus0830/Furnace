// 플레이어 명령 실행 직전 — "/명령 인자" 전체. 취소하거나 바꿀 수 있다(바꾸면 서명 없는 명령으로 실행). 채팅 명령은 연결 스레드에서 불린다.
// 표지판 명령(하위 이벤트)은 그 표지판의 region 스레드에서 온다 — 같은 플레이어의 두 경로가 동시에 같은 처리기에 들어올 수 있다.
// 표지판 클릭 명령도 {@link PlayerSignCommandPreprocessEvent} 로 같은 처리기에 온다(Paper 와 같다 — 명령 기록·보안 플러그인이 받는다).
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;

public class PlayerCommandPreprocessEvent extends PlayerEvent implements Cancellable {

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
