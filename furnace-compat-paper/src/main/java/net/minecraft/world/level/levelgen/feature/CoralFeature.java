package net.minecraft.world.level.levelgen.feature;

import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** 산호 지형지물 바탕(Mojang 이름 shim, N2b-6) — 종류 식별용. 생성 동작은 없다. */
public abstract class CoralFeature extends Feature<NoneFeatureConfiguration> {

    CoralFeature(String typeId) { super(typeId); }
}
