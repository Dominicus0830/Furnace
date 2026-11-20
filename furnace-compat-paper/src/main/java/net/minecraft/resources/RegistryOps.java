package net.minecraft.resources;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.util.Optional;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;

/**
 * 레지스트리를 아는 연산(Mojang 이름 shim, T-200): 안쪽 연산에 레지스트리 조회 맥락을 얹는다. 바닐라 1.21.1 {@code RegistryOps} 의 만들기·감싸기·
 * {@code owner}·{@code getter}·{@code withParent}·{@code injectRegistryContext}. 코덱 조합({@code retrieveGetter}·{@code retrieveElement}) 은 RecordCodecBuilder
 * 대역이 없어 두지 않았다. DataFixerUpper 가 있어야 올라간다.
 */
public class RegistryOps<T> extends DelegatingOps<T> {

    private final RegistryInfoLookup lookupProvider;

    public static <T> RegistryOps<T> create(DynamicOps<T> delegate, HolderLookup.Provider provider) {
        return create(delegate, new HolderLookupAdapter(provider));
    }

    public static <T> RegistryOps<T> create(DynamicOps<T> delegate, RegistryInfoLookup lookup) { return new RegistryOps<>(delegate, lookup); }

    public static <T> Dynamic<T> injectRegistryContext(Dynamic<T> dynamic, HolderLookup.Provider provider) {
        return new Dynamic<>(provider.createSerializationContext(dynamic.getOps()), dynamic.getValue());
    }

    private RegistryOps(DynamicOps<T> delegate, RegistryInfoLookup lookup) {
        super(delegate);
        this.lookupProvider = lookup;
    }

    public <U> RegistryOps<U> withParent(DynamicOps<U> parent) {
        if (parent == this.delegate) {
            @SuppressWarnings("unchecked") RegistryOps<U> same = (RegistryOps<U>) this;
            return same;
        }
        return new RegistryOps<>(parent, lookupProvider);
    }

    public <E> Optional<HolderOwner<E>> owner(ResourceKey<? extends Registry<? extends E>> registry) {
        return lookupProvider.<E>lookup(registry).map(RegistryInfo::owner);
    }

    public <E> Optional<HolderGetter<E>> getter(ResourceKey<? extends Registry<? extends E>> registry) {
        return lookupProvider.<E>lookup(registry).map(RegistryInfo::getter);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegistryOps<?> other = (RegistryOps<?>) o;
        return delegate.equals(other.delegate) && lookupProvider.equals(other.lookupProvider);
    }

    @Override
    public int hashCode() { return delegate.hashCode() * 31 + lookupProvider.hashCode(); }

    static final class HolderLookupAdapter implements RegistryInfoLookup {
        private final HolderLookup.Provider lookupProvider;

        HolderLookupAdapter(HolderLookup.Provider provider) { this.lookupProvider = provider; }

        @Override
        public <E> Optional<RegistryInfo<E>> lookup(ResourceKey<? extends Registry<? extends E>> registry) {
            return lookupProvider.<E>lookup(registry).map(RegistryInfo::fromRegistryLookup);
        }

        @Override
        public boolean equals(Object o) { return o instanceof HolderLookupAdapter a && lookupProvider.equals(a.lookupProvider); }

        @Override
        public int hashCode() { return lookupProvider.hashCode(); }
    }

    public interface RegistryInfoLookup {
        <T> Optional<RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registry);
    }

    public record RegistryInfo<T>(HolderOwner<T> owner, HolderGetter<T> getter, Lifecycle elementsLifecycle) {
        public static <T> RegistryInfo<T> fromRegistryLookup(HolderLookup.RegistryLookup<T> lookup) {
            return new RegistryInfo<>(lookup, lookup, Lifecycle.stable());
        }
    }
}
