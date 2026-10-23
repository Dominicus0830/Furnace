// 게임 모드가 바뀌기 직전 — 취소할 수 있다. 바꾸는 쪽 스레드(명령 등)에서 불린다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.GameMode;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;

public final class PlayerGameModeChangeEvent extends PlayerEvent implements Cancellable {

    private final GameMode newGameMode;
    private boolean cancelled;

    public PlayerGameModeChangeEvent(Player player, GameMode newGameMode) {
        super(player);
        this.newGameMode = newGameMode;
    }

    public GameMode newGameMode() { return newGameMode; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
