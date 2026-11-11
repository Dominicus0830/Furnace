package org.bukkit.event.server;

import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;

/** RCON 명령 실행 직전(ServerCommandEvent 와 처리기 목록이 따로다 — ServerCommandEvent 처리기는 RCON 명령을 받지 않는다). */
public class RemoteServerCommandEvent extends ServerCommandEvent {

    private static final HandlerList handlers = new HandlerList();

    public RemoteServerCommandEvent(CommandSender sender, String command) { super(sender, command); }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
