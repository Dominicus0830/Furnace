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
                awaitCallback(context, source.callback().onResult(ok[0], value[0]));
                return;
            }
        } catch (CommandSyntaxException e) {
            // 비-forked 에서만 여기로 온다(forked 면 runExecutable 이 삼킨다) — 바닐라 handleError
            context.takeDeferred();
            awaitCallback(context, source.callback().onFailure());
            source.handleError(e, forked);
            return;
        } catch (RuntimeException e) {
            context.takeDeferred();
            throw e;
        }
        Deferred d = new Deferred(deferred, forked, commandInput);
        context.suspend(d.future(), (v, ex) -> onDeferredDone(context, source, d, v, ex));
    }

    /** forked 에서 runExecutable 이 오류를 삼킨 경우도 콜백은 (false, 0) 이다 — 위 소비자가 받는다. */
    private static void onDeferredDone(ExecutionContext context, CommandSource source, Deferred d, Integer v, Throwable ex) {
        CompletableFuture<Void> cb;
        if (ex == null) {
            cb = source.callback().onResult(true, v == null ? 0 : v);
        } else {
            Throwable t = EntityOps.unwrap(ex);
            if (t instanceof CommandSyntaxException cse) {
                cb = source.callback().onFailure();
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
        awaitCallback(context, cb);
    }

    /** 콜백이 미완료 future 를 돌려주면(E4 store block 등) 그 완료까지 큐를 멈춘다. */
    private static void awaitCallback(ExecutionContext context, CompletableFuture<Void> cb) {
        if (cb.isDone()) {
            if (cb.isCompletedExceptionally()) logCallbackFailure(cb);
            return;
        }
        context.suspend(cb, (v, ex) -> {
            if (ex != null) log.warn("명령 결과 콜백 실패", EntityOps.unwrap(ex));
        });
    }

    private static void logCallbackFailure(CompletableFuture<Void> cb) {
        try {
            cb.join();
        } catch (RuntimeException e) {
            log.warn("명령 결과 콜백 실패", EntityOps.unwrap(e));
        }
    }
}
