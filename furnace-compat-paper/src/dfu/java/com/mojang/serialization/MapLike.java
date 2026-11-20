package com.mojang.serialization;

import com.mojang.datafixers.util.Pair;
import java.util.stream.Stream;

/** DataFixerUpper 8.0.16 대역(컴파일·시험 전용): 진짜와 같은 서술자. */
public interface MapLike<T> {
    T get(T key);

    T get(String key);

    Stream<Pair<T, T>> entries();

    static <T> MapLike<T> forMap(java.util.Map<T, T> map, DynamicOps<T> ops) {
        return new MapLike<>() {
            @Override
            public T get(T key) { return map.get(key); }

            @Override
            public T get(String key) { return get(ops.createString(key)); }

            @Override
            public Stream<Pair<T, T>> entries() { return map.entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue())); }
        };
    }
}
