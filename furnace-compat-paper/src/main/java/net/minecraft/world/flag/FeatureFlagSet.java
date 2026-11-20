package net.minecraft.world.flag;

import java.util.Arrays;
import java.util.Collection;

/** 바닐라 FeatureFlagSet(Mojang 이름 shim, T-211 3단계) — 바닐라 그대로. */
public final class FeatureFlagSet {

    private static final FeatureFlagSet EMPTY = new FeatureFlagSet(null, 0L);
    public static final int MAX_CONTAINER_SIZE = 64;
    private final FeatureFlagUniverse universe;
    private final long mask;

    private FeatureFlagSet(FeatureFlagUniverse universe, long mask) {
        this.universe = universe;
        this.mask = mask;
    }

    static FeatureFlagSet create(FeatureFlagUniverse universe, Collection<FeatureFlag> features) {
        if (features.isEmpty()) return EMPTY;
        return new FeatureFlagSet(universe, computeMask(universe, 0L, features));
    }

    public static FeatureFlagSet of() { return EMPTY; }

    public static FeatureFlagSet of(FeatureFlag feature) { return new FeatureFlagSet(feature.universe, feature.mask); }

    public static FeatureFlagSet of(FeatureFlag feature1, FeatureFlag... features) {
        long l = features.length == 0 ? feature1.mask : computeMask(feature1.universe, feature1.mask, Arrays.asList(features));
        return new FeatureFlagSet(feature1.universe, l);
    }

    private static long computeMask(FeatureFlagUniverse universe, long mask, Iterable<FeatureFlag> features) {
        for (FeatureFlag f : features) {
            if (universe != f.universe) throw new IllegalStateException("Mismatched feature universe, expected '" + universe + "', but got '" + f.universe + "'");
            mask |= f.mask;
        }
        return mask;
    }

    public boolean contains(FeatureFlag feature) { return universe == feature.universe && (mask & feature.mask) != 0L; }

    public boolean isEmpty() { return equals(EMPTY); }

    public boolean isSubsetOf(FeatureFlagSet features) {
        if (universe == null) return true;
        return universe == features.universe && (mask & ~features.mask) == 0L;
    }

    public boolean intersects(FeatureFlagSet features) {
        return universe != null && features.universe != null && universe == features.universe && (mask & features.mask) != 0L;
    }

    public FeatureFlagSet join(FeatureFlagSet features) {
        if (universe == null) return features;
        if (features.universe == null) return this;
        if (universe != features.universe) throw new IllegalArgumentException("Mismatched set elements: '" + universe + "' != '" + features.universe + "'");
        return new FeatureFlagSet(universe, mask | features.mask);
    }

    public FeatureFlagSet subtract(FeatureFlagSet features) {
        if (universe == null || features.universe == null) return this;
        if (universe != features.universe) throw new IllegalArgumentException("Mismatched set elements: '" + universe + "' != '" + features.universe + "'");
        long l = mask & ~features.mask;
        return l == 0L ? EMPTY : new FeatureFlagSet(universe, l);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof FeatureFlagSet s && universe == s.universe && mask == s.mask;
    }

    /** 바닐라: fastutil {@code HashCommon.mix(mask)} 를 int 로. */
    @Override
    public int hashCode() {
        long h = mask * 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        return (int) (h ^ h >>> 16);
    }
}
