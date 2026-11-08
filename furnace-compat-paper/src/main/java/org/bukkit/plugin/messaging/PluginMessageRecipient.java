package org.bukkit.plugin.messaging;

import java.util.Set;
import org.bukkit.plugin.Plugin;

/** 플러그인 메시지를 받을 수 있는 쪽(플레이어, 서버 = 모든 접속자). */
public interface PluginMessageRecipient {

    /**
     * 채널로 바이트를 보낸다. source 가 켜져 있고 그 채널을 보내는 채널로 등록했어야 한다. 받는 쪽 클라이언트가 그 채널을
     * minecraft:register 로 알리지 않았으면 보내지 않는다(Bukkit 과 같다).
     */
    void sendPluginMessage(Plugin source, String channel, byte[] message);

    /** 받는 쪽이 듣겠다고 알린 채널("bungeecord:main" 은 "BungeeCord" 로 보인다). */
    Set<String> getListeningPluginChannels();
}
