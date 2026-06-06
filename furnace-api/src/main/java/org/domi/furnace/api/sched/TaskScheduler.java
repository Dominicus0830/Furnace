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
     * delayTicks 틱 후 owner 스레드에서 task 실행. delayTicks는 서버 틱 단위 지연이다.
     * 0이면 다음 틱에 실행된다. owner 스레드가 밀리면 그만큼 늦게 실행된다.
     */
    CompletableFuture<Void> executeDelayed(Runnable task, long delayTicks);
}
