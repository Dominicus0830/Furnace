// 바닐라 ExecuteCommand(ib) — 실행 노드를 소스 하나로 돌리고 결과를 소스 콜백으로 보낸다.
// Furnace 지연 결과 어댑터: 명령 본문이 source.defer(future) 를 불렀으면 큐를 멈추고, 완료 시 전역 스레드에서 콜백·메시지를 처리한다.
package org.domi.furnace.server.command.exec;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.ContextChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExecuteCommand implements UnboundEntryAction {

    private static final Logger log = LoggerFactory.getLogger(ExecuteCommand.class);

    private static final Runnable NO_OP = () -> { };

    private final String commandInput;
    private final ChainModifiers modifiers;
    private final CommandContext<CommandSource> executionContext;

    public ExecuteCommand(String commandInput, ChainModifiers modifiers, CommandContext<CommandSource> executionContext) {
        this.commandInput = commandInput;
        this.modifiers = modifiers;
        this.executionContext = executionContext;
    }

    @Override
    public void execute(CommandSource sourceStack, ExecutionContext context, Frame frame) {
        boolean forked = modifiers.isForked();
        CommandSource source = sourceStack.withExecutionContext(context);
        context.incrementCost();
        CompletableFuture<Integer> deferred;
        try {
            // 바닐라 ContextChain.runExecutable(…, CommandResultCallback 로 보내는 소비자, forked). 지연 결과면 콜백은 완료 때로 미룬다.
            int[] value = new int[1];
            boolean[] ok = new boolean[1];
            ContextChain.runExecutable(executionContext, source, (c, success, result) -> {
                ok[0] = success;
                value[0] = result;
            }, forked);
            deferred = context.takeDeferred();
            if (deferred == null) {
                // 바닐라 순서: 소스 콜백(runExecutable 안) → 추적기 onReturn
                int result = value[0];
                awaitCallback(context, source.callback().onResult(ok[0], result), context.tracer() == null ? NO_OP : () -> traceReturn(context, frame, result));
                return;
            }
        } catch (CommandSyntaxException e) {
            // 비-forked 에서만 여기로 온다(forked 면 runExecutable 이 삼킨다) — 바닐라 handleError
            context.takeDeferred();
            awaitCallback(context, source.callback().onFailure(), () -> { });
            source.handleError(e, forked, context.tracer());
            return;
        } catch (RuntimeException e) {
            context.takeDeferred();
            throw e;
        }
        Deferred d = new Deferred(deferred, forked, commandInput);
        context.suspend(d.future(), (v, ex) -> onDeferredDone(context, frame, source, d, v, ex));
    }

    private void traceReturn(ExecutionContext context, Frame frame, int result) {
        TraceCallbacks tracer = context.tracer();
        if (tracer != null) tracer.onReturn(frame.depth(), commandInput, result);
    }

    /** forked 에서 runExecutable 이 오류를 삼킨 경우도 콜백은 (false, 0) 이다 — 위 소비자가 받는다. */
    private void onDeferredDone(ExecutionContext context, Frame frame, CommandSource source, Deferred d, Integer v, Throwable ex) {
        CompletableFuture<Void> cb;
        Runnable then = () -> { };
        if (ex == null) {
            int result = v == null ? 0 : v;
            cb = source.callback().onResult(true, result);
            then = () -> traceReturn(context, frame, result);
        } else {
            Throwable t = EntityOps.unwrap(ex);
            if (t instanceof CommandSyntaxException cse) {
                cb = source.callback().onFailure();
                if (context.tracer() != null) context.tracer().onError(cse.getRawMessage().plainText(java.util.Locale.US));
                if (!d.forked()) source.sendFailure(cse.getRawMessage());
            } else if (t instanceof TimeoutException) {
                cb = source.callback().onFailure();
                log.warn("명령 결과 대기 시간 초과: /{}", d.input());
                if (!d.forked()) source.sendFailure(Text.translatable("command.failed"));
            } else {
                EntityOps.reportFailure(source, t);
                cb = source.callback().onFailure();
            }
        }
        awaitCallback(context, cb, then);
    }

    /**
     * 콜백이 미완료 future 를 돌려주면(E4 store block 등) 그 완료까지 큐를 멈춘다.
     * 콜백의 실패(CommandSyntaxException 이 아닌 것 — 문법 오류는 store 콜백이 바닐라처럼 삼킨다)는 동기 콜백이 던진 예외와 같은 길로 보낸다:
     * 다시 던져 CommandQueue.drive 의 catch(바닐라 performCommand catch)가 command.failed 를 알리고 컨텍스트를 끝낸다.
     */
    private static void awaitCallback(ExecutionContext context, CompletableFuture<Void> cb, Runnable then) {
        if (cb.isDone()) {
            if (cb.isCompletedExceptionally()) rethrowCallbackFailure(cb);
            then.run();
            return;
        }
        context.suspend(cb, (v, ex) -> {
            if (ex != null) throw asRuntime(EntityOps.unwrap(ex));
            then.run();
        });
    }

    private static void rethrowCallbackFailure(CompletableFuture<Void> cb) {
        try {
            cb.join();
        } catch (RuntimeException e) {
            throw asRuntime(EntityOps.unwrap(e));
        }
    }

    private static RuntimeException asRuntime(Throwable t) {
        if (t instanceof RuntimeException re) return re;
        if (t instanceof Error err) throw err;
        return new RuntimeException("명령 결과 콜백 실패", t);
    }
}
