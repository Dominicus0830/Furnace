// 바닐라 CustomCommandExecutor(hp) — 실행 노드가 큐를 직접 다루는 명령(function·return). BuildContexts 가 소스마다 직접 부른다.
package org.domi.furnace.server.command.exec;

import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.Command;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.ContextChain;

public interface CustomCommandExecutor {

    void run(CommandSource source, ContextChain<CommandSource> chain, ChainModifiers modifiers, ExecutionControl control);

    /** 트리 노드의 executes 에 붙이는 어댑터. 보통 실행 경로로는 불리지 않는다. */
    interface CommandAdapter extends Command<CommandSource>, CustomCommandExecutor {
        @Override
        default int run(CommandContext<CommandSource> context) throws CommandSyntaxException {
            throw new UnsupportedOperationException("This function should not run");
        }
    }

    /** 바닐라 hp.b: 문법 오류는 handleError 후 소스 콜백 onFailure. */
    abstract class WithErrorHandling implements CustomCommandExecutor {
        @Override
        public final void run(CommandSource source, ContextChain<CommandSource> chain, ChainModifiers modifiers, ExecutionControl control) {
            try {
                runGuarded(source, chain, modifiers, control);
            } catch (CommandSyntaxException e) {
                onError(e, source, modifiers, control);
                source.callback().onFailure();
            }
        }

        protected void onError(CommandSyntaxException e, CommandSource source, ChainModifiers modifiers, ExecutionControl control) {
            source.handleError(e, modifiers.isForked(), control.tracer());
        }

        protected abstract void runGuarded(CommandSource source, ContextChain<CommandSource> chain, ChainModifiers modifiers,
                                           ExecutionControl control) throws CommandSyntaxException;
    }
}
