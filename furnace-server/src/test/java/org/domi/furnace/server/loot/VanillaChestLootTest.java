// 바닐라 1.21.1 서버에서 뽑은 정답지(chest{LootTable,LootTableSeed} 를 연 결과 280건)와 Furnace 전리품 평가 결과가
// 칸·아이템·개수·컴포넌트까지 같은지. 이 시험은 월드 없이(NO_WORLD) 표만 평가하므로 보물 지도(exploration_map)는 칸만 확인한다
// (지도 내용 — 가리키는 구조물·장식 — 은 map/ExplorationMapLootTest).
package org.domi.furnace.server.loot;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.domi.furnace.server.data.PackRepository;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.ResourceManager;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class VanillaChestLootTest {

    public static LootData vanillaLoot() throws Exception {
        Path root = Path.of("../tools/vanilla-data/server-1.21.1");
        Assumptions.assumeTrue(Files.isDirectory(root.resolve("data/minecraft/loot_table")), "로컬 바닐라 데이터 없음");
        PackRepository repo = new PackRepository(root, null);
        repo.reload();
        repo.setSelected(List.of("vanilla"));
        TagRegistries regs = new TagRegistries(RegistrySet.load("registries/1.21.1.txt"));
        try (ResourceManager rm = repo.openResourceManager()) {
            return LootData.load(rm, new LootRegistries(regs, regs.loadAll(rm)));
        }
    }

    static final LootWorld NO_WORLD = new LootWorld() {
        public NoiseRandom random() { throw new AssertionError("시드 있는 상자는 월드 난수를 쓰지 않는다"); }
        public NoiseRandom randomSequence(ResourceId id) { throw new AssertionError("시드 있는 상자는 난수 시퀀스를 쓰지 않는다"); }
    };

    @Test
    void chestLootMatchesVanillaServer() throws Exception {
        LootData data = vanillaLoot();
        assertEquals(1178, data.size());
        List<String> failures = new ArrayList<>();
        int cases = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                VanillaChestLootTest.class.getResourceAsStream("/loot/chests-1.21.1.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] p = line.split(" ", 3);
                String table = "minecraft:" + p[0];
                long seed = Long.parseLong(p[1]);
                Map<Integer, String> expected = new TreeMap<>();
                for (Object o : (List<?>) Snbt.parse(p[2])) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> it = (Map<String, Object>) o;
                    expected.put(((Number) it.get("Slot")).intValue(), describe((String) it.get("id"), ((Number) it.get("count")).intValue(), it.get("components")));
                }
                LootStack[] chest = new LootStack[27];
                data.table(table).fill(chest, data, NO_WORLD, new LootParams().with(LootParams.ORIGIN, new double[]{0.5, 100.5, 0.5}), seed);
                Map<Integer, String> actual = new TreeMap<>();
                for (int i = 0; i < chest.length; i++) {
                    if (chest[i] != null && !chest[i].isEmpty()) actual.put(i, describe(chest[i].item(), chest[i].count(), chest[i].encodedPatch()));
                }
                // 보물 지도: 바닐라는 filled_map(구조물 위치·지도 id), 월드 없는 이 평가에서는 구조물을 못 찾아 빈 map 으로 남는다
                expected.replaceAll((slot, d) -> d.startsWith("minecraft:filled_map ") ? "MAP" : d);
                actual.replaceAll((slot, d) -> d.startsWith("minecraft:map ") && "MAP".equals(expected.get(slot)) ? "MAP" : d);
                cases++;
                if (!expected.equals(actual)) failures.add(table + " " + seed + "\n  바닐라 " + expected + "\n  Furnace " + actual);
            }
        }
        assertEquals(280, cases);
        assertTrue(failures.isEmpty(), failures.size() + "건 불일치:\n" + String.join("\n", failures.subList(0, Math.min(5, failures.size()))));
    }

    private static String describe(String item, int count, Object components) {
        return item + " x" + count + (components == null || ((Map<?, ?>) components).isEmpty() ? "" : " " + Components.canon(components));
    }
}
