package org.bukkit.event.player;

import org.bukkit.entity.Player;

/** 클라이언트가 minecraft:unregister 로 채널을 거뒀다. */
public class PlayerUnregisterChannelEvent extends PlayerChannelEvent {

    public PlayerUnregisterChannelEvent(Player player, String channel) { super(player, channel); }
}
