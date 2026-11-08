package org.bukkit.plugin.messaging;

/** 채널 이름이 {@link Messenger#MAX_CHANNEL_SIZE} 글자를 넘는다(Paper 기본 32767). */
public class ChannelNameTooLongException extends RuntimeException {

    public ChannelNameTooLongException() {
        super("Attempted to send a Plugin Message to a channel that was too large. The maximum length a channel may be is "
                + Messenger.MAX_CHANNEL_SIZE + " chars.");
    }

    public ChannelNameTooLongException(String channel) {
        super("Attempted to send a Plugin Message to a channel that was too large. The maximum length a channel may be is "
                + Messenger.MAX_CHANNEL_SIZE + " chars (attempted " + channel.length() + " - '" + channel + ").");
    }
}
