package org.domi.furnace.compat.nms;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * 얼어 있는 내장 레지스트리 shim(바닐라 MappedRegistry 의 읽기 규칙). 번호는 넣은 순서(= 바닐라 레지스트리 번호).
 * 기본값 없는 것(소리·효과): 모르는 이름·번호·값이면 {@code get}·{@code byId}·{@code getKey} 는 null, {@code getId} 는 -1.
 * 기본값 있는 것은 {@link NmsDefaultedRegistry} — 그 기본값 쪽을 돌려준다. {@code getOptional} 류는 둘 다 비어 있다.
 */
public class NmsMappedRegistry<T> implements Registry<T> {

    private final ResourceKey<? extends Registry<T>> key;
    private final ResourceLocation defaultKey;
    private final List<Holder.Reference<T>> byId = new ArrayList<>();
    private final Map<ResourceLocation, Holder.Reference<T>> byLocation = new HashMap<>();
    private final Map<T, Integer> idOf = new IdentityHashMap<>();
    private final Map<T, Holder.Reference<T>> holderOf = new IdentityHashMap<>();
    private Holder.Reference<T> defaultValue;

    /** 기본값 없는 레지스트리. */
    public NmsMappedRegistry(ResourceKey<? extends Registry<T>> key) { this(key, null); }

    NmsMappedRegistry(ResourceKey<? extends Registry<T>> key, ResourceLocation defaultKey) {
        this.key = key;
        this.defaultKey = defaultKey;
    }

    /** 만들 때만 — 다음 번호로 넣는다. */
    public Holder.Reference<T> add(ResourceLocation location, T value) {
        @SuppressWarnings("unchecked")
        ResourceKey<T> k = ResourceKey.create((ResourceKey<Registry<T>>) (ResourceKey<?>) key, location);
        Holder.Reference<T> h = new Holder.Reference<>(k, value);
        idOf.put(value, byId.size());
        byId.add(h);
        byLocation.put(location, h);
        holderOf.put(value, h);
        if (location.equals(defaultKey)) defaultValue = h;
        return h;
    }

    private T defaultValue() { return defaultValue == null ? null : defaultValue.value(); }

    /** 기본값 이름(없으면 null). */
    final ResourceLocation defaultKey() { return defaultKey; }

    @Override
    public ResourceKey<? extends Registry<T>> key() { return key; }

    @Override
    public ResourceLocation getKey(T value) {
        Holder.Reference<T> h = holderOf.get(value);
        return h == null ? defaultKey : h.key().location();
    }

    @Override
    public Optional<ResourceKey<T>> getResourceKey(T value) { return Optional.ofNullable(holderOf.get(value)).map(Holder.Reference::key); }

    @Override
    public int getId(T value) {
        Integer id = idOf.get(value);
        return id != null ? id : defaultValue == null ? -1 : idOf.get(defaultValue.value());
    }

    @Override
    public T byId(int id) { return id >= 0 && id < byId.size() ? byId.get(id).value() : defaultValue(); }

    @Override
    public int size() { return byId.size(); }

    @Override
    public T get(ResourceKey<T> k) {
        if (k == null || !k.registry().equals(key.location())) return defaultValue();
        return get(k.location());
    }

    @Override
    public T get(ResourceLocation location) {
        Holder.Reference<T> h = location == null ? null : byLocation.get(location);
        return h == null ? defaultValue() : h.value();
    }

    @Override
    public Optional<T> getOptional(ResourceLocation location) { return Optional.ofNullable(byLocation.get(location)).map(Holder.Reference::value); }

    @Override
    public Optional<T> getOptional(ResourceKey<T> k) {
        return k != null && k.registry().equals(key.location()) ? getOptional(k.location()) : Optional.empty();
    }

    @Override
    public Optional<Holder.Reference<T>> getAny() { return byId.isEmpty() ? Optional.empty() : Optional.of(byId.get(0)); }

    @Override
    public Set<ResourceLocation> keySet() { return Collections.unmodifiableSet(byLocation.keySet()); }

    @Override
    public Set<Map.Entry<ResourceKey<T>, T>> entrySet() {
        Set<Map.Entry<ResourceKey<T>, T>> out = new LinkedHashSet<>();
        for (var h : byId) out.add(new AbstractMap.SimpleImmutableEntry<>(h.key(), h.value()));
        return Collections.unmodifiableSet(out);
    }

