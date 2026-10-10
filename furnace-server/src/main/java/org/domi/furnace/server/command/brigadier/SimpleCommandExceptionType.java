package org.domi.furnace.server.command.brigadier;

import org.domi.furnace.server.net.protocol.Text;

public final class SimpleCommandExceptionType {

    private final Text message;

    public SimpleCommandExceptionType(Text message) { this.message = message; }

    public CommandSyntaxException create() { return new CommandSyntaxException(this, message); }

    public CommandSyntaxException createWithContext(StringReader reader) {
        return new CommandSyntaxException(this, message, reader.getString(), reader.getCursor());
    }

    @Override
    public String toString() { return message.getString(); }
}
