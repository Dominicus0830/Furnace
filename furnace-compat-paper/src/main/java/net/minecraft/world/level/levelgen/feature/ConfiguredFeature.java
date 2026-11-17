package net.minecraft.world.level.levelgen.feature;

import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * 설정된 지형지물(Mojang 이름 shim, N2b-6). 바닐라 record: 지형지물 종류({@link #feature()})와 설정({@link #config()}).
 * shim 의 설정은 값이 없다(null) — 종류만 읽을 수 있다. {@code getFeatures()} 는 없다.
 * {@link #place} 는 서버의 지형 생성 코드로 놓는다(T-211, {@link org.domi.furnace.compat.nms.NmsFeatures}) — 레지스트리에 있는 것만 된다.
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

    /**
     * 바닐라 ConfiguredFeature#place(level, generator, random, origin) → Feature#place(…): {@code level.ensureCanWrite(origin)} 이면 놓는다.
     * 레지스트리 밖의 객체(플러그인이 만든 것)는 {@link org.domi.furnace.compat.nms.UnsupportedNmsCall}.
     */
    public boolean place(net.minecraft.world.level.WorldGenLevel level, net.minecraft.world.level.chunk.ChunkGenerator generator,
                         net.minecraft.util.RandomSource random, net.minecraft.core.BlockPos origin) {
        if (!level.ensureCanWrite(origin)) return false;
        var key = org.domi.furnace.compat.nms.NmsWorldgen.configuredFeatures().getKey(this);
        return org.domi.furnace.compat.nms.NmsFeatures.place(false, key == null ? null : key.toString(), "ConfiguredFeature#place",
                level, generator, random, origin);
    }

    @Override
    public String toString() { return "Configured: " + feature + ": " + config; }
}
