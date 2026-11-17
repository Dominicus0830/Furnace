package net.minecraft.world.level.levelgen.placement;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * 배치된 지형지물(Mojang 이름 shim, N2b-6). 바닐라 record 의 {@link #feature()}(가리키는 설정된 지형지물 손잡이)만 있다 —
 * 배치 조건({@code placement()})은 없다(PlacementModifier shim 이 없다). {@link #place} 는 서버의 지형 생성 코드로 놓는다(T-211) — 레지스트리에 있는 것만 된다.
 */
public final class PlacedFeature {

    private final Holder<ConfiguredFeature<?, ?>> feature;

    /** 호환 모듈 전용 — 레지스트리 shim 이 만든다. */
    public PlacedFeature(Holder<ConfiguredFeature<?, ?>> feature) { this.feature = feature; }

    public Holder<ConfiguredFeature<?, ?>> feature() { return feature; }

    /**
     * 바닐라 PlacedFeature#place(level, generator, random, origin): top feature 없이 배치 조건을 거쳐 놓는다 — 바이옴 거르기가 들어 있으면 바닐라처럼
     * IllegalStateException. 레지스트리 밖의 객체는 {@link org.domi.furnace.compat.nms.UnsupportedNmsCall}.
     */
    public boolean place(net.minecraft.world.level.WorldGenLevel level, net.minecraft.world.level.chunk.ChunkGenerator generator,
                         net.minecraft.util.RandomSource random, net.minecraft.core.BlockPos origin) {
        var key = org.domi.furnace.compat.nms.NmsWorldgen.placedFeatures().getKey(this);
        return org.domi.furnace.compat.nms.NmsFeatures.place(true, key == null ? null : key.toString(), "PlacedFeature#place",
                level, generator, random, origin);
    }

    @Override
    public String toString() { return "Placed " + feature; }
}
