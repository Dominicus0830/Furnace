package net.minecraft.data.worldgen.features;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/** Aquatic 설정된 지형지물 키 모음(Mojang 이름 shim, N2b-6). 키 이름만 있다 — 값은 서버 configured_feature 목록에 있는 같은 이름 항목이다. */
public class AquaticFeatures {

    public static final ResourceKey<ConfiguredFeature<?, ?>> SEAGRASS_SHORT = key("seagrass_short");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SEAGRASS_SLIGHTLY_LESS_SHORT = key("seagrass_slightly_less_short");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SEAGRASS_MID = key("seagrass_mid");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SEAGRASS_TALL = key("seagrass_tall");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SEA_PICKLE = key("sea_pickle");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SEAGRASS_SIMPLE = key("seagrass_simple");
    public static final ResourceKey<ConfiguredFeature<?, ?>> KELP = key("kelp");
    public static final ResourceKey<ConfiguredFeature<?, ?>> WARM_OCEAN_VEGETATION = key("warm_ocean_vegetation");

    private static ResourceKey<ConfiguredFeature<?, ?>> key(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.withDefaultNamespace(name));
    }
}
