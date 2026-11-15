package net.minecraft.core;

import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/**
 * 레지스트리 조회(Mojang 이름 shim, H7b → N2b-6). 목록 읽기({@code listElements}·{@code listTags})와 레지스트리 조회기({@link RegistryLookup})·묶음
 * 조회기({@link Provider})의 {@code lookup}·{@code lookupOrThrow}. 직렬화 맥락({@code createSerializationContext})·{@code filterFeatures} 는
 * DataFixerUpper·기능 플래그 shim 이 없어 두지 않았다(없는 멤버 — 호출 위치에서 실패).
 */
public interface HolderLookup<T> extends HolderGetter<T> {

    Stream<Holder.Reference<T>> listElements();

    default Stream<ResourceKey<T>> listElementIds() { return listElements().map(Holder.Reference::key); }

    Stream<HolderSet.Named<T>> listTags();

    default Stream<TagKey<T>> listTagIds() { return listTags().map(HolderSet.Named::key); }

    /** 레지스트리 하나의 조회기. */
    interface RegistryLookup<T> extends HolderLookup<T>, HolderOwner<T> {

        ResourceKey<? extends Registry<? extends T>> key();

        default RegistryLookup<T> filterElements(java.util.function.Predicate<T> filter) {
            RegistryLookup<T> self = this;
            return new RegistryLookup<>() {
                @Override
                public ResourceKey<? extends Registry<? extends T>> key() { return self.key(); }

                @Override
                public Stream<Holder.Reference<T>> listElements() { return self.listElements().filter(h -> filter.test(h.value())); }

                @Override
                public Stream<HolderSet.Named<T>> listTags() { return self.listTags(); }

                @Override
                public Optional<Holder.Reference<T>> get(ResourceKey<T> k) { return self.get(k).filter(h -> filter.test(h.value())); }

                @Override
                public Optional<HolderSet.Named<T>> get(TagKey<T> tag) { return self.get(tag); }
            };
        }
    }

    /** 레지스트리 묶음 조회기. */
    interface Provider {

        Stream<ResourceKey<? extends Registry<?>>> listRegistries();

        <T> Optional<RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> registry);

        default <T> RegistryLookup<T> lookupOrThrow(ResourceKey<? extends Registry<? extends T>> registry) {
            return this.<T>lookup(registry).orElseThrow(() -> new IllegalStateException("Registry " + registry.location() + " not found"));
        }

        default HolderGetter.Provider asGetterLookup() {
            Provider self = this;
            return new HolderGetter.Provider() {
                @Override
                public <T> Optional<HolderGetter<T>> lookup(ResourceKey<? extends Registry<? extends T>> registry) {
                    return self.<T>lookup(registry).map(l -> (HolderGetter<T>) l);
                }
            };
        }
    }
}
