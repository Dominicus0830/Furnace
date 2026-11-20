package com.mojang.brigadier;

/** Brigadier 1.3.10 대역(컴파일 전용 — jar 에 넣지 않는다, build.gradle.kts 의 brigadier 소스셋). */
public interface ImmutableStringReader {
    String getString();

    int getRemainingLength();

    int getTotalLength();

    int getCursor();

    String getRead();

    String getRemaining();

    boolean canRead(int length);

    boolean canRead();

    char peek();

    char peek(int offset);
}
