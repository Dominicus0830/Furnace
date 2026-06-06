// 고정 틱 간격을 유지하며 다음 틱 경계까지 남은 시간을 계산하는 드리프트 보정 타이머
package org.domi.furnace.server.sched;

public final class TickTimer {

    private final long tickNanos;
    private long nextTick;

    public TickTimer(long tickNanos) {
        this.tickNanos = tickNanos;
    }

    /** now 기준으로 첫 틱 경계를 한 틱 뒤로 잡는다. */
    public void start(long now) {
        nextTick = now + tickNanos;
    }

    /** now에서 다음 틱 경계까지 남은 나노초(음수면 0). 스케줄을 진행시키지 않음. */
    public long remaining(long now) {
        return Math.max(0, nextTick - now);
    }

    /**
     * 다음 틱 경계로 스케줄을 한 칸 진행. 너무 뒤처졌으면(now가 새 경계보다 뒤) 재동기해
     * 따라잡기 폭주(spiral of death)를 막는다.
     */
    public void advance(long now) {
        nextTick += tickNanos;
        if (nextTick < now) {
            nextTick = now + tickNanos;
        }
    }
}
