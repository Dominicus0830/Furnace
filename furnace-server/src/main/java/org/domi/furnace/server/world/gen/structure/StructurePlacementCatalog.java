// structure_set JSON 로드 → 청크별 구조물 placement 후보 조회 카탈로그.
package org.domi.furnace.server.world.gen.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.world.gen.WorldgenData;

public final class StructurePlacementCatalog {
    private static final List<String> SUPPORTED_SET_IDS = List.of(
        "minecraft:desert_pyramids",
        "minecraft:shipwrecks"
    );

    private final List<StructureSetPlacement> sets;

    private StructurePlacementCatalog(List<StructureSetPlacement> sets) {
        this.sets = List.copyOf(sets);
    }

    public static StructurePlacementCatalog load(WorldgenData data) {
        ArrayList<StructureSetPlacement> sets = new ArrayList<>(SUPPORTED_SET_IDS.size());
        for (String setId : SUPPORTED_SET_IDS) {
            sets.add(parseSet(setId, data.structureSet(setId)));
        }
        return new StructurePlacementCatalog(sets);
    }

    public List<StructureSetPlacement> sets() {
        return sets;
    }

    public List<StructureSetPlacement> placementsForChunk(long seed, int chunkX, int chunkZ) {
        ArrayList<StructureSetPlacement> matches = new ArrayList<>();
        for (StructureSetPlacement set : sets) {
            if (set.placement().isStructureChunk(seed, chunkX, chunkZ)) {
                matches.add(set);
            }
        }
        return List.copyOf(matches);
    }

    private static StructureSetPlacement parseSet(String setId, JsonObject root) {
        JsonArray structuresJson = root.getAsJsonArray("structures");
        ArrayList<String> structureIds = new ArrayList<>(structuresJson.size());
        for (JsonElement element : structuresJson) {
            JsonObject entry = element.getAsJsonObject();
            structureIds.add(entry.get("structure").getAsString());
        }

        JsonObject placementJson = root.getAsJsonObject("placement");
        String type = placementJson.get("type").getAsString();
        if (!"minecraft:random_spread".equals(type)) {
            throw new IllegalArgumentException("Unsupported placement type for " + setId + ": " + type);
        }

        RandomSpreadStructurePlacement placement = new RandomSpreadStructurePlacement(
            placementJson.get("spacing").getAsInt(),
            placementJson.get("separation").getAsInt(),
            parseSpreadType(placementJson),
            placementJson.get("salt").getAsInt(),
            parseFrequencyReductionMethod(placementJson),
            placementJson.has("frequency") ? placementJson.get("frequency").getAsFloat() : 1.0F
        );
        return new StructureSetPlacement(setId, List.copyOf(structureIds), placement);
    }

    private static RandomSpreadType parseSpreadType(JsonObject placementJson) {
        String value = placementJson.has("spread_type")
            ? placementJson.get("spread_type").getAsString()
            : "linear";
        return switch (value) {
            case "linear" -> RandomSpreadType.LINEAR;
            case "triangular" -> RandomSpreadType.TRIANGULAR;
            default -> throw new IllegalArgumentException("Unsupported spread type: " + value);
        };
    }

    private static FrequencyReductionMethod parseFrequencyReductionMethod(JsonObject placementJson) {
        String value = placementJson.has("frequency_reduction_method")
            ? placementJson.get("frequency_reduction_method").getAsString()
            : "default";
        return FrequencyReductionMethod.fromSerializedName(value);
    }
}
