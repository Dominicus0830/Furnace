// Paper ScheduledTask(전역·region·엔티티·비동기 스케줄러가 돌려주는 손잡이)에 해당.
package org.domi.furnace.compat.sched;

import org.domi.furnace.api.plugin.FurnacePlugin;

public interface CompatTask {

    FurnacePlugin owner();

    boolean isRepeating();

    /** 취소됐는가(도는 중에 취소한 것도 포함). */
    boolean isCancelled();

    ExecutionState state();

    /** 취소한다. 도는 중인 실행은 끝까지 간다(반복이면 다음 실행부터 사라진다). */
    CancelledState cancel();

    /** Paper ScheduledTask.ExecutionState. */
    enum ExecutionState {
        /** 도는 중이 아니고 앞으로 돌 실행이 남았다. */
        IDLE,
        RUNNING,
        /** 한 번 작업이 끝났다. */
        FINISHED,
        CANCELLED,
        /** 도는 중에 취소됨 — 그 실행이 끝나면 CANCELLED. */
        CANCELLED_RUNNING
    }

    /** Paper ScheduledTask.CancelledState. */
    enum CancelledState {
        CANCELLED_BY_CALLER,
        CANCELLED_ALREADY,
        /** 한 번 작업이 지금 돌고 있어 취소할 수 없다. */
        RUNNING,
        ALREADY_EXECUTED,
        /** 반복 작업이 지금 돌고 있어 다음 실행부터 취소했다. */
        NEXT_RUNS_CANCELLED,
        NEXT_RUNS_CANCELLED_ALREADY
    }
}
