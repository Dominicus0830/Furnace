package org.bukkit.plugin.messaging;

import java.util.Objects;
import org.bukkit.plugin.Plugin;

/** 받는 채널 등록 하나(메신저·플러그인·채널·리스너가 같으면 같은 등록). */
public final class PluginMessageListenerRegistration {

    private final Messenger messenger;
    private final Plugin plugin;
    private final String channel;
    private final PluginMessageListener listener;

    public PluginMessageListenerRegistration(Messenger messenger, Plugin plugin, String channel, PluginMessageListener listener) {
        if (messenger == null) throw new IllegalArgumentException("Messenger cannot be null!");
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null!");
        if (channel == null) throw new IllegalArgumentException("Channel cannot be null!");
        if (listener == null) throw new IllegalArgumentException("Listener cannot be null!");
        this.messenger = messenger;
        this.plugin = plugin;
        this.channel = channel;
        this.listener = listener;
    }

    public String getChannel() { return channel; }

    public PluginMessageListener getListener() { return listener; }

    public Plugin getPlugin() { return plugin; }

    /** 아직 메신저에 등록돼 있는가. */
    public boolean isValid() { return messenger.isRegistrationValid(this); }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PluginMessageListenerRegistration o)) return false;
        return messenger == o.messenger && plugin == o.plugin && channel.equals(o.channel) && listener == o.listener;
    }

    @Override
    public int hashCode() { return Objects.hash(System.identityHashCode(messenger), System.identityHashCode(plugin), channel, System.identityHashCode(listener)); }
}
