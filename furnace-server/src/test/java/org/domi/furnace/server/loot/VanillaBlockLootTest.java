// 바닐라 1.21.1 서버(월드 시드 1)에서 /loot ... mine 으로 뽑은 블록 드롭 정답지(61 블록 상태 × 도구 6 × 3회)와
// Furnace 결과가 같은지. 블록 드롭은 표의 random_sequence(월드 시드·표 id 로 정한 영속 Xoroshiro)를 쓰므로
// 같은 블록·도구를 이어서 3번 캐면 시퀀스가 이어지는 것까지 확인된다.
package org.domi.furnace.server.loot;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.junit.jupiter.api.Test;

class VanillaBlockLootTest {

    static LootStack tool(String key) {
        return switch (key) {
            case "hand" -> LootStack.empty();
            case "dpick" -> new LootStack("minecraft:diamond_pickaxe", 1);
            case "fortune3" -> enchanted("minecraft:diamond_pickaxe", "minecraft:fortune", 3);
            case "silk" -> enchanted("minecraft:diamond_pickaxe", "minecraft:silk_touch", 1);
            case "shears" -> new LootStack("minecraft:shears", 1);
            case "hoe_f2" -> enchanted("minecraft:iron_hoe", "minecraft:fortune", 2);
            default -> throw new IllegalArgumentException(key);
        };
    }

    private static LootStack enchanted(String item, String ench, int level) {
        LootStack s = new LootStack(item, 1);
        Enchantments.enchant(s, ench, level);
        return s;
    }

    private static final org.domi.furnace.server.world.BlockRegistry BLOCKS = org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt");

    /** "wheat[age=7]" → 기본 상태 속성 위에 지정한 속성을 덮는다(바닐라 setblock 과 같다). */
    static LootBlockState state(String spec) {
        int b = spec.indexOf('[');
        String block = "minecraft:" + (b < 0 ? spec : spec.substring(0, b));
        Map<String, String> props = new LinkedHashMap<>(LootBlockState.of(BLOCKS, BLOCKS.defaultStateId(block)).properties());
        if (b >= 0) {
            for (String kv : spec.substring(b + 1, spec.length() - 1).split(",")) {
                String[] p = kv.split("=");
                props.put(p[0], p[1]);
            }
        }
        return new LootBlockState(block, props);
    }

    @Test
    void blockDropsMatchVanillaServer() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        RandomSequences seqs = new RandomSequences(1L);
        LootWorld world = new LootWorld() {
            public NoiseRandom random() { throw new AssertionError("블록 표는 random_sequence 를 쓴다"); }
            public NoiseRandom randomSequence(ResourceId id) { return seqs.get(id); }
        };
        List<String> failures = new ArrayList<>();
        int cases = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                VanillaBlockLootTest.class.getResourceAsStream("/loot/blocks-1.21.1.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] p = line.split(" ", 4);
                if (p[2].equals("0")) seqs.clear();   // 각 (블록, 도구) 묶음 앞에서 random reset *
                LootBlockState st = state(p[0]);
                List<String> expected = new ArrayList<>();
                for (Object o : (List<?>) Snbt.parse(p[3])) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> it = (Map<String, Object>) o;
                    expected.add(describe((String) it.get("id"), ((Number) it.get("count")).intValue(), it.get("components")));
                }
                LootParams params = new LootParams().with(LootParams.ORIGIN, new double[]{2.5, 100.5, 0.5})
                        .with(LootParams.BLOCK_STATE, st).with(LootParams.TOOL, tool(p[1]));
                LootTable table = data.table(st.block().replace("minecraft:", "minecraft:blocks/"));
                List<String> actual = new ArrayList<>();
                for (LootStack s : table.getRandomItems(table.context(data, world, params, 0L))) {
                    if (!s.isEmpty()) actual.add(describe(s.item(), s.count(), s.encodedPatch()));
                }
                cases++;
                if (!expected.equals(actual)) failures.add(p[0] + " " + p[1] + " #" + p[2] + "\n  바닐라 " + expected + "\n  Furnace " + actual);
            }
        }
        assertEquals(1098, cases);
        assertTrue(failures.isEmpty(), failures.size() + "건 불일치:\n" + String.join("\n", failures.subList(0, Math.min(8, failures.size()))));
    }

    private static String describe(String item, int count, Object components) {
        return item + " x" + count + (components == null || ((Map<?, ?>) components).isEmpty() ? "" : " " + Components.canon(components));
    }
}
