// 작업을 특정 owner 스레드에서 비동기 실행하는 스케줄러 공통 형태
package org.domi.furnace.api.sched;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface TaskScheduler {

    /** owner 스레드에서 task 실행. 완료 시 완료되는 future. */
    CompletableFuture<Void> execute(Runnable task);

    /** owner 스레드에서 task 실행 후 결과를 담은 future. */
    <T> CompletableFuture<T> submit(Supplier<T> task);

    /**
     * delayTicks 틱 후 owner 스레드에서 task 실행. delayTicks는 서버 틱 단위 지연이다(전역·region 모두 /tick rate 를 따른다 —
     * rate 5 면 20틱은 4초. 전역은 /tick freeze 중에도 세고, /tick sprint 중에는 sprint 틱마다 센다).
     * 0과 1은 다음 틱에 실행된다. owner 스레드가 밀리면 그만큼 늦게 실행된다.
     */
    CompletableFuture<Void> executeDelayed(Runnable task, long delayTicks);

    /**
     * initialDelayTicks 뒤부터 periodTicks 마다 owner 스레드에서 task 를 반복한다. 예외가 나도 반복은 이어진다(예외는 로그).
     * 한 번 실행이 끝난 뒤 다음 실행을 예약하므로 실행끼리 겹치지 않는다.
     */
    default ScheduledTask runAtFixedRate(Runnable task, long initialDelayTicks, long periodTicks) {
        if (periodTicks <= 0) throw new IllegalArgumentException("periodTicks 는 1 이상: " + periodTicks);
        Repeating r = new Repeating(this, task, periodTicks);
        executeDelayed(r, Math.max(0, initialDelayTicks));
        return r;
    }

    /** delayTicks 뒤 한 번 — 취소할 수 있는 손잡이를 돌려준다. */
    default ScheduledTask runLater(Runnable task, long delayTicks) {
        Once o = new Once(task);
        executeDelayed(o, Math.max(0, delayTicks));
        return o;
    }

    /** {@link #runAtFixedRate} 구현. */
    final class Repeating implements Runnable, ScheduledTask {
        private final TaskScheduler owner;
        private final Runnable task;
        private final long period;
        private volatile boolean cancelled;

        Repeating(TaskScheduler owner, Runnable task, long period) {
            this.owner = owner;
            this.task = task;
            this.period = period;
        }

        @Override
        public void run() {
            if (cancelled) return;
            try {
                task.run();
            } catch (Throwable t) {
                System.getLogger("Furnace").log(System.Logger.Level.ERROR, "반복 작업 실패", t);
            }
            if (!cancelled) owner.executeDelayed(this, period);
        }

        @Override
        public void cancel() { cancelled = true; }

        @Override
        public boolean isCancelled() { return cancelled; }
    }

    /** {@link #runLater} 구현. */
    final class Once implements Runnable, ScheduledTask {
        private final Runnable task;
        private volatile boolean cancelled;

        Once(Runnable task) { this.task = task; }

        @Override
        public void run() { if (!cancelled) task.run(); }

        @Override
        public void cancel() { cancelled = true; }

        @Override
        public boolean isCancelled() { return cancelled; }
    }
}
