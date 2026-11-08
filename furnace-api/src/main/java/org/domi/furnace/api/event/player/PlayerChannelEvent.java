// 클라이언트가 minecraft:register / minecraft:unregister 로 플러그인 채널을 새로 알리거나 거둔 뒤(Bukkit PlayerRegisterChannelEvent·
// PlayerUnregisterChannelEvent). 채널 하나에 이벤트 하나 — 이미 알린 채널을 다시 알리면 오지 않는다.
// 그 플레이어의 연결(IO) 스레드에서 불린다. 구성 단계에서 알린 채널은 play 진입 때 PlayerJoinEvent 직전에 REGISTER 로 한꺼번에 온다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;

public final class PlayerChannelEvent extends PlayerEvent {

    /** 알렸는가, 거뒀는가. */
    public enum Action { REGISTER, UNREGISTER }

    private final String channel;
    private final Action action;

    public PlayerChannelEvent(Player player, String channel, Action action) {
        super(player);
        this.channel = channel;
        this.action = action;
    }

    /** 채널 이름(정규화된 "네임스페이스:이름"). */
    public String channel() { return channel; }

    public Action action() { return action; }
}
