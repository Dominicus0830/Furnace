package org.domi.furnace.server.command.brigadier;

import java.util.function.BiFunction;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.VanillaPort;

@VanillaPort("com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType")
public final class Dynamic2CommandExceptionType {

    private final BiFunction<Object, Object, Text> function;

    public Dynamic2CommandExceptionType(BiFunction<Object, Object, Text> function) { this.function = function; }

    public CommandSyntaxException create(Object a, Object b) { return new CommandSyntaxException(this, function.apply(a, b)); }

    public CommandSyntaxException createWithContext(StringReader reader, Object a, Object b) {
        return new CommandSyntaxException(this, function.apply(a, b), reader.getString(), reader.getCursor());
    }
}
