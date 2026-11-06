// 구조물의 자연 스폰 덮어쓰기(Structure.spawnOverrides — worldgen/structure/*.json 의 spawn_overrides).
package org.domi.furnace.server.entity.spawn;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.ResourceManager;
import org.domi.furnace.server.world.gen.structure.StructureRegistry;

public final class StructureSpawnOverrides {

    /** StructureSpawnOverride: bounding_box(piece=조각 안, full=구조물 전체 상자 안)와 그 안에서 쓸 목록. */
    public record Override(boolean piece, List<BiomeSpawns.SpawnerData> spawns) {}

    private static volatile Map<String, Map<MobCategory, Override>> byStructure;

    private StructureSpawnOverrides() {}

    /**
     * 데이터 팩의 worldgen/structure 에서 읽는다. 바닐라 worldgen 레지스트리는 서버 시작 때 한 번만 읽고 /reload 로 바뀌지 않으므로
     * 이미 설치됐으면 그대로 둔다.
     */
    public static void install(ResourceManager rm) {
        if (byStructure != null) return;
        Map<String, JsonObject> jsons = new HashMap<>();
        for (var e : rm.listResources("worldgen/structure", id -> id.path().endsWith(".json")).entrySet()) {
            ResourceId file = e.getKey();
            String path = file.path().substring("worldgen/structure/".length(), file.path().length() - ".json".length());
            try (InputStream in = e.getValue().open().get()) {
                jsons.put(file.namespace() + ":" + path, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception ex) {
                // 읽지 못한 구조물은 덮어쓰기 없음
            }
        }
        if (!jsons.isEmpty()) byStructure = parse(jsons);
    }

    /** 시험용: 다시 읽게 비운다. */
    static void reset() { byStructure = null; }

    /** 구조물 id 의 그 종류 덮어쓰기(없으면 null). */
    public static Override get(String structureId, MobCategory category) {
        Map<String, Map<MobCategory, Override>> m = byStructure;
        if (m == null) {
            // 데이터 팩을 읽기 전(시험·도구): 서버에 내장된 월드젠 정의
            m = parse(StructureRegistry.structureJsons());
            byStructure = m;
        }
        Map<MobCategory, Override> c = m.get(structureId);
        return c == null ? null : c.get(category);
    }

    private static Map<String, Map<MobCategory, Override>> parse(Map<String, JsonObject> jsons) {
        Map<String, Map<MobCategory, Override>> out = new HashMap<>();
        for (var e : jsons.entrySet()) {
            if (!(e.getValue().get("spawn_overrides") instanceof JsonObject so)) continue;
            Map<MobCategory, Override> cats = new EnumMap<>(MobCategory.class);
            for (var c : so.entrySet()) {
                JsonObject o = c.getValue().getAsJsonObject();
                List<BiomeSpawns.SpawnerData> list = new ArrayList<>();
                for (JsonElement el : o.getAsJsonArray("spawns")) {
                    JsonObject s = el.getAsJsonObject();
                    list.add(new BiomeSpawns.SpawnerData(s.get("type").getAsString(), s.get("weight").getAsInt(),
                        s.get("minCount").getAsInt(), s.get("maxCount").getAsInt()));
                }
                cats.put(MobCategory.byKey(c.getKey()), new Override("piece".equals(o.get("bounding_box").getAsString()), List.copyOf(list)));
            }
            out.put(e.getKey(), cats);
        }
        return out;
    }
}
