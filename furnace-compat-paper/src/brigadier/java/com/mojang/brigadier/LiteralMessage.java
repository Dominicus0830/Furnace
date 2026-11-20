package com.mojang.brigadier;

/** Brigadier 1.3.10 대역(컴파일·시험 전용): 글자 그대로의 메시지. */
public class LiteralMessage implements Message {
    private final String string;

    public LiteralMessage(String string) { this.string = string; }

    @Override
    public String getString() { return string; }

    @Override
    public String toString() { return string; }
}
