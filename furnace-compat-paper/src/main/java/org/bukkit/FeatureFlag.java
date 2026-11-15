// Paper 1.21.1 FeatureFlag(실험 기능 플래그). Paper 의 ALL_FLAGS(Adventure Index)는 호환 계층이 Adventure 를 싣지 않아 없다.
package org.bukkit;

public interface FeatureFlag extends Keyed {

    FeatureFlag VANILLA = FeatureFlagImpl.create("vanilla");

    FeatureFlag BUNDLE = FeatureFlagImpl.create("bundle");

    FeatureFlag TRADE_REBALANCE = FeatureFlagImpl.create("trade_rebalance");

    @Deprecated(since = "1.20")
    FeatureFlag UPDATE_1_20 = new FeatureFlagImpl.Deprecated(NamespacedKey.minecraft("update_1_20"));

    @Deprecated(since = "1.21")
    FeatureFlag UPDATE_121 = new FeatureFlagImpl.Deprecated(NamespacedKey.minecraft("update_1_21"));
}
