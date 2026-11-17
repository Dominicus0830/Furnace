package net.minecraft.data.worldgen.features;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/** Nether 설정된 지형지물 키 모음(Mojang 이름 shim, N2b-6). 키 이름만 있다 — 값은 서버 configured_feature 목록에 있는 같은 이름 항목이다. */
public class NetherFeatures {

    public static final ResourceKey<ConfiguredFeature<?, ?>> DELTA = key("delta");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SMALL_BASALT_COLUMNS = key("small_basalt_columns");
    public static final ResourceKey<ConfiguredFeature<?, ?>> LARGE_BASALT_COLUMNS = key("large_basalt_columns");
    public static final ResourceKey<ConfiguredFeature<?, ?>> BASALT_BLOBS = key("basalt_blobs");
    public static final ResourceKey<ConfiguredFeature<?, ?>> BLACKSTONE_BLOBS = key("blackstone_blobs");
    public static final ResourceKey<ConfiguredFeature<?, ?>> GLOWSTONE_EXTRA = key("glowstone_extra");
    public static final ResourceKey<ConfiguredFeature<?, ?>> CRIMSON_FOREST_VEGETATION = key("crimson_forest_vegetation");
    public static final ResourceKey<ConfiguredFeature<?, ?>> CRIMSON_FOREST_VEGETATION_BONEMEAL = key("crimson_forest_vegetation_bonemeal");
    public static final ResourceKey<ConfiguredFeature<?, ?>> WARPED_FOREST_VEGETION = key("warped_forest_vegetation");
    public static final ResourceKey<ConfiguredFeature<?, ?>> WARPED_FOREST_VEGETATION_BONEMEAL = key("warped_forest_vegetation_bonemeal");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NETHER_SPROUTS = key("nether_sprouts");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NETHER_SPROUTS_BONEMEAL = key("nether_sprouts_bonemeal");
    public static final ResourceKey<ConfiguredFeature<?, ?>> TWISTING_VINES = key("twisting_vines");
    public static final ResourceKey<ConfiguredFeature<?, ?>> TWISTING_VINES_BONEMEAL = key("twisting_vines_bonemeal");
    public static final ResourceKey<ConfiguredFeature<?, ?>> WEEPING_VINES = key("weeping_vines");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PATCH_CRIMSON_ROOTS = key("patch_crimson_roots");
    public static final ResourceKey<ConfiguredFeature<?, ?>> BASALT_PILLAR = key("basalt_pillar");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SPRING_LAVA_NETHER = key("spring_lava_nether");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SPRING_NETHER_CLOSED = key("spring_nether_closed");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SPRING_NETHER_OPEN = key("spring_nether_open");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PATCH_FIRE = key("patch_fire");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PATCH_SOUL_FIRE = key("patch_soul_fire");

    private static ResourceKey<ConfiguredFeature<?, ?>> key(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.withDefaultNamespace(name));
    }
}
