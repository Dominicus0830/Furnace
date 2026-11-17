package net.minecraft.world.level.levelgen.feature;

import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

/** 나무 지형지물(Mojang 이름 shim, N2b-6) — 종류 식별용(instanceof). 생성 동작은 없다. */
public class TreeFeature extends Feature<TreeConfiguration> {

    TreeFeature(String typeId) { super(typeId); }
}
