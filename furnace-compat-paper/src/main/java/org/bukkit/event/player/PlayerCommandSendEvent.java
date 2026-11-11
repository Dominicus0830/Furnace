package org.bukkit.event.player;

import java.util.Collection;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/** 접속한 플레이어에게 명령 목록(트리)을 보내기 직전. 목록에서 빼면 클라이언트 자동 완성에서 사라진다. Furnace 는 아직 알리지 않는다. */
public class PlayerCommandSendEvent extends PlayerEvent {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    protected Collection<String> commands;

    public PlayerCommandSendEvent(Player player, Collection<String> commands) {
        super(player);
        this.commands = commands;
    }

    public Collection<String> getCommands() { return commands; }
}
