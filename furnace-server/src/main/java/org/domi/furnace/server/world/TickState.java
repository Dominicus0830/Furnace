// /tick 이 바꾸는 서버 틱 상태: 틱 속도(기본 20), 정지 여부, 정지 중 한 걸음씩 진행(step), 그리고 /tick query 가 보여 줄 틱 시간 표본.
// Furnace 는 region 마다 따로 틱하므로 "서버 틱"은 하나가 아니다 — 속도는 모든 region 의 틱 주기와 전역 틱(시간·날씨) 주기에 함께 걸고,
// 정지는 각 region 이 월드 진행(예약 틱·무작위 틱·스폰·엔티티·블록 엔티티)만 건너뛰고 플레이어 처리는 계속하며,
// step 은 정지 중인 각 region 이 N 번씩 월드 진행을 하게 한다. sprint 는 전역·각 region 이 N 틱을 기다림 없이 돈다
// (docs/feature-parity/tick-sprint-design-2026-10-28.md). 틱 시간 표본은 region 틱 하나하나의 소요 시간이다.
package org.domi.furnace.server.world;

import java.util.concurrent.atomic.AtomicInteger;

public final class TickState {

    public static final float DEFAULT_RATE = 20.0f;
    /** 바닐라 MinecraftServer.tickTimesNanos 길이. */
    public static final int SAMPLE_COUNT = 100;

    private volatile float rate = DEFAULT_RATE;
    private volatile boolean frozen;
    private volatile int generation;
    private volatile int requestedSteps;
    private final AtomicInteger globalStepsLeft = new AtomicInteger();
    private final long[] samples = new long[SAMPLE_COUNT];
    private int nextSample;
    private volatile double averageNanos;

    public float rate() { return rate; }

    public boolean frozen() { return frozen; }

    /** 바닐라 ServerTickRateManager.nanosecondsPerTick = (long) (1e9 / rate). */
    public long nanosPerTick() { return (long) (1.0E9F / rate); }

    public void setRate(float rate) { this.rate = rate; }

    /** 정지/해제. 어느 쪽이든 진행 중이던 step 은 멈춘다. */
    public void setFrozen(boolean frozen) {
        cancelSteps();
        this.frozen = frozen;
    }

    // ------------------------------------------------------------------ step

    /** 정지 중 n 틱 진행을 요청한다(이미 진행 중이면 덮어쓴다). 모든 region 이 다음 틱에 이 세대를 알아챈다. */
    public void startStep(int n) {
        requestedSteps = n;
        globalStepsLeft.set(n);
        generation++;
    }

    /** 진행 중이던 step 을 멈춘다. 진행 중이었으면 참. */
    public boolean cancelSteps() {
        boolean was = globalStepsLeft.getAndSet(0) > 0;
        requestedSteps = 0;
        generation++;
        return was;
    }

    /** region 이 보는 세대(바뀌었으면 stepsRequested 로 자기 남은 걸음을 다시 채운다). */
    public int generation() { return generation; }

    public int requestedSteps() { return requestedSteps; }

    public int globalStepsLeft() { return globalStepsLeft.get(); }

    /** 전역 틱 하나를 걸음으로 쓴다. 쓸 걸음이 없으면 거짓. */
    public boolean takeGlobalStep() {
        while (true) {
            int cur = globalStepsLeft.get();
            if (cur <= 0) return false;
            if (globalStepsLeft.compareAndSet(cur, cur - 1)) return true;
        }
    }

    // ------------------------------------------------------------------ sprint (설계: docs/feature-parity/tick-sprint-design-2026-10-28.md)

    private volatile int sprintGeneration;
    private volatile boolean sprinting;
    private volatile int sprintTicks;
    private volatile long sprintGlobalLeft;
    private long sprintStartNanos;
    private boolean sprintWasFrozen;

    /** sprint 가 끝날 때의 보고 재료: 전역 틱으로 센 진행 틱 수와 시작부터의 벽시계 시간. */
    public record SprintResult(long ticks, long elapsedNanos) {}

    public boolean sprinting() { return sprinting; }

    /** region 이 보는 sprint 세대(바뀌었으면 sprinting 이면 sprintTicks 로, 아니면 0 으로 자기 남은 틱을 다시 채운다). */
    public int sprintGeneration() { return sprintGeneration; }

    public int sprintTicks() { return sprintTicks; }

    public long sprintGlobalLeft() { return sprintGlobalLeft; }

    /** 전역이 이번 sprint 에서 시작한 틱 수(요청 − 남은 수). region 은 자기 진행 틱이 이것을 넘으면 기다린다(lockstep). */
    public long sprintGlobalDone() { return sprintTicks - sprintGlobalLeft; }

    /**
     * 바닐라 ServerTickRateManager.requestGameToSprint: 지금 정지 상태를 기억하고 정지를 풀어(진행 중인 step 은 그대로) ticks 만큼 sprint.
     * 이미 sprint 중이었으면 참(앞의 sprint 는 보고 없이 버린다).
     */
    public synchronized boolean startSprint(int ticks, long nowNanos) {
        boolean was = sprinting;
        sprintTicks = ticks;
        sprintGlobalLeft = ticks;
        sprintStartNanos = nowNanos;
        sprintWasFrozen = frozen;
        frozen = false;
        sprinting = true;
        sprintGeneration++;
        return was;
    }

    /** 전역 틱 하나를 sprint 틱으로 쓴다(전역 스레드). */
    public void takeSprintGlobalTick() { if (sprintGlobalLeft > 0) sprintGlobalLeft--; }

    /** sprint 를 끝낸다(다 돌았거나 stop): 남은 region 틱을 거두고 정지 상태를 되돌린다. sprint 중이 아니었으면 null. */
    public synchronized SprintResult endSprint(long nowNanos) {
        if (!sprinting) return null;
        SprintResult r = new SprintResult(sprintTicks - sprintGlobalLeft, nowNanos - sprintStartNanos);
        sprinting = false;
        sprintGlobalLeft = 0;
        sprintGeneration++;
        frozen = sprintWasFrozen;
        return r;
    }

    // ------------------------------------------------------------------ 틱 시간

    /** region 틱 하나가 걸린 시간. 평균은 바닐라처럼 0.8 / 0.2 지수 평균. */
    public synchronized void record(long nanos) {
        samples[nextSample] = nanos;
        nextSample = (nextSample + 1) % SAMPLE_COUNT;
        averageNanos = averageNanos * 0.8 + nanos * 0.2F;
    }

    public long averageTickNanos() { return (long) averageNanos; }

    /** 표본 사본(길이 100, 아직 안 찬 칸은 0). */
    public synchronized long[] sampleCopy() { return samples.clone(); }
}
