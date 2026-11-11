package net.minecraft.tags;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * 태그 이름(Mojang 이름 shim, N0). 바닐라 1.21.1 {@code awu} 처럼 record 이고 {@code create} 가 intern 해 같은 이름이면 같은 객체다.
 * toString {@code TagKey[minecraft:block / minecraft:logs]}, {@code isFor} 는 레지스트리 키 동일 객체 비교. 코덱은 DataFixerUpper 가 없어 두지 않았다.
 */
public record TagKey<T>(ResourceKey<? extends Registry<T>> registry, ResourceLocation location) {

    private static final ConcurrentHashMap<TagKey<?>, TagKey<?>> INTERN = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> TagKey<T> create(ResourceKey<? extends Registry<T>> registry, ResourceLocation location) {
        TagKey<T> k = new TagKey<>(registry, location);
        return (TagKey<T>) INTERN.computeIfAbsent(k, x -> x);
    }

    public boolean isFor(ResourceKey<? extends Registry<?>> other) { return registry == other; }

    @SuppressWarnings("unchecked")
    public <E> Optional<TagKey<E>> cast(ResourceKey<? extends Registry<E>> other) { return isFor(other) ? Optional.of((TagKey<E>) this) : Optional.empty(); }

    @Override
    public String toString() { return "TagKey[" + registry.location() + " / " + location + "]"; }
}
