// 빈 서버 일시정지(바닐라 1.21.2+ pause-when-empty-seconds): 접속자가 없는 채로 정한 시간이 지나면 전역·region 게임 틱을 멈춘다.
// 멈출 때 바닐라처럼 한 번 저장하고(autoSave), 켜져 있으면 지역 파일 핸들을 닫고 GC 를 권한다(Paper #13864 — 쉬는 동안 메모리·fd 반납).
// 판단은 전역 틱(전역 스레드)에서만 한다. 저장·정리는 유지보수 스레드에서 — 전역 스레드가 region 저장을 기다리지 않게.
package org.domi.furnace.server.maintenance;

import java.util.concurrent.Executor;

public final class IdlePause {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IdlePause.class);

    /** 멈출 때·다시 돌 때 할 일(서버가 채운다). */
    public interface Hooks {
        /** 모든 차원 region 게임 틱을 붙잡는다 / 놓는다. */
        void holdWorlds(boolean on);

        /** 바닐라 autoSave(접속자 없음 — 청크·level.dat). 유지보수 스레드에서 부른다. 끝날 때까지 기다린다. */
        void saveAll();

        /** 저장 뒤 캐시 비우기(지역 파일 핸들 닫기 등). 유지보수 스레드에서. */
        void flushCaches();
    }

    private final int seconds;
    private final int thresholdTicks;
    private final boolean flush;
    private final Hooks hooks;
    private final Executor maintenance;
    private int emptyTicks;
    private volatile boolean paused;
    /** 일시정지에 들어간 횟수(관측·시험). */
    private volatile int pauses;

    public IdlePause(int seconds, boolean flush, Hooks hooks, Executor maintenance) {
        if (seconds <= 0) throw new IllegalArgumentException("seconds " + seconds);
        this.seconds = seconds;
        this.thresholdTicks = Math.multiplyExact(seconds, 20);
        this.flush = flush;
        this.hooks = hooks;
        this.maintenance = maintenance;
    }

    /**
     * 전역 틱마다(전역 스레드). 참이면 이번 전역 틱을 건너뛴다(일시정지 중). 바닐라 tickServer: 접속자가 없고 sprint 가 아니면 빈 틱을 세고,
     * 문턱에 닿는 틱에 "Server empty for N seconds, pausing" 을 찍고 저장한다. 접속자가 생기면 다음 틱부터 다시 돈다.
     */
    public boolean onGlobalTick(boolean empty, boolean sprinting) {
        if (empty && !sprinting) {
            if (emptyTicks < Integer.MAX_VALUE) emptyTicks++;
        } else {
            emptyTicks = 0;
        }
        if (emptyTicks >= thresholdTicks) {
            if (!paused) enter();
            return true;
        }
        if (paused) leave();
        return false;
    }

    private void enter() {
        paused = true;
        pauses++;
        log.info("Server empty for {} seconds, pausing", seconds);
        hooks.holdWorlds(true);
        try {
            maintenance.execute(() -> {
                try {
                    hooks.saveAll();
                    if (flush && paused) hooks.flushCaches();
                } catch (RuntimeException e) {
                    log.error("[PAUSE] 일시정지 저장 실패", e);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.warn("[PAUSE] 유지보수 스레드가 닫혀 일시정지 저장을 건너뛴다");
        }
    }

    private void leave() {
        paused = false;
        hooks.holdWorlds(false);
        log.info("[PAUSE] 접속자가 생겨 틱을 다시 돌린다");
    }

    public boolean paused() { return paused; }

    public int pauses() { return pauses; }

    public int seconds() { return seconds; }
}
