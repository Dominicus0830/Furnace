// 바닐라 CarverConfiguration 포팅 — cave/canyon carver 설정
package org.domi.furnace.server.world.gen.carver;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public sealed interface CarverConfig permits CarverConfig.Cave, CarverConfig.Canyon {

    float probability();
    HeightProvider y();
    FloatProvider yScale();
    int lavaLevelAboveBottom();

    record Cave(
        float probability,
        HeightProvider y,
        FloatProvider yScale,
        int lavaLevelAboveBottom,
        FloatProvider horizontalRadiusMultiplier,
        FloatProvider verticalRadiusMultiplier,
        FloatProvider floorLevel
    ) implements CarverConfig {

        public static Cave fromJson(JsonObject cfg) {
            float prob = cfg.get("probability").getAsFloat();
            HeightProvider yProv = HeightProvider.fromJson(cfg.get("y").getAsJsonObject());
            FloatProvider yScale = FloatProvider.fromJson(cfg.get("yScale").getAsJsonObject());
            int lavaLevel = cfg.get("lava_level").getAsJsonObject().get("above_bottom").getAsInt();
            FloatProvider hMult = FloatProvider.fromJson(cfg.get("horizontal_radius_multiplier").getAsJsonObject());
            FloatProvider vMult = FloatProvider.fromJson(cfg.get("vertical_radius_multiplier").getAsJsonObject());
            FloatProvider floor = FloatProvider.fromJson(cfg.get("floor_level").getAsJsonObject());
            return new Cave(prob, yProv, yScale, lavaLevel, hMult, vMult, floor);
        }
    }

    record Canyon(
        float probability,
        HeightProvider y,
        FloatProvider yScale,
        int lavaLevelAboveBottom,
        FloatProvider verticalRotation,
        FloatProvider shapeThickness,
        FloatProvider shapeDistanceFactor,
        FloatProvider shapeHorizontalRadiusFactor,
        float shapeVerticalRadiusCenterFactor,
        float shapeVerticalRadiusDefaultFactor,
        int shapeWidthSmoothness
    ) implements CarverConfig {

        public static Canyon fromJson(JsonObject cfg) {
            float prob = cfg.get("probability").getAsFloat();
            HeightProvider yProv = HeightProvider.fromJson(cfg.get("y").getAsJsonObject());
            // yScale is a plain float in canyon JSON, not a FloatProvider object
            float yScaleVal = cfg.get("yScale").getAsFloat();
            int lavaLevel = cfg.get("lava_level").getAsJsonObject().get("above_bottom").getAsInt();
            FloatProvider vRot = FloatProvider.fromJson(cfg.get("vertical_rotation").getAsJsonObject());
            JsonObject shape = cfg.get("shape").getAsJsonObject();
            FloatProvider thickness = FloatProvider.fromJson(shape.get("thickness").getAsJsonObject());
            FloatProvider distFactor = FloatProvider.fromJson(shape.get("distance_factor").getAsJsonObject());
            FloatProvider hrFactor = FloatProvider.fromJson(shape.get("horizontal_radius_factor").getAsJsonObject());
            float vrCenter = shape.get("vertical_radius_center_factor").getAsFloat();
            float vrDefault = shape.get("vertical_radius_default_factor").getAsFloat();
            int widthSmooth = shape.get("width_smoothness").getAsInt();
            return new Canyon(prob, yProv, FloatProvider.constant(yScaleVal), lavaLevel,
                vRot, thickness, distFactor, hrFactor, vrCenter, vrDefault, widthSmooth);
        }
    }
}
