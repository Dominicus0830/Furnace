package org.domi.furnace.server.world.gen;

import java.util.Set;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.Test;

class TreeFeatureScanTest {

    private static final Set<String> PURE_FOREST_BIOMES = Set.of(
        "minecraft:forest",
        "minecraft:flower_forest",
        "minecraft:birch_forest",
        "minecraft:old_growth_birch_forest",
        "minecraft:taiga",
        "minecraft:old_growth_pine_taiga",
        "minecraft:old_growth_spruce_taiga"
    );

    @Test
    void scanSeed1234ForTreeBiomes() throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(1234L, reg, registries);

        for (int cz = -64; cz <= 64; cz++) {
            for (int cx = -64; cx <= 64; cx++) {
                var biomes = source.collectNeighborBiomes(cx, cz);
                if (biomes.stream().anyMatch(name ->
                    name.contains("forest") || name.contains("taiga") || name.contains("savanna") || name.contains("jungle"))) {
                    System.out.printf("TREE_CHUNK cx=%d cz=%d biomes=%s%n", cx, cz, biomes);
                    return;
                }
            }
        }
        throw new IllegalStateException("No tree biome chunk found in search range");
    }

    @Test
    void scanSeed1234ForPureForestChunks() throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(1234L, reg, registries);

        int found = 0;
        for (int cz = -64; cz <= 64; cz++) {
            for (int cx = -64; cx <= 64; cx++) {
                var biomes = source.collectNeighborBiomes(cx, cz);
                if (!biomes.isEmpty() && PURE_FOREST_BIOMES.containsAll(biomes)) {
                    System.out.printf("PURE_TREE_CHUNK cx=%d cz=%d biomes=%s%n", cx, cz, biomes);
                    found++;
                    if (found >= 20) return;
                }
            }
        }
        throw new IllegalStateException("No pure forest chunk found in search range");
    }
}
