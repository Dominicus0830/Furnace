package org.bukkit.event.server;

import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class ServerCommandEvent extends ServerEvent implements Cancellable {

    private String command;
    private final CommandSender sender;
    private boolean cancelled;

    public ServerCommandEvent(CommandSender sender, String command) {
        this.command = command;
        this.sender = sender;
    }

    public String getCommand() { return command; }

    public void setCommand(String message) { this.command = message; }

    public CommandSender getSender() { return sender; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
