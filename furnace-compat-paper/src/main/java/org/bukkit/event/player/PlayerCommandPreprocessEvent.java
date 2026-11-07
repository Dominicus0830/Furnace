package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class PlayerCommandPreprocessEvent extends PlayerEvent implements Cancellable {

    private boolean cancel;
    private String message;

    public PlayerCommandPreprocessEvent(Player player, String message) {
        super(player);
        this.message = message;
    }

    /** 앞에 '/' 가 붙은 명령 줄. */
    public String getMessage() { return message; }

    public void setMessage(String command) {
        if (command == null) throw new IllegalArgumentException("Command cannot be null");
        if (command.isEmpty()) throw new IllegalArgumentException("Command cannot be empty");
        this.message = command;
    }

    @Override
    public boolean isCancelled() { return cancel; }

    @Override
    public void setCancelled(boolean cancel) { this.cancel = cancel; }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
