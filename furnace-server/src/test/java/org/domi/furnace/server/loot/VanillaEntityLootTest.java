// 바닐라 1.21.1 서버(월드 시드 1)에서 콘솔 /loot ... kill 로 뽑은 엔티티 드롭 정답지(63종 × 4회)와 Furnace 결과가
// 같은지(공격자 없음, 마법 피해). 엔티티 표도 random_sequence 를 쓰므로 4회 연속으로 시퀀스 이어짐까지 확인된다.
package org.domi.furnace.server.loot;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.junit.jupiter.api.Test;

class VanillaEntityLootTest {

    private static final Set<String> RAIDERS = Set.of("minecraft:pillager", "minecraft:vindicator", "minecraft:evoker",
            "minecraft:ravager", "minecraft:witch", "minecraft:illusioner");

    @Test
    void entityDropsMatchVanillaServer() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        RandomSequences seqs = new RandomSequences(1L);
        LootWorld world = new LootWorld() {
            public NoiseRandom random() { throw new AssertionError("엔티티 표는 random_sequence 를 쓴다"); }
            public NoiseRandom randomSequence(ResourceId id) { return seqs.get(id); }
        };
        LootRegistries regs = data.registries();
        LootDamageSource magic = new LootDamageSource() {
            public String type() { return "minecraft:magic"; }
            public boolean is(String tag) { return regs.tag("minecraft:damage_type", tag).contains("minecraft:magic"); }
            public LootEntity entity() { return null; }
            public LootEntity directEntity() { return null; }
        };
        List<String> failures = new ArrayList<>();
        int cases = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                VanillaEntityLootTest.class.getResourceAsStream("/loot/entities-1.21.1.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] p = line.split(" ", 3);
                if (p[1].equals("0")) seqs.clear();
                String type = "minecraft:" + p[0];
                LootEntity entity = new LootEntity() {
                    public String type() { return type; }
                    public Object typeSpecific(String key) {
                        if (key.equals("size") && (type.equals("minecraft:slime") || type.equals("minecraft:magma_cube"))) return 2;
                        if (key.equals("is_captain") && RAIDERS.contains(type)) return false;
                        return null;
                    }
                };
                Map<Integer, String> expected = new TreeMap<>();
                for (Object o : (List<?>) Snbt.parse(p[2])) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> it = (Map<String, Object>) o;
                    expected.put(((Number) it.get("Slot")).intValue(), describe((String) it.get("id"), ((Number) it.get("count")).intValue(), it.get("components")));
                }
                // 바닐라 getLootTable: 털 깎지 않은 양은 색깔별 표
                String tableId = type.equals("minecraft:sheep") ? "minecraft:entities/sheep/white" : type.replace("minecraft:", "minecraft:entities/");
                LootTable table = data.table(tableId);
                LootParams params = new LootParams().with(LootParams.THIS_ENTITY, entity).with(LootParams.DAMAGE_SOURCE, magic)
                        .with(LootParams.ORIGIN, new double[]{0.5, 100, 0.5});
                Map<Integer, String> actual = new TreeMap<>();
                List<LootStack> items = table.getRandomItems(table.context(data, world, params, 0L));
                for (int i = 0; i < items.size(); i++) {
                    LootStack s = items.get(i);
                    if (!s.isEmpty()) actual.put(i, describe(s.item(), s.count(), s.encodedPatch()));
                }
                cases++;
                if (!expected.equals(actual)) failures.add(p[0] + " #" + p[1] + "\n  바닐라 " + expected + "\n  Furnace " + actual);
            }
        }
        assertEquals(252, cases);
        assertTrue(failures.isEmpty(), failures.size() + "건 불일치:\n" + String.join("\n", failures.subList(0, Math.min(8, failures.size()))));
    }

    private static String describe(String item, int count, Object components) {
        return item + " x" + count + (components == null || ((Map<?, ?>) components).isEmpty() ? "" : " " + Components.canon(components));
    }
}
