package net.minecraft.world.level.levelgen.feature;

import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/** Furnace 전용: 이름만 아는 지형지물 종류(바닐라는 종류마다 클래스가 있다 — shim 은 나무·산호만 구분). */
public final class FurnaceFeature<FC extends FeatureConfiguration> extends Feature<FC> {

    FurnaceFeature(String typeId) { super(typeId); }
}
