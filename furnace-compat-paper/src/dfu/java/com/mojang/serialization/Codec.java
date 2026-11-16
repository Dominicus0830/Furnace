package com.mojang.serialization;

/** DataFixerUpper 8.0.16 대역(컴파일 전용, jar 에 넣지 않는다 — 실행 때는 서버 제공 라이브러리 묶음 datafixerupper 의 진짜). 호환 계층이 쓰는 모양만. */
public interface Codec<A> extends Encoder<A>, Decoder<A> {}
