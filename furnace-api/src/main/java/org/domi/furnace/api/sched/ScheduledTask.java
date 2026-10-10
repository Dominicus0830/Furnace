// 예약·반복 작업 손잡이 — 취소하면 다음 실행부터 돌지 않는다.
package org.domi.furnace.api.sched;

public interface ScheduledTask {

    /** 취소(이미 도는 중인 실행은 끝까지 간다). 여러 번 불러도 된다. */
    void cancel();

    boolean isCancelled();
}
