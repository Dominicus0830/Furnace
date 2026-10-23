// worldgen/structure, structure_set JSON + 바이옴 태그 로더 — 바닐라 레지스트리 순서(id 사전순)로 step별 구조물 그룹을 제공한다.
package org.domi.furnace.server.world.gen.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class StructureRegistry {

    /** vanilla GenerationStep.Decoration 순서. */
    public static final List<String> STEPS = List.of(
        "raw_generation", "lakes", "local_modifications", "underground_structures", "surface_structures",
        "strongholds", "underground_ores", "underground_decoration", "fluid_springs", "vegetal_decoration", "top_layer_modification");

    public record Def(String id, String type, int step, String terrainAdaptation, Set<String> biomes, JsonObject json) {}

    public record Entry(String structureId, int weight) {}

    public record ExclusionZone(String otherSet, int chunkCount) {}

    public record SetDef(String id, List<Entry> entries, RandomSpreadStructurePlacement spread, ConcentricRings rings, ExclusionZone exclusion) {}

    public record ConcentricRings(int distance, int spread, int count, Set<String> preferredBiomes, int salt) {}

    private final Map<String, Def> structures = new TreeMap<>();
    private final Map<String, SetDef> sets = new TreeMap<>();
    private final List<List<Def>> byStep;

    public StructureRegistry() {
        for (String name : STRUCTURE_NAMES) {
            JsonObject j = readJson("worldgen/structure/" + name + ".json");
            String id = "minecraft:" + name;
            String step = j.get("step").getAsString();
            String ta = j.has("terrain_adaptation") ? j.get("terrain_adaptation").getAsString() : "none";
            structures.put(id, new Def(id, j.get("type").getAsString(), STEPS.indexOf(step), ta, resolveBiomes(j.get("biomes")), j));
        }
        for (String name : SET_NAMES) {
            JsonObject j = readJson("worldgen/structure_set/" + name + ".json");
            String id = "minecraft:" + name;
            List<Entry> entries = new ArrayList<>();
            for (JsonElement e : j.getAsJsonArray("structures")) {
                JsonObject eo = e.getAsJsonObject();
                entries.add(new Entry(eo.get("structure").getAsString(), eo.get("weight").getAsInt()));
            }
            JsonObject pj = j.getAsJsonObject("placement");
            String ptype = pj.get("type").getAsString();
            RandomSpreadStructurePlacement spread = null;
            ConcentricRings rings = null;
            if (ptype.equals("minecraft:random_spread")) {
                spread = new RandomSpreadStructurePlacement(
                    pj.get("spacing").getAsInt(), pj.get("separation").getAsInt(),
                    pj.has("spread_type") && pj.get("spread_type").getAsString().equals("triangular") ? RandomSpreadType.TRIANGULAR : RandomSpreadType.LINEAR,
                    pj.get("salt").getAsInt(),
                    FrequencyReductionMethod.fromSerializedName(pj.has("frequency_reduction_method") ? pj.get("frequency_reduction_method").getAsString() : "default"),
                    pj.has("frequency") ? pj.get("frequency").getAsFloat() : 1.0F);
            } else if (ptype.equals("minecraft:concentric_rings")) {
                rings = new ConcentricRings(pj.get("distance").getAsInt(), pj.get("spread").getAsInt(), pj.get("count").getAsInt(),
                    resolveBiomes(pj.get("preferred_biomes")), pj.get("salt").getAsInt());
            } else {
                throw new IllegalArgumentException("미지원 placement: " + ptype);
            }
            ExclusionZone ez = null;
            if (pj.has("exclusion_zone")) {
                JsonObject zj = pj.getAsJsonObject("exclusion_zone");
                ez = new ExclusionZone(zj.get("other_set").getAsString(), zj.get("chunk_count").getAsInt());
            }
            sets.put(id, new SetDef(id, List.copyOf(entries), spread, rings, ez));
        }
        List<List<Def>> steps = new ArrayList<>();
        for (int i = 0; i < STEPS.size(); i++) steps.add(new ArrayList<>());
        for (Def d : structures.values()) steps.get(d.step()).add(d);   // TreeMap = id 사전순 = 바닐라 레지스트리 순서(가정, 첫 구조물 검증으로 확인)
        byStep = List.copyOf(steps);
    }

    public Def structure(String id) { return structures.get(id); }

    public Map<String, SetDef> sets() { return sets; }

    /** step ordinal별 구조물 목록(레지스트리 순서). */
    public List<Def> structuresOfStep(int step) { return byStep.get(step); }

    // ------------------------------------------------------------------ 바이옴 태그 해석

    static Set<String> resolveBiomes(JsonElement e) {
        Set<String> out = new HashSet<>();
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) addBiomeRef(out, x.getAsString());
        } else {
            addBiomeRef(out, e.getAsString());
        }
        return Set.copyOf(out);
    }

    private static void addBiomeRef(Set<String> out, String ref) {
        if (!ref.startsWith("#")) { out.add(ref); return; }
        String tag = ref.substring(1);
        String path = "worldgen/1.21.1/tags/worldgen/biome/" + tag.substring("minecraft:".length()) + ".json";
        JsonObject j = readResource(path);
        for (JsonElement v : j.getAsJsonArray("values")) {
            String s = v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString();
            addBiomeRef(out, s);
        }
    }

    private static JsonObject readJson(String rel) { return readResource("worldgen/1.21.1/" + rel); }

    private static JsonObject readResource(String path) {
        try (InputStream in = StructureRegistry.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalArgumentException("리소스 없음: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final List<String> STRUCTURE_NAMES = List.of(
        "ancient_city", "bastion_remnant", "buried_treasure", "desert_pyramid", "end_city", "fortress", "igloo", "jungle_pyramid",
        "mansion", "mineshaft", "mineshaft_mesa", "monument", "nether_fossil", "ocean_ruin_cold", "ocean_ruin_warm", "pillager_outpost",
        "ruined_portal", "ruined_portal_desert", "ruined_portal_jungle", "ruined_portal_mountain", "ruined_portal_nether",
        "ruined_portal_ocean", "ruined_portal_swamp", "shipwreck", "shipwreck_beached", "stronghold", "swamp_hut", "trail_ruins",
        "trial_chambers", "village_desert", "village_plains", "village_savanna", "village_snowy", "village_taiga");

    private static final List<String> SET_NAMES = List.of(
        "ancient_cities", "buried_treasures", "desert_pyramids", "end_cities", "igloos", "jungle_temples", "mineshafts",
        "nether_complexes", "nether_fossils", "ocean_monuments", "ocean_ruins", "pillager_outposts", "ruined_portals", "shipwrecks",
        "strongholds", "swamp_huts", "trail_ruins", "trial_chambers", "villages", "woodland_mansions");
}
