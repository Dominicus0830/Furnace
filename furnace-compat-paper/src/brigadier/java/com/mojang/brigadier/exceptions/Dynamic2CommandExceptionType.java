package com.mojang.brigadier.exceptions;

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.Message;

/** Brigadier 1.3.10 대역(컴파일·시험 전용). */
public class Dynamic2CommandExceptionType implements CommandExceptionType {
    private final Function function;

    public Dynamic2CommandExceptionType(Function function) { this.function = function; }

    public CommandSyntaxException create(Object a, Object b) { return new CommandSyntaxException(this, function.apply(a, b)); }

    public CommandSyntaxException createWithContext(ImmutableStringReader reader, Object a, Object b) {
        return new CommandSyntaxException(this, function.apply(a, b), reader.getString(), reader.getCursor());
    }

    public interface Function {
        Message apply(Object a, Object b);
    }
}
