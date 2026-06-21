package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.WorldgenData;
import org.junit.jupiter.api.Test;

class BiomeFeatureDataTest {

    @Test
    void load_parsesBiomePlacedFeatureAndConfiguredOreWithInterning() {
        WorldgenData data = WorldgenData.load("worldgen/test");
        BlockRegistry registry = BlockRegistry.load("blocks/1.21.1.txt");

        BiomeFeatureData features = BiomeFeatureData.load(
            data, List.of("minecraft:plains"), registry);

        List<PlacedFeature> step6 = features.featuresForStep("minecraft:plains", 6);
        assertEquals(2, step6.size());
        assertSame(step6.get(0), step6.get(1));
        assertInstanceOf(OreFeature.class, step6.get(0).feature());
        assertEquals(4, step6.get(0).placement().size());
        assertInstanceOf(PlacementModifier.CountPlacement.class, step6.get(0).placement().get(0));
        assertInstanceOf(PlacementModifier.InSquarePlacement.class, step6.get(0).placement().get(1));
        assertInstanceOf(PlacementModifier.HeightRangePlacement.class, step6.get(0).placement().get(2));
        assertInstanceOf(PlacementModifier.BiomeFilter.class, step6.get(0).placement().get(3));

        FeatureSorter.StepFeatureData sortedStep6 = FeatureSorter.build(features).featuresPerStep().get(6);
        assertEquals(0, sortedStep6.indexOf(step6.get(0)));
        assertEquals(1, sortedStep6.features().size());

        assertInstanceOf(DiskFeature.class,
            features.featuresForStep("minecraft:plains", 7).get(0).feature());
        assertInstanceOf(SpringFeature.class,
            features.featuresForStep("minecraft:plains", 8).get(0).feature());
        assertInstanceOf(FreezeTopLayerFeature.class,
            features.featuresForStep("minecraft:plains", 10).get(0).feature());
    }

    @Test
    void load_vanillaPlainsHasUndergroundOreFeatures() {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        BlockRegistry registry = BlockRegistry.load("blocks/1.21.1.txt");

        BiomeFeatureData features = BiomeFeatureData.load(
            data, List.of("minecraft:plains"), registry);

        assertFalse(features.featuresForStep("minecraft:plains", 6).isEmpty());
        // step9(VEGETAL_DECORATION): grass(random_patch+simple_state_provider)는 구현됨,
        // 미구현(weighted provider 꽃 등)은 placeholder(NoopFeature)로 보존 — index 슬롯 유지.
        List<PlacedFeature> step9 = features.featuresForStep("minecraft:plains", 9);
        assertFalse(step9.isEmpty());
        boolean hasGrass = false;
        for (PlacedFeature f : step9) {
            assertTrue(f.feature() instanceof RandomPatchFeature || f.feature() instanceof NoopFeature,
                "step9 entry는 구현된 RandomPatchFeature이거나 placeholder Noop이어야 함: " + f.feature());
            if (f.feature() instanceof RandomPatchFeature) hasGrass = true;
        }
        assertTrue(hasGrass, "plains step9에 구현된 grass random_patch가 있어야 함");
    }
}
