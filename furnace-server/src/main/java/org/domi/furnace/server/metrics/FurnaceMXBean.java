package org.domi.furnace.server.metrics;

import java.util.Map;

/** JMX 로 보이는 서버 값(org.domi.furnace:type=Server). 값은 읽을 때마다 새로 모은다. 속성 이름은 메트릭 이름에서 접두사를 뗀 것. */
public interface FurnaceMXBean {
    /** 메트릭 이름 → 값. 라벨이 있는 것은 이름{라벨=값,...} 형태 키. */
    Map<String, Double> getMetrics();

    double getPlayersOnline();

    /** 전역 TPS 5초 값(잴 수 없으면 NaN). */
    double getGlobalTps();

    double getLoadedChunks();
}
