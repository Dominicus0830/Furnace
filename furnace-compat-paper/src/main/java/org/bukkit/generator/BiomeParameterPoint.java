package org.bukkit.generator;

/** Paper 1.21.1 BiomeParameterPoint(바이옴 고르기 기후 값과 범위). */
public interface BiomeParameterPoint {

    double getTemperature();

    double getMaxTemperature();

    double getMinTemperature();

    double getHumidity();

    double getMaxHumidity();

    double getMinHumidity();

    double getContinentalness();

    double getMaxContinentalness();

    double getMinContinentalness();

    double getErosion();

    double getMaxErosion();

    double getMinErosion();

    double getDepth();

    double getMaxDepth();

    double getMinDepth();

    double getWeirdness();

    double getMaxWeirdness();

    double getMinWeirdness();
}
