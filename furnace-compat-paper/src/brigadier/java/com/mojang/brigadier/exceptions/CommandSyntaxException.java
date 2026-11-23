package com.mojang.brigadier.exceptions;

import com.mojang.brigadier.Message;

/** Brigadier 1.3.10 대역(컴파일·시험 전용): 진짜와 같은 서술자·문구("… at position N: …<--[HERE]"). */
public class CommandSyntaxException extends Exception {
    public static final int CONTEXT_AMOUNT = 10;
    public static boolean ENABLE_COMMAND_STACK_TRACES = true;
    public static BuiltInExceptionProvider BUILT_IN_EXCEPTIONS = new BuiltInExceptions();

    private final CommandExceptionType type;
    private final Message message;
    private final String input;
    private final int cursor;

    public CommandSyntaxException(CommandExceptionType type, Message message) {
        super(message.getString(), null, ENABLE_COMMAND_STACK_TRACES, ENABLE_COMMAND_STACK_TRACES);
        this.type = type;
        this.message = message;
        this.input = null;
        this.cursor = -1;
    }

    public CommandSyntaxException(CommandExceptionType type, Message message, String input, int cursor) {
        super(message.getString(), null, ENABLE_COMMAND_STACK_TRACES, ENABLE_COMMAND_STACK_TRACES);
        this.type = type;
        this.message = message;
        this.input = input;
        this.cursor = cursor;
    }

    @Override
    public String getMessage() {
        String m = message.getString();
        String context = getContext();
        if (context != null) m += " at position " + cursor + ": " + context;
        return m;
    }

    public Message getRawMessage() { return message; }

    public String getContext() {
        if (input == null || cursor < 0) return null;
        StringBuilder b = new StringBuilder();
        int c = Math.min(input.length(), cursor);
        if (c > CONTEXT_AMOUNT) b.append("...");
        b.append(input, Math.max(0, c - CONTEXT_AMOUNT), c);
        b.append("<--[HERE]");
        return b.toString();
    }

    public CommandExceptionType getType() { return type; }

    public String getInput() { return input; }

    public int getCursor() { return cursor; }
}
