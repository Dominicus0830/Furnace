package org.domi.furnace.api.registry;

/** 플러그인 레지스트리 항목 변화 알림. 플러그인이 꺼져 빠지는 항목도 removed 로 알린다. */
public interface RegistryListener<T> {

    default void added(String key, T value) {}

    default void removed(String key, T value) {}
}
