package com.mojang.serialization;

import com.mojang.datafixers.util.Pair;

/** DataFixerUpper 대역(컴파일 전용). */
public interface Decoder<A> {
    <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input);
}
