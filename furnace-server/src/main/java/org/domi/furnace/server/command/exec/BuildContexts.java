// 바닐라 BuildContexts(hy) — 수정자 단계를 돌며 소스 목록을 만들고, 실행 노드를 소스마다 큐에 넣는다.
// 예산: 수정자 단계마다 1(소스 수와 무관). 포크 한도: 누적 소스 수 + 새 소스 수 >= 한도면 command.forkLimit(실제 최대 한도-1).
package org.domi.furnace.server.command.exec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.ContextChain;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.RedirectModifier;

public class BuildContexts {

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
        ContextChain<CommandSource> currentStage = command;
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
