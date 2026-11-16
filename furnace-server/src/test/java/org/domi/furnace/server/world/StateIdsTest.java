// SPDX-License-Identifier: GPL-3.0-only
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.server.world.gen.feature.TreeFeature;
import org.junit.jupiter.api.Test;

/**
 * V-02: gradle/state-ids.spec 이 블록 정의 표에서 푼 StateIds 값이, 서로 다른 출처인 blockstates 표(블록 상태 전체 목록)와 맞는지,
 * 그리고 옛 손으로 쓴 숫자와 같은지(= 판이 바뀌어도 이름이 가리키는 블록은 그대로) 확인한다.
 */
class StateIdsTest {

    /** blockstates_<판>.txt: 블록 이름 → 그 블록의 상태 번호들(작은 것부터). 키는 "이름" 또는 "이름;속성=값,...". */
    private static final Map<String, List<Integer>> STATES = new LinkedHashMap<>();
    private static final Map<Integer, String> BLOCK_OF = new LinkedHashMap<>();
    private static final Map<Integer, String> KEY_OF = new LinkedHashMap<>();

    static {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                StateIdsTest.class.getClassLoader().getResourceAsStream("blocks/blockstates_" + GameVersion.RESOURCE_TAG + ".txt"), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) continue;
                int sp = line.lastIndexOf(' ');
                String key = line.substring(0, sp);
                int id = Integer.parseInt(line.substring(sp + 1));
                String block = key.contains(";") ? key.substring(0, key.indexOf(';')) : key;
                STATES.computeIfAbsent(block, k -> new ArrayList<>()).add(id);
                BLOCK_OF.put(id, block);
                KEY_OF.put(id, key);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int first(String block) { return STATES.get("minecraft:" + block).stream().min(Integer::compare).orElseThrow(); }
    private static int last(String block) { return STATES.get("minecraft:" + block).stream().max(Integer::compare).orElseThrow(); }

    @Test
    void 이름이_가리키는_블록이_blockstates_표와_같다() {
        assertEquals(first("air"), StateIds.AIR);
        assertEquals(first("void_air"), StateIds.VOID_AIR);
        assertEquals(first("cave_air"), StateIds.CAVE_AIR);
        assertEquals(first("water"), StateIds.WATER_SOURCE);
        assertEquals(first("water"), StateIds.WATER_FIRST);
        assertEquals(last("water"), StateIds.WATER_LAST);
        assertEquals(first("lava"), StateIds.LAVA_FIRST);
        assertEquals(last("lava"), StateIds.LAVA_LAST);
        assertEquals(first("bedrock"), StateIds.BEDROCK);
        assertEquals(first("magma_block"), StateIds.MAGMA_BLOCK);
        assertEquals(first("vine"), StateIds.VINE_FIRST);
        assertEquals(last("vine"), StateIds.VINE_LAST);
        assertEquals(first("glow_lichen"), StateIds.GLOW_LICHEN_FIRST);
        assertEquals(last("glow_lichen"), StateIds.GLOW_LICHEN_LAST);
        assertEquals(first("short_grass"), StateIds.SHORT_GRASS);
        assertEquals(first("fern"), StateIds.FERN);
        assertEquals(first("dead_bush"), StateIds.DEAD_BUSH);
        assertEquals(first("seagrass"), StateIds.SEAGRASS);
        assertEquals(first("tall_seagrass"), StateIds.TALL_SEAGRASS_FIRST);
        assertEquals(last("tall_seagrass"), StateIds.TALL_SEAGRASS_LAST);
        assertEquals(first("sunflower"), StateIds.TALL_FLOWERS_FIRST);
        assertEquals(last("large_fern"), StateIds.TALL_FLOWERS_LAST);
        assertEquals(first("pitcher_plant"), StateIds.PITCHER_PLANT_FIRST);
        assertEquals(last("pitcher_plant"), StateIds.PITCHER_PLANT_LAST);
        assertEquals(first("warped_roots"), StateIds.WARPED_ROOTS);
        assertEquals(first("nether_sprouts"), StateIds.NETHER_SPROUTS);
        assertEquals(first("crimson_roots"), StateIds.CRIMSON_ROOTS);
        assertEquals(first("hanging_roots"), StateIds.HANGING_ROOTS_FIRST);
        assertEquals(last("hanging_roots"), StateIds.HANGING_ROOTS_LAST);
        assertEquals(first("oak_leaves"), StateIds.LEAVES_FIRST);
        assertEquals(last("flowering_azalea_leaves"), StateIds.LEAVES_LAST);
        assertEquals(first("oak_log"), StateIds.LOG_FIRST);
        assertEquals(last("dark_oak_log"), StateIds.LOG_LAST);
        assertEquals(first("cherry_log"), StateIds.CHERRY_LOG_FIRST);
        assertEquals(last("cherry_log"), StateIds.CHERRY_LOG_LAST);
    }