    @Override
    public Set<ResourceKey<T>> registryKeySet() {
        Set<ResourceKey<T>> out = new LinkedHashSet<>();
        for (var h : byId) out.add(h.key());
        return Collections.unmodifiableSet(out);
    }

    @Override
    public boolean containsKey(ResourceLocation location) { return byLocation.containsKey(location); }

    @Override
    public boolean containsKey(ResourceKey<T> k) { return k != null && k.registry().equals(key.location()) && byLocation.containsKey(k.location()); }

    /** Furnace 내장 레지스트리는 서버 시작 때 이미 얼어 있다. */
    @Override
    public Registry<T> freeze() { return this; }

    @Override
    public Optional<Holder.Reference<T>> getHolder(int id) { return id >= 0 && id < byId.size() ? Optional.of(byId.get(id)) : Optional.empty(); }

    @Override
    public Optional<Holder.Reference<T>> getHolder(ResourceLocation location) { return Optional.ofNullable(byLocation.get(location)); }

    @Override
    public Optional<Holder.Reference<T>> getHolder(ResourceKey<T> k) {
        return k != null && k.registry().equals(key.location()) ? getHolder(k.location()) : Optional.empty();
    }

    @Override
    public Holder<T> wrapAsHolder(T value) {
        Holder.Reference<T> h = holderOf.get(value);
        return h != null ? h : Holder.direct(value);
    }

    @Override
    public Stream<Holder.Reference<T>> holders() { return byId.stream(); }

    @Override
    public Iterator<T> iterator() { return byId.stream().map(Holder.Reference::value).iterator(); }

    private final java.util.concurrent.ConcurrentHashMap<net.minecraft.tags.TagKey<T>, net.minecraft.core.HolderSet.Named<T>> named = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 바닐라 MappedRegistry 처럼 서버에 있는 태그이거나 이미 만든 묶음(getOrCreateTag)이면 그 묶음. 묶음은 TagKey 마다 하나다 —
     * 리로드로 태그가 사라져도 지도에 남아 빈 묶음이 된다(바닐라 bindTags 와 같다).
     */
    @Override
    public Optional<net.minecraft.core.HolderSet.Named<T>> getTag(net.minecraft.tags.TagKey<T> tag) {
        if (tag == null || !tag.isFor(key)) return Optional.empty();
        if (!named.containsKey(tag) && !NmsTags.exists(key, tag)) return Optional.empty();
        return Optional.of(getOrCreateTag(tag));
    }

    @Override
    public net.minecraft.core.HolderSet.Named<T> getOrCreateTag(net.minecraft.tags.TagKey<T> tag) {
        return named.computeIfAbsent(tag, t -> new net.minecraft.core.HolderSet.Named<>(t, new TagContents(t)));
    }

    /** 서버 태그 목록이 바뀔 때만(서버는 같은 태그 표에서 같은 목록 객체를 준다) 손잡이 목록을 다시 만든다. */
    private final class TagContents implements java.util.function.Supplier<List<Holder<T>>> {
        private final net.minecraft.tags.TagKey<T> tag;
        private volatile Object[] cache = {null, List.of()};

        TagContents(net.minecraft.tags.TagKey<T> tag) { this.tag = tag; }

        @SuppressWarnings("unchecked")
        @Override
        public List<Holder<T>> get() {
            List<String> ids = NmsTags.entries(key, tag);
            Object[] c = cache;
            if (c[0] == ids) return (List<Holder<T>>) c[1];
            List<Holder<T>> out = new ArrayList<>(ids.size());
            for (String id : ids) {
                Holder.Reference<T> h = byLocation.get(ResourceLocation.parse(id));
                if (h != null) out.add(h);
            }
            List<Holder<T>> result = List.copyOf(out);
            cache = new Object[] {ids, result};
            return result;
        }
    }

    /** 서버의 태그 이름 뒤에 여기서 만든 빈 묶음의 이름(바닐라처럼 getOrCreateTag 로 만든 것도 나온다). */
    @Override
    public Stream<net.minecraft.tags.TagKey<T>> getTagNames() {
        java.util.LinkedHashSet<net.minecraft.tags.TagKey<T>> all = new java.util.LinkedHashSet<>();
        NmsTags.names(key).forEach(all::add);
        all.addAll(named.keySet());
        return all.stream();
    }

    @Override
    public String toString() { return "Registry[" + key + " (Stable)]"; }
}
