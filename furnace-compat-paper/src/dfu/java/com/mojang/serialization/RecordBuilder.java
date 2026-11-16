package com.mojang.serialization;

/** DataFixerUpper 8.0.16 대역(컴파일 전용): 호환 계층이 쓰는 멤버만(진짜와 같은 서술자). */
public interface RecordBuilder<T> {
    RecordBuilder<T> add(String key, T value);
}
