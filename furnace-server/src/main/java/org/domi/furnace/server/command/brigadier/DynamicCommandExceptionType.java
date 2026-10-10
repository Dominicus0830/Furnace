package org.domi.furnace.server.command.brigadier;

import java.util.function.Function;
import org.domi.furnace.server.net.protocol.Text;

public final class DynamicCommandExceptionType {

    private final Function<Object, Text> function;

    public DynamicCommandExceptionType(Function<Object, Text> function) { this.function = function; }

    public CommandSyntaxException create(Object arg) { return new CommandSyntaxException(this, function.apply(arg)); }

    public CommandSyntaxException createWithContext(StringReader reader, Object arg) {
        return new CommandSyntaxException(this, function.apply(arg), reader.getString(), reader.getCursor());
    }
}
