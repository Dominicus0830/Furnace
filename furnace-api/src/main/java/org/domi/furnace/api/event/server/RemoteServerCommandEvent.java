// RCON 명령 실행 직전 — 취소하거나 바꿀 수 있다. RCON 연결 스레드에서 불린다(Paper RemoteServerCommandEvent 와 같은 자리).
// 취소하면 명령을 실행하지 않고 빈 응답을 돌려준다. 콘솔 입력의 ServerCommandEvent 는 이때 불리지 않는다.
package org.domi.furnace.api.event.server;

import org.domi.furnace.api.command.RemoteConsoleSender;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.Event;

public final class RemoteServerCommandEvent extends Event implements Cancellable {

    private final RemoteConsoleSender sender;
    private String command;
    private boolean cancelled;

    public RemoteServerCommandEvent(RemoteConsoleSender sender, String command) {
        this.sender = sender;
        this.command = command;
    }

    public RemoteConsoleSender sender() { return sender; }

    /** RCON 으로 받은 명령 줄 그대로('/' 로 시작할 수도 있다 — 바닐라가 앞 '/' 를 떼고 실행한다). */
    public String command() { return command; }

    public void command(String c) { this.command = c; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
