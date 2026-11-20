package com.mojang.serialization;

/** DataFixerUpper 8.0.16 대역(컴파일·시험 전용): 진짜와 같은 서술자. */
public interface Encoder<A> {
    <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix);

    default <T> DataResult<T> encodeStart(DynamicOps<T> ops, A input) { return encode(input, ops, ops.empty()); }
}