    @Test
    void 물과_용암은_level_0부터_15까지_이어_있다() {
        for (int level = 0; level <= 15; level++) {
            assertEquals("minecraft:water;level=" + level, KEY_OF.get(StateIds.WATER_FIRST + level));
            assertEquals("minecraft:lava;level=" + level, KEY_OF.get(StateIds.LAVA_FIRST + level));
        }
        assertEquals(StateIds.WATER_FIRST + 15, StateIds.WATER_LAST);
        assertEquals(StateIds.LAVA_FIRST + 15, StateIds.LAVA_LAST);
    }

    @Test
    void 잎_distance_공식이_열_종류_모두에서_속성값과_맞는다() {
        String[] leaves = {"oak", "spruce", "birch", "jungle", "acacia", "cherry", "dark_oak", "mangrove", "azalea", "flowering_azalea"};
        int total = 0;
        for (String leaf : leaves) {
            for (int id : STATES.get("minecraft:" + leaf + "_leaves")) {
                String key = KEY_OF.get(id);
                int distance = Integer.parseInt(key.substring(key.indexOf("distance=") + 9, key.indexOf("distance=") + 10));
                assertEquals(distance, (id - StateIds.LEAVES_FIRST) % StateIds.LEAVES_STATES_PER_BLOCK / StateIds.LEAVES_DISTANCE_STEP + 1, key);
                assertTrue(id >= StateIds.LEAVES_FIRST && id <= StateIds.LEAVES_LAST, key);
                total++;
            }
        }
        assertEquals(leaves.length * StateIds.LEAVES_STATES_PER_BLOCK, total);
        assertEquals(total, StateIds.LEAVES_LAST - StateIds.LEAVES_FIRST + 1);   // 잎 범위 안에 잎이 아닌 것이 끼어 있지 않다
    }

    @Test
    void 통나무_범위는_mangrove_와_껍질_벗긴_것을_넣지_않고_cherry_만_따로_뺀다() {
        TreeSet<String> inRange = new TreeSet<>();
        for (int s = StateIds.LOG_FIRST; s <= StateIds.LOG_LAST; s++) inRange.add(BLOCK_OF.get(s));
        assertEquals(new TreeSet<>(List.of("minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log", "minecraft:jungle_log",
                "minecraft:acacia_log", "minecraft:cherry_log", "minecraft:dark_oak_log")), inRange);
        for (int s = 0; s < 30000; s++) {
            boolean old = s >= 130 && s <= 150 && s != 145 && s != 146 && s != 147;   // StateIds 이전의 손으로 쓴 식
            assertEquals(old, TreeFeature.isLog(s, null), "state " + s);
            boolean oldLeaves = s >= 237 && s <= 516;
            assertEquals(oldLeaves, TreeFeature.isLeaves(s), "state " + s);
        }
    }

    @Test
    void 해초_바닥_어긋남은_지금_동작을_그대로_둔_것이다() {
        // 알려진 어긋남(gradle/state-ids.spec 주석, user-todo 항목): 숫자가 바뀌면 이 시험이 알린다.
        assertEquals(2013, StateIds.SEAGRASS_FLOOR_NONSOLID_LAST);
        assertEquals("minecraft:piston", BLOCK_OF.get(StateIds.TALL_SEAGRASS_LAST + 1));
        assertEquals("minecraft:piston", BLOCK_OF.get(StateIds.SEAGRASS_FLOOR_NONSOLID_LAST));
        assertEquals("minecraft:kelp;age=22", KEY_OF.get(StateIds.KELP_FLOOR_NONSOLID_FIRST));
        assertEquals("minecraft:kelp_plant", KEY_OF.get(StateIds.KELP_FLOOR_NONSOLID_LAST));
    }
}
