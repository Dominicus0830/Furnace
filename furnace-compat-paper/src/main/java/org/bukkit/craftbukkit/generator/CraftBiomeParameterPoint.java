package org.bukkit.craftbukkit.generator;

import org.bukkit.generator.BiomeParameterPoint;
import org.domi.furnace.api.worldgen.Climate;

/**
 * Paper CraftBiomeParameterPoint — BiomeProvider#getBiome 이 받는 그 칸의 기후. 값은 그 칸의 기후(처음 읽을 때 계산), 범위는 기후 밀도 함수의
 * 범위(T-264 P2c-3, 옛 BukkitBiomeProviders$Point). 생성자는 Paper 처럼 private 이고 호환 계층은 CraftAccess 로 만든다.
 */
public class CraftBiomeParameterPoint implements BiomeParameterPoint {
    private final Climate c;

    private CraftBiomeParameterPoint(Climate c) { this.c = c; }

    @Override public double getTemperature() { return c.value(Climate.Parameter.TEMPERATURE); }
    @Override public double getMaxTemperature() { return c.max(Climate.Parameter.TEMPERATURE); }
    @Override public double getMinTemperature() { return c.min(Climate.Parameter.TEMPERATURE); }
    @Override public double getHumidity() { return c.value(Climate.Parameter.HUMIDITY); }
    @Override public double getMaxHumidity() { return c.max(Climate.Parameter.HUMIDITY); }
    @Override public double getMinHumidity() { return c.min(Climate.Parameter.HUMIDITY); }
    @Override public double getContinentalness() { return c.value(Climate.Parameter.CONTINENTALNESS); }
    @Override public double getMaxContinentalness() { return c.max(Climate.Parameter.CONTINENTALNESS); }
    @Override public double getMinContinentalness() { return c.min(Climate.Parameter.CONTINENTALNESS); }
    @Override public double getErosion() { return c.value(Climate.Parameter.EROSION); }
    @Override public double getMaxErosion() { return c.max(Climate.Parameter.EROSION); }
    @Override public double getMinErosion() { return c.min(Climate.Parameter.EROSION); }
    @Override public double getDepth() { return c.value(Climate.Parameter.DEPTH); }
    @Override public double getMaxDepth() { return c.max(Climate.Parameter.DEPTH); }
    @Override public double getMinDepth() { return c.min(Climate.Parameter.DEPTH); }
    @Override public double getWeirdness() { return c.value(Climate.Parameter.WEIRDNESS); }
    @Override public double getMaxWeirdness() { return c.max(Climate.Parameter.WEIRDNESS); }
    @Override public double getMinWeirdness() { return c.min(Climate.Parameter.WEIRDNESS); }
}
