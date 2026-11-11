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
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * 얼어 있는 내장 레지스트리 shim(바닐라 DefaultedMappedRegistry 의 읽기 규칙). 번호는 넣은 순서(= 바닐라 레지스트리 번호).
 * 모르는 이름·번호·값이면 {@code get}·{@code byId}·{@code getKey}·{@code getId} 가 기본값 쪽을 돌려주고, {@code getOptional} 류는 비어 있다.
 */
public final class NmsDefaultedRegistry<T> implements DefaultedRegistry<T> {

    private final ResourceKey<? extends Registry<T>> key;
    private final ResourceLocation defaultKey;
    private final List<Holder.Reference<T>> byId = new ArrayList<>();
    private final Map<ResourceLocation, Holder.Reference<T>> byLocation = new HashMap<>();
    private final Map<T, Integer> idOf = new IdentityHashMap<>();
    private final Map<T, Holder.Reference<T>> holderOf = new IdentityHashMap<>();
    private Holder.Reference<T> defaultValue;

    public NmsDefaultedRegistry(ResourceKey<? extends Registry<T>> key, ResourceLocation defaultKey) {
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

    @Override
    public ResourceLocation getDefaultKey() { return defaultKey; }

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

    @Override
    public Optional<net.minecraft.core.HolderSet.Named<T>> getTag(net.minecraft.tags.TagKey<T> tag) {
        if (tag == null || !tag.isFor(key) || !NmsTags.exists(key, tag)) return Optional.empty();
        return Optional.of(getOrCreateTag(tag));
    }

    @Override
    public net.minecraft.core.HolderSet.Named<T> getOrCreateTag(net.minecraft.tags.TagKey<T> tag) {
        return named.computeIfAbsent(tag, t -> new net.minecraft.core.HolderSet.Named<>(t, () -> {
            List<Holder<T>> out = new ArrayList<>();
            if (t.isFor(key)) for (String id : NmsTags.entries(key, t)) {
                Holder.Reference<T> h = byLocation.get(ResourceLocation.parse(id));
                if (h != null) out.add(h);
            }
            return List.copyOf(out);
        }));
    }

    @Override
    public Stream<net.minecraft.tags.TagKey<T>> getTagNames() { return NmsTags.names(key); }

    @Override
    public String toString() { return "Registry[" + key + " (Stable)]"; }
}
