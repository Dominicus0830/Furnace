// Brigadier ContextChain — 리다이렉트 사슬을 "수정자 단계들 + 실행 노드" 로 평탄화하고 단계별로 진행한다.
package org.domi.furnace.server.command.brigadier;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ContextChain<S> {

    public enum Stage { MODIFY, EXECUTE }

    private final List<CommandContext<S>> modifiers;
    private final CommandContext<S> executable;
    private ContextChain<S> nextStageCache;

    public ContextChain(List<CommandContext<S>> modifiers, CommandContext<S> executable) {
        if (executable.getCommand() == null) throw new IllegalArgumentException("Last command in chain must be executable");
        this.modifiers = modifiers;
        this.executable = executable;
    }

    /** 사슬 끝이 실행 가능하지 않으면 빈 값. */
    public static <S> Optional<ContextChain<S>> tryFlatten(CommandContext<S> rootContext) {
        List<CommandContext<S>> modifiers = new java.util.ArrayList<>();
        CommandContext<S> current = rootContext;
        while (true) {
            CommandContext<S> child = current.getChild();
            if (child == null) {
                if (current.getCommand() == null) return Optional.empty();
                return Optional.of(new ContextChain<>(modifiers, current));
            }
            modifiers.add(current);
            current = child;
        }
    }

    public static <S> Collection<S> runModifier(CommandContext<S> modifier, S source, ResultConsumer<S> resultConsumer, boolean forkedMode)
            throws CommandSyntaxException {
        RedirectModifier<S> sourceModifier = modifier.getRedirectModifier();
        if (sourceModifier == null) return Collections.singleton(source);
        CommandContext<S> contextToUse = modifier.copyFor(source);
        try {
            return sourceModifier.apply(contextToUse);
        } catch (CommandSyntaxException ex) {
            resultConsumer.onCommandComplete(contextToUse, false, 0);
            if (forkedMode) return Collections.emptyList();
            throw ex;
        }
    }

    public static <S> int runExecutable(CommandContext<S> executable, S source, ResultConsumer<S> resultConsumer, boolean forkedMode)
            throws CommandSyntaxException {
        CommandContext<S> contextToUse = executable.copyFor(source);
        try {
            int result = executable.getCommand().run(contextToUse);
            resultConsumer.onCommandComplete(contextToUse, true, result);
            return forkedMode ? 1 : result;
        } catch (CommandSyntaxException ex) {
            resultConsumer.onCommandComplete(contextToUse, false, 0);
            if (forkedMode) return 0;
            throw ex;
        }
    }

    public Stage getStage() { return modifiers.isEmpty() ? Stage.EXECUTE : Stage.MODIFY; }

    public CommandContext<S> getTopContext() { return modifiers.isEmpty() ? executable : modifiers.get(0); }

    public ContextChain<S> nextStage() {
        int size = modifiers.size();
        if (size == 0) return null;
        if (nextStageCache == null) nextStageCache = new ContextChain<>(modifiers.subList(1, size), executable);
        return nextStageCache;
    }
}
