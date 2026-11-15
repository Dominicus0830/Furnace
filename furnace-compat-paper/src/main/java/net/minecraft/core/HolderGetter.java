package net.minecraft.core;

import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/** 손잡이 조회기(Mojang 이름 shim, N2b-6). 없는 키·태그면 바닐라와 같은 문구로 던진다({@code Missing element …}·{@code Missing tag …}). */
public interface HolderGetter<T> {

    Optional<Holder.Reference<T>> get(ResourceKey<T> key);

    default Holder.Reference<T> getOrThrow(ResourceKey<T> key) {
        return get(key).orElseThrow(() -> new IllegalStateException("Missing element " + key));
    }

    Optional<HolderSet.Named<T>> get(TagKey<T> tag);

    default HolderSet.Named<T> getOrThrow(TagKey<T> tag) {
        return get(tag).orElseThrow(() -> new IllegalStateException("Missing tag " + tag));
    }

    /** 레지스트리 키로 조회기를 고르는 묶음. */
    interface Provider {

        <T> Optional<HolderGetter<T>> lookup(ResourceKey<? extends Registry<? extends T>> registry);

        default <T> HolderGetter<T> lookupOrThrow(ResourceKey<? extends Registry<? extends T>> registry) {
            return this.<T>lookup(registry).orElseThrow(() -> new IllegalStateException("Missing registry: " + registry));
        }

        default <T> Optional<Holder.Reference<T>> get(ResourceKey<? extends Registry<? extends T>> registry, ResourceKey<T> key) {
            return this.<T>lookup(registry).flatMap(g -> g.get(key));
        }
    }
}
