package net.minecraft.world.flag;

/** 바닐라 FeatureFlag(Mojang 이름 shim, T-211 3단계). */
public class FeatureFlag {

    public final FeatureFlagUniverse universe;
    public final long mask;

    FeatureFlag(FeatureFlagUniverse universe, int id) {
        this.universe = universe;
        this.mask = 1L << id;
    }
}
