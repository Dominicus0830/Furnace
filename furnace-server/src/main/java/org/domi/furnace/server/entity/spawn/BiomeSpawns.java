// 바이옴의 몹 스폰 설정(MobSpawnSettings): 종류별 목록(가중치·묶음 크기)·스폰 비용(에너지 예산·전하)·동물 생성 확률. biome_spawners-1.21.1.json 에서 읽는다.
package org.domi.furnace.server.entity.spawn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.RandomSource;

public final class BiomeSpawns {
    public record SpawnerData(String type, int weight, int minCount, int maxCount) {}

    public record SpawnCost(double energyBudget, double charge) {}

    private record Settings(float probability, Map<MobCategory, List<SpawnerData>> spawners, Map<String, SpawnCost> costs) {}

    private static final Map<String, Settings> BY_BIOME = new HashMap<>();

    static {
        try (var in = BiomeSpawns.class.getClassLoader().getResourceAsStream("entities/biome_spawners-1.21.1.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : root.entrySet()) {
                JsonObject b = e.getValue().getAsJsonObject();
                Map<MobCategory, List<SpawnerData>> spawners = new HashMap<>();
                for (var c : b.getAsJsonObject("spawners").entrySet()) {
                    List<SpawnerData> list = new ArrayList<>();
                    for (JsonElement el : (JsonArray) c.getValue()) {
                        JsonObject o = el.getAsJsonObject();
                        list.add(new SpawnerData(o.get("type").getAsString(), o.get("weight").getAsInt(), o.get("minCount").getAsInt(), o.get("maxCount").getAsInt()));
                    }
                    spawners.put(MobCategory.byKey(c.getKey()), list);
                }
                Map<String, SpawnCost> costs = new HashMap<>();
                for (var c : b.getAsJsonObject("costs").entrySet()) {
                    JsonObject o = c.getValue().getAsJsonObject();
                    costs.put(c.getKey(), new SpawnCost(o.get("energy_budget").getAsDouble(), o.get("charge").getAsDouble()));
                }
                BY_BIOME.put(e.getKey(), new Settings(b.get("prob").getAsFloat(), spawners, costs));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private BiomeSpawns() {}

    private static final Settings EMPTY = new Settings(0.1F, Map.of(), Map.of());

    private static Settings of(String biome) { return BY_BIOME.getOrDefault(biome == null ? "" : biome, EMPTY); }

    public static float creatureSpawnProbability(String biome) { return of(biome).probability(); }

    public static List<SpawnerData> mobs(String biome, MobCategory category) { return of(biome).spawners().getOrDefault(category, List.of()); }

    public static SpawnCost cost(String biome, String type) { return of(biome).costs().get(type); }

    /** WeightedRandomList.getRandom: 가중치 합에서 nextInt 로 하나(빈 목록이면 난수 없이 null). */
    public static SpawnerData getRandom(List<SpawnerData> list, RandomSource random) {
        int total = 0;
        for (SpawnerData d : list) total += d.weight();
        if (total == 0) return null;
        int i = random.nextInt(total);
        for (SpawnerData d : list) {
            i -= d.weight();
            if (i < 0) return d;
        }
        return null;
    }
}
