package net.minecraft.resources;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.Registry;

/**
 * 레지스트리 항목 키(Mojang 이름 shim, N0). 같은 (레지스트리, 위치)는 같은 객체(바닐라 {@code akq} 처럼 내부 표에 붙잡아 두어 {@code ==} 로 비교된다).
 * toString {@code ResourceKey[minecraft:block / minecraft:stone]} 은 바닐라와 같다. 코덱·StreamCodec 은 DataFixerUpper·Netty 코덱이 없어 두지 않았다.
 */
public class ResourceKey<T> {

    private static final ConcurrentMap<InternKey, ResourceKey<?>> VALUES = new ConcurrentHashMap<>();

    private final ResourceLocation registryName;
    private final ResourceLocation location;

    private ResourceKey(ResourceLocation registryName, ResourceLocation location) {
        this.registryName = registryName;
        this.location = location;
    }

    public static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>> registryKey, ResourceLocation location) {
        return create(registryKey.location, location);
    }

    public static <T> ResourceKey<Registry<T>> createRegistryKey(ResourceLocation location) {
        return create(Registries.ROOT_REGISTRY_NAME, location);
    }

    @SuppressWarnings("unchecked")
    private static <T> ResourceKey<T> create(ResourceLocation registryName, ResourceLocation location) {
        return (ResourceKey<T>) VALUES.computeIfAbsent(new InternKey(registryName, location), k -> new ResourceKey<>(k.registry, k.location));
    }

    @Override
    public String toString() { return "ResourceKey[" + registryName + " / " + location + "]"; }

    public boolean isFor(ResourceKey<? extends Registry<?>> registryKey) { return registryName.equals(registryKey.location()); }

    @SuppressWarnings("unchecked")
    public <E> Optional<ResourceKey<E>> cast(ResourceKey<? extends Registry<E>> registryKey) {
        return isFor(registryKey) ? Optional.of((ResourceKey<E>) this) : Optional.empty();
    }

    public ResourceLocation location() { return location; }

    public ResourceLocation registry() { return registryName; }

    public ResourceKey<Registry<T>> registryKey() { return createRegistryKey(registryName); }

    private record InternKey(ResourceLocation registry, ResourceLocation location) {}

    /** 루트 이름(바닐라 Registries.ROOT_REGISTRY_NAME) — Registries 의 클래스 초기화 순서와 엮이지 않게 따로 둔다. */
    private static final class Registries {
        static final ResourceLocation ROOT_REGISTRY_NAME = ResourceLocation.withDefaultNamespace("root");
    }
}
