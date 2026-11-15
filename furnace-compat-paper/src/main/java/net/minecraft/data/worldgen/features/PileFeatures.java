package net.minecraft.data.worldgen.features;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/** Pile 설정된 지형지물 키 모음(Mojang 이름 shim, N2b-6). 키 이름만 있다 — 값은 서버 configured_feature 목록에 있는 같은 이름 항목이다. */
public class PileFeatures {

    public static final ResourceKey<ConfiguredFeature<?, ?>> PILE_HAY = key("pile_hay");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PILE_MELON = key("pile_melon");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PILE_SNOW = key("pile_snow");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PILE_ICE = key("pile_ice");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PILE_PUMPKIN = key("pile_pumpkin");

    private static ResourceKey<ConfiguredFeature<?, ?>> key(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.withDefaultNamespace(name));
    }
}
