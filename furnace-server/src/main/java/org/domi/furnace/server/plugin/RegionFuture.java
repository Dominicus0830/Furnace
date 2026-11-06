// 플러그인 API 가 돌려주는 Future. 서버 스레드(region·전역)에서 미완료 상태로 get()/join() 하면 데드락이 되므로 거부한다.
// (다른 region 을 기다리는 동안 이쪽 region 도 못 돌고, 상대가 이쪽에 일을 맡기면 서로 기다린다.) thenAccept 등으로 잇는다.
package org.domi.furnace.server.plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class RegionFuture<T> extends CompletableFuture<T> {

    /** src 의 결과를 옮겨 받는 가드 Future. */
    public static <T> RegionFuture<T> wrap(CompletableFuture<T> src) {
        if (src instanceof RegionFuture<T> r) return r;
        RegionFuture<T> out = new RegionFuture<>();
        src.whenComplete((v, e) -> {
            if (e != null) out.completeExceptionally(e);
            else out.complete(v);
        });
        return out;
    }

    /** 서버 스레드(region 풀·전역 스레드)인가 — 스레드 이름 규약(furnace-region*, furnace-global)으로 판별한다. */
    static boolean onServerThread() {
        String n = Thread.currentThread().getName();
        return n.startsWith("furnace-region") || n.startsWith("furnace-global");
    }

    private void guard() {
        if (!isDone() && onServerThread()) {
            throw new IllegalStateException("서버 스레드(" + Thread.currentThread().getName() + ")에서 미완료 Future 를 get()/join() 으로 기다릴 수 없다 — thenAccept 로 잇는다");
        }
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        guard();
        return super.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        guard();
        return super.get(timeout, unit);
    }

    @Override
    public T join() {
        guard();
        return super.join();
    }

    @Override
    public <U> CompletableFuture<U> newIncompleteFuture() { return new RegionFuture<>(); }
}
