package org.domi.furnace.server.command.brigadier;

import org.domi.furnace.api.text.Text;

public final class Dynamic3CommandExceptionType {

    @FunctionalInterface
    public interface Function3 {
        Text apply(Object a, Object b, Object c);
    }

    private final Function3 function;

    public Dynamic3CommandExceptionType(Function3 function) { this.function = function; }

    public CommandSyntaxException create(Object a, Object b, Object c) { return new CommandSyntaxException(this, function.apply(a, b, c)); }

    public CommandSyntaxException createWithContext(StringReader reader, Object a, Object b, Object c) {
        return new CommandSyntaxException(this, function.apply(a, b, c), reader.getString(), reader.getCursor());
    }
}
