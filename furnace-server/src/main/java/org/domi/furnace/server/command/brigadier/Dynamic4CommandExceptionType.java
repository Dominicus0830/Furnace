package org.domi.furnace.server.command.brigadier;

import org.domi.furnace.api.text.Text;

public final class Dynamic4CommandExceptionType {

    /** 인자 네 개를 받아 메시지를 만드는 함수. */
    @FunctionalInterface
    public interface Function4 {
        Text apply(Object a, Object b, Object c, Object d);
    }

    private final Function4 function;

    public Dynamic4CommandExceptionType(Function4 function) { this.function = function; }

    public CommandSyntaxException create(Object a, Object b, Object c, Object d) { return new CommandSyntaxException(this, function.apply(a, b, c, d)); }

    public CommandSyntaxException createWithContext(StringReader reader, Object a, Object b, Object c, Object d) {
        return new CommandSyntaxException(this, function.apply(a, b, c, d), reader.getString(), reader.getCursor());
    }
}
