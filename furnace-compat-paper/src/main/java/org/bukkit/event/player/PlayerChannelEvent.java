package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * 플레이어(클라이언트)가 플러그인 채널을 알리거나 거둔 뒤. 처리기 목록은 이 클래스가 들고 있어 두 하위 이벤트가 나눠 쓴다(Bukkit 과 같다 —
 * 이 클래스를 받는 처리기는 둘 다 받는다). Furnace 는 그 플레이어의 연결 스레드에서 부른다.
 */
public abstract class PlayerChannelEvent extends PlayerEvent {

    private static final HandlerList handlers = new HandlerList();
    private final String channel;

    public PlayerChannelEvent(Player player, String channel) {
        super(player);
        this.channel = channel;
    }

    /** 채널 이름("bungeecord:main" 은 "BungeeCord"). */
    public final String getChannel() { return channel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
