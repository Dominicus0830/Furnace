package com.mojang.serialization;

/** DataFixerUpper 대역(컴파일 전용). */
public interface Encoder<A> {
    <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix);
}
