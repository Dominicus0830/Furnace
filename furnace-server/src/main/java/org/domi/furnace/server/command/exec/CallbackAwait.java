// Furnace 전용: 결과 콜백 future(store block/entity 처럼 region 왕복일 수 있다)가 끝난 뒤 다음 일을 한다.
// 이미 끝났으면 그 자리에서, 아니면 컨텍스트를 멈췄다가 전역 스레드에서 이어 간다(바닐라는 콜백이 동기라 순서만 지키면 된다).
package org.domi.furnace.server.command.exec;

import java.util.concurrent.CompletableFuture;
import org.domi.furnace.server.command.EntityOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CallbackAwait {

    private static final Logger log = LoggerFactory.getLogger(CallbackAwait.class);

    private CallbackAwait() {}

    public static void then(ExecutionContext context, CompletableFuture<Void> callback, Runnable next) {
        if (callback.isDone()) {
            if (callback.isCompletedExceptionally()) {
                try {
                    callback.join();
                } catch (RuntimeException e) {
                    log.warn("명령 결과 콜백 실패", EntityOps.unwrap(e));
                }
            }
            next.run();
            return;
        }
        context.suspend(callback, (v, ex) -> {
            if (ex != null) log.warn("명령 결과 콜백 실패", EntityOps.unwrap(ex));
            next.run();
        });
    }
}
