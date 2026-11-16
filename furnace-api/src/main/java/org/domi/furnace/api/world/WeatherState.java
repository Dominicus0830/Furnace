// 월드 날씨 상태(level.dat 의 같은 이름 칸). 불변 값.
package org.domi.furnace.api.world;

/**
 * @param raining 비 깃발(바닐라 LevelData.isRaining. 비 세기가 아니라 깃발이다)
 * @param thundering 천둥 깃발
 * @param rainTime 비 깃발이 바뀔 때까지 남은 틱
 * @param thunderTime 천둥 깃발이 바뀔 때까지 남은 틱
 * @param clearWeatherTime /weather clear 로 맑음을 붙잡은 남은 틱
 */
public record WeatherState(boolean raining, boolean thundering, int rainTime, int thunderTime, int clearWeatherTime) {}
