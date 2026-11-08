package org.bukkit.plugin.messaging;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Bukkit 의 기본 메신저. 등록 표만 들고 있고, 클라이언트에 알리기·플러그인 수명 묶기는 Furnace 호환 계층의 하위 클래스가 한다.
 * 돌려주는 집합은 모두 바꿀 수 없는 사본이다.
 */
public class StandardMessenger implements Messenger {

    private final Map<String, Set<PluginMessageListenerRegistration>> incomingByChannel = new HashMap<>();
    private final Map<Plugin, Set<PluginMessageListenerRegistration>> incomingByPlugin = new HashMap<>();
    private final Map<String, Set<Plugin>> outgoingByChannel = new HashMap<>();
    private final Map<Plugin, Set<String>> outgoingByPlugin = new HashMap<>();
    private final Object incomingLock = new Object();
    private final Object outgoingLock = new Object();

    // ------------------------------------------------------------------ 표 조작

    private void addToOutgoing(Plugin plugin, String channel) {
        synchronized (outgoingLock) {
            outgoingByChannel.computeIfAbsent(channel, k -> new LinkedHashSet<>()).add(plugin);
            outgoingByPlugin.computeIfAbsent(plugin, k -> new LinkedHashSet<>()).add(channel);
        }
    }

    private void removeFromOutgoing(Plugin plugin, String channel) {
        synchronized (outgoingLock) {
            Set<Plugin> plugins = outgoingByChannel.get(channel);
            Set<String> channels = outgoingByPlugin.get(plugin);
            if (plugins != null) {
                plugins.remove(plugin);
                if (plugins.isEmpty()) outgoingByChannel.remove(channel);
            }
            if (channels != null) {
                channels.remove(channel);
                if (channels.isEmpty()) outgoingByPlugin.remove(plugin);
            }
        }
    }

    private void removeFromOutgoing(Plugin plugin) {
        synchronized (outgoingLock) {
            Set<String> channels = outgoingByPlugin.get(plugin);
            if (channels == null) return;
            for (String channel : channels.toArray(new String[0])) removeFromOutgoing(plugin, channel);
        }
    }

