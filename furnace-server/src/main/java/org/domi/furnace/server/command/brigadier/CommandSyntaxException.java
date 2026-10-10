// Brigadier CommandSyntaxException — 메시지(Text)와 입력·커서(오류 위치 표시용).
package org.domi.furnace.server.command.brigadier;

import org.domi.furnace.server.net.protocol.Text;

public class CommandSyntaxException extends Exception {

    public static final int CONTEXT_AMOUNT = 10;
    /** 바닐라가 Brigadier 기본 예외 메시지를 번역 키로 바꿔 끼운 것(BrigadierExceptions). */
    public static final BuiltInExceptions BUILT_IN_EXCEPTIONS = new BuiltInExceptions();

    private final Object type;
    private final Text message;
    private final String input;
    private final int cursor;

    public CommandSyntaxException(Object type, Text message) {
        super(message.getString(), null, false, false);
        this.type = type;
        this.message = message;
        this.input = null;
        this.cursor = -1;
    }

    public CommandSyntaxException(Object type, Text message, String input, int cursor) {
        super(message.getString(), null, false, false);
        this.type = type;
        this.message = message;
        this.input = input;
        this.cursor = cursor;
    }

    @Override
    public String getMessage() {
        String m = message.getString();
        String context = getContext();
        return context != null ? m + " at position " + cursor + ": " + context : m;
    }

    public Text getRawMessage() { return message; }

    public String getContext() {
        if (input == null || cursor < 0) return null;
        StringBuilder b = new StringBuilder();
        int c = Math.min(input.length(), cursor);
        if (c > CONTEXT_AMOUNT) b.append("...");
        b.append(input, Math.max(0, c - CONTEXT_AMOUNT), c);
        b.append("<--[HERE]");
        return b.toString();
    }

    public Object getType() { return type; }

    public String getInput() { return input; }

    public int getCursor() { return cursor; }
}
