package net.minecraft.data.worldgen.features;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/** Tree 설정된 지형지물 키 모음(Mojang 이름 shim, N2b-6). 키 이름만 있다 — 값은 서버 configured_feature 목록에 있는 같은 이름 항목이다. */
public class TreeFeatures {

    public static final ResourceKey<ConfiguredFeature<?, ?>> CRIMSON_FUNGUS = key("crimson_fungus");
    public static final ResourceKey<ConfiguredFeature<?, ?>> CRIMSON_FUNGUS_PLANTED = key("crimson_fungus_planted");
    public static final ResourceKey<ConfiguredFeature<?, ?>> WARPED_FUNGUS = key("warped_fungus");
    public static final ResourceKey<ConfiguredFeature<?, ?>> WARPED_FUNGUS_PLANTED = key("warped_fungus_planted");
    public static final ResourceKey<ConfiguredFeature<?, ?>> HUGE_BROWN_MUSHROOM = key("huge_brown_mushroom");
    public static final ResourceKey<ConfiguredFeature<?, ?>> HUGE_RED_MUSHROOM = key("huge_red_mushroom");
    public static final ResourceKey<ConfiguredFeature<?, ?>> OAK = key("oak");
    public static final ResourceKey<ConfiguredFeature<?, ?>> DARK_OAK = key("dark_oak");
    public static final ResourceKey<ConfiguredFeature<?, ?>> BIRCH = key("birch");
    public static final ResourceKey<ConfiguredFeature<?, ?>> ACACIA = key("acacia");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SPRUCE = key("spruce");
    public static final ResourceKey<ConfiguredFeature<?, ?>> PINE = key("pine");
    public static final ResourceKey<ConfiguredFeature<?, ?>> JUNGLE_TREE = key("jungle_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FANCY_OAK = key("fancy_oak");
    public static final ResourceKey<ConfiguredFeature<?, ?>> JUNGLE_TREE_NO_VINE = key("jungle_tree_no_vine");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MEGA_JUNGLE_TREE = key("mega_jungle_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MEGA_SPRUCE = key("mega_spruce");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MEGA_PINE = key("mega_pine");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SUPER_BIRCH_BEES_0002 = key("super_birch_bees_0002");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SUPER_BIRCH_BEES = key("super_birch_bees");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SWAMP_OAK = key("swamp_oak");
    public static final ResourceKey<ConfiguredFeature<?, ?>> JUNGLE_BUSH = key("jungle_bush");
    public static final ResourceKey<ConfiguredFeature<?, ?>> AZALEA_TREE = key("azalea_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MANGROVE = key("mangrove");
    public static final ResourceKey<ConfiguredFeature<?, ?>> TALL_MANGROVE = key("tall_mangrove");
    public static final ResourceKey<ConfiguredFeature<?, ?>> CHERRY = key("cherry");
    public static final ResourceKey<ConfiguredFeature<?, ?>> OAK_BEES_0002 = key("oak_bees_0002");
    public static final ResourceKey<ConfiguredFeature<?, ?>> OAK_BEES_002 = key("oak_bees_002");
    public static final ResourceKey<ConfiguredFeature<?, ?>> OAK_BEES_005 = key("oak_bees_005");
    public static final ResourceKey<ConfiguredFeature<?, ?>> BIRCH_BEES_0002 = key("birch_bees_0002");
    public static final ResourceKey<ConfiguredFeature<?, ?>> BIRCH_BEES_002 = key("birch_bees_002");
    public static final ResourceKey<ConfiguredFeature<?, ?>> BIRCH_BEES_005 = key("birch_bees_005");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FANCY_OAK_BEES_0002 = key("fancy_oak_bees_0002");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FANCY_OAK_BEES_002 = key("fancy_oak_bees_002");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FANCY_OAK_BEES_005 = key("fancy_oak_bees_005");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FANCY_OAK_BEES = key("fancy_oak_bees");
    public static final ResourceKey<ConfiguredFeature<?, ?>> CHERRY_BEES_005 = key("cherry_bees_005");

    private static ResourceKey<ConfiguredFeature<?, ?>> key(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.withDefaultNamespace(name));
    }
}
