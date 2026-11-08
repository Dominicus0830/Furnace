package org.bukkit.event.player;

import org.bukkit.entity.Player;

/** 클라이언트가 minecraft:register 로 채널을 새로 알렸다. */
public class PlayerRegisterChannelEvent extends PlayerChannelEvent {

    public PlayerRegisterChannelEvent(Player player, String channel) { super(player, channel); }
}
