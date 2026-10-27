// 비동기 수정자(Furnace 확장) — 소스 하나에서 새 소스 목록을 future 로 돌려준다. region 에서 읽어야 하는 as/at/on 등이 쓴다.
// BuildContexts 가 미완료 future 를 만나면 큐를 멈췄다가 전역 스레드에서 이어 간다. 직접 dispatcher.execute 경로에서는 끝난 future 만 받는다.
package org.domi.furnace.server.command.brigadier;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface AsyncRedirectModifier<S> extends RedirectModifier<S> {

    CompletableFuture<Collection<S>> applyAsync(CommandContext<S> context) throws CommandSyntaxException;

    @Override
    default Collection<S> apply(CommandContext<S> context) throws CommandSyntaxException {
        CompletableFuture<Collection<S>> f = applyAsync(context);
        if (!f.isDone()) throw new IllegalStateException("비동기 수정자는 실행 코어(BuildContexts)로만 돌릴 수 있다");
        try {
            return f.join();
        } catch (java.util.concurrent.CompletionException e) {
            Throwable t = e.getCause();
            if (t instanceof CommandSyntaxException cse) throw cse;
            if (t instanceof RuntimeException re) throw re;
            throw e;
        }
    }
}
