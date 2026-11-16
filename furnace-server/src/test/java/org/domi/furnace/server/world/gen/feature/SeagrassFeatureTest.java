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

    /** SeagrassBlock.mayPlaceOn = 윗면이 단단한(isFaceSturdy UP) 블록. 옛 숫자 범위 근사가 틀리던 바닥들이 바닐라와 같아졌는지(V-02 뒤 정렬). 켈프는 움직임을 막지 않아 높이맵에 안 잡히므로 판정 함수를 직접 본다. */
    @Test
    void seagrass_floorFollowsVanillaFaceSturdy() {
        BlockRegistry r = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        java.util.function.BiFunction<String, String[], Integer> st = (name, kv) -> {
            java.util.Map<String, String> m = new java.util.HashMap<>();
            for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
            return r.stateIdOrAir(name, m);
        };
        // 단단한 바닥
        assertTrue(SeagrassFeature.isSolidFloor(r.defaultStateId("minecraft:dirt")), "흙");
        assertTrue(SeagrassFeature.isSolidFloor(r.defaultStateId("minecraft:glass")), "유리 윗면은 꽉 차서 바닐라에서도 심긴다");
        assertTrue(SeagrassFeature.isSolidFloor(st.apply("minecraft:oak_slab", new String[]{"type", "top", "waterlogged", "false"})), "위쪽 슬랩");
        assertTrue(SeagrassFeature.isSolidFloor(st.apply("minecraft:piston", new String[]{"extended", "false", "facing", "north"})), "접힌 피스톤");
        assertTrue(SeagrassFeature.isSolidFloor(st.apply("minecraft:piston", new String[]{"extended", "true", "facing", "down"})), "펼친 피스톤(아래 방향)은 윗면이 꽉 찬다");
        // 단단하지 않은 바닥
        for (int age = 0; age <= 25; age++) {
            assertFalse(SeagrassFeature.isSolidFloor(st.apply("minecraft:kelp", new String[]{"age", Integer.toString(age)})), "켈프 age=" + age + "(전: 0~21 은 단단하다고 쳤다)");
        }
        assertFalse(SeagrassFeature.isSolidFloor(r.defaultStateId("minecraft:kelp_plant")));
        assertFalse(SeagrassFeature.isSolidFloor(r.defaultStateId("minecraft:seagrass")));
        assertFalse(SeagrassFeature.isSolidFloor(r.defaultStateId("minecraft:water")));
        assertFalse(SeagrassFeature.isSolidFloor(r.defaultStateId("minecraft:air")));
        assertFalse(SeagrassFeature.isSolidFloor(st.apply("minecraft:piston", new String[]{"extended", "true", "facing", "up"})), "펼친 피스톤(위 방향, 전: 단단하다고 쳤다)");
        assertFalse(SeagrassFeature.isSolidFloor(st.apply("minecraft:piston", new String[]{"extended", "true", "facing", "north"})));
        assertFalse(SeagrassFeature.isSolidFloor(st.apply("minecraft:oak_slab", new String[]{"type", "bottom", "waterlogged", "false"})), "아래쪽 슬랩(윗면이 반 칸)");
        assertFalse(SeagrassFeature.isSolidFloor(st.apply("minecraft:oak_stairs", new String[]{"facing", "north", "half", "bottom", "shape", "straight", "waterlogged", "false"})), "아래쪽 계단");
        assertFalse(SeagrassFeature.isSolidFloor(st.apply("minecraft:oak_trapdoor", new String[]{"facing", "north", "half", "bottom", "open", "true", "powered", "false", "waterlogged", "false"})), "열린 다락문");
        assertFalse(SeagrassFeature.isSolidFloor(r.defaultStateId("minecraft:cobblestone_wall")), "벽");
        assertFalse(SeagrassFeature.isSolidFloor(r.defaultStateId("minecraft:oak_leaves")), "잎");
    }

    /** place() 쪽: 마그마 위에는 심지 않고 흙 위에는 심는다(판정 함수와 별개로 연결 확인). */
    @Test
    void place_usesFloorRule() {
        BlockRegistry r = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        assertTrue(plantedOn(r.defaultStateId("minecraft:dirt"), r) > 0);
        assertEquals(0, plantedOn(r.defaultStateId("minecraft:magma_block"), r));
        assertEquals(0, plantedOn(r.stateIdOrAir("minecraft:piston", java.util.Map.of("extended", "true", "facing", "up")), r));
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
}
