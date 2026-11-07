package org.bukkit.event.player;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class PlayerTeleportEvent extends PlayerMoveEvent {

    private final TeleportCause cause;

    public PlayerTeleportEvent(Player player, Location from, Location to) { this(player, from, to, TeleportCause.UNKNOWN); }

    public PlayerTeleportEvent(Player player, Location from, Location to, TeleportCause cause) {
        super(player, from, to);
        this.cause = cause;
    }

    /** Furnace 의 텔레포트 이벤트는 원인을 알리지 않아 UNKNOWN 이다(원인 사슬은 Furnace 의 CauseChain 이 따로 있다). */
    public TeleportCause getCause() { return cause; }

    public enum TeleportCause {
        ENDER_PEARL, COMMAND, PLUGIN, NETHER_PORTAL, END_PORTAL, SPECTATE, END_GATEWAY, CHORUS_FRUIT, DISMOUNT, EXIT_BED, UNKNOWN
    }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
