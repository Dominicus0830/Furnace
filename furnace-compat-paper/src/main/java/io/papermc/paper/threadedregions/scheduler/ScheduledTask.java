package io.papermc.paper.threadedregions.scheduler;

import org.bukkit.plugin.Plugin;

/** Paper(Folia) 스케줄러가 돌려주는 작업 손잡이. 호환 계층 구현은 org.domi.furnace.compat.paper.CompatPaperSchedulers. */
public interface ScheduledTask {

    Plugin getOwningPlugin();

    boolean isRepeatingTask();

    /** 취소한다. 도는 중인 한 번짜리는 취소할 수 없고(RUNNING), 반복이면 다음 실행부터 사라진다. */
    CancelledState cancel();

    ExecutionState getExecutionState();

    default boolean isCancelled() {
        ExecutionState state = getExecutionState();
        return state == ExecutionState.CANCELLED || state == ExecutionState.CANCELLED_RUNNING;
    }

    enum CancelledState {
        CANCELLED_BY_CALLER,
        CANCELLED_ALREADY,
        RUNNING,
        ALREADY_EXECUTED,
        NEXT_RUNS_CANCELLED,
        NEXT_RUNS_CANCELLED_ALREADY,
    }

    enum ExecutionState {
        IDLE,
        RUNNING,
        FINISHED,
        CANCELLED,
        CANCELLED_RUNNING;
    }
}
