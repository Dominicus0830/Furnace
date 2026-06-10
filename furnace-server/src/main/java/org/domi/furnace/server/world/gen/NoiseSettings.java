// 오버월드 노이즈 생성 설정값을 보관하는 불변 레코드 (noise_settings JSON 파싱 포함)
package org.domi.furnace.server.world.gen;

import com.google.gson.JsonObject;

/**
 * noise_settings JSON 의 핵심 파라미터를 담는 불변 레코드.
 *
 * cellWidth  = 4 * size_horizontal
 * cellHeight = 4 * size_vertical
 * lavaFloorY = -54 (오버월드 글로벌 상수; aquifer lava floor)
 *
 * defaultBlock / defaultFluid 는 바닐라 Name 문자열 그대로 저장.
 * 블록 상태 ID 해석은 Task 13 의 BlockRegistry 에서 수행.
 */
public record NoiseSettings(
        int minY,
        int height,
        int cellWidth,
        int cellHeight,
        int seaLevel,
        String defaultBlock,
        String defaultFluid,
        int lavaFloorY
) {

    /** 오버월드 lava floor Y 상수 */
    public static final int LAVA_FLOOR_Y = -54;

    /**
     * noise_settings JSON 객체에서 NoiseSettings 를 파싱한다.
     * noiseSettingsJson: data.noiseSettings("minecraft:overworld") 의 반환값.
     */
    public static NoiseSettings fromJson(JsonObject noiseSettingsJson) {
        JsonObject noise = noiseSettingsJson.get("noise").getAsJsonObject();
        int minY           = noise.get("min_y").getAsInt();
        int height         = noise.get("height").getAsInt();
        int cellWidth      = 4 * noise.get("size_horizontal").getAsInt();
        int cellHeight     = 4 * noise.get("size_vertical").getAsInt();
        int seaLevel       = noiseSettingsJson.get("sea_level").getAsInt();

        String defaultBlock = noiseSettingsJson
                .get("default_block").getAsJsonObject()
                .get("Name").getAsString();
        String defaultFluid = noiseSettingsJson
                .get("default_fluid").getAsJsonObject()
                .get("Name").getAsString();

        return new NoiseSettings(minY, height, cellWidth, cellHeight, seaLevel,
                defaultBlock, defaultFluid, LAVA_FLOOR_Y);
    }
}
