// 바닐라 EntryAction(hr) — 실행 큐 항목 하나가 하는 일.
package org.domi.furnace.server.command.exec;

import org.domi.furnace.server.VanillaPort;

@VanillaPort("net.minecraft.commands.execution.EntryAction")
@FunctionalInterface
public interface EntryAction {
    void execute(ExecutionContext context, Frame frame);
}
