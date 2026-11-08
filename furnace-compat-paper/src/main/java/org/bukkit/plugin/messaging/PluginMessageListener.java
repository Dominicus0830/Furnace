package org.bukkit.plugin.messaging;

import org.bukkit.entity.Player;

/** 플러그인 채널로 온 메시지를 받는다. Furnace 는 전역 스레드("주 스레드")에서 부른다 — Paper 의 주 스레드와 같은 자리. */
public interface PluginMessageListener {

    void onPluginMessageReceived(String channel, Player player, byte[] message);
}
