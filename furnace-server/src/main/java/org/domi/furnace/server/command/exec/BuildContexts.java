// 바닐라 BuildContexts(hy) — 수정자 단계를 돌며 소스 목록을 만들고, 실행 노드를 소스마다 큐에 넣는다.
// 예산: 수정자 단계마다 1(소스 수와 무관). 포크 한도: 누적 소스 수 + 새 소스 수 >= 한도면 command.forkLimit(실제 최대 한도-1).
package org.domi.furnace.server.command.exec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.brigadier.AsyncRedirectModifier;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.ContextChain;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.RedirectModifier;

public class BuildContexts {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BuildContexts.class);

    public static final DynamicCommandExceptionType ERROR_FORK_LIMIT_REACHED =
            new DynamicCommandExceptionType(limit -> Text.translatable("command.forkLimit", limit));

    private final String commandInput;
    private final ContextChain<CommandSource> command;

    public BuildContexts(String commandInput, ContextChain<CommandSource> command) {
        this.commandInput = commandInput;
        this.command = command;
    }

    protected void execute(CommandSource originalSource, List<CommandSource> initialSources, ExecutionContext context, Frame frame,
                           ChainModifiers initialModifiers) {
        runStages(originalSource, command, initialModifiers, initialSources, context, frame);
    }

    /** 수정자 단계를 돌고 실행 노드를 큐에 넣는다. 비동기 수정자가 미완료면 거기서 멈췄다가 {@link AsyncStage} 가 다음 단계부터 이어 부른다. */
    private void runStages(CommandSource originalSource, ContextChain<CommandSource> stage, ChainModifiers initialModifiers,
                           List<CommandSource> initialSources, ExecutionContext context, Frame frame) {
        ContextChain<CommandSource> currentStage = stage;
        ChainModifiers modifiers = initialModifiers;
        List<CommandSource> currentSources = initialSources;
        if (currentStage.getStage() != ContextChain.Stage.EXECUTE) {
            int forkLimit = context.forkLimit();
            for (; currentStage.getStage() != ContextChain.Stage.EXECUTE; currentStage = currentStage.nextStage()) {
                CommandContext<CommandSource> modifier = currentStage.getTopContext();
                if (modifier.isForked()) modifiers = modifiers.setForked();
                RedirectModifier<CommandSource> modifierFn = modifier.getRedirectModifier();
                if (modifierFn instanceof CustomModifierExecutor custom) {
                    custom.apply(originalSource, currentSources, currentStage, modifiers, ExecutionControl.create(context, frame));
                    return;
                }
                if (modifierFn instanceof AsyncRedirectModifier<CommandSource> async) {
                    context.incrementCost();
                    new AsyncStage(originalSource, currentStage, modifier, async, modifiers, currentSources, context, frame).step();
                    return;
                }
                if (modifierFn != null) {
                    context.incrementCost();
                    boolean forkedMode = modifiers.isForked();
                    List<CommandSource> nextSources = new ArrayList<>();
                    for (CommandSource source : currentSources) {
                        try {
                            Collection<CommandSource> newSources = ContextChain.runModifier(modifier, source, (c, s, r) -> {}, forkedMode);
                            if (nextSources.size() + newSources.size() >= forkLimit) {
                                originalSource.handleError(ERROR_FORK_LIMIT_REACHED.create(forkLimit), forkedMode);
                                return;
                            }
                            nextSources.addAll(newSources);
                        } catch (CommandSyntaxException e) {
                            source.handleError(e, forkedMode);
                            if (!forkedMode) return;
                        }
                    }
                    currentSources = nextSources;
                }
            }
        }
        if (currentSources.isEmpty()) {
            if (modifiers.isReturn()) context.queueNext(new CommandQueueEntry(frame, FallthroughTask.INSTANCE));
            return;
        }
        CommandContext<CommandSource> executable = currentStage.getTopContext();
        if (executable.getCommand() instanceof CustomCommandExecutor custom) {
            ExecutionControl control = ExecutionControl.create(context, frame);
            for (CommandSource source : currentSources) custom.run(source, currentStage, modifiers, control);
        } else {
            if (modifiers.isReturn()) {
                CommandSource returningSource = currentSources.get(0);
                returningSource = returningSource.withCallback(ResultCallback.chain(returningSource.callback(), frame.returnValueConsumer()));
                currentSources = List.of(returningSource);
            }
            ExecuteCommand action = new ExecuteCommand(commandInput, modifiers, executable);
            ContinuationTask.schedule(context, frame, currentSources, (f, s) -> new CommandQueueEntry(f, action.bind(s)));
        }
    }

    /**
     * 비동기 수정자 한 단계: 소스를 순서대로 하나씩 적용하다 미완료 future 를 만나면 큐를 멈추고, 완료 때 전역 스레드에서 같은 자리부터 이어 간다.
     * 결과 순서는 소스 순서를 유지한다. 오류·포크 한도 규칙은 동기 수정자와 같다(비포크 오류는 체인 중단).
     */
    private final class AsyncStage {
        private final CommandSource originalSource;
        private final ContextChain<CommandSource> stage;
        private final CommandContext<CommandSource> modifier;
        private final AsyncRedirectModifier<CommandSource> fn;
        private final ChainModifiers modifiers;
        private final List<CommandSource> sources;
        private final ExecutionContext context;
        private final Frame frame;
        private final boolean forkedMode;
        private final List<CommandSource> nextSources = new ArrayList<>();
        private int index;

        AsyncStage(CommandSource originalSource, ContextChain<CommandSource> stage, CommandContext<CommandSource> modifier,
                   AsyncRedirectModifier<CommandSource> fn, ChainModifiers modifiers, List<CommandSource> sources, ExecutionContext context, Frame frame) {
            this.originalSource = originalSource;
            this.stage = stage;
            this.modifier = modifier;
            this.fn = fn;
            this.modifiers = modifiers;
            this.sources = sources;
            this.context = context;
            this.frame = frame;
            this.forkedMode = modifiers.isForked();
        }

        void step() {
            while (index < sources.size()) {
                CommandSource source = sources.get(index);
                CompletableFuture<Collection<CommandSource>> f;
                try {
                    f = fn.applyAsync(modifier.copyFor(source));
                } catch (CommandSyntaxException e) {
                    source.handleError(e, forkedMode);
                    if (!forkedMode) return;
                    index++;
                    continue;
                } catch (RuntimeException e) {
                    EntityOps.reportFailure(source, e);
                    return;
                }
                if (!f.isDone()) {
                    final CommandSource waiting = source;
                    context.suspend(f, (v, ex) -> {
                        if (accept(waiting, v, ex)) step();
                    });
                    return;
                }
                Collection<CommandSource> v = null;
                Throwable ex = null;
                try {
                    v = f.join();
                } catch (RuntimeException e) {
                    ex = e;
                }
                if (!accept(source, v, ex)) return;
            }
            runStages(originalSource, stage.nextStage(), modifiers, nextSources, context, frame);
        }

        /** 소스 하나의 결과를 받는다. 이어 가도 되면 true(index 전진), 체인을 멈춰야 하면 false. */
        private boolean accept(CommandSource source, Collection<CommandSource> v, Throwable ex) {
            if (ex != null) {
                Throwable t = EntityOps.unwrap(ex);
                if (t instanceof CommandSyntaxException cse) {
                    source.handleError(cse, forkedMode);
                } else if (t instanceof TimeoutException) {
                    log.warn("수정자 결과 대기 시간 초과: /{}", commandInput);
                    if (!forkedMode) source.sendFailure(Text.translatable("command.failed"));
                } else {
                    EntityOps.reportFailure(source, t);
                }
                if (!forkedMode) return false;
                index++;
                return true;
            }
            int forkLimit = context.forkLimit();
            if (nextSources.size() + v.size() >= forkLimit) {
                originalSource.handleError(ERROR_FORK_LIMIT_REACHED.create(forkLimit), forkedMode);
                return false;
            }
            nextSources.addAll(v);
            index++;
            return true;
        }
    }

    @Override
    public String toString() { return commandInput; }

    /** 바닐라 hy.a(Continuation): 이미 정해진 소스 목록과 수정자로 이어 간다. */
    public static final class Continuation extends BuildContexts implements EntryAction {
        private final ChainModifiers modifiers;
        private final CommandSource originalSource;
        private final List<CommandSource> sources;

        public Continuation(String commandInput, ContextChain<CommandSource> command, ChainModifiers modifiers, CommandSource originalSource,
                            List<CommandSource> sources) {
            super(commandInput, command);
            this.originalSource = originalSource;
            this.sources = sources;
            this.modifiers = modifiers;
        }

        @Override
        public void execute(ExecutionContext context, Frame frame) { execute(originalSource, sources, context, frame, modifiers); }
    }

    /** 바닐라 hy.b(TopLevel): 최상위 명령. */
    public static final class TopLevel extends BuildContexts implements EntryAction {
        private final CommandSource source;

        public TopLevel(String commandInput, ContextChain<CommandSource> command, CommandSource source) {
            super(commandInput, command);
            this.source = source;
        }

        @Override
        public void execute(ExecutionContext context, Frame frame) { execute(source, List.of(source), context, frame, ChainModifiers.DEFAULT); }
    }

    /** 바닐라 hy.c(Unbound): 함수 줄처럼 소스가 나중에 묶이는 명령. */
    public static final class Unbound extends BuildContexts implements UnboundEntryAction {
        public Unbound(String commandInput, ContextChain<CommandSource> command) { super(commandInput, command); }

        @Override
        public void execute(CommandSource source, ExecutionContext context, Frame frame) {
            execute(source, List.of(source), context, frame, ChainModifiers.DEFAULT);
        }
    }
}
