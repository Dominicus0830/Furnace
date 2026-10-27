// 명령 실행 future: 전역 스레드에서 join/get 하면 자기 자신을 기다리는 데드락이므로 assert 로 막는다(설계 §1.5).
// region 스레드도 블로킹 대기를 하지 않는다(scheduler 규칙 6) — 같이 막는다. 기다릴 수 있는 곳은 테스트·플러그인 비동기 스레드뿐이다.
package org.domi.furnace.server.command.exec;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.domi.furnace.api.concurrent.ThreadOwner;

public final class CommandFuture<T> extends CompletableFuture<T> {

    private final ThreadOwner global;

    public CommandFuture(ThreadOwner global) { this.global = global; }

    private void checkNotGlobal() {
        assert !global.isOwnerThread() : "전역 스레드에서 명령 future 를 기다리면 데드락이다";
        assert !org.domi.furnace.server.sched.RegionThread.isRegionThread() : "region 스레드에서 명령 future 를 기다리지 않는다(scheduler 규칙 6)";
    }

    @Override
    public T join() {
        checkNotGlobal();
        return super.join();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        checkNotGlobal();
        return super.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        checkNotGlobal();
        return super.get(timeout, unit);
    }

    @Override
    public <U> CompletableFuture<U> newIncompleteFuture() { return new CommandFuture<>(global); }
}
