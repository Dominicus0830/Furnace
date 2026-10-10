// 콘솔 명령 실행 직전 — 취소하거나 바꿀 수 있다. 콘솔 입력 스레드에서 불린다.
package org.domi.furnace.api.event.server;

import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.Event;

public final class ServerCommandEvent extends Event implements Cancellable {

    private final CommandSender sender;
    private String command;
    private boolean cancelled;

    public ServerCommandEvent(CommandSender sender, String command) {
        this.sender = sender;
        this.command = command;
    }

    public CommandSender sender() { return sender; }

    /** '/' 없는 명령 줄. */
    public String command() { return command; }

    public void command(String c) { this.command = c; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
