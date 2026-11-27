package net.minecraft.commands;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.flag.FeatureFlagSet;

/**
 * 명령을 만들 때 쓰는 레지스트리 문맥(Mojang 이름 shim). 바닐라 {@code simple} 은 레지스트리 조회기를 기능 플래그로 거르는데, 이 shim 은 기능 플래그 거르기가
 * 없어 {@code provider} 를 그대로 건넨다(Furnace 는 1.21.1 기본 기능만 켠다). {@code FeatureFlagSet} 인자는 받기만 한다.
 */
interface CommandBuildContext {

    static CommandBuildContext simple(HolderLookup.Provider provider, FeatureFlagSet enabledFeatures) {
        java.util.Objects.requireNonNull(provider, "provider");
        java.util.Objects.requireNonNull(enabledFeatures, "enabledFeatures");
        return new CommandBuildContext() {
            @Override
            public java.util.stream.Stream<net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>>> listRegistries() { return provider.listRegistries(); }

            @Override
            public <T> java.util.Optional<HolderLookup.RegistryLookup<T>> lookup(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<? extends T>> registry) {
                return provider.lookup(registry);
            }
        };
    }
}
