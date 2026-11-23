package com.mojang.brigadier.exceptions;

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.Message;

/** Brigadier 1.3.10 대역(컴파일·시험 전용). */
public class SimpleCommandExceptionType implements CommandExceptionType {
    private final Message message;

    public SimpleCommandExceptionType(Message message) { this.message = message; }

    public CommandSyntaxException create() { return new CommandSyntaxException(this, message); }

    public CommandSyntaxException createWithContext(ImmutableStringReader reader) {
        return new CommandSyntaxException(this, message, reader.getString(), reader.getCursor());
    }

    @Override
    public String toString() { return message.getString(); }
}
