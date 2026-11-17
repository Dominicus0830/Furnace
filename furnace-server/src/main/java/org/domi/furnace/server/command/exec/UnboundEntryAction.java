// 바닐라 UnboundEntryAction(hw) — 소스를 아직 받지 않은 항목. bind 로 소스를 묶어 EntryAction 이 된다.
package org.domi.furnace.server.command.exec;

import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.VanillaPort;

@VanillaPort("net.minecraft.commands.execution.UnboundEntryAction")
@FunctionalInterface
public interface UnboundEntryAction {
    void execute(CommandSource source, ExecutionContext context, Frame frame);

    default EntryAction bind(CommandSource source) { return (ctx, frame) -> execute(source, ctx, frame); }
}
