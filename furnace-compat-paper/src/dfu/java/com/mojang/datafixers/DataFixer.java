package com.mojang.datafixers;

/** DataFixerUpper 대역(컴파일 전용). */
public interface DataFixer {
    <T> com.mojang.serialization.Dynamic<T> update(DSL.TypeReference type, com.mojang.serialization.Dynamic<T> input, int version, int newVersion);

    com.mojang.datafixers.schemas.Schema getSchema(int key);
}
