package org.bukkit.plugin.messaging;

import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 플러그인 채널(custom_payload) 등록·전달 창구. 서버마다 하나({@code Bukkit.getMessenger()}).
 * 채널 이름은 "네임스페이스:이름" 소문자이고, 옛 이름 "BungeeCord" 는 "bungeecord:main" 으로 바뀐다({@link StandardMessenger#validateAndCorrectChannel}).
 */
public interface Messenger {

    /** 플러그인 메시지 본문의 최대 바이트 수. */
    int MAX_MESSAGE_SIZE = 1048576;

    /** 채널 이름의 최대 글자 수. */
    int MAX_CHANNEL_SIZE = 64;

    /** 플러그인이 쓸 수 없는 채널인가("minecraft" 를 담은 이름, minecraft:brand 는 예외). */
    boolean isReservedChannel(String channel);

    void registerOutgoingPluginChannel(Plugin plugin, String channel);

    void unregisterOutgoingPluginChannel(Plugin plugin, String channel);

    void unregisterOutgoingPluginChannel(Plugin plugin);

    PluginMessageListenerRegistration registerIncomingPluginChannel(Plugin plugin, String channel, PluginMessageListener listener);

    void unregisterIncomingPluginChannel(Plugin plugin, String channel, PluginMessageListener listener);

    void unregisterIncomingPluginChannel(Plugin plugin, String channel);

    void unregisterIncomingPluginChannel(Plugin plugin);

    Set<String> getOutgoingChannels();

    Set<String> getOutgoingChannels(Plugin plugin);

    Set<String> getIncomingChannels();

    Set<String> getIncomingChannels(Plugin plugin);

    Set<PluginMessageListenerRegistration> getIncomingChannelRegistrations(Plugin plugin);

    Set<PluginMessageListenerRegistration> getIncomingChannelRegistrations(String channel);

    Set<PluginMessageListenerRegistration> getIncomingChannelRegistrations(Plugin plugin, String channel);

    boolean isRegistrationValid(PluginMessageListenerRegistration registration);

    boolean isIncomingChannelRegistered(Plugin plugin, String channel);

    boolean isOutgoingChannelRegistered(Plugin plugin, String channel);

    /** 받은 메시지를 그 채널의 리스너들에게 넘긴다(서버가 부른다). */
    void dispatchIncomingMessage(Player source, String channel, byte[] message);
}
