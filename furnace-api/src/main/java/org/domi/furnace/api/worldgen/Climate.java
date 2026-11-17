// 한 칸의 바닐라 기후 값(Climate.TargetPoint 를 실수로 — Climate#unquantizeCoord) — BiomeProvider 가 받는다.
package org.domi.furnace.api.worldgen;

public interface Climate {

    enum Parameter { TEMPERATURE, HUMIDITY, CONTINENTALNESS, EROSION, DEPTH, WEIRDNESS }

    /** 그 칸의 값(양자화한 값 / 10000 — 바닐라 바이옴 고르기가 보는 값). */
    double value(Parameter parameter);

    /** 이 월드에서 그 값이 가질 수 있는 가장 작은 값(그 기후 밀도 함수의 minValue). */
    double min(Parameter parameter);

    /** 이 월드에서 그 값이 가질 수 있는 가장 큰 값(그 기후 밀도 함수의 maxValue). */
    double max(Parameter parameter);
}