    private void addToIncoming(PluginMessageListenerRegistration registration) {
        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> byChannel = incomingByChannel.computeIfAbsent(registration.getChannel(), k -> new LinkedHashSet<>());
            if (byChannel.contains(registration)) throw new IllegalArgumentException("This registration already exists");
            byChannel.add(registration);
            incomingByPlugin.computeIfAbsent(registration.getPlugin(), k -> new LinkedHashSet<>()).add(registration);
        }
    }

    private void removeFromIncoming(PluginMessageListenerRegistration registration) {
        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> byChannel = incomingByChannel.get(registration.getChannel());
            if (byChannel != null) {
                byChannel.remove(registration);
                if (byChannel.isEmpty()) incomingByChannel.remove(registration.getChannel());
            }
            Set<PluginMessageListenerRegistration> byPlugin = incomingByPlugin.get(registration.getPlugin());
            if (byPlugin != null) {
                byPlugin.remove(registration);
                if (byPlugin.isEmpty()) incomingByPlugin.remove(registration.getPlugin());
            }
        }
    }

    private void removeFromIncoming(Plugin plugin, String channel) {
        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> regs = incomingByPlugin.get(plugin);
            if (regs == null) return;
            for (PluginMessageListenerRegistration r : regs.toArray(new PluginMessageListenerRegistration[0])) {
                if (r.getChannel().equals(channel)) removeFromIncoming(r);
            }
        }
    }

    private void removeFromIncoming(Plugin plugin) {
        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> regs = incomingByPlugin.get(plugin);
            if (regs == null) return;
            for (PluginMessageListenerRegistration r : regs.toArray(new PluginMessageListenerRegistration[0])) removeFromIncoming(r);
        }
    }

    // ------------------------------------------------------------------ Messenger

    @Override
    public boolean isReservedChannel(String channel) {
        channel = validateAndCorrectChannel(channel);
        return channel.equals("minecraft:register") || channel.equals("minecraft:unregister");   // Paper 규칙(Spigot 은 "minecraft" 를 담으면 모두)
    }

    @Override
    public void registerOutgoingPluginChannel(Plugin plugin, String channel) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        channel = validateAndCorrectChannel(channel);
        if (isReservedChannel(channel)) throw new ReservedChannelException(channel);
        addToOutgoing(plugin, channel);
    }

    @Override
    public void unregisterOutgoingPluginChannel(Plugin plugin, String channel) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        channel = validateAndCorrectChannel(channel);
        removeFromOutgoing(plugin, channel);
    }

    @Override
    public void unregisterOutgoingPluginChannel(Plugin plugin) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        removeFromOutgoing(plugin);
    }

    @Override
    public PluginMessageListenerRegistration registerIncomingPluginChannel(Plugin plugin, String channel, PluginMessageListener listener) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        channel = validateAndCorrectChannel(channel);
        if (isReservedChannel(channel)) throw new ReservedChannelException(channel);
        if (listener == null) throw new IllegalArgumentException("Listener cannot be null");
        PluginMessageListenerRegistration result = new PluginMessageListenerRegistration(this, plugin, channel, listener);
        addToIncoming(result);
        return result;
    }

    @Override
    public void unregisterIncomingPluginChannel(Plugin plugin, String channel, PluginMessageListener listener) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        if (listener == null) throw new IllegalArgumentException("Listener cannot be null");
        channel = validateAndCorrectChannel(channel);
        removeFromIncoming(new PluginMessageListenerRegistration(this, plugin, channel, listener));
    }

    @Override
    public void unregisterIncomingPluginChannel(Plugin plugin, String channel) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        channel = validateAndCorrectChannel(channel);
        removeFromIncoming(plugin, channel);
    }

    @Override
    public void unregisterIncomingPluginChannel(Plugin plugin) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        removeFromIncoming(plugin);
    }

    @Override
    public Set<String> getOutgoingChannels() {
        synchronized (outgoingLock) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(outgoingByChannel.keySet()));
        }
    }

    @Override
    public Set<String> getOutgoingChannels(Plugin plugin) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        synchronized (outgoingLock) {
            Set<String> channels = outgoingByPlugin.get(plugin);
            return channels == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(channels));
        }
    }

    @Override
    public Set<String> getIncomingChannels() {
        synchronized (incomingLock) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(incomingByChannel.keySet()));
        }
    }

    @Override
    public Set<String> getIncomingChannels(Plugin plugin) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> regs = incomingByPlugin.get(plugin);
            if (regs == null) return Collections.emptySet();
            Set<String> out = new LinkedHashSet<>();
            for (PluginMessageListenerRegistration r : regs) out.add(r.getChannel());
            return Collections.unmodifiableSet(out);
        }
    }

    @Override
    public Set<PluginMessageListenerRegistration> getIncomingChannelRegistrations(Plugin plugin) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> regs = incomingByPlugin.get(plugin);
            return regs == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(regs));
        }
    }

    @Override
    public Set<PluginMessageListenerRegistration> getIncomingChannelRegistrations(String channel) {
        channel = validateAndCorrectChannel(channel);
        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> regs = incomingByChannel.get(channel);
            return regs == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(regs));
        }
    }

    @Override
    public Set<PluginMessageListenerRegistration> getIncomingChannelRegistrations(Plugin plugin, String channel) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        channel = validateAndCorrectChannel(channel);
        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> regs = incomingByPlugin.get(plugin);
            if (regs == null) return Collections.emptySet();
            Set<PluginMessageListenerRegistration> out = new LinkedHashSet<>();
            for (PluginMessageListenerRegistration r : regs) if (r.getChannel().equals(channel)) out.add(r);
            return Collections.unmodifiableSet(out);
        }
    }

    @Override
    public boolean isRegistrationValid(PluginMessageListenerRegistration registration) {
        if (registration == null) throw new IllegalArgumentException("Registration cannot be null");
        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> regs = incomingByPlugin.get(registration.getPlugin());
            return regs != null && regs.contains(registration);
        }
    }

    @Override
    public boolean isIncomingChannelRegistered(Plugin plugin, String channel) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        channel = validateAndCorrectChannel(channel);
        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> regs = incomingByPlugin.get(plugin);
            if (regs == null) return false;
            for (PluginMessageListenerRegistration r : regs) if (r.getChannel().equals(channel)) return true;
            return false;
        }
    }

    @Override
    public boolean isOutgoingChannelRegistered(Plugin plugin, String channel) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        channel = validateAndCorrectChannel(channel);
        synchronized (outgoingLock) {
            Set<String> channels = outgoingByPlugin.get(plugin);
            return channels != null && channels.contains(channel);
        }
    }

    @Override
    public void dispatchIncomingMessage(Player source, String channel, byte[] message) {
        if (source == null) throw new IllegalArgumentException("Player source cannot be null");
        if (message == null) throw new IllegalArgumentException("Message cannot be null");
        channel = validateAndCorrectChannel(channel);   // "bungeecord:main" → "BungeeCord": 리스너는 옛 이름을 받는다
        // 아래 조회가 한 번 더 바로잡아 등록 키("bungeecord:main")로 찾는다
        for (PluginMessageListenerRegistration registration : getIncomingChannelRegistrations(channel)) {
            deliver(registration, channel, source, message);
        }
    }

    /** 리스너 하나에 넘긴다. 리스너 예외는 그 플러그인 로거에 남기고 다음 리스너로 간다(Bukkit 과 같다). */
    protected static void deliver(PluginMessageListenerRegistration registration, String channel, Player source, byte[] message) {
        try {
            registration.getListener().onPluginMessageReceived(channel, source, message);
        } catch (Throwable t) {
            registration.getPlugin().getLogger().log(Level.WARNING,
                    String.format("Plugin %s generated an exception whilst handling plugin message", registration.getPlugin().getDescription().getFullName()), t);
        }
    }

    // ------------------------------------------------------------------ 검사

    /** @deprecated {@link #validateAndCorrectChannel(String)} 을 쓴다. */
    @Deprecated
    public static void validateChannel(String channel) { validateAndCorrectChannel(channel); }

    /**
     * 채널 이름을 검사하고 옛 BungeeCord 이름을 바로잡는다. "BungeeCord" → "bungeecord:main", 거꾸로 "bungeecord:main" → "BungeeCord"
     * (등록은 새 이름으로 하고, 받은 메시지는 옛 이름으로 리스너에 넘기기 위해서다 — 그래서 같은 값에 두 번 부르면 되돌아온다).
     * ':' 가 없거나 대문자가 있으면 IllegalArgumentException, {@link Messenger#MAX_CHANNEL_SIZE} 를 넘으면 {@link ChannelNameTooLongException}.
     */
    public static String validateAndCorrectChannel(String channel) {
        if (channel == null) throw new IllegalArgumentException("Channel cannot be null");
        if (channel.equals("BungeeCord")) return "bungeecord:main";
        if (channel.equals("bungeecord:main")) return "BungeeCord";
        if (channel.length() > Messenger.MAX_CHANNEL_SIZE) throw new ChannelNameTooLongException(channel);
        if (channel.indexOf(':') == -1) throw new IllegalArgumentException("Channel must contain : separator (attempted to use " + channel + ")");
        if (!channel.toLowerCase(Locale.ROOT).equals(channel)) throw new IllegalArgumentException("Channel must be entirely lowercase (attempted to use " + channel + ")");
        return channel;
    }

    /** 보내기 전 검사: 켜진 플러그인, 보내는 채널로 등록, 크기 한도. */
    public static void validatePluginMessage(Messenger messenger, Plugin source, String channel, byte[] message) {
        if (messenger == null) throw new IllegalArgumentException("Messenger cannot be null");
        if (source == null) throw new IllegalArgumentException("Plugin source cannot be null");
        if (!source.isEnabled()) throw new IllegalArgumentException("Plugin must be enabled to send messages");
        if (message == null) throw new IllegalArgumentException("Message cannot be null");
        if (!messenger.isOutgoingChannelRegistered(source, channel)) throw new ChannelNotRegisteredException(channel);
        if (message.length > Messenger.MAX_MESSAGE_SIZE) throw new MessageTooLargeException(message);
    }
}
