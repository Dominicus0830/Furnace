package org.bukkit.craftbukkit.scheduler;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.domi.furnace.compat.sched.LegacyTask;

/** callSyncMethod 의 결과(Paper 와 같은 이름·패키지 전용 — T-264). 기다림·취소 규칙은 스케줄러 core 의 future 가 정한다. */
class CraftFuture<T> extends CraftTask implements Future<T> {
    private final Future<T> result;

    CraftFuture(Future<T> result, LegacyTask task) {
        super(task);
        this.result = result;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) { return result.cancel(mayInterruptIfRunning); }

    /** BukkitTask·Future 둘 다: 작업이 취소됐는가(future 를 취소해도 작업이 취소된다). */
    @Override
    public boolean isCancelled() { return task.isCancelled() || result.isCancelled(); }

    @Override
    public boolean isDone() { return result.isDone(); }

    @Override
    public T get() throws InterruptedException, ExecutionException { return result.get(); }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException { return result.get(timeout, unit); }
}
