// 바닐라 CustomModifierExecutor(hq) — 수정자 단계가 큐를 직접 다루는 경우(return run·execute if function).
package org.domi.furnace.server.command.exec;

import java.util.Collection;
import java.util.List;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.ContextChain;
import org.domi.furnace.server.command.brigadier.RedirectModifier;

public interface CustomModifierExecutor {

    void apply(CommandSource originalSource, List<CommandSource> currentSources, ContextChain<CommandSource> currentStep,
               ChainModifiers modifiers, ExecutionControl control);

    interface ModifierAdapter extends RedirectModifier<CommandSource>, CustomModifierExecutor {
        @Override
        default Collection<CommandSource> apply(CommandContext<CommandSource> context) throws CommandSyntaxException {
            throw new UnsupportedOperationException("This function should not run");
        }
    }
}
