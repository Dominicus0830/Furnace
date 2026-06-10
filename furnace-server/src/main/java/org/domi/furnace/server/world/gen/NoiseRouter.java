// 오버월드 NoiseRouter: noise_settings/overworld.json 에서 final_density DF 를 파싱해 보관
package org.domi.furnace.server.world.gen;

import com.google.gson.JsonObject;
import org.domi.furnace.server.world.gen.df.DensityFunction;
import org.domi.furnace.server.world.gen.df.DensityFunctionParser;

/**
 * 바닐라 NoiseRouter 의 경량 포트.
 *
 * 현재 Task 11 에서는 final_density 만 파싱해 보관한다.
 * barrier, fluid_level_*, vein_* 등 나머지 엔트리는 Task 12c 에서 추가한다.
 */
public final class NoiseRouter {

    private final DensityFunction finalDensity;

    private NoiseRouter(DensityFunction finalDensity) {
        this.finalDensity = finalDensity;
    }

    /**
     * overworld noise_settings 에서 NoiseRouter 를 빌드한다.
     *
     * @param data     worldgen 리소스 접근자
     * @param settings 파싱된 NoiseSettings (현재는 참조만; 추후 cellWidth/Height 전달용)
     * @param seed     월드 시드 (DensityFunctionParser 노이즈 시딩에 사용)
     */
    public static NoiseRouter build(WorldgenData data, NoiseSettings settings, long seed) {
        DensityFunctionParser parser = new DensityFunctionParser(data, seed);
        JsonObject noiseSettingsJson = data.noiseSettings("minecraft:overworld");
        JsonObject routerJson = noiseSettingsJson.get("noise_router").getAsJsonObject();
        DensityFunction finalDensity = parser.parseElement(routerJson.get("final_density"));
        return new NoiseRouter(finalDensity);
    }

    /** 블록 고체/공기 결정에 사용되는 최종 밀도 함수. */
    public DensityFunction finalDensity() {
        return finalDensity;
    }
}
