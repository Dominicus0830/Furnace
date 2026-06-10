// 바닐라 NoiseRouter의 경량 포트. final_density + 6개 기후 DF 보유.
package org.domi.furnace.server.world.gen;

import com.google.gson.JsonObject;
import org.domi.furnace.server.world.gen.df.DensityFunction;
import org.domi.furnace.server.world.gen.df.DensityFunctionParser;

public final class NoiseRouter {

    private final DensityFunction finalDensity;
    private final DensityFunction temperature;
    private final DensityFunction humidity;   // JSON 필드명: "vegetation"
    private final DensityFunction continents;
    private final DensityFunction erosion;
    private final DensityFunction depth;
    private final DensityFunction ridges;     // JSON 필드명: "ridges" (= weirdness)

    private NoiseRouter(DensityFunction finalDensity,
                        DensityFunction temperature, DensityFunction humidity,
                        DensityFunction continents, DensityFunction erosion,
                        DensityFunction depth, DensityFunction ridges) {
        this.finalDensity = finalDensity;
        this.temperature  = temperature;
        this.humidity     = humidity;
        this.continents   = continents;
        this.erosion      = erosion;
        this.depth        = depth;
        this.ridges       = ridges;
    }

    public static NoiseRouter build(WorldgenData data, NoiseSettings settings, long seed) {
        DensityFunctionParser parser = new DensityFunctionParser(data, seed);
        JsonObject routerJson = data.noiseSettings("minecraft:overworld")
                                    .get("noise_router").getAsJsonObject();
        return new NoiseRouter(
            parser.parseElement(routerJson.get("final_density")),
            parser.parseElement(routerJson.get("temperature")),
            parser.parseElement(routerJson.get("vegetation")),   // humidity
            parser.parseElement(routerJson.get("continents")),
            parser.parseElement(routerJson.get("erosion")),
            parser.parseElement(routerJson.get("depth")),
            parser.parseElement(routerJson.get("ridges"))
        );
    }

    public DensityFunction finalDensity() { return finalDensity; }
    public DensityFunction temperature()  { return temperature;  }
    public DensityFunction humidity()     { return humidity;     }
    public DensityFunction continents()   { return continents;   }
    public DensityFunction erosion()      { return erosion;      }
    public DensityFunction depth()        { return depth;        }
    public DensityFunction ridges()       { return ridges;       }
}
