package com.mojang.datafixers.util;

/** DataFixerUpper 8.0.16 대역(컴파일·시험 전용): 호환 계층이 쓰는 멤버만(진짜와 같은 서술자). */
public class Pair<F, S> {
    private final F first;
    private final S second;

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    public static <F, S> Pair<F, S> of(F first, S second) { return new Pair<>(first, second); }

    public F getFirst() { return first; }

    public S getSecond() { return second; }

    @Override
    public boolean equals(Object o) { return o instanceof Pair<?, ?> p && java.util.Objects.equals(first, p.first) && java.util.Objects.equals(second, p.second); }

    @Override
    public int hashCode() { return java.util.Objects.hash(first, second); }

    @Override
    public String toString() { return "(" + first + ", " + second + ")"; }
}
