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

    /** 바닐라: 손잡이 ↔ 레지스트리 번호 표(번호는 그 값의 번호, 없는 번호면 null). 팔레트 컨테이너의 바이옴 표로 쓰인다. */
    default IdMap<Holder<T>> asHolderIdMap() {
        return new IdMap<>() {
            @Override
            public int getId(Holder<T> holder) { return Registry.this.getId(holder.value()); }

            @Override
            public Holder<T> byId(int id) { return Registry.this.getHolder(id).orElse(null); }

            @Override
            public int size() { return Registry.this.size(); }

            @Override
            public java.util.Iterator<Holder<T>> iterator() { return Registry.this.holders().<Holder<T>>map(h -> h).iterator(); }
        };
    }

    /** 정의된 태그면 그 묶음(같은 TagKey 면 같은 객체), 없거나 다른 레지스트리의 태그면 empty. */
    Optional<HolderSet.Named<T>> getTag(net.minecraft.tags.TagKey<T> tag);

    default Iterable<Holder<T>> getTagOrEmpty(net.minecraft.tags.TagKey<T> tag) {
        return getTag(tag).<Iterable<Holder<T>>>map(n -> n).orElse(java.util.List.of());
    }

    /** 바닐라처럼 없는 태그면 빈 묶음을 만든다(다음 리로드에 생기면 내용이 찬다). */
    HolderSet.Named<T> getOrCreateTag(net.minecraft.tags.TagKey<T> tag);

    Stream<net.minecraft.tags.TagKey<T>> getTagNames();

    /** 바닐라: 묶인 태그마다 (키, 태그 집합) 짝. */
    default Stream<com.mojang.datafixers.util.Pair<net.minecraft.tags.TagKey<T>, HolderSet.Named<T>>> getTags() {
        return getTagNames().map(k -> com.mojang.datafixers.util.Pair.of(k, getOrCreateTag(k)));
    }

    /**
     * 이 레지스트리의 조회기(바닐라 {@code asLookup()}). 목록·키 조회·태그 조회가 이 레지스트리를 그대로 본다 —
     * 키로 꺼낸 손잡이는 {@link #getHolder(ResourceKey)} 와 같은 객체다.
     */
    default HolderLookup.RegistryLookup<T> asLookup() {
        Registry<T> self = this;
        return new HolderLookup.RegistryLookup<>() {
            @Override
            public ResourceKey<? extends Registry<? extends T>> key() { return self.key(); }

            @Override
            public Stream<Holder.Reference<T>> listElements() { return self.holders(); }

            @Override
            public Stream<HolderSet.Named<T>> listTags() { return self.getTagNames().map(self::getOrCreateTag); }

            @Override
            public Optional<Holder.Reference<T>> get(ResourceKey<T> k) { return self.getHolder(k); }

            @Override
            public Optional<HolderSet.Named<T>> get(net.minecraft.tags.TagKey<T> tag) { return self.getTag(tag); }
        };
    }
}
