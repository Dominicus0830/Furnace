package net.minecraft.data.worldgen.features;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/** End 설정된 지형지물 키 모음(Mojang 이름 shim, N2b-6). 키 이름만 있다 — 값은 서버 configured_feature 목록에 있는 같은 이름 항목이다. */
public class EndFeatures {

    public static final ResourceKey<ConfiguredFeature<?, ?>> END_PLATFORM = key("end_platform");
    public static final ResourceKey<ConfiguredFeature<?, ?>> END_SPIKE = key("end_spike");
    public static final ResourceKey<ConfiguredFeature<?, ?>> END_GATEWAY_RETURN = key("end_gateway_return");
    public static final ResourceKey<ConfiguredFeature<?, ?>> END_GATEWAY_DELAYED = key("end_gateway_delayed");
    public static final ResourceKey<ConfiguredFeature<?, ?>> CHORUS_PLANT = key("chorus_plant");
    public static final ResourceKey<ConfiguredFeature<?, ?>> END_ISLAND = key("end_island");

    private static ResourceKey<ConfiguredFeature<?, ?>> key(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.withDefaultNamespace(name));
    }
}
