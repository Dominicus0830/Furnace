package net.minecraft.data.worldgen.features;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/** Cave 설정된 지형지물 키 모음(Mojang 이름 shim, N2b-6). 키 이름만 있다 — 값은 서버 configured_feature 목록에 있는 같은 이름 항목이다. */
public class CaveFeatures {

    public static final ResourceKey<ConfiguredFeature<?, ?>> MONSTER_ROOM = key("monster_room");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FOSSIL_COAL = key("fossil_coal");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FOSSIL_DIAMONDS = key("fossil_diamonds");
    public static final ResourceKey<ConfiguredFeature<?, ?>> DRIPSTONE_CLUSTER = key("dripstone_cluster");
    public static final ResourceKey<ConfiguredFeature<?, ?>> LARGE_DRIPSTONE = key("large_dripstone");
    public static final ResourceKey<ConfiguredFeature<?, ?>> POINTED_DRIPSTONE = key("pointed_dripstone");
    public static final ResourceKey<ConfiguredFeature<?, ?>> UNDERWATER_MAGMA = key("underwater_magma");
    public static final ResourceKey<ConfiguredFeature<?, ?>> GLOW_LICHEN = key("glow_lichen");
    public static final ResourceKey<ConfiguredFeature<?, ?>> ROOTED_AZALEA_TREE = key("rooted_azalea_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> CAVE_VINE = key("cave_vine");
    public static final ResourceKey<ConfiguredFeature<?, ?>> CAVE_VINE_IN_MOSS = key("cave_vine_in_moss");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MOSS_VEGETATION = key("moss_vegetation");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MOSS_PATCH = key("moss_patch");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MOSS_PATCH_BONEMEAL = key("moss_patch_bonemeal");
    public static final ResourceKey<ConfiguredFeature<?, ?>> DRIPLEAF = key("dripleaf");
    public static final ResourceKey<ConfiguredFeature<?, ?>> CLAY_WITH_DRIPLEAVES = key("clay_with_dripleaves");
    public static final ResourceKey<ConfiguredFeature<?, ?>> CLAY_POOL_WITH_DRIPLEAVES = key("clay_pool_with_dripleaves");
    public static final ResourceKey<ConfiguredFeature<?, ?>> LUSH_CAVES_CLAY = key("lush_caves_clay");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MOSS_PATCH_CEILING = key("moss_patch_ceiling");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SPORE_BLOSSOM = key("spore_blossom");
    public static final ResourceKey<ConfiguredFeature<?, ?>> AMETHYST_GEODE = key("amethyst_geode");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SCULK_PATCH_DEEP_DARK = key("sculk_patch_deep_dark");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SCULK_PATCH_ANCIENT_CITY = key("sculk_patch_ancient_city");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SCULK_VEIN = key("sculk_vein");

    private static ResourceKey<ConfiguredFeature<?, ?>> key(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.withDefaultNamespace(name));
    }
}
