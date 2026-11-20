package com.mojang.serialization;

import com.mojang.datafixers.util.Pair;

/** DataFixerUpper 8.0.16 대역(컴파일·시험 전용): 진짜와 같은 서술자. */
public interface Decoder<A> {
    <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input);

    default <T> DataResult<A> parse(DynamicOps<T> ops, T input) { return decode(ops, input).map(Pair::getFirst); }
}
