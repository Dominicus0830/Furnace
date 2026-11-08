package org.bukkit.plugin.messaging;

/** 본문이 {@link Messenger#MAX_MESSAGE_SIZE} 바이트를 넘는다. */
public class MessageTooLargeException extends RuntimeException {

    public MessageTooLargeException() { super("Attempted to send a plugin message that was too large. The maximum length a plugin message may be is " + Messenger.MAX_MESSAGE_SIZE + "."); }

    public MessageTooLargeException(byte[] message) { this(message.length); }

    public MessageTooLargeException(int length) {
        this("Attempted to send a plugin message that was too large. The maximum length a plugin message may be is "
                + Messenger.MAX_MESSAGE_SIZE + " (attempted " + length + ").");
    }

    public MessageTooLargeException(String msg) { super(msg); }
}
