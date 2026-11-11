package net.minecraft.core;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * 레지스트리(Mojang 이름 shim, N0). 읽기·조회 표면만 둔다 — 등록({@code register})·태그·코덱·무작위·조회기({@code asLookup})는
 * Furnace 레지스트리가 서버 시작 때 고정되고 태그·DataFixerUpper shim 이 없어 두지 않았다(부르면 H7 덫이 {@code UnsupportedNmsLink}).
 * 예외 문구 {@code Missing key in …: …} 는 바닐라 {@code jz} 와 같다.
 */
public interface Registry<T> extends IdMap<T> {

    ResourceKey<? extends Registry<T>> key();

    ResourceLocation getKey(T value);

    Optional<ResourceKey<T>> getResourceKey(T value);

    @Override
    int getId(T value);

    T get(ResourceKey<T> key);

    T get(ResourceLocation location);

    default Optional<T> getOptional(ResourceLocation location) { return Optional.ofNullable(get(location)); }

    default Optional<T> getOptional(ResourceKey<T> key) { return Optional.ofNullable(get(key)); }

    Optional<Holder.Reference<T>> getAny();

    default T getOrThrow(ResourceKey<T> key) {
        T v = get(key);
        if (v == null) throw new IllegalStateException("Missing key in " + key() + ": " + key);
        return v;
    }

    Set<ResourceLocation> keySet();

    Set<Map.Entry<ResourceKey<T>, T>> entrySet();

    Set<ResourceKey<T>> registryKeySet();

    default Stream<T> stream() { return java.util.stream.StreamSupport.stream(spliterator(), false); }

    boolean containsKey(ResourceLocation location);

    boolean containsKey(ResourceKey<T> key);

    Registry<T> freeze();

    Optional<Holder.Reference<T>> getHolder(int id);

    Optional<Holder.Reference<T>> getHolder(ResourceLocation location);

    Optional<Holder.Reference<T>> getHolder(ResourceKey<T> key);

    Holder<T> wrapAsHolder(T value);

    default Holder.Reference<T> getHolderOrThrow(ResourceKey<T> key) {
        return getHolder(key).orElseThrow(() -> new IllegalStateException("Missing key in " + key() + ": " + key));
    }

    Stream<Holder.Reference<T>> holders();
}
