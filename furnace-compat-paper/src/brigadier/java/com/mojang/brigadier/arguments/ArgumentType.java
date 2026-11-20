package com.mojang.brigadier.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/** Brigadier 1.3 대역(컴파일·시험 전용): 진짜와 같은 서술자(기본 메서드는 진짜에만 있다). */
public interface ArgumentType<T> {
    T parse(StringReader reader) throws CommandSyntaxException;
}
