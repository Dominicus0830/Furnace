package com.mojang.serialization;

/** DataFixerUpper 대역(컴파일·시험 전용). 진짜는 실험·사용 중단 판도 있다 — 대역은 stable 하나. */
public class Lifecycle {

    private static final Lifecycle STABLE = new Lifecycle() {
        @Override
        public String toString() { return "Stable"; }
    };

    public static Lifecycle stable() { return STABLE; }
}
