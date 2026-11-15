package net.minecraft.world.level.levelgen.feature;

import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * 설정된 지형지물(Mojang 이름 shim, N2b-6). 바닐라 record: 지형지물 종류({@link #feature()})와 설정({@link #config()}).
 * shim 의 설정은 값이 없다(null) — 종류만 읽을 수 있다. {@code place(…)}·{@code getFeatures()} 는 없다.
 */
public final class ConfiguredFeature<FC extends FeatureConfiguration, F extends Feature<FC>> {

    private final F feature;
    private final FC config;

    public ConfiguredFeature(F feature, FC config) {
        this.feature = feature;
        this.config = config;
    }

    public F feature() { return feature; }

    public FC config() { return config; }

    @Override
    public String toString() { return "Configured: " + feature + ": " + config; }
}
