// 바닐라 CommandResultCallback(eq) — 명령 결과(success, result)를 받는 콜백. 결과는 반환값이 아니라 이 콜백으로 흐른다.
// Furnace 차이: store block/entity 처럼 region 왕복이 필요한 콜백을 위해 완료 future 를 돌려준다(동기 콜백은 완료된 future).
// 미완료 future 를 돌려주는 콜백은 그 future 를 전역 스레드에서 완성해야 한다(chain 이 이어 부르는 다음 콜백도 그 스레드에서 돈다).
package org.domi.furnace.server.command.exec;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface ResultCallback {

    CompletableFuture<Void> DONE = CompletableFuture.completedFuture(null);

    ResultCallback EMPTY = new ResultCallback() {
        @Override
        public CompletableFuture<Void> onResult(boolean success, int result) { return DONE; }

        @Override
        public String toString() { return "<empty>"; }
    };

    CompletableFuture<Void> onResult(boolean success, int result);

    default CompletableFuture<Void> onSuccess(int result) { return onResult(true, result); }

    default CompletableFuture<Void> onFailure() { return onResult(false, 0); }

    /** 바닐라 chain: 둘 다 부른다(a 다음 b). 한쪽이 EMPTY 면 다른 쪽 그대로. */
    static ResultCallback chain(ResultCallback a, ResultCallback b) {
        if (a == EMPTY) return b;
        if (b == EMPTY) return a;
        return (success, result) -> {
            CompletableFuture<Void> fa = a.onResult(success, result);
            if (fa.isDone() && !fa.isCompletedExceptionally()) return b.onResult(success, result);
            // a 가 실패해도 b 는 부른다(바닐라는 동기라 늘 둘 다 불린다). a 의 예외는 b 뒤에 그대로 전한다
            return fa.handle((v, ex) -> ex).thenCompose(ex -> b.onResult(success, result).thenApply(v -> {
                if (ex != null) throw ex instanceof java.util.concurrent.CompletionException ce ? ce : new java.util.concurrent.CompletionException(ex);
                return null;
            }));
        };
    }

    /** 동기 콜백 도우미. */
    static ResultCallback sync(java.util.function.BiConsumer<Boolean, Integer> fn) {
        return (success, result) -> {
            fn.accept(success, result);
            return DONE;
        };
    }
}
