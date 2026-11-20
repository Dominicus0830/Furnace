package net.minecraft.world.flag;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/** 바닐라 FeatureFlagRegistry(Mojang 이름 shim, T-211 3단계) — 코덱은 없다. */
public class FeatureFlagRegistry {

    private final FeatureFlagUniverse universe;
    public final Map<ResourceLocation, FeatureFlag> names;
    private final FeatureFlagSet allFlags;

    FeatureFlagRegistry(FeatureFlagUniverse universe, FeatureFlagSet allFlags, Map<ResourceLocation, FeatureFlag> names) {
        this.universe = universe;
        this.names = names;
        this.allFlags = allFlags;
    }

    public boolean isSubset(FeatureFlagSet features) { return features.isSubsetOf(allFlags); }

    public FeatureFlagSet allFlags() { return allFlags; }

    /** 모르는 이름은 바닐라처럼 경고를 남기고 건너뛴다. */
    public FeatureFlagSet fromNames(Iterable<ResourceLocation> features) {
        return fromNames(features, f -> org.slf4j.LoggerFactory.getLogger(FeatureFlagRegistry.class).warn("Unknown feature flag: {}", f));
    }

    public FeatureFlagSet subset(FeatureFlag... features) { return FeatureFlagSet.create(universe, Arrays.asList(features)); }

    public FeatureFlagSet fromNames(Iterable<ResourceLocation> features, Consumer<ResourceLocation> unknown) {
        Set<FeatureFlag> set = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (ResourceLocation id : features) {
            FeatureFlag f = names.get(id);
            if (f == null) unknown.accept(id);
            else set.add(f);
        }
        return FeatureFlagSet.create(universe, set);
    }

    public Set<ResourceLocation> toNames(FeatureFlagSet features) {
        Set<ResourceLocation> set = new HashSet<>();
        names.forEach((id, f) -> {
            if (features.contains(f)) set.add(id);
        });
        return set;
    }

    public static class Builder {
        private final FeatureFlagUniverse universe;
        private int id;
        private final Map<ResourceLocation, FeatureFlag> flags = new LinkedHashMap<>();

        public Builder(String universe) { this.universe = new FeatureFlagUniverse(universe); }

        public FeatureFlag createVanilla(String feature) { return create(ResourceLocation.withDefaultNamespace(feature)); }

        public FeatureFlag create(ResourceLocation feature) {
            if (id >= 64) throw new IllegalStateException("Too many feature flags");
            FeatureFlag f = new FeatureFlag(universe, id++);
            if (flags.put(feature, f) != null) throw new IllegalStateException("Duplicate feature flag " + feature);
            return f;
        }

        public FeatureFlagRegistry build() {
            return new FeatureFlagRegistry(universe, FeatureFlagSet.create(universe, flags.values()), Map.copyOf(flags));
        }
    }
}
