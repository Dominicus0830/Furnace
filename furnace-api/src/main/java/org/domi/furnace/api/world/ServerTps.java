// 서버 TPS 관측 사본(읽기 전용, 아무 스레드). Furnace 는 region 마다 따로 틱하므로 "서버 TPS"를 한 가지로 줄이지 않고 세 가지를 함께 준다.
// 정의는 docs/api/tps.md 와 docs/feature-parity/tps-q3-2026-11-07.md.
package org.domi.furnace.api.world;

/**
 * 모든 TPS 는 초당 틱 수이고, 잴 수 없으면(가동 직후 등) NaN 이다. 목표는 {@code /tick rate}(기본 20)지만 서버 전역 값은 늘 20 기준이다.
 *
 * @param active5s      활성 region(플레이어가 있는 region)의 평균 TPS, 최근 5초. 플레이어 수로 가중한다 — "플레이어 한 명이 겪는 평균 틱 속도".
 *                      활성 region 이 없으면(접속자 없음) 전역 박동 값으로 대신한다.
 * @param active1m      같은 값의 최근 1분 평균(1초마다 잰 값의 평균). 아래 5m·15m 도 같다.
 * @param active5m      5분
 * @param active15m     15분
 * @param activeRegions 지금 활성 region 수(잴 수 있는 것만)
 * @param activePlayers 그 활성 region 들의 플레이어 수 합
 * @param slowestActive 활성 region 중 가장 느린 region 의 최근 5초 TPS(없으면 NaN)
 * @param slowestRegion 플레이어가 없는 region 까지 포함해 가장 느린 region 의 최근 5초 TPS(없으면 NaN)
 * @param global5s      전역 스레드 박동(시간·날씨·명령 큐·보더)의 최근 5초 TPS
 * @param global1m      전역 박동 1분
 * @param global5m      전역 박동 5분
 * @param global15m     전역 박동 15분
 */
public record ServerTps(double active5s, double active1m, double active5m, double active15m,
                        int activeRegions, int activePlayers, double slowestActive, double slowestRegion,
                        double global5s, double global1m, double global5m, double global15m) {

    /** 서버가 값을 주지 않을 때. */
    public static final ServerTps UNKNOWN = new ServerTps(Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, 0,
            Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);

    /** 지금 값: 활성 region 평균 최근 5초. */
    public double current() { return active5s; }

    /** Paper {@code Bukkit.getTPS()} 모양: {1분, 5분, 15분} 활성 region 평균. */
    public double[] oneFiveFifteen() { return new double[] {active1m, active5m, active15m}; }
}
