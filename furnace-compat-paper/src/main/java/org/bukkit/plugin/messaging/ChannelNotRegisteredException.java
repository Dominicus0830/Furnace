package org.bukkit.plugin.messaging;

/** 보내는 채널로 등록하지 않은 채널로 보내려 했다. */
public class ChannelNotRegisteredException extends RuntimeException {

    public ChannelNotRegisteredException() { super("Attempted to send a plugin message through an unregistered channel."); }

    public ChannelNotRegisteredException(String channel) {
        super("Attempted to send a plugin message through the unregistered channel `" + channel + "'.");
    }
}
