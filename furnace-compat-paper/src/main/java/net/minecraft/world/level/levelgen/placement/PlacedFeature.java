package net.minecraft.world.level.levelgen.placement;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * 배치된 지형지물(Mojang 이름 shim, N2b-6). 바닐라 record 의 {@link #feature()}(가리키는 설정된 지형지물 손잡이)만 있다 —
 * 배치 조건({@code placement()})과 {@code place(…)} 는 없다(PlacementModifier shim 이 없다).
 */
public final class PlacedFeature {

    private final Holder<ConfiguredFeature<?, ?>> feature;

    /** 호환 모듈 전용 — 레지스트리 shim 이 만든다. */
    public PlacedFeature(Holder<ConfiguredFeature<?, ?>> feature) { this.feature = feature; }

    public Holder<ConfiguredFeature<?, ?>> feature() { return feature; }

    @Override
    public String toString() { return "Placed " + feature; }
}
