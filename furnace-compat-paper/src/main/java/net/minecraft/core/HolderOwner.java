package net.minecraft.core;

/** 손잡이 소유자 표지(Mojang 이름 shim, N2b-6). 바닐라 인터페이스와 같은 모양(canSerializeIn 기본 구현). */
public interface HolderOwner<T> {

    default boolean canSerializeIn(HolderOwner<T> other) { return other == this; }
}
