// 바닐라 worldgen JSON 리소스를 Gson으로 로드하는 데이터 접근 계층
package org.domi.furnace.server.world.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * 바닐라 worldgen JSON 을 클래스패스 리소스에서 지연 로드한다.
 * 디렉토리 열거 대신 id 별 on-demand 로드 방식을 사용한다.
 *
 * 지원 경로:
 *   density_function/<id-path>.json  (id = "minecraft:<id-path>")
 *   noise/<name>.json                (id = "minecraft:<name>")
 *   noise_settings/<name>.json       (id = "minecraft:<name>")
 */
public final class WorldgenData {

    private final String basePath; // e.g. "worldgen/1.21.1"

    private WorldgenData(String basePath) {
        this.basePath = basePath;
    }

    /**
     * 지정된 basePath 를 사용하는 WorldgenData 인스턴스를 반환한다.
     * 실제 로드는 각 메서드 호출 시 lazy 하게 수행된다.
     */
    public static WorldgenData load(String basePath) {
        return new WorldgenData(basePath);
    }

    // ------------------------------------------------------------------
    // Public accessors
    // ------------------------------------------------------------------

    /**
     * 밀도 함수 JSON 을 반환한다.
     * id 형식: "minecraft:overworld/base_3d_noise" → 파일 경로:
     *   basePath/density_function/overworld/base_3d_noise.json
     *
     * 최상위 id (namespace 없는 경우) 도 허용:
     *   "minecraft:shift_x" → density_function/shift_x.json
     */
    public JsonObject densityFunction(String id) {
        String path = idToPath(id, "density_function");
        JsonElement el = loadResource(path);
        if (el == null) {
            throw new IllegalArgumentException("Density function not found: " + id + " (path: " + path + ")");
        }
        return el.getAsJsonObject();
    }

    /**
     * 노이즈 파라미터 JSON 을 반환한다.
     * id 형식: "minecraft:continentalness" → basePath/noise/continentalness.json
     */
    public JsonElement noiseParams(String id) {
        String path = idToPath(id, "noise");
        JsonElement el = loadResource(path);
        if (el == null) {
            throw new IllegalArgumentException("Noise params not found: " + id + " (path: " + path + ")");
        }
        return el;
    }

    /**
     * biome_parameters.json 배열을 반환한다.
     * 파일 경로: basePath/biome_parameters.json
     */
    public JsonArray biomeParameters() {
        JsonElement el = loadResource(basePath + "/biome_parameters.json");
        if (el == null) {
            throw new IllegalStateException("biome_parameters.json not found: " + basePath + "/biome_parameters.json");
        }
        return el.getAsJsonArray();
    }

    /**
     * noise_settings JSON 을 반환한다.
     * id 형식: "minecraft:overworld" → basePath/noise_settings/overworld.json
     */
    public JsonObject noiseSettings(String id) {
        String path = idToPath(id, "noise_settings");
        JsonElement el = loadResource(path);
        if (el == null) {
            throw new IllegalArgumentException("Noise settings not found: " + id + " (path: " + path + ")");
        }
        return el.getAsJsonObject();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * "minecraft:overworld/base_3d_noise" → "worldgen/1.21.1/density_function/overworld/base_3d_noise.json"
     * "minecraft:continentalness"         → "worldgen/1.21.1/noise/continentalness.json"
     */
    private String idToPath(String id, String subdir) {
        // strip "minecraft:" namespace prefix if present
        String localPath = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return basePath + "/" + subdir + "/" + localPath + ".json";
    }

    private JsonElement loadResource(String resourcePath) {
        ClassLoader cl = WorldgenData.class.getClassLoader();
        InputStream is = cl.getResourceAsStream(resourcePath);
        if (is == null) return null;
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse resource: " + resourcePath, e);
        }
    }
}
