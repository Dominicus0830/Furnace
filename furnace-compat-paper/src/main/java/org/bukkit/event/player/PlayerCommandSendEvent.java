package org.bukkit.event.player;

import java.util.Collection;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * 플레이어에게 명령 목록(트리)을 보내기 직전(접속·권한 변경·명령 등록 변경·updateCommands). 목록에서 빼면 클라이언트 자동 완성에서 사라진다 —
 * 서버에서 쳐서 실행하는 것은 그대로다(Paper 와 같다). 이름을 더해도 아무 일도 없다.
 */
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
