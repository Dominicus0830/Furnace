// 오버월드 carver 3개 설정 로딩 — cave(0.15) + cave_extra_underground(0.07) + canyon(0.01)
package org.domi.furnace.server.world.gen.carver;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.util.List;

public final class OverworldCarvers {

    public record Entry(WorldCarver carver, CarverConfig config) {}

    private final List<Entry> entries;

    private OverworldCarvers(List<Entry> entries) {
        this.entries = entries;
    }

    public List<Entry> entries() { return entries; }

    public static OverworldCarvers load() {
        Gson gson = new Gson();
        JsonObject cave      = loadJson(gson, "worldgen/1.21.1/configured_carver/cave.json");
        JsonObject caveExtra = loadJson(gson, "worldgen/1.21.1/configured_carver/cave_extra_underground.json");
        JsonObject canyon    = loadJson(gson, "worldgen/1.21.1/configured_carver/canyon.json");
        return new OverworldCarvers(List.of(
            new Entry(new CaveWorldCarver(),   CarverConfig.Cave.fromJson(cave.get("config").getAsJsonObject())),
            new Entry(new CaveWorldCarver(),   CarverConfig.Cave.fromJson(caveExtra.get("config").getAsJsonObject())),
            new Entry(new CanyonWorldCarver(), CarverConfig.Canyon.fromJson(canyon.get("config").getAsJsonObject()))
        ));
    }

    private static JsonObject loadJson(Gson gson, String path) {
        try (var in = OverworldCarvers.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Resource not found: " + path);
            return gson.fromJson(new InputStreamReader(in), JsonObject.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
