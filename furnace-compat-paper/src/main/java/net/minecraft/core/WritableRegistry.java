package net.minecraft.core;

import net.minecraft.resources.ResourceKey;

/**
 * 쓸 수 있는 레지스트리(Mojang 이름 shim, N2b-6). 바닐라 {@code MappedRegistry} 가 구현하는 인터페이스라 플러그인이 {@code (WritableRegistry<T>)} 로 캐스팅한다.
 * Furnace 레지스트리는 서버 시작 때 얼어 있어 {@link #register} 는 바닐라 얼린 레지스트리처럼 {@code IllegalStateException} 으로 거절한다.
 */
public interface WritableRegistry<T> extends Registry<T> {

    Holder.Reference<T> register(ResourceKey<T> key, T value, RegistrationInfo info);

    boolean isEmpty();

    HolderGetter<T> createRegistrationLookup();
}
