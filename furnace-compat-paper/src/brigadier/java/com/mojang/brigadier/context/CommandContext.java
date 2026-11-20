package com.mojang.brigadier.context;

/** Brigadier 1.3.10 대역(컴파일 전용). */
public class CommandContext<S> {
    public S getSource() { throw new UnsupportedOperationException("stub"); }

    public <V> V getArgument(String name, Class<V> clazz) { throw new UnsupportedOperationException("stub"); }
}
