// 바닐라 NoiseRouter의 경량 포트. final_density + initialDensityWithoutJaggedness + 6개 기후 DF 보유.
package org.domi.furnace.server.world.gen;

import com.google.gson.JsonObject;
import org.domi.furnace.server.world.gen.df.DensityFunction;
import org.domi.furnace.server.world.gen.df.DensityFunctionParser;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;

public final class NoiseRouter {

    private final DensityFunction finalDensity;
    private final DensityFunction initialDensityWithoutJaggedness;
    private final DensityFunction temperature;
    private final DensityFunction humidity;   // JSON 필드명: "vegetation"
    private final DensityFunction continents;
    private final DensityFunction erosion;
    private final DensityFunction depth;
    private final DensityFunction ridges;     // JSON 필드명: "ridges" (= weirdness)
    private final DensityFunction barrierNoise;
    private final DensityFunction fluidLevelFloodednessNoise;
    private final DensityFunction fluidLevelSpreadNoise;
    private final DensityFunction lavaNoise;
    private final XoroshiroRandomSource.XoroshiroPositionalRandomFactory aquiferRandom;

    private NoiseRouter(DensityFunction finalDensity,
                        DensityFunction initialDensityWithoutJaggedness,
                        DensityFunction temperature, DensityFunction humidity,
                        DensityFunction continents, DensityFunction erosion,
                        DensityFunction depth, DensityFunction ridges,
                        DensityFunction barrierNoise,
                        DensityFunction fluidLevelFloodednessNoise,
                        DensityFunction fluidLevelSpreadNoise,
                        DensityFunction lavaNoise,
                        XoroshiroRandomSource.XoroshiroPositionalRandomFactory aquiferRandom) {
        this.finalDensity = finalDensity;
        this.initialDensityWithoutJaggedness = initialDensityWithoutJaggedness;
        this.temperature  = temperature;
        this.humidity     = humidity;
        this.continents   = continents;
        this.erosion      = erosion;
        this.depth        = depth;
        this.ridges       = ridges;
        this.barrierNoise = barrierNoise;
        this.fluidLevelFloodednessNoise = fluidLevelFloodednessNoise;
        this.fluidLevelSpreadNoise = fluidLevelSpreadNoise;
        this.lavaNoise = lavaNoise;
        this.aquiferRandom = aquiferRandom;
    }

    public static NoiseRouter build(WorldgenData data, NoiseSettings settings, long seed) {
        DensityFunctionParser parser = new DensityFunctionParser(data, seed);
        JsonObject routerJson = data.noiseSettings("minecraft:overworld")
                                    .get("noise_router").getAsJsonObject();
        return new NoiseRouter(
            parser.parseElement(routerJson.get("final_density")),
            parser.parseElement(routerJson.get("initial_density_without_jaggedness")),
            parser.parseElement(routerJson.get("temperature")),
            parser.parseElement(routerJson.get("vegetation")),   // humidity
            parser.parseElement(routerJson.get("continents")),
            parser.parseElement(routerJson.get("erosion")),
            parser.parseElement(routerJson.get("depth")),
            parser.parseElement(routerJson.get("ridges")),
            parser.parseElement(routerJson.get("barrier")),
            parser.parseElement(routerJson.get("fluid_level_floodedness")),
            parser.parseElement(routerJson.get("fluid_level_spread")),
            parser.parseElement(routerJson.get("lava")),
            parser.aquiferRandom()
        );
    }

    public DensityFunction finalDensity() { return finalDensity; }
    public DensityFunction initialDensityWithoutJaggedness() { return initialDensityWithoutJaggedness; }
    public DensityFunction temperature() { return temperature; }
    public DensityFunction humidity() { return humidity; }
    public DensityFunction continents() { return continents; }
    public DensityFunction erosion() { return erosion; }
    public DensityFunction depth() { return depth; }
    public DensityFunction ridges() { return ridges; }
    public DensityFunction barrierNoise() { return barrierNoise; }
    public DensityFunction fluidLevelFloodednessNoise() { return fluidLevelFloodednessNoise; }
    public DensityFunction fluidLevelSpreadNoise() { return fluidLevelSpreadNoise; }
    public DensityFunction lavaNoise() { return lavaNoise; }
    public XoroshiroRandomSource.XoroshiroPositionalRandomFactory aquiferRandom() { return aquiferRandom; }
}
