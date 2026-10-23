// 바닐라 CarverConfiguration 포팅 — cave/canyon carver 설정
package org.domi.furnace.server.world.gen.carver;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public sealed interface CarverConfig permits CarverConfig.Cave, CarverConfig.Canyon {

    float probability();
    HeightProvider y();
    FloatProvider yScale();
    int lavaLevelAboveBottom();

    /** FloatProvider 코덱: 숫자 하나는 상수. */
    static FloatProvider fp(JsonElement e) {
        return e.isJsonPrimitive() ? FloatProvider.constant(e.getAsFloat()) : FloatProvider.fromJson(e.getAsJsonObject());
    }

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
            FloatProvider yScale = fp(cfg.get("yScale"));
            int lavaLevel = cfg.get("lava_level").getAsJsonObject().get("above_bottom").getAsInt();
            FloatProvider hMult = fp(cfg.get("horizontal_radius_multiplier"));
            FloatProvider vMult = fp(cfg.get("vertical_radius_multiplier"));
            FloatProvider floor = fp(cfg.get("floor_level"));
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
