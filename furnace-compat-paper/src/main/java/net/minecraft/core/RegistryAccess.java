package net.minecraft.core;

import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;

/**
 * 레지스트리 묶음(Mojang 이름 shim, H7b → N2b-6). {@code MinecraftServer#registryAccess()}·{@code Level#registryAccess()} 가 돌려준다.
 * {@code registryOrThrow}·{@code registry}·{@code lookup}·{@code lookupOrThrow} 로 레지스트리를 꺼낸다 — 블록·아이템·유체·소리·상태 효과·지도 표시 종류·
 * 바이옴과 월드젠(설정된/배치된 지형지물·구조물). 다른 레지스트리는 바닐라가 있는 키라도 {@code Missing registry}(shim 이 아직 값 타입을 안 만든 것).
 * 직렬화 맥락({@code createSerializationContext}, T-200)은 {@link HolderLookup.Provider} 에서 물려받는다.
 */
public interface RegistryAccess extends HolderLookup.Provider {

    /** 서버가 가진 얼린 묶음 하나. */
    Frozen SERVER = new org.domi.furnace.compat.nms.NmsRegistryAccess();

    <E> Optional<Registry<E>> registry(ResourceKey<? extends Registry<? extends E>> key);

    @Override
    default <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
        return this.<T>registry(key).map(Registry::asLookup);
    }

    default <E> Registry<E> registryOrThrow(ResourceKey<? extends Registry<? extends E>> key) {
        return this.<E>registry(key).orElseThrow(() -> new IllegalStateException("Missing registry: " + key));
    }

    Stream<RegistryEntry<?>> registries();

    @Override
    default Stream<ResourceKey<? extends Registry<?>>> listRegistries() {
        return registries().<ResourceKey<? extends Registry<?>>>map(RegistryEntry::key);
    }

    default Frozen freeze() { return (Frozen) this; }

    /** 얼린(바뀌지 않는) 묶음. */
    interface Frozen extends RegistryAccess {}

    /** 레지스트리 키와 그 레지스트리. */
    record RegistryEntry<T>(ResourceKey<? extends Registry<T>> key, Registry<T> value) {}
}
