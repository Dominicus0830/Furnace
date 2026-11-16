package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class SeagrassFeatureTest {

    @Test
    void seagrassFeature_placesTallSeagrassInWater() {
        BlockRegistry registry = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        ChunkColumn col = new ChunkColumn(0);
        int[] blockIds = new int[ChunkColumn.SECTION_COUNT * 16 * 16 * 16];
        int[] biomeIds = new int[ChunkColumn.SECTION_COUNT * 64];
        int floorY = 10;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                blockIds[(floorY - ChunkColumn.MIN_Y) * 256 + z * 16 + x] = registry.defaultStateId("minecraft:dirt");
                blockIds[(floorY + 1 - ChunkColumn.MIN_Y) * 256 + z * 16 + x] = registry.defaultStateId("minecraft:water");
                blockIds[(floorY + 2 - ChunkColumn.MIN_Y) * 256 + z * 16 + x] = registry.defaultStateId("minecraft:water");
            }
        }
        col.loadGenerated(blockIds, biomeIds);
        FeatureWorldView view = new FeatureWorldView(col, 0, 0, biomeIds);
        SeagrassFeature feature = new SeagrassFeature(
            1.0f,
            registry.defaultStateId("minecraft:seagrass"),
            registry.defaultStateId("minecraft:tall_seagrass"),
            registry.stateIdOrAir("minecraft:tall_seagrass", java.util.Map.of("half", "upper")),
            registry.defaultStateId("minecraft:magma_block"));
        WorldgenRandom random = new WorldgenRandom();
        random.setSeed(1234L);

        assertTrue(feature.place(view, random, 8, 0, 8));

        boolean foundLower = false;
        boolean foundUpper = false;
        int lower = registry.defaultStateId("minecraft:tall_seagrass");
        int upper = registry.stateIdOrAir("minecraft:tall_seagrass", java.util.Map.of("half", "upper"));
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                if (view.getBlock(x, floorY + 1, z) == lower) foundLower = true;
                if (view.getBlock(x, floorY + 2, z) == upper) foundUpper = true;
            }
        }
        assertTrue(foundLower);
        assertTrue(foundUpper);
    }

    /** 바닥 블록 하나로 바다를 채우고 해초(일반)를 40번 심어 본 뒤, 심긴 칸 수. */
    private static int plantedOn(int floorState, BlockRegistry registry) {
        ChunkColumn col = new ChunkColumn(0);
        int[] blockIds = new int[ChunkColumn.SECTION_COUNT * 16 * 16 * 16];
        int[] biomeIds = new int[ChunkColumn.SECTION_COUNT * 64];
        int floorY = 10;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                blockIds[(floorY - ChunkColumn.MIN_Y) * 256 + z * 16 + x] = floorState;
                blockIds[(floorY + 1 - ChunkColumn.MIN_Y) * 256 + z * 16 + x] = registry.defaultStateId("minecraft:water");
                blockIds[(floorY + 2 - ChunkColumn.MIN_Y) * 256 + z * 16 + x] = registry.defaultStateId("minecraft:water");
            }
        }
        col.loadGenerated(blockIds, biomeIds);
        FeatureWorldView view = new FeatureWorldView(col, 0, 0, biomeIds);
        int seagrass = registry.defaultStateId("minecraft:seagrass");
        SeagrassFeature feature = new SeagrassFeature(0.0f, seagrass, registry.defaultStateId("minecraft:tall_seagrass"),
            registry.stateIdOrAir("minecraft:tall_seagrass", java.util.Map.of("half", "upper")), registry.defaultStateId("minecraft:magma_block"));
        WorldgenRandom random = new WorldgenRandom();
        random.setSeed(99L);
        for (int i = 0; i < 40; i++) feature.place(view, random, 8, 0, 8);
        int n = 0;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) if (view.getBlock(x, floorY + 1, z) == seagrass) n++;
        return n;
    }

    /** SeagrassBlock.mayPlaceOn = 윗면이 단단한(isFaceSturdy UP) 블록. 옛 숫자 범위 근사가 틀리던 바닥들이 바닐라와 같아졌는지(V-02 뒤 정렬). */
    @Test
    void seagrass_floorFollowsVanillaFaceSturdy() {
        BlockRegistry r = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        var none = java.util.Map.<String, String>of();
        assertTrue(plantedOn(r.defaultStateId("minecraft:dirt"), r) > 0, "흙은 심긴다");
        assertTrue(plantedOn(r.stateIdOrAir("minecraft:piston", java.util.Map.of("extended", "false", "facing", "north")), r) > 0, "접힌 피스톤은 윗면이 꽉 차서 심긴다(옛 범위에서는 일부가 안 심겼다)");
        assertEquals(0, plantedOn(r.stateIdOrAir("minecraft:piston", java.util.Map.of("extended", "true", "facing", "north")), r), "펼친 피스톤(북)은 윗면이 꽉 차지 않는다");
        assertEquals(0, plantedOn(r.stateIdOrAir("minecraft:piston", java.util.Map.of("extended", "true", "facing", "up")), r), "펼친 피스톤(위)은 윗면이 꽉 차지 않는다");
        assertTrue(plantedOn(r.stateIdOrAir("minecraft:piston", java.util.Map.of("extended", "true", "facing", "down")), r) > 0, "펼친 피스톤(아래)은 윗면이 꽉 찬다");
        assertEquals(0, plantedOn(r.stateIdOrAir("minecraft:kelp", java.util.Map.of("age", "0")), r), "켈프는 age 와 상관없이 윗면이 단단하지 않다");
        assertEquals(0, plantedOn(r.stateIdOrAir("minecraft:kelp", java.util.Map.of("age", "25")), r));
        assertEquals(0, plantedOn(r.defaultStateId("minecraft:seagrass"), r));
        assertEquals(0, plantedOn(r.defaultStateId("minecraft:magma_block"), r), "마그마 블록 제외");
    }
}
