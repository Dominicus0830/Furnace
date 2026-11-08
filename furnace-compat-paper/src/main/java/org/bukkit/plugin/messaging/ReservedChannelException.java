package org.bukkit.plugin.messaging;

/** 예약된 채널({@link Messenger#isReservedChannel})에 등록하려 했다. */
public class ReservedChannelException extends RuntimeException {

    public ReservedChannelException() { super("Attempted to register for a reserved channel name."); }

    public ReservedChannelException(String name) { super("Attempted to register for a reserved channel name ('" + name + "')"); }
}
