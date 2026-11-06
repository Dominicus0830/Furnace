// 플러그인별 시간 프로파일러(Bukkit timings 에 해당) — 리스너·예약 작업·명령·수명 함수에 쓴 시간을 플러그인마다 모은다.
// 꺼져 있을 때 재는 지점마다 남는 비용은 volatile 읽기 하나다. 여러 region 스레드가 동시에 기록해도 된다.
// 플러그인에는 "자기 시간"(그 안에서 잰 다른 구간을 뺀 값)을 매긴다 — 리스너 안에서 다른 플러그인의 이벤트·명령이 돌아도 두 번 세지 않는다.
// 서버 운영자는 /timings 로 켜고 끄고 본다. 플러그인은 timing(...) 으로 자기 코드 구간을 따로 잴 수 있다(종류 CUSTOM).
package org.domi.furnace.api.profiler;

import java.util.List;
import org.domi.furnace.api.plugin.FurnacePlugin;

public interface PluginProfiler {

    /** 측정 구간의 종류. */
    enum Kind {
        /** 이벤트 리스너. */
        LISTENER,
        /** 틱 스레드(전역·region·엔티티)에서 도는 예약 작업. */
        TASK,
        /** 비동기 실행기(가상 스레드) 작업. */
        ASYNC,
        /** 명령 실행·자동완성. */
        COMMAND,
        /** onLoad·onEnable·onDisable. */
        LIFECYCLE,
        /** 플러그인이 직접 잰 구간. */
        CUSTOM
    }

    /** 시간을 쓴 스레드의 종류. */
    enum ThreadKind {
        /** region 틱 스레드(워커). */
        REGION,
        /** 전역 틱 스레드(명령·전역 작업). */
        GLOBAL,
        /** 그 밖(비동기 가상 스레드·네트워크 스레드 등) — 틱을 늦추지 않는 시간. */
        OTHER
    }

    boolean isRunning();

    /** 재기 시작(이미 켜져 있으면 아무것도 안 함). 모은 값은 그대로 이어진다. */
    void start();

    /** 재기 멈춤. 모은 값은 남는다. */
    void stop();

    /** 모은 값을 모두 지운다(켜짐 상태는 그대로). */
    void reset();

    /** 그 플러그인의 측정 구간. 같은 (플러그인, 종류, 이름) 이면 같은 구간을 돌려준다. */
    Timing timing(FurnacePlugin plugin, Kind kind, String name);

    /** 지금까지 모은 값(자기 시간이 큰 플러그인 순, 그 안은 자기 시간이 큰 구간 순). 호출이 없던 구간은 빠진다. */
    Report report();

    /**
     * @param measuredNanos 켜져 있던 시간의 합(마지막 reset 뒤)
     * @param tickNanos     틱 하나의 예산(보통 50ms) — "틱당 시간" 환산과 초과 횟수 기준
     */
    record Report(long measuredNanos, boolean running, long tickNanos, List<PluginStats> plugins) {}

    /** 플러그인 하나의 합계. 시간은 모두 자기 시간이다. */
    record PluginStats(String plugin, long selfNanos, long calls, long regionNanos, long globalNanos, long otherNanos,
                       List<SectionStats> sections) {}

    /**
     * 구간 하나.
     * @param totalNanos 안에서 잰 다른 구간까지 포함한 시간
     * @param selfNanos  그 구간을 뺀 자기 시간
     * @param overBudget 한 번에 틱 예산(tickNanos)을 넘긴 호출 수
     */
    record SectionStats(Kind kind, String name, long calls, long totalNanos, long selfNanos, long maxNanos, long overBudget) {}

    /** 아무것도 재지 않는 프로파일러(서버가 프로파일러를 주지 않을 때). */
    PluginProfiler NONE = new PluginProfiler() {
        @Override
        public boolean isRunning() { return false; }

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public void reset() {}

        @Override
        public Timing timing(FurnacePlugin plugin, Kind kind, String name) { return Timing.NONE; }

        @Override
        public Report report() { return new Report(0, false, 50_000_000L, List.of()); }
    };
}
