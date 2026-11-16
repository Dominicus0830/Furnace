package com.mojang.serialization;

/** DataFixerUpper 대역(컴파일 전용 — 시험 클래스패스에서는 값을 담는 상자로만 돈다). 진짜 Dynamic 과 같은 생성자·getOps·getValue 만 있다. */
public class Dynamic<T> {
    private final DynamicOps<T> ops;
    private final T value;

    public Dynamic(DynamicOps<T> ops, T value) {
        this.ops = ops;
        this.value = value;
    }

    public DynamicOps<T> getOps() { return ops; }

    public T getValue() { return value; }
}
