package net.minecraft.world.flag;

import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

/** 바닐라 FeatureFlags(Mojang 이름 shim, T-211 3단계) — 1.21.1: vanilla · bundle · trade_rebalance. 코덱은 없다. */
public class FeatureFlags {

    public static final FeatureFlag VANILLA;
    public static final FeatureFlag BUNDLE;
    public static final FeatureFlag TRADE_REBALANCE;
    public static final FeatureFlagRegistry REGISTRY;
    public static final FeatureFlagSet VANILLA_SET;
    public static final FeatureFlagSet DEFAULT_FLAGS;

    public static String printMissingFlags(FeatureFlagSet featuresToCheck, FeatureFlagSet features) {
        return printMissingFlags(REGISTRY, featuresToCheck, features);
    }

    public static String printMissingFlags(FeatureFlagRegistry registry, FeatureFlagSet featuresToCheck, FeatureFlagSet features) {
        Set<ResourceLocation> set = registry.toNames(features);
        Set<ResourceLocation> set2 = registry.toNames(featuresToCheck);
        return set.stream().filter(id -> !set2.contains(id)).map(ResourceLocation::toString).collect(Collectors.joining(", "));
    }

    public static boolean isExperimental(FeatureFlagSet features) { return !features.isSubsetOf(VANILLA_SET); }

    static {
        FeatureFlagRegistry.Builder builder = new FeatureFlagRegistry.Builder("main");
        VANILLA = builder.createVanilla("vanilla");
        BUNDLE = builder.createVanilla("bundle");
        TRADE_REBALANCE = builder.createVanilla("trade_rebalance");
        REGISTRY = builder.build();
        VANILLA_SET = FeatureFlagSet.of(VANILLA);
        DEFAULT_FLAGS = VANILLA_SET;
    }
}
